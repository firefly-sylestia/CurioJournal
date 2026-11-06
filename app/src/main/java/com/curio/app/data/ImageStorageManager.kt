package com.curio.app.data

import android.content.Context
import java.io.File

/**
 * Manages the image attachments that live in app-private storage.
 *
 * Images picked in the editors stay as provider URIs (document picker +
 * persisted permission) and are never copied on save. The one place Curio
 * OWNS image bytes is restore-from-backup: the backup JSON carries the raw
 * bytes, and restore writes them to `filesDir/images/{entryId}/{n}.img`,
 * rewriting each capture's image URIs to those file paths. This manager
 * owns that directory — write, delete per entry, wipe all (pre-restore).
 *
 * The file extension is irrelevant: Coil sniffs image content from the
 * bytes, not the name.
 */
object ImageStorageManager {

    private const val IMAGE_DIR = "images"

    /**
     * Write one restored image to `filesDir/images/{entryId}/{index}.img`
     * and return its absolute path (the caller rewrites the capture's URI
     * to `Uri.fromFile(File(path))`).
     *
     * @throws IllegalArgumentException when [entryId] could escape the images
     *   directory (it comes from a user-supplied backup file — never trust it
     *   as a bare path segment).
     */
    fun restoreImage(context: Context, entryId: String, index: Int, bytes: ByteArray): String {
        // Hardening: a crafted backup could set entryId to "../../x" and
        // write outside the images directory. Refuse separators and the
        // dot segments; real capture ids are UUIDs, so this never blocks a
        // legitimate restore.
        if (entryId.isBlank() || entryId.contains("/") || entryId.contains("\\") ||
            entryId == "." || entryId == ".."
        ) {
            throw IllegalArgumentException("Unsafe image entry id: $entryId")
        }
        val imagesRoot = File(context.filesDir, IMAGE_DIR).apply { mkdirs() }
        val entryDir = File(imagesRoot, entryId).apply { mkdirs() }
        val destFile = File(entryDir, "$index.img")
        destFile.writeBytes(bytes)
        // Belt-and-braces: confirm the resolved path is still contained.
        if (!destFile.canonicalPath.startsWith(imagesRoot.canonicalPath)) {
            destFile.delete()
            throw IllegalStateException("Image path escaped the images directory")
        }
        return destFile.absolutePath
    }

    /**
     * Delete every restored image for one entry (`filesDir/images/{entryId}/`).
     * Safe to call for any entry — no-ops when nothing was restored.
     */
    fun deleteImagesForEntry(context: Context, entryId: String) {
        val imagesRoot = File(context.filesDir, IMAGE_DIR)
        val entryDir = File(imagesRoot, entryId)
        if (!entryDir.exists()) return
        // Only ever delete inside our own images directory.
        if (entryDir.canonicalPath.startsWith(imagesRoot.canonicalPath)) {
            entryDir.deleteRecursively()
        }
    }

    /** Delete all restored images (used by restore-from-backup before re-writing). */
    fun deleteAllImages(context: Context) {
        val imagesRoot = File(context.filesDir, IMAGE_DIR)
        if (imagesRoot.exists()) {
            imagesRoot.listFiles()?.forEach { it.deleteRecursively() }
        }
    }
}
