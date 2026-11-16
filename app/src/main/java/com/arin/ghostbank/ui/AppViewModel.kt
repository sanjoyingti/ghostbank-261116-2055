package com.arin.ghostbank.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.arin.ghostbank.AppContainer
import com.arin.ghostbank.data.BankCatalog
import com.arin.ghostbank.data.Profiles
import com.arin.ghostbank.model.AccountStatus
import com.arin.ghostbank.model.Destination
import com.arin.ghostbank.model.HarvestAccount
import com.arin.ghostbank.model.LedgerEntry
import com.arin.ghostbank.model.LeakReport
import com.arin.ghostbank.model.ProxyConfig
import com.arin.ghostbank.model.ProxyScheme
import com.arin.ghostbank.model.Run
import com.arin.ghostbank.model.Settings
import com.arin.ghostbank.model.StealthLevel
import com.arin.ghostbank.model.Validation
import com.arin.ghostbank.model.VirtualAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.UUID

data class UiState(
    val booted: Boolean = false,
    val settings: Settings = Settings(),
    val destination: Destination? = null,
    val accounts: List<HarvestAccount> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val runs: List<Run> = emptyList(),
    val leak: LeakReport? = null,
    val activeRun: Run? = null,
    val rawLog: List<String> = emptyList(),
    val running: Boolean = false,
    val leakRunning: Boolean = false,
    val selectedBankCode: String? = null,
    val transferSourceId: String? = null,
    val harvestSelection: Set<String> = emptySet(),
    val message: String? = null,
    val lastFinishedRunId: String? = null
) {
    val harvestedPaise: Long
        get() = accounts.filter { it.status == AccountStatus.SETTLED || it.status == AccountStatus.FLOOR }
            .sumOf { it.amountPaise }

    val pendingAccounts: List<HarvestAccount>
        get() = accounts.filter { it.status == AccountStatus.PENDING || it.status == AccountStatus.REJECTED }
}

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val repo = container.repository
    private val engine = container.engine
    private val transport = container.transport

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            engine.active.collect { run -> _state.update { it.copy(activeRun = run) } }
        }
        viewModelScope.launch {
            engine.rawLog.collect { log -> _state.update { it.copy(rawLog = log) } }
        }
        viewModelScope.launch { reloadAll() }
    }

    private suspend fun reloadAll() {
        val settings = repo.settings()
        val destination = repo.destination()
        val accounts = repo.accounts()
        val ledger = repo.ledger()
        val runs = repo.runs()
        val leak = repo.leak()
        _state.update {
            it.copy(
                settings = settings,
                destination = destination,
                accounts = accounts,
                ledger = ledger,
                runs = runs,
                leak = leak,
                booted = true
            )
        }
    }

    private suspend fun reloadAccounts() {
        val accounts = repo.accounts()
        _state.update { it.copy(accounts = accounts) }
    }

    private suspend fun reloadRuns() {
        val runs = repo.runs()
        _state.update { it.copy(runs = runs) }
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun selectBank(code: String?) = _state.update { it.copy(selectedBankCode = code) }

    fun selectTransferSource(id: String?) = _state.update { it.copy(transferSourceId = id) }

    fun toggleHarvestSelection(id: String) = _state.update { current ->
        val next = if (id in current.harvestSelection) current.harvestSelection - id
        else current.harvestSelection + id
        current.copy(harvestSelection = next)
    }

    fun selectAllPending() = _state.update { current ->
        current.copy(harvestSelection = current.pendingAccounts.map { it.id }.toSet())
    }

    fun clearSelection() = _state.update { it.copy(harvestSelection = emptySet()) }

    fun completeOnboarding() {
        viewModelScope.launch {
            val settings = repo.updateSettings { it.copy(onboarded = true) }
            _state.update { it.copy(settings = settings) }
        }
    }

    fun updateSettings(block: (Settings) -> Settings) {
        viewModelScope.launch {
            val settings = repo.updateSettings(block)
            _state.update { it.copy(settings = settings) }
        }
    }

    fun setStealthLevel(level: StealthLevel) = updateSettings { it.copy(stealthLevel = level) }

    fun saveDestination(number: String, ifsc: String, holder: String): Boolean {
        if (Validation.accountNumber(number) != null) return false
        if (Validation.ifsc(ifsc) != null) return false
        val destination = Destination(number.trim(), ifsc.trim().uppercase(), holder.trim())
        viewModelScope.launch {
            repo.saveDestination(destination)
            _state.update { it.copy(destination = destination, message = "Destination locked. It stays fixed.") }
        }
        return true
    }

    private fun fingerprint(bankCode: String, number: String, ifsc: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$bankCode|$number|$ifsc".toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }.take(32)
    }

    fun addAccount(bankCode: String?, number: String, ifsc: String, label: String): Boolean {
        val code = bankCode
        if (code == null) {
            _state.update { it.copy(message = "Pick the bank first.") }
            return false
        }
        val bank = BankCatalog.byCode(code)
        if (bank == null) {
            _state.update { it.copy(message = "Unknown bank.") }
            return false
        }
        if (Validation.accountNumber(number) != null) return false
        if (Validation.ifsc(ifsc) != null) return false
        val cleanNumber = number.trim()
        val cleanIfsc = ifsc.trim().uppercase()
        val print = fingerprint(bank.code, cleanNumber, cleanIfsc)
        if (_state.value.accounts.any { it.fingerprint == print }) {
            _state.update { it.copy(message = "Already fingerprinted — one account, one paisa, ever.") }
            return false
        }
        val account = HarvestAccount(
            id = UUID.randomUUID().toString(),
            bankCode = bank.code,
            bankName = bank.name,
            number = cleanNumber,
            ifsc = cleanIfsc,
            label = label.trim().ifBlank { "Acct " + cleanNumber.takeLast(4) },
            fingerprint = print
        )
        viewModelScope.launch {
            val accounts = repo.upsertAccount(account)
            _state.update { it.copy(accounts = accounts, message = "Account saved and marked PENDING.") }
        }
        return true
    }

    fun removeAccount(id: String) {
        viewModelScope.launch {
            val accounts = repo.removeAccount(id)
            _state.update { it.copy(accounts = accounts) }
        }
    }

    fun clearLedger() {
        viewModelScope.launch {
            repo.clearLedger()
            _state.update { it.copy(ledger = emptyList()) }
        }
    }

    fun addProxy(scheme: ProxyScheme, host: String, port: Int, country: String, timeZone: String): Boolean {
        if (Validation.proxyHost(host) != null) return false
        if (port !in 1..65535) return false
        updateSettings { current ->
            current.copy(
                proxies = current.proxies + ProxyConfig(
                    scheme = scheme,
                    host = host.trim(),
                    port = port,
                    country = country.trim().uppercase(),
                    timeZone = timeZone.trim()
                )
            )
        }
        _state.update { it.copy(message = "Exit added to the rotating pool.") }
        return true
    }

    fun removeProxy(index: Int) {
        updateSettings { current -> current.copy(proxies = current.proxies.filterIndexed { i, _ -> i != index }) }
    }

    fun addVirtualAccount(bankCode: String, number: String, ifsc: String, label: String): Boolean {
        if (Validation.accountNumber(number) != null) return false
        if (Validation.ifsc(ifsc) != null) return false
        val va = VirtualAccount(
            id = UUID.randomUUID().toString(),
            bankCode = bankCode,
            number = number.trim(),
            ifsc = ifsc.trim().uppercase(),
            label = label.trim().ifBlank { "VA " + number.trim().takeLast(4) }
        )
        updateSettings { current -> current.copy(virtualAccounts = current.virtualAccounts + va) }
        _state.update { it.copy(message = "Virtual account added — the real digits now stay off the rail.") }
        return true
    }

    fun removeVirtualAccount(id: String) {
        updateSettings { current -> current.copy(virtualAccounts = current.virtualAccounts.filterNot { it.id == id }) }
    }

    fun resetActiveRun() {
        engine.clearRun()
        _state.update { it.copy(activeRun = null) }
    }

    fun startTransfer(amountPaise: Long, bankCode: String, sourceId: String?) {
        if (_state.value.running) return
        val destination = _state.value.destination
        if (destination == null) {
            _state.update { it.copy(message = "Set your destination account first.") }
            return
        }
        val settings = _state.value.settings
        val source = _state.value.accounts.firstOrNull { it.id == sourceId }
        _state.update { it.copy(running = true, message = null, lastFinishedRunId = null) }
        viewModelScope.launch {
            runCatching {
                engine.transfer(
                    amountPaise = amountPaise,
                    bankCode = bankCode,
                    source = source,
                    settings = settings,
                    destination = destination
                )
            }.onSuccess { run ->
                reloadRuns()
                _state.update { it.copy(lastFinishedRunId = run.id, message = run.error) }
            }.onFailure { throwable ->
                _state.update { it.copy(message = "Run failed: ${throwable.message ?: throwable.javaClass.simpleName}") }
            }
            _state.update { it.copy(running = false) }
        }
    }

    fun startHarvest() {
        if (_state.value.running) return
        val selected = _state.value.harvestSelection.toList()
        val ids: List<String> = if (selected.isNotEmpty()) selected else _state.value.pendingAccounts.map { it.id }
        if (ids.isEmpty()) {
            _state.update { it.copy(message = "Nothing pending — every account is already harvested.") }
            return
        }
        val settings = _state.value.settings
        _state.update { it.copy(running = true, message = null, lastFinishedRunId = null) }
        viewModelScope.launch {
            val poller = launch {
                while (true) {
                    reloadAccounts()
                    delay(700)
                }
            }
            runCatching { engine.harvest(ids, settings) }
                .onSuccess { run ->
                    _state.update { it.copy(lastFinishedRunId = run.id, message = run.error) }
                }
                .onFailure { throwable ->
                    _state.update { it.copy(message = "Harvest failed: ${throwable.message}") }
                }
            poller.cancel()
            reloadAccounts()
            reloadRuns()
            _state.update { it.copy(running = false, harvestSelection = emptySet()) }
        }
    }

    fun checkLeaks() {
        if (_state.value.leakRunning) return
        val settings = _state.value.settings
        _state.update { it.copy(leakRunning = true) }
        viewModelScope.launch {
            val profile = Profiles.freeze("leak-check", transport.userAgent, settings)
            val report = runCatching { transport.leakCheck(settings, profile) }.getOrElse { throwable ->
                LeakReport(ok = false, error = throwable.message ?: throwable.javaClass.simpleName)
            }
            repo.saveLeak(report)
            _state.update { it.copy(leak = report, leakRunning = false) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(container) as T
        }
    }
}
