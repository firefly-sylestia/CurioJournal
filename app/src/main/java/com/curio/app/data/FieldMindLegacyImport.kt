package com.curio.app.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Preview counts from a FieldMind archive — shown to the user before they
 * confirm a restore, so an import is never a blind action.
 */
data class FieldMindArchivePreview(
    val observations: Int,
    val notes: Int,
    val images: Int,
    val species: Int
)

/**
 * Result of a completed FieldMind restore.
 *
 * [observations] / [notes] count NEW entries actually written to the
 * Cabinet; [skipped] counts records whose entry ids already existed (a
 * previous restore) — re-importing the same archive never duplicates.
 */
data class FieldMindRestoreSummary(
    val observations: Int,
    val notes: Int,
    val images: Int,
    val species: Int,
    val skipped: Int
)

/**
 * Imports a FieldMind V3 archive (a `.fieldmind` ZIP package — `archive.json`
 * + `media/observations/{id}/<files>` — or the plain `archive.json` JSON export)
 * into Curio as LEGACY Cabinet entries.
 *
 * Every FieldMind **observation** becomes a `FieldNotes` Curio entry
 * (Observed = facts, Surprised me = evidence summary, Want to learn next =
 * mood/context), its evidence attachments are copied into Curio's app-private
 * image storage, and the remaining metadata (FieldMind category, confidence,
 * location, weather) becomes searchable #tags. FieldMind **notes** become
 * `Marginalia` journal entries with their package media attached. The archive's
 * **species catalog** is written to `filesDir/fieldmind/species.json` so later
 * Curio features can consume it.
 *
 * The restore is deliberately NON-DESTRUCTIVE and idempotent: it only INSERTS
 * entries with the deterministic ids `fieldmind-obs-{id}` /
 * `fieldmind-note-{id}` (already-present ids are skipped), never touches or
 * wipes existing Curio captures, and every failure degrades to a reported     * message instead of a crash. Restored entries are marked legacy by the
 * explicit persisted [CurioEntry.isLegacy] provenance flag, which drives the
 * small "LEGACY" badge on Cabinet cards and the detail page.
 */
object FieldMindLegacyImport {

    /** Topic subtype that marks an entry as imported from FieldMind. */
    const val LEGACY_SUBTYPE = "Legacy"

    /** The app-private file where the imported species catalog is kept. */
    private const val SPECIES_FILE = "fieldmind/species.json"

    // ── Public API ─────────────────────────────────────────────────────────

    /**
     * Read + count a FieldMind archive WITHOUT writing anything — the
     * confirmation dialog calls this first.
     */
    suspend fun preview(context: Context, uri: Uri): FieldMindArchivePreview =
        withContext(Dispatchers.IO) {
            val archive = readArchive(context, uri)
            try {
                FieldMindArchivePreview(
                    observations = archive.observations.size,
                    notes = archive.notes.size,
                    images = archive.media.values.sumOf { files -> files.count(::isSupportedImage) } +
                        archive.noteMedia.values.sumOf { files -> files.count(::isSupportedImage) },
                    species = archive.speciesCount
                )
            } finally {
                // A ZIP package extracted its media to a temp dir even for the
                // preview — never leave it behind.
                archive.tempDir?.deleteRecursively()
            }
        }

