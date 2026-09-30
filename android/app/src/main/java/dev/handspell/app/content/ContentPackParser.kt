package dev.handspell.app.content

import dev.handspell.app.core.model.Letter
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Validates bundled lessons before a camera session can start. */
internal object ContentPackParser {
    fun parse(root: JsonObject): ContentPack {
        val kind = enumValue<PackKind>(root.string("kind"))
        val items = root.array("items").map { value ->
            val item = value.jsonObject
            val id = item.string("id").also { require(it.isNotBlank()) }
            when (kind) {
                PackKind.DRILL -> PackItem.Drill(id, letter(item.getValue("letter").jsonPrimitive, allowMotion = true),
                    item.string("prompt"), item.string("description"), item["hintId"]?.jsonPrimitive?.contentOrNull)
                PackKind.STORY -> {
                    val narration = item["narration"]?.jsonPrimitive?.contentOrNull
                    val word = item["spellWord"]?.jsonPrimitive?.contentOrNull
                    // Narration beats deliberately omit letters in the authored content.
                    val letters = item["letters"]?.jsonArray?.map { letter(it.jsonPrimitive) }.orEmpty()
                    require(!narration.isNullOrBlank() || !word.isNullOrBlank()) { "empty story step" }
                    if (word != null) {
                        require(letters.isNotEmpty() && letters.joinToString("") { it.name } == word) {
                            "word does not match its prompts"
                        }
                    } else require(letters.isEmpty()) { "narration has prompts but no word" }
                    PackItem.StoryStep(id, narration, word, letters)
                }
                PackKind.SPEED -> PackItem.SpeedRound(
                    id, item.string("title"), item.int("durationSeconds"),
                    item.array("letters").map { letter(it.jsonPrimitive) }, item.int("targetCorrect"),
                ).also {
                    require(it.title.isNotBlank() && it.durationSeconds > 0 && it.targetCorrect >= 0 && it.letters.isNotEmpty()) {
                        "invalid speed round"
                    }
                }
            }
        }
        require(items.map { it.id }.distinct().size == items.size) { "duplicate item ids" }
        return ContentPack(root.int("schemaVersion"), root.string("packId"), root.int("packVersion"),
            kind, enumValue<Tier>(root.string("tier")), root.string("title"), root.string("summary"),
            root.string("releasedOn"), items).also {
            require(it.packId.isNotBlank() && it.title.isNotBlank() && it.packVersion > 0)
        }
    }

    private fun letter(value: JsonPrimitive, allowMotion: Boolean = false): Letter =
        (Letter.fromNameOrNull(value.content) ?: error("unknown letter")).also {
            require(allowMotion || !it.requiresMotion) { "motion letters cannot be used in static lessons" }
        }

    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int
    private fun JsonObject.array(name: String) = getValue(name).jsonArray
    private inline fun <reified T : Enum<T>> enumValue(value: String): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: error("unknown ${T::class.simpleName}")
}
