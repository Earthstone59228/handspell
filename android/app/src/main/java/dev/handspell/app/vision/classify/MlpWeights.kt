package dev.handspell.app.vision.classify

import dev.handspell.app.core.model.NormalizedHand
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.BufferUnderflowException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32

/** Per-layer non-linearity, encoded as the int32 in the HSML header (docs/CLASSIFIER.md §4). */
enum class MlpActivation(val code: Int) {
    RELU(0),
    IDENTITY(1),
    ;

    companion object {
        fun fromCode(code: Int): MlpActivation? = entries.firstOrNull { it.code == code }
    }
}

/**
 * One dense layer: `y = W · x + b`, then [activation].
 *
 * [weights] is row-major `[outDim][inDim]`, exactly as written by `training/scripts/export_weights.py`,
 * so row `o` starts at `o * inDim` and the forward pass walks memory in order.
 */
class MlpLayer(
    val inDim: Int,
    val outDim: Int,
    val activation: MlpActivation,
    val weights: FloatArray,
    val bias: FloatArray,
) {
    init {
        require(weights.size == inDim * outDim) {
            "expected ${inDim * outDim} weights, got ${weights.size}"
        }
        require(bias.size == outDim) { "expected $outDim biases, got ${bias.size}" }
    }
}

/**
 * Parsed `assets/classifier/mlp-v1.bin` — the HSML container defined in docs/CLASSIFIER.md §4.
 *
 * The format is deliberately a flat little-endian blob rather than a serialisation-library format:
 * it is written by one 60-line Python script and read by this one class, and a trailing CRC32 over
 * everything before it turns a truncated or half-written file into a typed load failure instead of
 * a plausible-looking model that quietly mis-scores. docs/QUALITY.md §3 requires that failure to be
 * visible, so every check below throws [ClassifierAssetException] and none of them fall back to a
 * default.
 */
