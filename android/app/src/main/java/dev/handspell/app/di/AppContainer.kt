package dev.handspell.app.di

import dev.handspell.app.vision.FakeSignDetector
import dev.handspell.app.vision.SignDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers

/**
 * Hand-written dependency graph (docs/ARCHITECTURE.md §6) — no Hilt. Constructed once in
 * [dev.handspell.app.HandspellApplication].
 *
 * [signDetector] is [FakeSignDetector] today; swapping in `CameraSignDetector` once the vision
 * pipeline lands is a one-line change here, per docs/CONTRACTS.md §6.
 */
class AppContainer {
    private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val signDetector: SignDetector = FakeSignDetector(applicationScope)
}
