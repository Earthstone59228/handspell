package dev.handspell.app.calibration

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.handspell.app.core.model.HandLandmarks
import dev.handspell.app.core.model.Letter
import dev.handspell.app.core.model.NormalizedHand
import dev.handspell.app.vision.HandNormalizer
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.sqrt

/** Local-only normalized vectors explicitly saved by the user; never frames or raw landmarks. */
data class PersonalCalibrationSnapshot(
    val exemplarsByLetter: Map<Letter, List<NormalizedHand>> = emptyMap(),
    /** Lets the UI explain a damaged local file instead of pretending calibration was never saved. */
    val storageIssue: PersonalCalibrationStorageIssue? = null,
) {
    companion object {
        const val MAX_PER_LETTER = 24
    }
}

enum class PersonalCalibrationStorageIssue { Unreadable, Corrupt }

/** Persistence boundary for the later production calibration UI. */
interface PersonalCalibrationStore {
    val snapshot: Flow<PersonalCalibrationSnapshot>
    suspend fun replace(letter: Letter, exemplars: List<NormalizedHand>)
    suspend fun delete(letter: Letter)
    suspend fun clearAll()
}

/**
 * In-memory implementation for a calibration screen preview and JVM tests. It has the same bounds
 * and validation as the persistent store, so a UI cannot accidentally rely on invalid data being
 * accepted only in previews.
 */
class InMemoryPersonalCalibrationStore : PersonalCalibrationStore {
    private val mutableSnapshot = MutableStateFlow(PersonalCalibrationSnapshot())
    override val snapshot: StateFlow<PersonalCalibrationSnapshot> = mutableSnapshot.asStateFlow()

    override suspend fun replace(letter: Letter, exemplars: List<NormalizedHand>) {
        validateExemplars(letter, exemplars)
        mutableSnapshot.value = mutableSnapshot.value.copy(
            exemplarsByLetter = mutableSnapshot.value.exemplarsByLetter + (letter to exemplars.toList()),
            storageIssue = null,
        )
    }

    override suspend fun delete(letter: Letter) {
        mutableSnapshot.value = mutableSnapshot.value.copy(
            exemplarsByLetter = mutableSnapshot.value.exemplarsByLetter - letter,
            storageIssue = null,
        )
    }

    override suspend fun clearAll() {
        mutableSnapshot.value = PersonalCalibrationSnapshot()
    }
}

private val Context.personalCalibrationDataStore by preferencesDataStore(name = "personal_calibration")
private val PERSONAL_CALIBRATION_JSON = stringPreferencesKey("snapshot")

/**
 * Release-safe local persistence for personal calibration. Only the 66 normalized floats selected
 * by a user are stored: no frames, image landmarks, world landmarks, signer id, or timestamps.
 */
class DataStorePersonalCalibrationStore(context: Context) : PersonalCalibrationStore {
    private val appContext = context.applicationContext

    override val snapshot: Flow<PersonalCalibrationSnapshot> = appContext.personalCalibrationDataStore.data
        .map { preferences -> PersonalCalibrationJson.decode(preferences[PERSONAL_CALIBRATION_JSON]) }
        .catch { error ->
            if (error is IOException) {
                emit(PersonalCalibrationSnapshot(storageIssue = PersonalCalibrationStorageIssue.Unreadable))
            } else {
                throw error
            }
        }

    override suspend fun replace(letter: Letter, exemplars: List<NormalizedHand>) {
        validateExemplars(letter, exemplars)
        appContext.personalCalibrationDataStore.edit { preferences ->
            val previous = PersonalCalibrationJson.decode(preferences[PERSONAL_CALIBRATION_JSON])
            val next = previous.copy(
                exemplarsByLetter = previous.exemplarsByLetter + (letter to exemplars.toList()),
                storageIssue = null,
            )
            preferences[PERSONAL_CALIBRATION_JSON] = PersonalCalibrationJson.encode(next)
        }
    }

    override suspend fun delete(letter: Letter) {
        appContext.personalCalibrationDataStore.edit { preferences ->
            val previous = PersonalCalibrationJson.decode(preferences[PERSONAL_CALIBRATION_JSON])
            preferences[PERSONAL_CALIBRATION_JSON] = PersonalCalibrationJson.encode(
                previous.copy(exemplarsByLetter = previous.exemplarsByLetter - letter, storageIssue = null),
            )
        }
    }

    override suspend fun clearAll() {
        appContext.personalCalibrationDataStore.edit { preferences -> preferences.remove(PERSONAL_CALIBRATION_JSON) }
    }
}

/** Backend-only session: UI must call [accept] only while an opted-in user is holding Record. */
data class CalibrationSessionState(
    val letter: Letter? = null,
    val accepted: List<NormalizedHand> = emptyList(),
    val duplicateCount: Int = 0,
    val rejectedCount: Int = 0,
) {
    val canSave get() = accepted.size >= MINIMUM_SAMPLES
    companion object { const val MINIMUM_SAMPLES = 8 }
}

