package com.arin.ghostbank.data

import android.content.Context
import com.arin.ghostbank.model.Destination
import com.arin.ghostbank.model.HarvestAccount
import com.arin.ghostbank.model.LedgerEntry
import com.arin.ghostbank.model.LeakReport
import com.arin.ghostbank.model.Run
import com.arin.ghostbank.model.Settings
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable

class AppRepository(context: Context) {

    private val appContext = context.applicationContext

    private val settingsStore = JsonFileStore(appContext, "settings.enc", Settings.serializer()) { Settings() }
    private val destinationStore =
        JsonFileStore<Destination?>(appContext, "destination.enc", Destination.serializer().nullable) { null }
    private val accountsStore =
        JsonFileStore(appContext, "accounts.enc", ListSerializer(HarvestAccount.serializer())) { emptyList() }
    private val ledgerStore =
        JsonFileStore(appContext, "ledger.enc", ListSerializer(LedgerEntry.serializer())) { emptyList() }
    private val runsStore =
        JsonFileStore(appContext, "runs.enc", ListSerializer(Run.serializer())) { emptyList() }
    private val leakStore = JsonFileStore<LeakReport?>(appContext, "leak.enc", LeakReport.serializer().nullable) { null }

    suspend fun settings(): Settings = settingsStore.read()

    suspend fun saveSettings(settings: Settings) = settingsStore.write(settings)

    suspend fun updateSettings(block: (Settings) -> Settings): Settings = settingsStore.update(block)

    suspend fun destination(): Destination? = destinationStore.read()

    suspend fun saveDestination(destination: Destination) = destinationStore.write(destination)

    suspend fun accounts(): List<HarvestAccount> = accountsStore.read()

    suspend fun saveAccounts(accounts: List<HarvestAccount>) = accountsStore.write(accounts)

    suspend fun updateAccount(id: String, block: (HarvestAccount) -> HarvestAccount): List<HarvestAccount> =
        accountsStore.update { list -> list.map { if (it.id == id) block(it) else it } }

    suspend fun upsertAccount(account: HarvestAccount): List<HarvestAccount> =
        accountsStore.update { list ->
            if (list.any { it.fingerprint == account.fingerprint }) list else list + account
        }

    suspend fun removeAccount(id: String): List<HarvestAccount> =
        accountsStore.update { list -> list.filterNot { it.id == id } }

    suspend fun ledger(): List<LedgerEntry> = ledgerStore.read()

    suspend fun addLedger(entries: List<LedgerEntry>): List<LedgerEntry> =
        ledgerStore.update { it + entries }

    suspend fun clearLedger() = ledgerStore.write(emptyList())

    suspend fun runs(): List<Run> = runsStore.read()

    suspend fun saveRun(run: Run): List<Run> = runsStore.update { existing ->
        val merged = existing.filterNot { it.id == run.id } + run
        merged.sortedByDescending { it.createdAt }.take(120)
    }

    suspend fun run(id: String): Run? = runsStore.read().firstOrNull { it.id == id }

    suspend fun leak(): LeakReport? = leakStore.read()

    suspend fun saveLeak(report: LeakReport) = leakStore.write(report)
}
