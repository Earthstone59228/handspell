package dev.handspell.app.vision.camera

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PackRgbaRowsTest {

    @Test
    fun dropsRowPaddingAndKeepsPixelOrder() {
        // 2x3 RGBA image, rowStride 12 (8 bytes of pixels + 4 of padding); the last row is unpadded.
        val padded = ByteBuffer.wrap(
            byteArrayOf(
                1, 2, 3, 4, 5, 6, 7, 8, 0, 0, 0, 0,
                9, 10, 11, 12, 13, 14, 15, 16, 0, 0, 0, 0,
                17, 18, 19, 20, 21, 22, 23, 24,
            ),
        )
        val packed = packRgbaRows(padded, rowStride = 12, rowBytes = 8, height = 3, into = ByteBuffer.allocate(24))

        val out = ByteArray(packed.remaining()).also { packed.get(it) }
        assertArrayEquals(ByteArray(24) { (it + 1).toByte() }, out)
    }

    @Test
    fun reusedDestinationIsOverwritten() {
        val into = ByteBuffer.allocate(4)
        packRgbaRows(ByteBuffer.wrap(byteArrayOf(9, 9, 9, 9)), rowStride = 4, rowBytes = 4, height = 1, into = into)
        val packed = packRgbaRows(ByteBuffer.wrap(byteArrayOf(1, 2, 3, 4)), rowStride = 4, rowBytes = 4, height = 1, into = into)

        val out = ByteArray(packed.remaining()).also { packed.get(it) }
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), out)
    }

    @Test
    fun refusesToReadPastTheSourceLimit() {
        // Capacity holds two full rows, but the plane only declares 12 valid bytes.
        val padded = ByteBuffer.allocate(24).apply { limit(12) }
        assertThrows(IllegalArgumentException::class.java) {
            packRgbaRows(padded, rowStride = 12, rowBytes = 8, height = 2, into = ByteBuffer.allocate(16))
        }
    }

    @Test
    fun refusesAStrideShorterThanARow() {
        assertThrows(IllegalArgumentException::class.java) {
            packRgbaRows(ByteBuffer.allocate(16), rowStride = 4, rowBytes = 8, height = 2, into = ByteBuffer.allocate(16))
        }
    }
}
