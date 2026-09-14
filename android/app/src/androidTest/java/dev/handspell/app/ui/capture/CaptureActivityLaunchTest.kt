package dev.handspell.app.ui.capture

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test

/**
 * Confirms `CaptureActivity` (docs/CLASSIFIER.md §7) launches without crashing, driven entirely
 * in-process via instrumentation. `CaptureActivity` is deliberately `exported=false`
 * (docs/QUALITY.md §8 — debug surface, never callable by another app), so this is the only way to
 * exercise it end to end: `adb shell am start -n .../.ui.capture.CaptureActivity` is refused by
 * the platform for a non-exported component regardless of the app being debuggable, but code
 * running inside the app's own process (which is exactly what an instrumented test is) can start
 * any of its own activities, exported or not — that restriction only ever applied to other apps.
 *
 * Run with: `adb shell am instrument -w dev.handspell.app.test/androidx.test.runner.AndroidJUnitRunner`
 */
class CaptureActivityLaunchTest {

    @Test
    fun launchesWithoutCrashing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, CaptureActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        InstrumentationRegistry.getInstrumentation().startActivitySync(intent)
    }
}
