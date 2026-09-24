package dev.handspell.app.ui.alphabet

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Subtle detents for the alphabet menu. The WebView's `navigator.vibrate` is unreliable (needs a fresh user
 * gesture and is ignored by many WebView builds), so the page calls these through the bridge instead.
 * Prefers the actuator's own low-tick primitive at reduced strength, the lightest thing most phones can do.
 */
object Haptics {
    fun tick(context: Context) = play(context, primitive = 0.5f, effect = VibrationEffect.EFFECT_TICK, legacyMs = 6L)

    fun confirm(context: Context) = play(context, primitive = 0.8f, effect = VibrationEffect.EFFECT_CLICK, legacyMs = 12L)

    // VIBRATE is declared in AndroidManifest.xml (and shows in the merged manifest); lint does not see it here.
    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun play(context: Context, primitive: Float, effect: Int, legacyMs: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        } ?: return
        if (!vibrator.hasVibrator()) return
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    vibrator.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK) ->
                    vibrator.vibrate(
                        VibrationEffect.startComposition()
                            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, primitive).compose(),
                    )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> vibrator.vibrate(VibrationEffect.createPredefined(effect))
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O ->
                    vibrator.vibrate(VibrationEffect.createOneShot(legacyMs, 40))
                else -> vibrator.vibrate(legacyMs)
            }
        }
    }
}
