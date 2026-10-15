#!/usr/bin/env ruby
# Read or update listing text using the existing publishing account; never uploads a bundle.
require 'json'
require 'digest'
require 'fileutils'
require 'google/apis/androidpublisher_v3'
require 'googleauth'
$stdout.sync = true

ROOT = File.expand_path('../..', __dir__)
mode = ARGV.shift || 'inspect'
abort 'Mode must be inspect, update, or export' unless %w[inspect update export].include?(mode)
games = ARGV.empty? ? %w[klondike spider freecell blackjack holdem] : ARGV
abort 'Unknown game' unless (games - %w[klondike spider freecell blackjack holdem]).empty?

def canonical_listings(game)
  source = File.read(File.join(ROOT, 'brand/google-play', game, 'listing.md'))
  title = source[/\*\*Title(?:\s*\([^\n]*\))?:\*\*\s*([^\n]+)/, 1]
  short = source[/\*\*Short description(?:\s*\([^\n]*\))?:\*\*\s*([^\n]+)/, 1]
  full = source.split('**Full description:**', 2).fetch(1).split(/^## /, 2).first.strip
  result = { 'en-US' => { 'title' => title, 'short-description' => short, 'full-description' => full } }
  JSON.parse(File.read(File.join(ROOT, 'brand/google-play/localizations.json'))).each do |language, family|
    text = family.fetch('games').fetch(game)
    result[language] = { 'title' => text.fetch('title'), 'short-description' => text.fetch('shortDescription'),
      'full-description' => [text.fetch('intro'), family.fetch('promise'), text.fetch('body'), family.fetch('rest'), family.fetch('footer')].join("\n\n") }
  end
  result.each do |language, fields|
    { 'title' => 30, 'short-description' => 80, 'full-description' => 4000 }.each do |field, limit|
      raise "Invalid #{game} #{language} #{field}" unless fields[field] && !fields[field].empty? && fields[field].length <= limit
    end
  end
  result
end

# Generated publishing metadata is a mechanical export of the reviewed local sources.
# No credentials or network access are needed for this mode.
if mode == 'export'
  games.each do |game|
    canonical_listings(game).each do |language, fields|
      directory = File.join(ROOT, 'games', game, 'app/src/main/play/listings', language)
      FileUtils.mkdir_p(directory)
      fields.each { |field, value| File.write(File.join(directory, "#{field}.txt"), value + "\n") }
      puts JSON.generate(game: game, language: language, exported: true,
                         lengths: fields.transform_values(&:length))
    end
  end
  exit
end

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

games.each do |game|
  abort 'Unknown game' unless %w[klondike spider freecell blackjack holdem].include?(game)
  package = "org.finiteplay.#{game}"
  edit = nil
  committed = false
  begin
    edit = service.insert_edit(package, Google::Apis::AndroidpublisherV3::AppEdit.new)
    current = service.list_edit_listings(package, edit.id).listings || []
    images = service.list_edit_images(package, edit.id, 'en-US', 'phoneScreenshots').images || []
    if mode == 'inspect'
      local_images = Dir[File.join(ROOT, 'brand/google-play', game, 'screenshots', '*.png')].map do |path|
        { name: File.basename(path), sha256: Digest::SHA256.file(path).hexdigest }
      end
      puts JSON.generate(game: game, listings: current.map { |x| JSON.parse(x.to_json) },
                         screenshots: images.map { |x| { id: x.id, sha256: x.sha256 } },
                         local_images: local_images)
    else
      text_by_language = canonical_listings(game)
      title, short, full = text_by_language.fetch('en-US').values_at('title', 'short-description', 'full-description')
      text_by_language.each do |language, fields|
        localized = Google::Apis::AndroidpublisherV3::Listing.new(title: fields['title'], short_description: fields['short-description'],
                                                                full_description: fields['full-description'], language: language)
        service.update_edit_listing(package, edit.id, language, localized)
      end
      order_path = File.join(ROOT, 'brand/google-play', game, 'screenshot-order.txt')
      if File.exist?(order_path)
        desired_paths = File.readlines(order_path, chomp: true).reject(&:empty?).map do |name|
          raise 'Unsafe screenshot path' unless File.basename(name) == name
          File.join(ROOT, 'brand/google-play', game, 'screenshots', name)
        end
        desired_hashes = desired_paths.map { |path| Digest::SHA256.file(path).hexdigest }
        # Preserve every current asset. Any new screenshots must be explicitly listed locally.
        raise 'Screenshot order would drop an existing asset' unless (images.map(&:sha256) - desired_hashes).empty?
        unless images.map(&:sha256) == desired_hashes
          images.each { |image| service.delete_edit_image(package, edit.id, 'en-US', 'phoneScreenshots', image.id) }
          desired_paths.each do |path|
            service.upload_edit_image(package, edit.id, 'en-US', 'phoneScreenshots',
                                      upload_source: path, content_type: 'image/png')
          end
          actual_hashes = service.list_edit_images(package, edit.id, 'en-US', 'phoneScreenshots').images.map(&:sha256)
          raise 'Uploaded screenshot order differs' unless actual_hashes == desired_hashes
        end
      end
      service.validate_edit(package, edit.id)
      service.commit_edit(package, edit.id)
      committed = true
      puts JSON.generate(game: game, saved: true, automatic_review_submission: true,
                         title: title, short_length: short.length, full_length: full.length)
    end
  rescue StandardError => error
    api_message = begin
      JSON.parse(error.body).dig('error', 'message') if error.respond_to?(:body)
    rescue JSON::ParserError
      nil
    end
    # Never print request headers, authentication objects, or raw responses.
    warn JSON.generate(game: game, error_class: error.class.name, status: error.respond_to?(:status_code) ? error.status_code : nil,
                       api_message: api_message)
    exit 1
  ensure
    service.delete_edit(package, edit.id) if edit && !committed
  end
end