class PersonalCalibrationSession(
    private val normalizer: HandNormalizer,
    private val store: PersonalCalibrationStore,
) {
    private val mutableState = MutableStateFlow(CalibrationSessionState())
    val state: StateFlow<CalibrationSessionState> = mutableState.asStateFlow()

    fun begin(letter: Letter) {
        require(!letter.requiresMotion) { "motion letters cannot be calibrated as static handshapes" }
        mutableState.value = CalibrationSessionState(letter = letter)
    }

    fun accept(landmarks: HandLandmarks) {
        val current = mutableState.value
        if (current.letter == null) return
        val hand = normalizer.normalize(landmarks)
        if (hand == null || !hand.vector.all(Float::isFinite)) {
            mutableState.value = current.copy(rejectedCount = current.rejectedCount + 1)
        } else if (current.accepted.any { distance(it, hand) < DEDUPE_DISTANCE }) {
            mutableState.value = current.copy(duplicateCount = current.duplicateCount + 1)
        } else if (current.accepted.size >= PersonalCalibrationSnapshot.MAX_PER_LETTER) {
            mutableState.value = current.copy(rejectedCount = current.rejectedCount + 1)
        } else {
            mutableState.value = current.copy(accepted = current.accepted + hand)
        }
    }

    suspend fun save(): Boolean {
        val current = mutableState.value
        val letter = current.letter ?: return false
        if (!current.canSave) return false
        store.replace(letter, current.accepted)
        discard()
        return true
    }

    fun discard() { mutableState.value = CalibrationSessionState() }

    private fun distance(a: NormalizedHand, b: NormalizedHand): Float {
        var sum = 0.0
        for (index in a.vector.indices) { val delta = a.vector[index] - b.vector[index]; sum += delta * delta }
        return sqrt(sum).toFloat()
    }

    private companion object { const val DEDUPE_DISTANCE = 0.05f }
}

private fun validateExemplars(letter: Letter, exemplars: List<NormalizedHand>) {
    require(!letter.requiresMotion) { "motion letters cannot be calibrated as static handshapes" }
    require(exemplars.size in CalibrationSessionState.MINIMUM_SAMPLES..PersonalCalibrationSnapshot.MAX_PER_LETTER) {
        "expected ${CalibrationSessionState.MINIMUM_SAMPLES}..${PersonalCalibrationSnapshot.MAX_PER_LETTER} exemplars"
    }
    require(exemplars.all { hand ->
        hand.vector.size == NormalizedHand.VECTOR_DIM && hand.vector.all(Float::isFinite)
    }) { "exemplars must be finite ${NormalizedHand.VECTOR_DIM}-float normalized hands" }
}

private object PersonalCalibrationJson {
    private const val SCHEMA_VERSION = 1
    private const val VERSION = "schemaVersion"
    private const val LETTERS = "letters"
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: PersonalCalibrationSnapshot): String = buildJsonObject {
        put(VERSION, JsonPrimitive(SCHEMA_VERSION))
        put(LETTERS, JsonObject(snapshot.exemplarsByLetter
            .toSortedMap(compareBy { it.ordinal })
            .map { (letter, hands) ->
                letter.name to JsonArray(hands.map { hand -> JsonArray(hand.vector.map(::JsonPrimitive)) })
            }
            .toMap()))
    }.toString()

    fun decode(serialized: String?): PersonalCalibrationSnapshot {
        if (serialized == null) return PersonalCalibrationSnapshot()
        return try {
            val root = json.parseToJsonElement(serialized).jsonObject
            if (root[VERSION]?.jsonPrimitive?.contentOrNull != SCHEMA_VERSION.toString()) {
                PersonalCalibrationSnapshot(storageIssue = PersonalCalibrationStorageIssue.Corrupt)
            } else {
                val letters = root[LETTERS]?.jsonObject ?: throw IllegalArgumentException("missing letters")
                val parsed = buildMap {
                    for ((name, value) in letters) {
                        val letter = Letter.fromNameOrNull(name)
                            ?: throw IllegalArgumentException("unknown letter")
                        if (letter.requiresMotion) throw IllegalArgumentException("motion letter")
                        val hands = value.jsonArray.map(::decodeHand)
                        validateExemplars(letter, hands)
                        put(letter, hands)
                    }
                }
                PersonalCalibrationSnapshot(exemplarsByLetter = parsed)
            }
        } catch (_: IllegalArgumentException) {
            PersonalCalibrationSnapshot(storageIssue = PersonalCalibrationStorageIssue.Corrupt)
        } catch (_: kotlinx.serialization.SerializationException) {
            PersonalCalibrationSnapshot(storageIssue = PersonalCalibrationStorageIssue.Corrupt)
        }
    }

    private fun decodeHand(value: kotlinx.serialization.json.JsonElement): NormalizedHand {
        val values = value.jsonArray.map { element ->
            element.jsonPrimitive.contentOrNull?.toFloatOrNull()
                ?: throw IllegalArgumentException("non-numeric vector value")
        }
        if (values.size != NormalizedHand.VECTOR_DIM || values.any { !it.isFinite() }) {
            throw IllegalArgumentException("invalid vector")
        }
        return NormalizedHand(values.toFloatArray())
    }
}
