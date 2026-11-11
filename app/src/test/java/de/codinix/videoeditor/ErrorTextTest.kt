package de.codinix.videoeditor

import org.junit.Assert.assertEquals
import org.junit.Test

/** Error text for toasts and the export error sheet: localised resource first, then system message, then fallback. */
class ErrorTextTest {
    private val resolve: (Int, Array<out Any>) -> String = { id, args -> "res$id(${args.joinToString(",")})" }

    @Test fun userFacingException_isResolvedFromResourceWithArgs() =
        assertEquals("res7(404)", ErrorText.of(UserFacingException(7, 404), "fallback", resolve))

    @Test fun userFacingException_withoutArgs() =
        assertEquals("res3()", ErrorText.of(UserFacingException(3), "fallback", resolve))

    @Test fun otherException_keepsItsOwnMessage() =
        assertEquals("disk full", ErrorText.of(java.io.IOException("disk full"), "fallback", resolve))

    @Test fun exceptionWithoutMessage_usesFallback() =
        assertEquals("fallback", ErrorText.of(IllegalStateException(), "fallback", resolve))

    @Test fun userFacingException_neverShowsItsLogMessage() {
        val e = UserFacingException(5, "x")
        assertEquals("res5(x)", ErrorText.of(e, "fallback", resolve))
    }
}
