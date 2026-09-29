package dev.handspell.app.vision.words

import dev.handspell.app.vision.classify.ClassifierAssetException
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import kotlin.math.exp
import kotlin.math.max

/**
 * The word-sign network: embed, three 1D convolutions, mean+max pooling, a small head. A port of
 * `training/handspell/words_net.py` (container HSCN, layer order and arithmetic are defined in its docstring),
 * pinned to it by WordNetGoldenTest. Arithmetic is done in Double, like the letter classifier.
 */
class WordNet private constructor(
    val steps: Int,
    val stepDim: Int,
    private val mu: FloatArray,
    private val sd: FloatArray,
    private val wIn: FloatArray,
    private val bIn: FloatArray,
    private val convs: List<Conv>,
    private val wH1: FloatArray,
    private val bH1: FloatArray,
    private val wH2: FloatArray,
    private val bH2: FloatArray,
    val labels: List<String>,
    private val emb: Int,
    private val ch: Int,
    private val hidden: Int,
) {
    private class Conv(val w: FloatArray, val b: FloatArray, val stride: Int, val cIn: Int, val cOut: Int)

    /** Probabilities in label order for a [WordFeatures] vector (only its first steps*stepDim floats are read). */
    fun probabilities(features: FloatArray): DoubleArray {
        val n = steps * stepDim
        require(features.size >= n) { "expected at least $n features, got ${features.size}" }
        val seq = DoubleArray(n) { (features[it] - mu[it]) / sd[it].toDouble() }

        // Embed each step from [s_t, s_t - s_{t-1}].
        var z = Array(emb) { DoubleArray(steps) }
        for (t in 0 until steps) {
            for (o in 0 until emb) {
                var sum = bIn[o].toDouble()
                val row = o * 2 * stepDim
                for (i in 0 until stepDim) {
                    val s = seq[t * stepDim + i]
                    val d = if (t == 0) 0.0 else s - seq[(t - 1) * stepDim + i]
                    sum += wIn[row + i] * s + wIn[row + stepDim + i] * d
                }
                z[o][t] = max(sum, 0.0)
            }
        }
        for (conv in convs) z = conv1d(z, conv)

        val pooled = DoubleArray(2 * ch)
        for (c in 0 until ch) {
            var total = 0.0
            var peak = Double.NEGATIVE_INFINITY
            for (v in z[c]) {
                total += v
                if (v > peak) peak = v
            }
            pooled[c] = total / z[c].size
            pooled[ch + c] = peak
        }
        val h = DoubleArray(hidden) { o ->
            var sum = bH1[o].toDouble()
            for (i in pooled.indices) sum += wH1[o * pooled.size + i] * pooled[i]
            max(sum, 0.0)
        }
        val logits = DoubleArray(labels.size) { o ->
            var sum = bH2[o].toDouble()
            for (i in 0 until hidden) sum += wH2[o * hidden + i] * h[i]
            sum
        }
        val top = logits.max()
        var total = 0.0
        val out = DoubleArray(logits.size) { exp(logits[it] - top).also { e -> total += e } }
        for (i in out.indices) out[i] /= total
        return out
    }

    /** Zero padding 1, kernel 3, cross-correlation with ReLU, like torch.nn.Conv1d followed by ReLU. */
    private fun conv1d(x: Array<DoubleArray>, conv: Conv): Array<DoubleArray> {
        val tIn = x[0].size
        val tOut = (tIn + 2 - KERNEL) / conv.stride + 1
        return Array(conv.cOut) { o ->
            DoubleArray(tOut) { i ->
                var sum = conv.b[o].toDouble()
                for (c in 0 until conv.cIn) {
                    val base = (o * conv.cIn + c) * KERNEL
                    for (k in 0 until KERNEL) {
                        val pos = i * conv.stride + k - 1
                        if (pos in 0 until tIn) sum += conv.w[base + k] * x[c][pos]
                    }
                }
                max(sum, 0.0)
            }
        }
    }

    companion object {
        const val SPEC_VERSION = 30
        const val ASSET_NAME = "classifier/words-v3.bin"
        private const val FORMAT_VERSION = 1
        private const val KERNEL = 3
        private val MAGIC = byteArrayOf('H'.code.toByte(), 'S'.code.toByte(), 'C'.code.toByte(), 'N'.code.toByte())
        private const val MAX_DIM = 4096
        private const val MAX_CLASSES = 512

        fun parse(source: () -> InputStream, assetName: String = ASSET_NAME): WordNet {
            val bytes = try {
                source().use(InputStream::readBytes)
            } catch (e: FileNotFoundException) {
                throw ClassifierAssetException.Missing(assetName, e)
            } catch (e: IOException) {
                throw ClassifierAssetException.Unreadable(assetName, e)
            }
            return parse(bytes, assetName)
        }

        fun parse(bytes: ByteArray, assetName: String = ASSET_NAME): WordNet =
            try {
                parseChecked(bytes, assetName)
            } catch (e: BufferUnderflowException) {
                throw ClassifierAssetException.Malformed(assetName, "file ends mid-field", e)
            }

        private fun parseChecked(bytes: ByteArray, assetName: String): WordNet {
            fun bad(detail: String): Nothing = throw ClassifierAssetException.Malformed(assetName, detail)
            if (bytes.size < MAGIC.size + 9 * 4 + 4) bad("file is ${bytes.size} bytes, too short")
            for (i in MAGIC.indices) if (bytes[i] != MAGIC[i]) bad("magic is not 'HSCN'")
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(MAGIC.size)
            val format = buffer.int
            if (format != FORMAT_VERSION) bad("format version is $format, this build reads $FORMAT_VERSION")
            val spec = buffer.int
            if (spec != SPEC_VERSION) throw ClassifierAssetException.SpecVersionMismatch(assetName, SPEC_VERSION, spec)
            val steps = buffer.int
            val stepDim = buffer.int
            val emb = buffer.int
            val ch = buffer.int
            val hidden = buffer.int
            val classes = buffer.int
            val nConv = buffer.int
            for (v in intArrayOf(steps, stepDim, emb, ch, hidden)) if (v !in 1..MAX_DIM) bad("dimension $v out of range")
            if (classes !in 1..MAX_CLASSES) bad("class count $classes out of range")
            if (nConv !in 1..8) bad("conv count $nConv out of range")
            val strides = IntArray(nConv) { buffer.int.also { s -> if (s !in 1..4) bad("stride $s out of range") } }

            fun floats(count: Int): FloatArray {
                if (count < 0 || count * 4L > buffer.remaining()) bad("file ends mid-array")
                return FloatArray(count) { buffer.float }
            }

            val mu = floats(steps * stepDim)
            val sd = floats(steps * stepDim)
            val wIn = floats(emb * 2 * stepDim)
            val bIn = floats(emb)
            val convs = strides.mapIndexed { i, stride ->
                val cIn = if (i == 0) emb else ch
                Conv(floats(ch * cIn * KERNEL), floats(ch), stride, cIn, ch)
            }
            val wH1 = floats(hidden * 2 * ch)
            val bH1 = floats(hidden)
            val wH2 = floats(classes * hidden)
            val bH2 = floats(classes)
            val labelCount = buffer.int
            if (labelCount != classes) bad("label count $labelCount does not match $classes classes")
            val labels = List(labelCount) {
                val length = buffer.int
                if (length !in 1..64) bad("label length $length out of range")
                ByteArray(length).also { b -> buffer.get(b) }.toString(Charsets.UTF_8)
            }
            val bodyEnd = buffer.position()
            if (bytes.size - bodyEnd != 4) bad("${bytes.size - bodyEnd} bytes after the labels, expected 4")
            val stored = buffer.int.toLong() and 0xFFFFFFFFL
            val computed = CRC32().apply { update(bytes, 0, bodyEnd) }.value
            if (stored != computed) throw ClassifierAssetException.ChecksumMismatch(assetName, stored, computed)
            return WordNet(steps, stepDim, mu, sd, wIn, bIn, convs, wH1, bH1, wH2, bH2, labels, emb, ch, hidden)
        }
    }
}
