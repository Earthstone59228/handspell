package dev.handspell.app.ui.capture

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** What the export button did, so the screen can tell the person why nothing was shared. */
enum class ExportResult { SHARED, NOTHING_RECORDED, FAILED }

/**
 * Debug-only: hands the recorded capture CSVs (`<external files>/captures/<signer>/<letter>_<session>.csv`) to the
 * system share sheet, so they can leave the phone without `adb pull`.
 *
 * Two shapes, both built fresh in the app cache and shared read-only through a FileProvider:
 *  - **ZIP** keeps the `<signer>/<file>.csv` layout, so after unzipping it can be passed straight to
 *    `training/scripts/build_references.py --captures <folder>`.
 *  - **CSV** is every row of every file in one file (one header), for quick looks in a spreadsheet.
 *
 * The rows are raw hand-landmark numbers (no images), the same data `docs/PRIVACY.md` describes for this tool.
 */
object CaptureExport {
    private const val EXPORT_DIR = "exports"

    fun captureFiles(context: Context): List<File> =
        captureRoot(context).walkTopDown().filter { it.isFile && it.extension == "csv" }.sortedBy { it.path }.toList()

    fun share(context: Context, asZip: Boolean, chooserTitle: String): ExportResult {
        val files = captureFiles(context)
        if (files.isEmpty()) return ExportResult.NOTHING_RECORDED
        return try {
            val export = if (asZip) buildZip(context, files) else buildCombinedCsv(context, files)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.captures", export)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = if (asZip) "application/zip" else "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, export.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            ExportResult.SHARED
        } catch (error: Exception) {
            ExportResult.FAILED
        }
    }

    private fun captureRoot(context: Context) = File(context.getExternalFilesDir(null), "captures")

    private fun freshExportDir(context: Context): File =
        File(context.cacheDir, EXPORT_DIR).also { dir ->
            dir.deleteRecursively() // only ever the latest export; nothing lingers in the cache
            dir.mkdirs()
        }

    private fun stamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(System.currentTimeMillis())

    private fun buildZip(context: Context, files: List<File>): File {
        val root = captureRoot(context)
        val zip = File(freshExportDir(context), "handspell-captures-${stamp()}.zip")
        ZipOutputStream(zip.outputStream().buffered()).use { out ->
            for (file in files) {
                out.putNextEntry(ZipEntry(file.relativeTo(root).path.replace(File.separatorChar, '/')))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }

    private fun buildCombinedCsv(context: Context, files: List<File>): File {
        val csv = File(freshExportDir(context), "handspell-captures-${stamp()}.csv")
        csv.bufferedWriter().use { out ->
            var headerWritten = false
            for (file in files) {
                file.useLines { lines ->
                    lines.forEachIndexed { index, line ->
                        if (index == 0) {
                            if (!headerWritten) { out.appendLine(line); headerWritten = true }
                        } else if (line.isNotBlank()) {
                            out.appendLine(line)
                        }
                    }
                }
            }
        }
        return csv
    }
}
