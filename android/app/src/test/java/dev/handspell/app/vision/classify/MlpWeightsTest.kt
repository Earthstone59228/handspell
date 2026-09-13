package dev.handspell.app.vision.classify

import dev.handspell.app.core.model.NormalizedHand
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Corrupts the golden HSML file one field at a time and checks each corruption is refused with the
 * right typed failure.
 *
 * Using the real generated file rather than a hand-built byte array is the point: if the Python
 * writer and this parser ever disagree about a field's width or order, these tests stop being about
 * corruption and start failing outright, which is the failure we want to hear about.
 */
class MlpWeightsTest {

    private fun goldenBytes(): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/$GOLDEN_BIN")) {
            "$GOLDEN_BIN missing from test resources; run `python -m handspell.mlp_golden`"
        }.use(InputStream::readBytes)

    /** Overwrites a little-endian int32 and repairs the trailing CRC, isolating the field check. */
    private fun patchInt(bytes: ByteArray, offset: Int, value: Int): ByteArray {
        val patched = bytes.copyOf()
        ByteBuffer.wrap(patched).order(ByteOrder.LITTLE_ENDIAN).putInt(offset, value)
        return repairCrc(patched)
    }

    private fun repairCrc(bytes: ByteArray): ByteArray {
        val crc = CRC32().apply { update(bytes, 0, bytes.size - 4) }.value
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size - 4, crc.toInt())
        return bytes
    }

    private fun parse(bytes: ByteArray) = MlpWeights.parse(bytes)

    @Test
    fun `the golden file parses into the documented shape`() {
        val weights = parse(goldenBytes())

        assertEquals(MlpWeights.FORMAT_VERSION, weights.formatVersion)
        assertEquals(NormalizedHand.SPEC_VERSION, weights.specVersion)
        assertEquals(NormalizedHand.VECTOR_DIM, weights.inputDim)
        assertEquals(listOf("A", "B", "C", "D"), weights.labels)
        assertEquals(listOf(66, 8, 4), weights.layers.map { it.inDim })
        assertEquals(listOf(8, 4, 4), weights.layers.map { it.outDim })
        assertEquals(
            listOf(MlpActivation.RELU, MlpActivation.RELU, MlpActivation.IDENTITY),
            weights.layers.map { it.activation },
        )
        assertEquals(4, weights.outputDim)
        for (layer in weights.layers) {
            assertEquals(layer.inDim * layer.outDim, layer.weights.size)
            assertEquals(layer.outDim, layer.bias.size)
        }
    }

    @Test
    fun `a stream is read and closed`() {
        var closed = false
        val source = {
            object : ByteArrayInputStream(goldenBytes()) {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        }
        MlpWeights.parse(source)
        assertTrue(closed)
    }

    @Test
    fun `bad magic is refused`() {
        val bytes = goldenBytes()
        bytes[2] = 'X'.code.toByte()
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) { parse(repairCrc(bytes)) }
        assertTrue(error.message!!.contains("magic"))
    }

    @Test
    fun `another format version is refused`() {
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) {
            parse(patchInt(goldenBytes(), 4, 2))
        }
        assertTrue(error.message!!.contains("format version"))
    }

    @Test
    fun `another normalisation spec version is refused`() {
        val error = assertThrows(ClassifierAssetException.SpecVersionMismatch::class.java) {
            parse(patchInt(goldenBytes(), 8, NormalizedHand.SPEC_VERSION + 1))
        }
        assertEquals(NormalizedHand.SPEC_VERSION + 1, error.found)
        assertEquals("error_classifier_spec_mismatch", error.messageId)
    }

    @Test
    fun `another input dim is refused`() {
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) {
            parse(patchInt(goldenBytes(), 12, NormalizedHand.VECTOR_DIM - 1))
        }
        assertTrue(error.message!!.contains("input dim"))
    }

    @Test
    fun `an absurd layer count is refused before anything is allocated`() {
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) {
            parse(patchInt(goldenBytes(), 16, Int.MAX_VALUE))
        }
        assertTrue(error.message!!.contains("layer count"))
    }

    @Test
    fun `an unknown activation code is refused`() {
        // Layer 0's activation is the second int32 of the per-layer header block, at offset 24.
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) {
            parse(patchInt(goldenBytes(), 24, 9))
        }
        assertTrue(error.message!!.contains("activation"))
    }

    @Test
    fun `a single flipped weight bit fails the checksum`() {
        val bytes = goldenBytes()
        bytes[100] = (bytes[100].toInt() xor 0x01).toByte()
        val error = assertThrows(ClassifierAssetException.ChecksumMismatch::class.java) { parse(bytes) }
        assertEquals("error_classifier_asset_corrupt", error.messageId)
    }

    @Test
    fun `a flipped checksum byte fails the checksum`() {
        val bytes = goldenBytes()
        val last = bytes.size - 1
        bytes[last] = (bytes[last].toInt() xor 0x7F).toByte()
        assertThrows(ClassifierAssetException.ChecksumMismatch::class.java) { parse(bytes) }
    }

    @Test
    fun `a truncated file is refused`() {
        val bytes = goldenBytes()
        assertThrows(ClassifierAssetException.Malformed::class.java) {
            parse(bytes.copyOf(bytes.size / 2))
        }
        assertThrows(ClassifierAssetException.Malformed::class.java) { parse(ByteArray(8)) }
    }

    @Test
    fun `trailing junk after the labels is refused`() {
        val bytes = goldenBytes()
        val padded = bytes.copyOf(bytes.size + 4)
        // Move the CRC to the new end so the file is self-consistent apart from the extra bytes.
        System.arraycopy(bytes, bytes.size - 4, padded, padded.size - 4, 4)
        val error = assertThrows(ClassifierAssetException.Malformed::class.java) { parse(repairCrc(padded)) }
        assertTrue(error.message!!.contains("after the labels"))
    }

    @Test
    fun `an absent weight file is reported as missing`() {
        val error = assertThrows(ClassifierAssetException.Missing::class.java) {
            MlpWeights.parse({ throw FileNotFoundException(MlpWeights.ASSET_NAME) })
        }
        assertEquals("error_classifier_asset_missing", error.messageId)
    }

    private companion object {
        const val GOLDEN_BIN = "mlp_golden.bin"
    }
}
