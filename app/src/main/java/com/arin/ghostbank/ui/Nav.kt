package com.arin.ghostbank.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arin.ghostbank.ui.screens.AccountSetupScreen
import com.arin.ghostbank.ui.screens.AuditScreen
import com.arin.ghostbank.ui.screens.BankPickerScreen
import com.arin.ghostbank.ui.screens.HarvestScreen
import com.arin.ghostbank.ui.screens.HomeScreen
import com.arin.ghostbank.ui.screens.LeakLabScreen
import com.arin.ghostbank.ui.screens.LedgerScreen
import com.arin.ghostbank.ui.screens.OnboardingScreen
import com.arin.ghostbank.ui.screens.ScoreScreen
import com.arin.ghostbank.ui.screens.ShieldScreen
import com.arin.ghostbank.ui.screens.SplashScreen
import com.arin.ghostbank.ui.screens.StealthScreen
import com.arin.ghostbank.ui.screens.TransferScreen

object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val ACCOUNT = "account"
    const val BANKS = "banks/{target}"
    const val TRANSFER = "transfer"
    const val HARVEST = "harvest"
    const val LEDGER = "ledger"
    const val SHIELD = "shield"
    const val LEAK = "leak"
    const val STEALTH = "stealth"
    const val AUDIT = "audit/{runId}"
    const val SCORE = "score/{runId}"

    fun banks(target: String) = "banks/$target"
    fun audit(runId: String) = "audit/$runId"
    fun score(runId: String) = "score/$runId"
}

@Composable
fun GhostNavHost(vm: AppViewModel) {
    val navController = rememberNavController()
    val state by vm.state.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen()
            LaunchedEffect(state.booted) {
                if (state.booted) {
                    val next = if (state.settings.onboarded) Routes.HOME else Routes.ONBOARDING
                    navController.navigate(next) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            }
        }

        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onFinish = {
                    vm.completeOnboarding()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onTransfer = { navController.navigate(Routes.TRANSFER) },
                onHarvest = { navController.navigate(Routes.HARVEST) },
                onLedger = { navController.navigate(Routes.LEDGER) },
                onAccounts = { navController.navigate(Routes.ACCOUNT) },
                onShield = { navController.navigate(Routes.SHIELD) },
                onLeak = { navController.navigate(Routes.LEAK) },
                onStealth = { navController.navigate(Routes.STEALTH) },
                onAudit = { runId -> navController.navigate(Routes.audit(runId)) },
                onDismissMessage = vm::dismissMessage
            )
        }

        composable(Routes.ACCOUNT) {
            AccountSetupScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onPickBank = { navController.navigate(Routes.banks("account")) },
                onSaveDestination = vm::saveDestination,
                onAddAccount = vm::addAccount,
                onRemoveAccount = vm::removeAccount,
                onDismissMessage = vm::dismissMessage
            )
        }

        composable(
            route = Routes.BANKS,
            arguments = listOf(navArgument("target") { type = NavType.StringType })
        ) {
            BankPickerScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onPick = { code ->
                    vm.selectBank(code)
                    navController.popBackStack()
                }
            )
        }

        composable(Routes.TRANSFER) {
            TransferScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onPickBank = { navController.navigate(Routes.banks("transfer")) },
                onSelectSource = vm::selectTransferSource,
                onStart = vm::startTransfer,
                onReset = vm::resetActiveRun,
                onAudit = { runId -> navController.navigate(Routes.audit(runId)) },
                onScore = { runId -> navController.navigate(Routes.score(runId)) },
                onDismissMessage = vm::dismissMessage
            )
        }

        composable(Routes.HARVEST) {
            HarvestScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onToggle = vm::toggleHarvestSelection,
                onSelectAll = vm::selectAllPending,
                onClearSelection = vm::clearSelection,
                onStart = vm::startHarvest,
                onAccounts = { navController.navigate(Routes.ACCOUNT) },
                onAudit = { runId -> navController.navigate(Routes.audit(runId)) },
                onDismissMessage = vm::dismissMessage
            )
        }

        composable(Routes.LEDGER) {
            LedgerScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onClear = vm::clearLedger,
                onAudit = { runId -> navController.navigate(Routes.audit(runId)) }
            )
        }

        composable(Routes.SHIELD) {
            ShieldScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onUpdate = vm::updateSettings,
                onAddProxy = vm::addProxy,
                onRemoveProxy = vm::removeProxy,
                onAddVirtualAccount = vm::addVirtualAccount,
                onRemoveVirtualAccount = vm::removeVirtualAccount,
                onLeak = { navController.navigate(Routes.LEAK) },
                onPickBank = { navController.navigate(Routes.banks("va")) }
            )
        }

        composable(Routes.LEAK) {
            LeakLabScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onCheck = vm::checkLeaks
            )
        }

        composable(Routes.STEALTH) {
            StealthScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onLevel = vm::setStealthLevel,
                onUpdate = vm::updateSettings
            )
        }

        composable(
            route = Routes.AUDIT,
            arguments = listOf(navArgument("runId") { type = NavType.StringType })
        ) { entry ->
            val runId = entry.arguments?.getString("runId").orEmpty()
            AuditScreen(
                state = state,
                runId = runId,
                onBack = { navController.popBackStack() },
                onScore = { navController.navigate(Routes.score(it)) }
            )
        }

        composable(
            route = Routes.SCORE,
            arguments = listOf(navArgument("runId") { type = NavType.StringType })
        ) { entry ->
            val runId = entry.arguments?.getString("runId").orEmpty()
            ScoreScreen(state = state, runId = runId, onBack = { navController.popBackStack() })
        }
    }
}
