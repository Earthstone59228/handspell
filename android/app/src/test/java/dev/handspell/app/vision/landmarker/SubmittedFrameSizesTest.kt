package dev.handspell.app.vision.landmarker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubmittedFrameSizesTest {
    @Test fun `delayed result keeps its own frame size while dropped results are bounded`() {
        val sizes = SubmittedFrameSizes(capacity = 3)
        sizes.record(10, 640, 480)
        sizes.record(20, 800, 600)
        sizes.record(30, 1280, 720)

        assertEquals(SubmittedFrameSizes.Size(640, 480), sizes.remove(10))
        // Timestamp 20 is dropped by MediaPipe; timestamp 30 arrives after another submission.
        sizes.record(40, 1920, 1080)
        assertEquals(SubmittedFrameSizes.Size(1280, 720), sizes.remove(30))
        sizes.record(50, 320, 240)
        sizes.record(60, 400, 300)
        assertNull(sizes.remove(20))
        assertEquals(SubmittedFrameSizes.Size(1920, 1080), sizes.remove(40))
    }
}
