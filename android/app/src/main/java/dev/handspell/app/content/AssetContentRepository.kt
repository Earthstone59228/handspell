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
import kotlinx.serialization.json.JsonPrimitive
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
        val supported = supportedLetters().toSet()
        packsState.value
            .asSequence()
            .filter { it.kind == PackKind.DRILL && it.tier == Tier.FREE }
            .flatMap { it.items.asSequence() }
            .filterIsInstance<PackItem.Drill>()
            .filter { it.letter in supported }
            .toList()
    }

    private suspend fun ensureLoaded() = loadMutex.withLock {
        if (loaded) return
        loaded = true
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
                        if (pack.packId != packId) error("pack id does not match index entry")
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
    }

    private fun parsePack(root: JsonObject): ContentPack {
        val schemaVersion = root.int("schemaVersion")
        val packId = root.string("packId")
        if (schemaVersion != SCHEMA_VERSION) {
            throw UnsupportedSchemaException(packId, schemaVersion)
        }
        val kind = enumValue<PackKind>(root.string("kind"))
        val tier = enumValue<Tier>(root.string("tier"))
        return ContentPack(
            schemaVersion = schemaVersion,
            packId = packId,
            packVersion = root.int("packVersion"),
            kind = kind,
            tier = tier,
            title = root.string("title"),
            summary = root.string("summary"),
            releasedOn = root.string("releasedOn"),
            items = root.array("items").map { parseItem(it.jsonObject, kind) },
        )
    }

    private fun parseItem(item: JsonObject, kind: PackKind): PackItem = when (kind) {
        PackKind.DRILL -> PackItem.Drill(
            id = item.string("id"),
            letter = Letter.fromNameOrNull(item.string("letter")) ?: error("unknown drill letter"),
            prompt = item.string("prompt"),
            description = item.string("description"),
            hintId = item["hintId"]?.jsonPrimitive?.content,
        )
        PackKind.STORY -> PackItem.StoryStep(
            id = item.string("id"),
            narration = item["narration"]?.jsonPrimitive?.contentOrNull,
            spellWord = item["spellWord"]?.jsonPrimitive?.contentOrNull,
            letters = item.array("letters").map { letter(it.jsonPrimitive) },
        )
        PackKind.SPEED -> PackItem.SpeedRound(
            id = item.string("id"),
            title = item.string("title"),
            durationSeconds = item.int("durationSeconds"),
            letters = item.array("letters").map { letter(it.jsonPrimitive) },
            targetCorrect = item.int("targetCorrect"),
        )
    }

    private fun letter(value: JsonPrimitive): Letter =
        Letter.fromNameOrNull(value.content) ?: error("unknown letter ${value.content}")

    private fun readObject(path: String): JsonObject =
        assets.open(path).bufferedReader().use { Json.parseToJsonElement(it.readText()).jsonObject }

    private fun JsonObject.string(name: String): String = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String): Int = getValue(name).jsonPrimitive.int
    private fun JsonObject.array(name: String) = getValue(name).jsonArray

    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: error("unknown ${T::class.simpleName} $value")

    private class UnsupportedSchemaException(val packId: String, val found: Int) :
        IllegalArgumentException("unsupported schema $found for $packId")

    private companion object {
        const val INDEX_PATH = "content/index.json"
        const val SCHEMA_VERSION = 1
    }
}
