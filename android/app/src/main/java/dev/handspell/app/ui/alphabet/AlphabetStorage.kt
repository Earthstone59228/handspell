package dev.handspell.app.ui.alphabet

import android.webkit.WebStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Clears the embedded alphabet's localStorage during the confirmed Settings deletion. */
object AlphabetStorage {
    private const val ORIGIN = "https://appassets.androidplatform.net"

    suspend fun clear() {
        val storage = WebStorage.getInstance()
        storage.deleteOrigin(ORIGIN)
        suspendCancellableCoroutine<Unit> { continuation ->
            storage.getOrigins { origins ->
                if (!continuation.isActive) return@getOrigins
                if (origins?.containsKey(ORIGIN) != true) continuation.resume(Unit)
                else continuation.resumeWith(Result.failure(IllegalStateException("Web storage was not cleared")))
            }
        }
    }
}