    /**
     * Restore the archive into the Cabinet. Skips records that are already
     * present (idempotent), copies every attachment into app-private storage,
     * and saves the species catalog for later use.
     */
    suspend fun restore(context: Context, uri: Uri): FieldMindRestoreSummary =
        withContext(Dispatchers.IO) {
            val archive = readArchive(context, uri)
            try {
                val existingIds = CurioRepositoryHolder.repo.getAll().map { it.id }.toSet()
                var savedObs = 0
                var savedNotes = 0
                var copiedImages = 0
                var skipped = 0
                val importedIds = existingIds.toMutableSet()
                val createdIds = mutableListOf<String>()

                try {
                    archive.observations.forEach { obs ->
                        val entryId = "fieldmind-obs-${obs.id}"
                        if (!importedIds.add(entryId)) {
                            skipped++
                            return@forEach
                        }
                        val media = archive.media[obs.id].orEmpty().filter(::isSupportedImage)
                        val images = media.mapIndexedNotNull { index, item ->
                            copyMediaToApp(context, entryId, index, item)?.also { copiedImages++ }
                        }
                        CurioRepositoryHolder.repo.save(
                            buildObservationEntry(obs, entryId, images, media.map { it.caption })
                        )
                        createdIds += entryId
                        savedObs++
                    }

                    archive.notes.forEach { note ->
                        val entryId = "fieldmind-note-${note.id}"
                        if (!importedIds.add(entryId)) {
                            skipped++
                            return@forEach
                        }
                        val media = archive.noteMedia[note.id].orEmpty().filter(::isSupportedImage)
                        val images = media.mapIndexedNotNull { index, item ->
                            copyMediaToApp(context, entryId, index, item)?.also { copiedImages++ }
                        }
                        CurioRepositoryHolder.repo.save(
                            buildNoteEntry(note, entryId, images, media.map { it.caption })
                        )
                        createdIds += entryId
                        savedNotes++
                    }
                    if (archive.speciesJson.isNotBlank()) {
                        writeSpeciesCatalog(context, archive.speciesJson)
                    }
                    FieldMindRestoreSummary(
                        observations = savedObs,
                        notes = savedNotes,
                        images = copiedImages,
                        species = archive.speciesCount,
                        skipped = skipped
                    )
                } catch (error: Throwable) {
                    createdIds.forEach { id ->
                        runCatching { CurioRepositoryHolder.repo.deleteById(id) }
                        ImageStorageManager.deleteImagesForEntry(context, id)
                    }
                    throw error
                }
            } finally {
                // Package media was extracted to a temp dir — never leave it.
                archive.tempDir?.deleteRecursively()
            }
        }

    /** Absolute path of the imported species catalog, or null before any import. */
    fun speciesCatalogPath(context: Context): String? {
        val file = File(context.filesDir, SPECIES_FILE)
        return if (file.isFile) file.absolutePath else null
    }

    /** Returns the saved catalog for inclusion in a Curio backup, when present. */
    fun speciesCatalogJson(context: Context): String? =
        speciesCatalogPath(context)?.let { path -> File(path).readText() }

    /** Restores a catalog carried by a Curio backup into app-private storage. */
    fun restoreSpeciesCatalog(context: Context, json: String) {
        if (json.isBlank()) return
        writeSpeciesCatalog(context, json)
    }

    // ── Archive reading ─────────────────────────────────────────────────────

    private data class ObsRecord(
        val id: Long,
        val subject: String,
        val category: String,
        val facts: String,
        val evidence: String,
        val context: String,
        val timestamp: Long,
        val confidence: String,
        val location: String,
        val weather: String,
        val tags: List<String>,
        val metadata: String,
        val fieldMindMetadata: FieldMindMetadata
    )

    private data class NoteRecord(
        val id: Long,
        val title: String,
        val body: String,
        val category: String,
        val tags: List<String>,
        val timestamp: Long,
        val metadata: String,
        val fieldMindMetadata: FieldMindMetadata
    )

    private data class MediaFile(
        val file: File,
        val caption: String,
        val mimeType: String
    )

    private data class ParsedArchive(
        val observations: List<ObsRecord>,
        val notes: List<NoteRecord>,
        /** Raw JSON array text of the archive's species — written to disk for later use. */
        val speciesJson: String,
        val speciesCount: Int,
        /** observationId → extracted package media files. */
        val media: Map<Long, List<MediaFile>>,
        /** noteId → extracted package media files. */
        val noteMedia: Map<Long, List<MediaFile>>,
        /** The temp dir package media was extracted into (cleaned up after restore). */
        val tempDir: File? = null
    )

    /** Reads either a `.fieldmind`/`.zip` package or a plain JSON archive. */
    private fun readArchive(context: Context, uri: Uri): ParsedArchive {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Couldn't read the chosen file.")
        // A ZIP package starts with the "PK" magic bytes; otherwise the file
        // is a plain FieldMind JSON archive export.
        return if (bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            readZipPackage(bytes, context)
        } else {
            val tempDir = File(context.cacheDir, "fieldmind_import_${System.currentTimeMillis()}").apply { mkdirs() }
            runCatching {
                parseArchiveJson(String(bytes, Charsets.UTF_8), emptyMap(), emptyMap(), tempDir, context)
            }.onFailure { tempDir.deleteRecursively() }.getOrThrow()
        }
    }

