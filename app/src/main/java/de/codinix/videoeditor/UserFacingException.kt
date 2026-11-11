package de.codinix.videoeditor

import androidx.annotation.StringRes

/**
 * Error whose text is shown to the user. The text comes from a string resource, so it follows the app
 * language. Thrown where no Context is at hand (WhisperEngine, ModelManager, OverlayAudioRenderer);
 * the UI resolves it with [ErrorText.of]. The exception message is only for the log.
 */
class UserFacingException(@StringRes val resId: Int, vararg val args: Any) :
    IllegalStateException("UserFacingException(res=$resId, args=${args.joinToString()})")

object ErrorText {
    /**
     * Text for an error message: a [UserFacingException] is resolved via [resolve]; any other exception
     * keeps its own message (Android/Media3 texts); without a message the localised [fallback] is used.
     */
    fun of(e: Throwable, fallback: String, resolve: (Int, Array<out Any>) -> String): String =
        if (e is UserFacingException) resolve(e.resId, e.args) else e.message ?: fallback
}
