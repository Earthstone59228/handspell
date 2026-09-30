package dev.handspell.app.content

import android.content.res.AssetManager
import dev.handspell.app.core.model.Letter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads the bundled content index and packs. Asset reads stay here rather than in a screen so a
 * malformed pack becomes an explicit [ContentError], never a silent empty practice grid.
 */
class AssetContentRepository(
    private val assets: AssetManager,
    private val supportedLetters: () -> List<Letter>,
) : ContentRepository {
    private val loadMutex = Mutex()
    private var loaded = false
    private val packsState = MutableStateFlow<List<ContentPack>>(emptyList())
    private val errorsState = MutableStateFlow<List<ContentError>>(emptyList())

    override val packs = packsState.asStateFlow()
    override val errors = errorsState.asStateFlow()

    override suspend fun pack(packId: String): ContentPack? = withContext(Dispatchers.IO) {
        ensureLoaded()
        packsState.value.firstOrNull { it.packId == packId }
    }

    override suspend fun availableDrills(): List<PackItem.Drill> = withContext(Dispatchers.IO) {
        ensureLoaded()
        practiceDrills(packsState.value, supportedLetters())
    }

    private suspend fun ensureLoaded(): Unit = loadMutex.withLock {
        if (loaded) return@withLock
        val errors = mutableListOf<ContentError>()
        val parsedPacks = mutableListOf<ContentPack>()
        try {
            val index = readObject(INDEX_PATH)
            val schemaVersion = index.int("schemaVersion")
            if (schemaVersion != SCHEMA_VERSION) {
                errors += ContentError.UnsupportedSchema("index", schemaVersion, SCHEMA_VERSION)
            } else {
                index.array("packs").forEach { refElement ->
                    val ref = refElement.jsonObject
                    val packId = ref.string("packId")
                    try {
                        val pack = parsePack(readObject(ref.string("path")))
                        if (pack.packId != packId || !pack.kind.name.equals(ref.string("kind"), true) ||
                            !pack.tier.name.equals(ref.string("tier"), true)) error("pack does not match index entry")
                        parsedPacks += pack
                    } catch (failure: UnsupportedSchemaException) {
                        errors += ContentError.UnsupportedSchema(
                            failure.packId,
                            failure.found,
                            SCHEMA_VERSION,
                        )
                    } catch (failure: Throwable) {
                        errors += ContentError.Unreadable(packId, failure)
                    }
                }
            }
        } catch (failure: Throwable) {
            errors += ContentError.Unreadable("index", failure)
        }
        packsState.value = parsedPacks
        errorsState.value = errors
        // Assets are immutable in a normal APK, but do not turn a transient/read failure into a
        // permanently cached blank screen: the Home retry action must perform another read.
        loaded = errors.isEmpty()
    }

    private fun parsePack(root: JsonObject): ContentPack {
        val schemaVersion = root.int("schemaVersion")
        val packId = root.string("packId")
        if (schemaVersion != SCHEMA_VERSION) {
            throw UnsupportedSchemaException(packId, schemaVersion)
        }
        return ContentPackParser.parse(root)
    }

    private fun readObject(path: String): JsonObject =
        assets.open(path).bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int
    private fun JsonObject.array(name: String) = getValue(name).jsonArray

    private class UnsupportedSchemaException(val packId: String, val found: Int) :
        IllegalArgumentException("unsupported schema $found for $packId")

    private companion object {
        const val INDEX_PATH = "content/index.json"
        const val SCHEMA_VERSION = 1
    }
}

/** Motion drills use the camera's path recognizer, so static classifier labels must not exclude them. */
internal fun practiceDrills(packs: List<ContentPack>, staticLetters: List<Letter>): List<PackItem.Drill> {
    if (staticLetters.isEmpty()) return emptyList()
    val supported = (staticLetters + listOf(Letter.J, Letter.Z)).toSet()
    return packs.asSequence()
        .filter { it.kind == PackKind.DRILL && it.tier == Tier.FREE }
        .flatMap { it.items.asSequence() }
        .filterIsInstance<PackItem.Drill>()
        .filter { it.letter in supported }
        .toList()
}
