package dev.handspell.app.ui.alphabet

import android.content.Context
import android.webkit.WebStorage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Clears the embedded alphabet's saved state during the confirmed Settings deletion.
 *
 * The page keeps its checkmarks in the WebView's localStorage, and deleting one origin's data is not reliable on
 * current WebView builds (the checkmarks survived a "delete" and were then reported back into the fresh app
 * progress). So the deletion does two things: it asks WebView to drop everything it stores, and it changes a reset
 * token that the page compares with the one it last saw, wiping its own saved state on the next load if they differ.
 * The page's data is then gone even if WebView still holds the old bytes.
 */
object AlphabetStorage {
    private const val ORIGIN = "https://appassets.androidplatform.net"
    private const val PREFS = "alphabet_storage"
    private const val TOKEN_KEY = "reset_token"

    /** Changes with every deletion; "0" until the first one. Read by the page through the bridge. */
    fun resetToken(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(TOKEN_KEY, "0") ?: "0"

    suspend fun clear(context: Context) {
        val storage = WebStorage.getInstance()
        storage.deleteAllData()
        storage.deleteOrigin(ORIGIN)
        // commit(), not apply(): the deletion is only reported done once the new token is on disk.
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(TOKEN_KEY, System.nanoTime().toString()).commit()
        suspendCancellableCoroutine<Unit> { continuation ->
            storage.getOrigins { origins ->
                if (!continuation.isActive) return@getOrigins
                if (origins?.containsKey(ORIGIN) != true) continuation.resume(Unit)
                else continuation.resumeWith(Result.failure(IllegalStateException("Web storage was not cleared")))
            }
        }
    }
}