    /**
     * Reads a `.fieldmind` ZIP package: `archive.json` + `media-manifest.json`
     * (captions) in memory, and `media/observations/{id}/<files>` +
     * `media/notes/{id}/<files>` extracted to a temp dir for the restore.
     */
    private fun readZipPackage(bytes: ByteArray, context: Context): ParsedArchive {
        val tempDir = File(context.cacheDir, "fieldmind_import_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        try {
            var archiveJson = ""
            val manifestEntries = mutableMapOf<String, Pair<String, String>>() // path → mime/caption
            val extracted = mutableMapOf<String, File>() // entryPath → file

            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name.replace('\\', '/')
                    when {
                        name == "archive.json" ->
                            archiveJson = zip.readBytes().toString(Charsets.UTF_8)
                        name == "media-manifest.json" -> {
                            val manifest = JSONArray(zip.readBytes().toString(Charsets.UTF_8))
                            for (i in 0 until manifest.length()) {
                                val obj = manifest.getJSONObject(i)
                                val path = "media/${obj.optString("entityType")}s/" +
                                    obj.optLong("entityId") + "/" + obj.optString("fileName")
                                manifestEntries[path] =
                                    obj.optString("mimeType", "application/octet-stream") to
                                        obj.optString("caption")
                            }
                        }
                        name.startsWith("media/observations/") || name.startsWith("media/notes/") -> {
                            val target = File(tempDir, name)
                            val rootPath = tempDir.canonicalPath + File.separator
                            if (!target.canonicalPath.startsWith(rootPath)) {
                                throw SecurityException("Unsafe path in FieldMind archive")
                            }
                            target.parentFile?.mkdirs()
                            target.writeBytes(zip.readBytes())
                            extracted[name] = target
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            fun groupMedia(type: String): Map<Long, List<MediaFile>> {
                val result = mutableMapOf<Long, MutableList<MediaFile>>()
                extracted.forEach { (path, file) ->
                    val parts = path.split("/")
                    if (parts.size >= 4 && parts[0] == "media" && parts[1] == "${type}s") {
                        val id = parts[2].toLongOrNull() ?: return@forEach
                        val (mime, caption) = manifestEntries[path]
                            ?: ("application/octet-stream" to "")
                        result.getOrPut(id) { mutableListOf() }
                            .add(MediaFile(file, caption, mime))
                    }
                }
                return result
            }

            if (archiveJson.isBlank()) {
                throw IllegalArgumentException("That package has no FieldMind archive data.")
            }
            return parseArchiveJson(
                archiveJson,
                groupMedia("observation"),
                groupMedia("note"),
                tempDir,
                context
            )
        } catch (error: Throwable) {
            tempDir.deleteRecursively()
            throw error
        }
    }

    /** Parses the V3 archive JSON (mirrors the legacy `FieldMindExport` keys). */
    private fun parseArchiveJson(
        raw: String,
        media: Map<Long, List<MediaFile>>,
        noteMedia: Map<Long, List<MediaFile>>,
        tempDir: File?,
        context: Context
    ): ParsedArchive {
        val root = runCatching { JSONObject(raw) }
            .getOrElse { throw IllegalArgumentException("That file isn't a FieldMind archive.") }
        val format = root.optString("format")
        if (!format.startsWith("fieldmind-archive")) {
            throw IllegalArgumentException("That file isn't a FieldMind archive.")
        }

        val speciesRecords = root.optJSONArray("species").mapObjects { o ->
            FieldMindSpecies(
                commonName = o.optString("commonName"),
                scientificName = o.optString("scientificName"),
                kingdom = o.optString("kingdom"),
                phylum = o.optString("phylum"),
                className = o.optString("class").ifBlank { o.optString("classs") },
                order = o.optString("order"),
                family = o.optString("family"),
                genus = o.optString("genus"),
                species = o.optString("species"),
                conservationStatus = o.optString("conservationStatus"),
                lifeStage = o.optString("lifeStage").ifBlank { o.optString("life_stage") }
                    .ifBlank { o.optString("stage") },
                sex = o.optString("sex"),
                observationCount = o.optInt("observationCount", -1).takeIf { it >= 0 },
                notes = o.optString("notes")
            )
        }
        fun JSONObject.optionalDouble(key: String): Double? =
            optDouble(key).takeIf { !it.isNaN() }
        fun speciesFor(o: JSONObject): FieldMindSpecies? {
            val details = runCatching { JSONObject(o.optString("structuredDetailsJson")) }.getOrNull()
            val speciesInfo = details?.optJSONObject("speciesInfo")
            val name = details?.optString("speciesName").orEmpty()
                .ifBlank { speciesInfo?.optString("speciesName").orEmpty() }
                .ifBlank { o.optString("subject") }
            val scientific = details?.optString("scientificName").orEmpty()
                .ifBlank { speciesInfo?.optString("scientificName").orEmpty() }
            val lifeStage = details?.optString("lifeStage").orEmpty()
                .ifBlank { details?.optString("life_stage").orEmpty() }
                .ifBlank { details?.optString("stage").orEmpty() }
                .ifBlank { speciesInfo?.optString("lifeStage").orEmpty() }
                .ifBlank { o.optString("lifeStage") }
                .ifBlank { o.optString("life_stage") }
                .ifBlank { o.optString("stage") }
            val sex = details?.optString("sex").orEmpty()
                .ifBlank { speciesInfo?.optString("sex").orEmpty() }
                .ifBlank { o.optString("sex") }
            val matched = speciesRecords.firstOrNull { record ->
                listOf(record.commonName, record.scientificName, record.species)
                    .any { it.isNotBlank() && (it.equals(name, true) || it.equals(scientific, true)) }
            }
            val genericSubject = name.equals("Field observation", true) || name.equals("Observation", true)
            return matched ?: if ((name.isNotBlank() && !genericSubject) || scientific.isNotBlank() || lifeStage.isNotBlank() || sex.isNotBlank()) {
                FieldMindSpecies(
                    commonName = name.takeIf { it.isNotBlank() && !genericSubject }.orEmpty(),
                    scientificName = scientific,
                    conservationStatus = details?.optString("conservationStatus").orEmpty()
                        .ifBlank { speciesInfo?.optString("conservationStatus").orEmpty() }
                        .ifBlank { o.optString("conservationStatus") },
                    lifeStage = lifeStage,
                    sex = sex,
                    notes = speciesInfo?.optString("speciesDescription").orEmpty()
                        .ifBlank { details?.optString("speciesDescription").orEmpty() }
                )
            } else null
        }

        val observations = root.optJSONArray("observations").mapObjects { o ->
            val fieldMind = FieldMindMetadata(
                recordType = "observation",
                category = o.optString("category"),
                confidence = o.optString("confidenceLevel"),
                date = o.optString("date"),
                time = o.optString("time"),
                location = o.optString("manualLocation"),
                latitude = o.optionalDouble("latitude"),
                longitude = o.optionalDouble("longitude"),
                weather = buildWeather(o),
                weatherCondition = o.optString("weatherCondition"),
                weatherTemperature = o.optionalDouble("weatherTemperature"),
                humidity = o.optInt("weatherHumidity", -1).takeIf { it >= 0 },
                windSpeed = o.optionalDouble("weatherWindSpeed"),
                cloudCover = o.optInt("weatherCloudCover", -1).takeIf { it >= 0 },
                pressure = o.optionalDouble("weatherPressure"),
                durationMs = o.optLong("durationMs", -1L).takeIf { it >= 0L },
                startedAt = o.optLong("startedAt", -1L).takeIf { it >= 0L },
                endedAt = o.optLong("endedAt", -1L).takeIf { it >= 0L },
                changeObservedAt = o.optLong("changeObservedAt", -1L).takeIf { it >= 0L },
                changeDurationMs = o.optLong("changeDurationMs", -1L).takeIf { it >= 0L },
                weatherSnapshotAt = o.optLong("weatherSnapshotAt", -1L).takeIf { it >= 0L },
                parentObservationId = o.optLong("parentObservationId", -1L).takeIf { it >= 0L },
                followUpScheduledAt = o.optLong("followUpScheduledAt", -1L).takeIf { it >= 0L },
                archivedAt = o.optLong("archivedAt", -1L).takeIf { it >= 0L },
                deletedAt = o.optLong("deletedAt", -1L).takeIf { it >= 0L },
                createdAt = o.optLong("createdAt", -1L).takeIf { it >= 0L },
                updatedAt = o.optLong("updatedAt", -1L).takeIf { it >= 0L },
                timeNote = o.optString("timeNote"),
                status = o.optString("status"),
                projectId = o.optLong("projectId", -1L).takeIf { it >= 0L },
                sourceId = o.optLong("sourceId", -1L).takeIf { it >= 0L },
                qualityScore = o.optInt("qualityScore", -1).takeIf { it >= 0 },
                tags = splitTags(o.optString("tags")),
                structuredDetailsJson = o.optString("structuredDetailsJson"),
                species = speciesFor(o)
            )
            ObsRecord(
                id = o.optLong("id", 0L),
                subject = o.optString("subject", "Field observation"),
                category = o.optString("category"),
                facts = o.optString("factsOnlyNotes"),
                evidence = o.optString("evidenceSummary"),
                context = o.optString("moodOrContext"),
                timestamp = o.optLong("timestamp", o.optLong("createdAt", System.currentTimeMillis())),
                confidence = o.optString("confidenceLevel"),
                location = o.optString("manualLocation"),
                weather = buildWeather(o),
                tags = splitTags(o.optString("tags")),
                metadata = observationMetadata(o),
                fieldMindMetadata = fieldMind
            )
        }

        val notes = root.optJSONArray("notes").mapObjects { o ->
            val fieldMind = FieldMindMetadata(
                recordType = "note",
                category = o.optString("category"),
                status = o.optString("status"),
                projectId = o.optLong("projectId", -1L).takeIf { it >= 0L },
                sourceId = o.optLong("sourceId", -1L).takeIf { it >= 0L },
                createdAt = o.optLong("createdAt", -1L).takeIf { it >= 0L },
                updatedAt = o.optLong("updatedAt", -1L).takeIf { it >= 0L },
                archivedAt = o.optLong("archivedAt", -1L).takeIf { it >= 0L },
                deletedAt = o.optLong("deletedAt", -1L).takeIf { it >= 0L },
                tags = splitTags(o.optString("tags"))
            )
            NoteRecord(
                id = o.optLong("id", 0L),
                title = o.optString("title", "FieldMind note"),
                body = o.optString("body"),
                category = o.optString("category"),
                tags = splitTags(o.optString("tags")),
                timestamp = o.optLong("timestamp", o.optLong("createdAt", System.currentTimeMillis())),
                metadata = noteMetadata(o),
                fieldMindMetadata = fieldMind
            )
        }

        val species = root.optJSONArray("species")
        val speciesJson = species?.toString(2).orEmpty()
        val jsonMedia = extractJsonMedia(root, context, tempDir)

        if (observations.isEmpty() && notes.isEmpty()) {
            throw IllegalArgumentException(
                "No FieldMind observations or notes found in this archive."
            )
        }
        return ParsedArchive(
            observations = observations,
            notes = notes,
            speciesJson = speciesJson,
            speciesCount = species?.length() ?: 0,
            media = mergeMedia(media, jsonMedia.first),
            noteMedia = mergeMedia(noteMedia, jsonMedia.second),
            tempDir = tempDir
        )
    }

    // ── Entry building ──────────────────────────────────────────────────────

    private fun buildObservationEntry(
        obs: ObsRecord,
        entryId: String,
        images: List<String>,
        attachmentCaptions: List<String> = emptyList()
    ): CurioEntry {
        val name = obs.subject.ifBlank { "Field observation" }
        return CurioEntry(
            id = entryId,
            topic = legacyTopic(entryId, name, obs.category),
            format = CaptureFormat.FieldNotes,
            captureData = CaptureData.FieldNotes(
                observed = obs.facts,
                surprised = joinPreserved(
                    obs.evidence,
                    joinPreserved(obs.metadata, attachmentCaptionText(attachmentCaptions))
                ),
                learnNext = obs.context,
                imageUris = images,
                fieldMindMetadata = obs.fieldMindMetadata
            ),
            capturedAtMillis = obs.timestamp,
            isLegacy = true,
            tags = legacyTags(obs.category, obs.tags).let { base ->
                base + listOfNotNull(
                    obs.confidence.takeIf { it.isNotBlank() && !it.equals("Needs Verification", true) },
                    obs.location.takeIf { it.isNotBlank() },
                    obs.weather.takeIf { it.isNotBlank() }
                )
            }.distinct()
        )
    }

    private fun buildNoteEntry(
        note: NoteRecord,
        entryId: String,
        images: List<String>,
        attachmentCaptions: List<String> = emptyList()
    ): CurioEntry {
        val name = note.title.ifBlank { "FieldMind note" }
        return CurioEntry(
            id = entryId,
            topic = legacyTopic(entryId, name, note.category),
            format = CaptureFormat.Marginalia,
            captureData = CaptureData.Marginalia(
                journalText = joinPreserved(
                    note.body,
                    joinPreserved(note.metadata, attachmentCaptionText(attachmentCaptions))
                ),
                quotes = emptyList(),
                imageUris = images,
                fieldMindMetadata = note.fieldMindMetadata
            ),
            capturedAtMillis = note.timestamp,
            isLegacy = true,
            tags = legacyTags(note.category, note.tags)
        )
    }

    /** A synthetic Wildcard topic carrying the legacy subtype + FieldMind byline. */
    private fun legacyTopic(id: String, name: String, category: String): CurioTopic = CurioTopic(
        id = id,
        categoryId = CategoryId.WILDCARD,
        subtype = LEGACY_SUBTYPE,
        name = name,
        teaser = "Legacy FieldMind observation" +
            (if (category.isNotBlank()) " · $category" else ""),
        imageUrl = "",
        exploreAction = ExploreAction(
            verb = "Explore",
            targetName = name,
            durationMinutes = 30,
            instruction = "Revisit this legacy FieldMind observation: $name"
        ),
        tags = emptyList(),
        tier = 1,
        byline = "FieldMind"
    )

    /** Every restored entry carries the `legacy` tag + its FieldMind category. */
    private fun legacyTags(category: String, archiveTags: List<String>): List<String> = buildList {
        add("legacy")
        if (category.isNotBlank() && !category.equals("Other", true)) add(category)
        archiveTags.forEach { if (it.isNotBlank()) add(it) }
    }

    // ── Media + species persistence ─────────────────────────────────────────

    /** Copies one supported image file into Curio's image storage. */
    private fun copyMediaToApp(context: Context, entryId: String, index: Int, media: MediaFile): String? =
        runCatching {
            val bytes = media.file.readBytes()
            if (bytes.isEmpty()) null else ImageStorageManager.restoreImage(context, entryId, index, bytes)
        }.getOrNull()?.let { Uri.fromFile(File(it)).toString() }

    /**
     * Accept image bytes even when FieldMind exported a misleading filename
     * or generic MIME type (some archives contain image bytes named `.bat`).
     * Content sniffing is the source of truth; extensions are only a fallback
     * for formats Android's BitmapFactory can still decode.
     */
    private fun isSupportedImage(media: MediaFile): Boolean {
        val mime = media.mimeType.lowercase()
        if (mime.startsWith("image/")) return true
        val bytes = runCatching { media.file.readBytes() }.getOrNull() ?: return false
        val hasJpegHeader = bytes.size >= 3 &&
            bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
        val hasPngHeader = bytes.size >= 8 && bytes.copyOf(8).contentEquals(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        )
        val hasGifHeader = bytes.size >= 6 &&
            String(bytes, 0, 6, Charsets.US_ASCII).let { it == "GIF87a" || it == "GIF89a" }
        val hasWebpHeader = bytes.size >= 12 &&
            String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        val hasBmpHeader = bytes.size >= 2 && bytes[0] == 'B'.code.toByte() && bytes[1] == 'M'.code.toByte()
        if (hasJpegHeader || hasPngHeader || hasGifHeader || hasWebpHeader || hasBmpHeader) return true
        // Some valid formats (including HEIC variants and vendor exports) do
        // not have a simple signature we can recognize. Let Android's image
        // decoder decide from the bytes before falling back to the filename.
        if (BitmapFactory.decodeByteArray(bytes, 0, bytes.size) != null) return true
        val name = media.file.name.lowercase()
        return name.endsWith(".jpg") || name.endsWith(".jpeg") ||
            name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif") ||
            name.endsWith(".heic") || name.endsWith(".heif")
    }

    private fun joinPreserved(primary: String, metadata: String): String =
        listOf(primary, metadata).filter { it.isNotBlank() }.joinToString("\n\n")

    private fun attachmentCaptionText(captions: List<String>): String =
        captions.filter { it.isNotBlank() }
            .joinToString("\n")
            .let { if (it.isBlank()) "" else "Attachment captions:\n$it" }

    private fun observationMetadata(o: JSONObject): String = buildList {
        o.optString("date").takeIf { it.isNotBlank() }?.let { add("Date: $it") }
        o.optString("time").takeIf { it.isNotBlank() }?.let { add("Time: $it") }
        o.optDouble("latitude").takeIf { !it.isNaN() }?.let { add("Latitude: $it") }
        o.optDouble("longitude").takeIf { !it.isNaN() }?.let { add("Longitude: $it") }
        o.optString("manualLocation").takeIf { it.isNotBlank() }?.let { add("Location: $it") }
        o.optString("confidenceLevel").takeIf { it.isNotBlank() }?.let { add("Confidence: $it") }
        o.optString("weatherCondition").takeIf { it.isNotBlank() }?.let { add("Weather: $it") }
        o.optDouble("weatherTemperature").takeIf { !it.isNaN() }?.let { add("Temperature: $it°C") }
        o.optInt("weatherHumidity", -1).takeIf { it >= 0 }?.let { add("Humidity: $it%") }
        o.optDouble("weatherWindSpeed").takeIf { !it.isNaN() }?.let { add("Wind: $it") }
        o.optInt("weatherCloudCover", -1).takeIf { it >= 0 }?.let { add("Cloud cover: $it%") }
        o.optDouble("weatherPressure").takeIf { !it.isNaN() }?.let { add("Pressure: $it") }
        o.optLong("weatherSnapshotAt", -1L).takeIf { it >= 0L }?.let { add("Weather snapshot: $it") }
        o.optLong("durationMs", -1L).takeIf { it >= 0L }?.let { add("Duration: ${it}ms") }
        o.optLong("startedAt", -1L).takeIf { it >= 0L }?.let { add("Started at: $it") }
        o.optLong("endedAt", -1L).takeIf { it >= 0L }?.let { add("Ended at: $it") }
        o.optLong("changeObservedAt", -1L).takeIf { it >= 0L }?.let { add("Change observed at: $it") }
        o.optLong("changeDurationMs", -1L).takeIf { it >= 0L }?.let { add("Change duration: ${it}ms") }
        o.optString("timeNote").takeIf { it.isNotBlank() }?.let { add("Time note: $it") }
        o.optString("status").takeIf { it.isNotBlank() }?.let { add("Status: $it") }
        o.optLong("projectId", -1L).takeIf { it >= 0L }?.let { add("Project ID: $it") }
        o.optLong("parentObservationId", -1L).takeIf { it >= 0L }?.let { add("Parent observation: $it") }
        o.optLong("followUpScheduledAt", -1L).takeIf { it >= 0L }?.let { add("Follow-up scheduled: $it") }
        o.optInt("qualityScore", -1).takeIf { it >= 0 }?.let { add("Quality: $it") }
        o.optLong("createdAt", -1L).takeIf { it >= 0L }?.let { add("Created at: $it") }
        o.optLong("updatedAt", -1L).takeIf { it >= 0L }?.let { add("Updated at: $it") }
        o.optLong("archivedAt", -1L).takeIf { it >= 0L }?.let { add("Archived at: $it") }
        o.optLong("deletedAt", -1L).takeIf { it >= 0L }?.let { add("Deleted at: $it") }
        splitTags(o.optString("tags")).takeIf { it.isNotEmpty() }?.let { add("Tags: ${it.joinToString(", ")}") }
    }.joinToString("\n").let { if (it.isBlank()) "" else "FieldMind metadata:\n$it" }

    /**
     * Opens imported media without allowing a JSON archive to read arbitrary
     * filesystem paths. Content-provider URIs are intentionally supported so
     * the existing document-picker flow continues to work. Local paths are
     * accepted only when they already resolve inside this import's temp dir
     * (used by ZIP extraction); all other schemes and paths are rejected.
     */
    private fun openSafeMediaInput(context: Context, tempDir: File, source: String): InputStream? {
        val parsed = Uri.parse(source)
        return when (parsed.scheme?.lowercase()) {
            "content" -> context.contentResolver.openInputStream(parsed)
            "file" -> openContainedLocalFile(tempDir, File(parsed.path ?: return null))
            null -> openContainedLocalFile(tempDir, File(source))
            else -> null
        }
    }

    private fun openContainedLocalFile(root: File, requested: File): InputStream? {
        val rootPath = root.canonicalPath.trimEnd(File.separatorChar) + File.separator
        val candidate = requested.canonicalFile
        if (!candidate.path.startsWith(rootPath) || !candidate.isFile) return null
        return candidate.inputStream()
    }

    private fun noteMetadata(o: JSONObject): String = buildList {
        o.optString("category").takeIf { it.isNotBlank() }?.let { add("Category: $it") }
        splitTags(o.optString("tags")).takeIf { it.isNotEmpty() }?.let { add("Tags: ${it.joinToString(", ")}") }
        o.optString("status").takeIf { it.isNotBlank() }?.let { add("Status: $it") }
        o.optLong("projectId", -1L).takeIf { it >= 0L }?.let { add("Project ID: $it") }
        o.optLong("sourceId", -1L).takeIf { it >= 0L }?.let { add("Source ID: $it") }
        o.optLong("createdAt", -1L).takeIf { it >= 0L }?.let { add("Created at: $it") }
        o.optLong("updatedAt", -1L).takeIf { it >= 0L }?.let { add("Updated at: $it") }
        o.optLong("archivedAt", -1L).takeIf { it >= 0L }?.let { add("Archived at: $it") }
        o.optLong("deletedAt", -1L).takeIf { it >= 0L }?.let { add("Deleted at: $it") }
    }.joinToString("\n").let { if (it.isBlank()) "" else "FieldMind metadata:\n$it" }

    private fun extractJsonMedia(
        root: JSONObject,
        context: Context,
        tempDir: File?
    ): Pair<Map<Long, List<MediaFile>>, Map<Long, List<MediaFile>>> {
        if (tempDir == null) return emptyMap<Long, List<MediaFile>>() to emptyMap()
        val observations = mutableMapOf<Long, MutableList<MediaFile>>()
        val notes = mutableMapOf<Long, MutableList<MediaFile>>()
        fun add(target: MutableMap<Long, MutableList<MediaFile>>, id: Long, uri: String, mime: String, caption: String) {
            if (uri.isBlank()) return
            runCatching {
                val source = openSafeMediaInput(context, tempDir, uri)
                val extension = uri.substringAfterLast('.', "").takeIf { it.length in 2..5 }?.let { ".$it" }.orEmpty()
                val file = File(tempDir, "json_${target.size}_${System.nanoTime()}$extension")
                source?.use { input -> file.outputStream().use { input.copyTo(it) } }
                if (file.isFile && file.length() > 0L) {
                    target.getOrPut(id) { mutableListOf() }.add(MediaFile(file, caption, mime))
                } else file.delete()
            }
        }
        root.optJSONArray("evidenceAttachments")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val sourceUri = o.optString("localPath").ifBlank { o.optString("uri") }
                add(observations, o.optLong("observationId"), sourceUri, o.optString("type").ifBlank { "image/*" }, o.optString("caption"))
            }
        }
        root.optJSONArray("notes")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                o.optString("attachmentUris").split('\n').filter { it.isNotBlank() }.forEach { line ->
                    val parts = line.split("|", limit = 3)
                    if (parts.size >= 3) add(notes, o.optLong("id"), parts[2].trim(), parts[0].trim().ifBlank { "image/*" }, parts[1].trim())
                }
            }
        }
        return observations to notes
    }

    private fun mergeMedia(
        packaged: Map<Long, List<MediaFile>>,
        json: Map<Long, List<MediaFile>>
    ): Map<Long, List<MediaFile>> = buildMap {
        (packaged.keys + json.keys).forEach { id -> put(id, packaged[id].orEmpty() + json[id].orEmpty()) }
    }

    /** Writes the species catalog JSON to app-private storage for later use. */
    private fun writeSpeciesCatalog(context: Context, speciesJson: String) {
        val dir = File(context.filesDir, "fieldmind").apply { mkdirs() }
        val pretty = runCatching { JSONArray(speciesJson).toString(2) }.getOrDefault(speciesJson)
        File(dir, "species.json").writeText(pretty)
    }

    // ── Small helpers ───────────────────────────────────────────────────────

    /** Splits FieldMind's comma/newline-delimited tag strings into clean tags. */
    private fun splitTags(raw: String): List<String> = raw
        .split(',', '\n')
        .map { it.trim() }
        .filter { it.isNotBlank() }

    /** A compact one-line weather summary ("18.5°C · Clear"). */
    private fun buildWeather(o: JSONObject): String = buildList {
        o.optDouble("weatherTemperature").takeIf { !it.isNaN() }?.let {
            add(String.format(java.util.Locale.US, "%.1f°C", it))
        }
        val condition = o.optString("weatherCondition")
        if (condition.isNotBlank()) add(condition)
    }.joinToString(" · ")

}

/** Maps a nullable JSONArray to a typed list, or empty when absent. */
private inline fun <T> org.json.JSONArray?.mapObjects(block: (org.json.JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    val out = mutableListOf<T>()
    for (i in 0 until length()) {
        runCatching { block(getJSONObject(i)) }.getOrNull()?.let { out.add(it) }
    }
    return out
}
