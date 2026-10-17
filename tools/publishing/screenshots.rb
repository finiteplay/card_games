#!/usr/bin/env ruby
# Plans or applies the localized phone screenshots on Google Play, from brand/google-play/<game>/screenshots-localized/<language>/.
#
#   ruby tools/publishing/screenshots.rb plan  [game ...] [--languages a,b]   read-only: what would be deleted and uploaded
#   ruby tools/publishing/screenshots.rb apply [game ...] [--languages a,b]   saves, and submits for review automatically
#
# The language folders use Play's language codes (en-US, iw-IL, no-NO, ...). Each language gets exactly the files in its
# screenshot-order.txt, in that order, replacing whatever phone screenshots the listing has for that language. A language whose
# images already match (same SHA-256, same order) is left alone, so a run that stopped part way is simply run again.
# Apply saves in batches of CHUNK languages, one Play edit each, so a transient Play error (503) loses at most one batch; every
# call is retried with a back-off first. Needs the existing publishing account (fastlane/Appfile); credentials are never
# printed. Apply only after the plan has been read and approved (brand/google-play/AGENTS.md).
require 'json'
require 'digest'
require 'google/apis/androidpublisher_v3'
require 'googleauth'
$stdout.sync = true

ROOT = File.expand_path('../..', __dir__)
GAMES = %w[klondike spider freecell blackjack holdem].freeze
CHUNK = 6
mode = ARGV.shift
abort 'Mode must be plan or apply' unless %w[plan apply].include?(mode)
languages_filter = nil
args = []
while (arg = ARGV.shift)
  if arg == '--languages'
    languages_filter = ARGV.shift.to_s.split(',')
  else
    args << arg
  end
end
games = args.empty? ? GAMES : args
abort 'Unknown game' unless (games - GAMES).empty?

appfile_path = File.join(ROOT, 'fastlane/Appfile')
config = Object.new
config.define_singleton_method(:json_key) { |value| @publishing_key_path = value }
config.define_singleton_method(:json_key_file) { |value| @publishing_key_path = value }
config.instance_eval(File.read(appfile_path), appfile_path)
key_path = config.instance_variable_get(:@publishing_key_path)
abort 'Publishing credential configuration not found' unless key_path
service = Google::Apis::AndroidpublisherV3::AndroidPublisherService.new
service.authorization = Google::Auth::ServiceAccountCredentials.make_creds(
  json_key_io: File.open(key_path),
  scope: 'https://www.googleapis.com/auth/androidpublisher'
)

# Play answers 503 now and then; a call that fails that way is repeated after a pause. Anything else is raised at once.
def retrying(what)
  attempts = 0
  begin
    yield
  rescue Google::Apis::ServerError, Google::Apis::TransmissionError => error
    attempts += 1
    raise if attempts >= 6
    pause = [5 * (2**(attempts - 1)), 60].min
    warn JSON.generate(retry: what, attempt: attempts, error_class: error.class.name, wait_seconds: pause)
    sleep pause
    retry
  end
end

def desired_files(game, language)
  dir = File.join(ROOT, 'brand/google-play', game, 'screenshots-localized', language)
  order = File.join(dir, 'screenshot-order.txt')
  abort "Missing #{order}" unless File.exist?(order)
  File.readlines(order, chomp: true).reject(&:empty?).map do |name|
    abort 'Unsafe screenshot path' unless File.basename(name) == name
    path = File.join(dir, name)
    abort "Missing #{path}" unless File.exist?(path)
    path
  end
end

totals = { languages: 0, unchanged: 0, deleted: 0, uploaded: 0, saved_batches: 0 }
games.each do |game|
  package = "org.finiteplay.#{game}"
  folders = Dir[File.join(ROOT, 'brand/google-play', game, 'screenshots-localized', '*')].select { |p| File.directory?(p) }
  languages = folders.map { |p| File.basename(p) }.sort
  languages &= languages_filter if languages_filter
  languages.each_slice(CHUNK) do |slice|
    edit = nil
    committed = false
    batch_attempts = 0
    begin
      edit = retrying('insert_edit') { service.insert_edit(package, Google::Apis::AndroidpublisherV3::AppEdit.new) }
      changed = 0
      slice.each do |language|
        paths = desired_files(game, language)
        hashes = paths.map { |p| Digest::SHA256.file(p).hexdigest }
        current = retrying('list') { service.list_edit_images(package, edit.id, language, 'phoneScreenshots').images || [] }
        totals[:languages] += 1
        if current.map(&:sha256) == hashes
          totals[:unchanged] += 1
          puts JSON.generate(game: game, language: language, action: 'unchanged', images: hashes.length)
          next
        end
        puts JSON.generate(game: game, language: language, action: mode == 'plan' ? 'would replace' : 'replacing',
                           delete: current.length, upload: paths.length)
        totals[:deleted] += current.length
        totals[:uploaded] += paths.length
        next if mode == 'plan'
        retrying('delete') { service.deleteall_edit_image(package, edit.id, language, 'phoneScreenshots') }
        paths.each do |path|
          retrying("upload #{language}") do
            service.upload_edit_image(package, edit.id, language, 'phoneScreenshots', upload_source: path, content_type: 'image/png')
          end
        end
        actual = retrying('list') { service.list_edit_images(package, edit.id, language, 'phoneScreenshots').images.map(&:sha256) }
        raise "Uploaded screenshot order differs for #{language}" unless actual == hashes
        changed += 1
      end
      if mode == 'apply' && changed.positive?
        retrying('validate') { service.validate_edit(package, edit.id) }
        retrying('commit') { service.commit_edit(package, edit.id) }
        committed = true
        totals[:saved_batches] += 1
        puts JSON.generate(game: game, saved: true, languages: slice, automatic_review_submission: true)
      end
    rescue StandardError => error
      api_message = begin
        JSON.parse(error.body).dig('error', 'message') if error.respond_to?(:body)
      rescue JSON::ParserError
        nil
      end
      # Play occasionally drops an edit that is still open; nothing in it was saved, so the batch is simply started again.
      if api_message.to_s.include?('has been deleted') && (batch_attempts += 1) <= 3
        warn JSON.generate(game: game, languages: slice, restarting_batch: batch_attempts)
        edit = nil
        retry
      end
      # Never print request headers, authentication objects, or raw responses.
      warn JSON.generate(game: game, languages: slice, error_class: error.class.name,
                         status: error.respond_to?(:status_code) ? error.status_code : nil, api_message: api_message,
                         note: 'this batch was not saved; run again to continue')
      puts JSON.generate(mode: mode, totals: totals, stopped: true)
      exit 1
    ensure
      service.delete_edit(package, edit.id) if edit && !committed
    end
  end
end
puts JSON.generate(mode: mode, totals: totals)
