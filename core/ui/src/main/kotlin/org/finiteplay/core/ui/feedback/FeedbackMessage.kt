package org.finiteplay.core.ui.feedback

/**
 * What a feedback message is about. [tag] is what the subject line says, in English whatever language the player
 * writes in, so the mailbox can be sorted at a glance.
 */
enum class FeedbackCategory(val tag: String) { PROBLEM("Problem"), IDEA("Idea"), OTHER("Other") }

/** What the player may choose to send along with a message: the build and device it came from, nothing else. */
data class DeviceInfo(
    val app: String,
    val versionName: String,
    val androidRelease: String,
    val sdk: Int,
    val device: String,
    val language: String,
)

/** The longest message the form takes: far more than anyone writes, and small enough for an Intent. */
const val FEEDBACK_MAX_CHARS = 4000

/** The subject: the app and build, then the category, for example `Klondike by FinitePlay 0.3.1 – Problem`. */
fun feedbackSubject(app: String, versionName: String, category: FeedbackCategory): String =
    "$app $versionName – ${category.tag}"

/** The mail body: the message as typed, then, if the player allowed it, a short block saying which build and device it is from. */
fun feedbackBody(message: String, info: DeviceInfo?): String = buildString {
    append(message.trim().take(FEEDBACK_MAX_CHARS))
    if (info != null) {
        append("\n\n--\n")
        append("App: ${info.app} ${info.versionName}\n")
        append("Android: ${info.androidRelease} (API ${info.sdk})\n")
        append("Device: ${info.device}\n")
        append("Language: ${info.language}\n")
    }
}

/** A message with nothing in it is not sent. */
fun canSendFeedback(message: String): Boolean = message.isNotBlank()
