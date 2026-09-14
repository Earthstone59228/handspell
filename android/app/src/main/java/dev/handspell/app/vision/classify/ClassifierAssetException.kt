package dev.handspell.app.vision.classify

/**
 * Why a classifier asset could not be loaded.
 *
 * Both stage-1 (`references-v1.csv`) and stage-2 (`mlp-v1.bin`) loading failures are reported as
 * one of these, so the app container has a single `catch` and can map [messageId] straight onto
 * `DetectorStatus.Failed(messageId, cause)` — docs/QUALITY.md §3 requires a real message, never an
 * exception string, so the loader carries a string-resource key rather than prose for the UI.
 *
 * [Missing] is deliberately separate from [Unreadable]: before the first capture session the
 * reference set simply does not exist yet (see `assets/classifier/README.md`), which is an expected
 * state of this repo, not a corrupt install.
 */
sealed class ClassifierAssetException(
    /** Key into `strings.xml`; the UI resolves it, the exception never renders itself. */
    val messageId: String,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {

    /** The asset is not in the APK at all. */
    class Missing(assetName: String, cause: Throwable? = null) : ClassifierAssetException(
        messageId = "error_classifier_asset_missing",
        message = "classifier asset '$assetName' is not present in this build",
        cause = cause,
    )

    /** The asset exists but could not be read (I/O failure below the parser). */
    class Unreadable(assetName: String, cause: Throwable? = null) : ClassifierAssetException(
        messageId = "error_classifier_asset_unreadable",
        message = "classifier asset '$assetName' could not be read",
        cause = cause,
    )

    /** The bytes are present but do not parse: bad magic, bad header, wrong dimensions, short file. */
    class Malformed(assetName: String, detail: String, cause: Throwable? = null) : ClassifierAssetException(
        messageId = "error_classifier_asset_malformed",
        message = "classifier asset '$assetName' is malformed: $detail",
        cause = cause,
    )

    /** The asset was built against a different normalisation spec than this build implements. */
    class SpecVersionMismatch(
        assetName: String,
        val expected: Int,
        val found: Int,
    ) : ClassifierAssetException(
        messageId = "error_classifier_spec_mismatch",
        message = "classifier asset '$assetName' declares spec version $found, this build needs $expected",
    )

    /** Trailing CRC32 does not match the bytes before it — a truncated or corrupted download. */
    class ChecksumMismatch(
        assetName: String,
        val expected: Long,
        val found: Long,
    ) : ClassifierAssetException(
        messageId = "error_classifier_asset_corrupt",
        message = "classifier asset '$assetName' failed its CRC32 check (stored $expected, computed $found)",
    )
}
