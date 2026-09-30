package dev.handspell.app.content

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentPackParserTest {
    private fun parse(text: String) = ContentPackParser.parse(Json.parseToJsonElement(text).jsonObject)
    private fun bundled(name: String) = parse(File("src/main/assets/content/packs/$name.json").readText())

    @Test fun allThreeStoriesLoadNarrationWithoutLettersAndFiveWordPrompts() {
        listOf("story-market-morning", "story-river-walk", "story-bedtime-routine").forEach { name ->
            val pack = bundled(name)
            assertEquals(PackKind.STORY, pack.kind)
            assertEquals(Tier.PRO, pack.tier)
            val steps = pack.items.filterIsInstance<PackItem.StoryStep>()
            assertEquals(10, steps.size)
            assertEquals(5, steps.count { !it.narration.isNullOrBlank() && it.letters.isEmpty() })
            assertEquals(5, steps.count { !it.spellWord.isNullOrBlank() && it.letters.isNotEmpty() })
            steps.filter { it.letters.isNotEmpty() }.forEach {
                assertEquals(it.spellWord, it.letters.joinToString("") { letter -> letter.name })
                assertTrue(it.letters.none { letter -> letter.requiresMotion })
            }
        }
    }

    @Test fun alphabetDrillsIncludeEveryLetterAndMotionInstructions() {
        val drills = bundled("drills-core").items.filterIsInstance<PackItem.Drill>()
        assertEquals(dev.handspell.app.core.model.Letter.entries.toSet(), drills.map { it.letter }.toSet())
        assertEquals(26, drills.size)
        assertTrue(drills.filter { it.letter.requiresMotion }.all { it.description.contains("Hold briefly") })
    }

    @Test fun speedPackContainsThreePlayableStaticRounds() {
        val rounds = bundled("speed-core").items.filterIsInstance<PackItem.SpeedRound>()
        assertEquals(listOf(30, 60, 60), rounds.map { it.durationSeconds })
        assertEquals(24, rounds.single { it.id == "round-all-letters" }.letters.distinct().size)
        assertTrue(rounds.all { it.letters.isNotEmpty() && it.letters.none { letter -> letter.requiresMotion } })
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMismatchBetweenWordAndCameraPrompts() {
        val source = File("src/main/assets/content/packs/story-market-morning.json").readText()
        parse(source.replace("\"spellWord\": \"BREAD\"", "\"spellWord\": \"BRAD\""))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsMotionLettersInStaticSpeedSessions() {
        val source = File("src/main/assets/content/packs/speed-core.json").readText()
        parse(source.replace("\"B\"", "\"J\""))
    }
}