class MlpWeights internal constructor(
    val formatVersion: Int,
    val specVersion: Int,
    val inputDim: Int,
    val layers: List<MlpLayer>,
    val labels: List<String>,
) {
    /** Number of classes the final layer emits. */
    val outputDim: Int get() = layers.last().outDim

    companion object {
        /** Path inside `assets/`; written by `training/scripts/export_weights.py`. */
        const val ASSET_NAME: String = "classifier/mlp-v1.bin"

        /** ASCII `HSML`, little-endian-agnostic because it is compared byte by byte. */
        private val MAGIC: ByteArray = byteArrayOf('H'.code.toByte(), 'S'.code.toByte(), 'M'.code.toByte(), 'L'.code.toByte())

        /** The only container version this build understands. */
        const val FORMAT_VERSION: Int = 1

        /** Width of the trailing CRC32 field. */
        private const val CRC_BYTES = 4

        /** Guard against a corrupt header asking for gigabytes before the CRC is ever checked. */
        private const val MAX_LAYERS = 16
        private const val MAX_LAYER_DIM = 4096
        private const val MAX_LABELS = 64
        private const val MAX_LABEL_BYTES = 64

        /**
         * Reads and validates [source], closing the stream.
         *
         * @throws ClassifierAssetException for a missing, unreadable, mis-versioned, malformed or
         *   CRC-failing weight file. The container falls back to stage 1 with a visible notice
         *   (docs/CLASSIFIER.md §4, docs/QUALITY.md §3) rather than crashing.
         */
        fun parse(source: () -> InputStream, assetName: String = ASSET_NAME): MlpWeights {
            val bytes = try {
                source().use(InputStream::readBytes)
            } catch (e: FileNotFoundException) {
                throw ClassifierAssetException.Missing(assetName, e)
            } catch (e: IOException) {
                throw ClassifierAssetException.Unreadable(assetName, e)
            }
            return parse(bytes, assetName)
        }

        /** Byte-array entry point, for callers that already hold the file (and for tests). */
        fun parse(bytes: ByteArray, assetName: String = ASSET_NAME): MlpWeights =
            try {
                parseChecked(bytes, assetName)
            } catch (e: BufferUnderflowException) {
                throw ClassifierAssetException.Malformed(assetName, "file ends mid-field", e)
            }

        private fun parseChecked(bytes: ByteArray, assetName: String): MlpWeights {
            val headerBytes = MAGIC.size + 4 * 4
            if (bytes.size < headerBytes + CRC_BYTES) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "file is ${bytes.size} bytes, too short to hold a header and a checksum",
                )
            }
            for (i in MAGIC.indices) {
                if (bytes[i] != MAGIC[i]) {
                    throw ClassifierAssetException.Malformed(
                        assetName,
                        "magic is not 'HSML'",
                    )
                }
            }

            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            buffer.position(MAGIC.size)

            val formatVersion = buffer.int
            if (formatVersion != FORMAT_VERSION) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "format version is $formatVersion, this build reads $FORMAT_VERSION",
                )
            }

            val specVersion = buffer.int
            if (specVersion != NormalizedHand.SPEC_VERSION) {
                throw ClassifierAssetException.SpecVersionMismatch(
                    assetName,
                    expected = NormalizedHand.SPEC_VERSION,
                    found = specVersion,
                )
            }

            val inputDim = buffer.int
            if (inputDim != NormalizedHand.VECTOR_DIM) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "input dim is $inputDim, this build normalises to ${NormalizedHand.VECTOR_DIM}",
                )
            }

            val layerCount = buffer.int
            if (layerCount !in 1..MAX_LAYERS) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "layer count is $layerCount, expected 1..$MAX_LAYERS",
                )
            }

            // Header: outDim + activation per layer, all up front, so the body is one contiguous
            // float run and can be read without seeking back.
            val outDims = IntArray(layerCount)
            val activations = arrayOfNulls<MlpActivation>(layerCount)
            for (layer in 0 until layerCount) {
                val outDim = buffer.int
                if (outDim !in 1..MAX_LAYER_DIM) {
                    throw ClassifierAssetException.Malformed(
                        assetName,
                        "layer $layer declares outDim $outDim, expected 1..$MAX_LAYER_DIM",
                    )
                }
                val activationCode = buffer.int
                val activation = MlpActivation.fromCode(activationCode)
                    ?: throw ClassifierAssetException.Malformed(
                        assetName,
                        "layer $layer declares unknown activation code $activationCode",
                    )
                outDims[layer] = outDim
                activations[layer] = activation
            }

            val layers = ArrayList<MlpLayer>(layerCount)
            var layerInDim = inputDim
            for (layer in 0 until layerCount) {
                val outDim = outDims[layer]
                val weights = FloatArray(layerInDim * outDim)
                for (i in weights.indices) weights[i] = buffer.float
                val bias = FloatArray(outDim)
                for (i in bias.indices) bias[i] = buffer.float
                layers.add(
                    MlpLayer(
                        inDim = layerInDim,
                        outDim = outDim,
                        activation = requireNotNull(activations[layer]),
                        weights = weights,
                        bias = bias,
                    ),
                )
                layerInDim = outDim
            }

            val labelCount = buffer.int
            if (labelCount !in 1..MAX_LABELS) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "label count is $labelCount, expected 1..$MAX_LABELS",
                )
            }
            if (labelCount != layers.last().outDim) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "label count $labelCount does not match final layer outDim ${layers.last().outDim}",
                )
            }
            val labels = ArrayList<String>(labelCount)
            for (index in 0 until labelCount) {
                val length = buffer.int
                if (length !in 1..MAX_LABEL_BYTES) {
                    throw ClassifierAssetException.Malformed(
                        assetName,
                        "label $index declares $length bytes, expected 1..$MAX_LABEL_BYTES",
                    )
                }
                val labelBytes = ByteArray(length)
                buffer.get(labelBytes)
                labels.add(String(labelBytes, Charsets.UTF_8))
            }

            val bodyEnd = buffer.position()
            if (bytes.size - bodyEnd != CRC_BYTES) {
                throw ClassifierAssetException.Malformed(
                    assetName,
                    "${bytes.size - bodyEnd} bytes after the labels, expected exactly $CRC_BYTES of CRC32",
                )
            }
            val storedCrc = buffer.int.toLong() and 0xFFFFFFFFL
            val computed = CRC32().apply { update(bytes, 0, bodyEnd) }.value
            if (storedCrc != computed) {
                throw ClassifierAssetException.ChecksumMismatch(assetName, expected = storedCrc, found = computed)
            }

            return MlpWeights(
                formatVersion = formatVersion,
                specVersion = specVersion,
                inputDim = inputDim,
                layers = layers,
                labels = labels,
            )
        }
    }
}
