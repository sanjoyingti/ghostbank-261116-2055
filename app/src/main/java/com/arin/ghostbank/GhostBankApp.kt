package com.arin.ghostbank

import android.app.Application
import android.content.Context
import com.arin.ghostbank.data.AppRepository
import com.arin.ghostbank.data.Engine
import com.arin.ghostbank.data.Transport

/**
 * Holds the hand-wired dependencies. No annotation processors, no service locator library.
 */
class AppContainer(context: Context) {
    val repository: AppRepository = AppRepository(context)
    val transport: Transport = Transport(context)
    val engine: Engine = Engine(repository, transport)
}

class GhostBankApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
