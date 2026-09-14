package dev.handspell.app.ui.capture

import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Handedness
import dev.handspell.app.core.model.Landmark3
import dev.handspell.app.core.model.Letter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureCsvRowTest {

    private fun sampleLandmarks(): HandLandmarks {
        val points = (0 until HandLandmarks.LANDMARK_COUNT).map { i ->
            Landmark3(i * 0.01f, i * 0.02f, i * 0.03f)
        }
        return HandLandmarks(
            world = points,
            image = points,
            handedness = Handedness.RIGHT,
            handednessScore = 0.97f,
            timestampMs = 123_456L,
            imageWidth = 480,
            imageHeight = 640,
        )
    }

    private fun sampleContext() = CaptureRowContext(
        sessionId = "sess1",
        signerId = "s1",
        letter = Letter.A,
        capturedAtIso = "2026-09-13T00:00:00Z",
        rotationDegrees = 90,
        deviceModel = "SM-S928U",
        appVersion = "0.1.0",
        landmarkerModel = "hand_landmarker.task",
    )

    @Test
    fun `header has 141 columns in the documented order`() {
        assertEquals(141, CAPTURE_CSV_HEADER.size)
        assertEquals(
            listOf(
                "schema_version", "frame_convention", "session_id", "signer_id", "letter",
                "captured_at_iso", "timestamp_ms", "handedness", "handedness_score", "image_width",
                "image_height", "rotation_degrees", "device_model", "app_version", "landmarker_model",
            ),
            CAPTURE_CSV_HEADER.subList(0, 15),
        )
        assertEquals("wx0", CAPTURE_CSV_HEADER[15])
        assertEquals("wz20", CAPTURE_CSV_HEADER[15 + 62])
        assertEquals("ix0", CAPTURE_CSV_HEADER[15 + 63])
        assertEquals("iz20", CAPTURE_CSV_HEADER[140])
    }

    @Test
    fun `row has one value per header column`() {
        val row = buildCaptureCsvRow(sampleContext(), sampleLandmarks())
        assertEquals(141, row.size)
        assertEquals(CAPTURE_CSV_HEADER.size, row.size)
    }

    @Test
    fun `row records the frame convention tag and metadata in order`() {
        val row = buildCaptureCsvRow(sampleContext(), sampleLandmarks())
        assertEquals("1", row[0])
        assertEquals("selfie-upright-v1", row[1])
        assertEquals("sess1", row[2])
        assertEquals("s1", row[3])
        assertEquals("A", row[4])
        assertEquals("RIGHT", row[7])
        assertEquals("90", row[11])
    }

    @Test
    fun `world block precedes image block and both are 63 columns`() {
        val row = buildCaptureCsvRow(sampleContext(), sampleLandmarks())
        val worldBlock = row.subList(15, 15 + 63)
        val imageBlock = row.subList(15 + 63, 141)
        assertEquals(63, worldBlock.size)
        assertEquals(63, imageBlock.size)
        // landmark 0 is (0,0,0) in the fixture; landmark 20's z is 20 * 0.03.
        assertEquals("0.0", worldBlock[0])
        assertTrue(worldBlock.last().toFloat() > 0f)
    }

    @Test
    fun `five opaque signer ids are offered, never a name`() {
        assertEquals(listOf("s1", "s2", "s3", "s4", "s5"), CAPTURE_SIGNER_IDS)
    }
}
