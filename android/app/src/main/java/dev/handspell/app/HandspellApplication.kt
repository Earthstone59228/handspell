package dev.handspell.app

import android.app.Application
import dev.handspell.app.di.AppContainer

/**
 * Builds [AppContainer] and nothing else. RevenueCat configuration is a separate workstream
 * (docs/CONTRACTS.md §6); this class does not touch it.
 */
class HandspellApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
