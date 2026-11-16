package com.arin.ghostbank

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arin.ghostbank.ui.AppViewModel
import com.arin.ghostbank.ui.GhostNavHost
import com.arin.ghostbank.ui.theme.GhostBankTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GhostBankApp).container
        setContent {
            GhostBankTheme {
                val vm: AppViewModel = viewModel(factory = AppViewModel.factory(container))
                GhostNavHost(vm = vm)
            }
        }
    }
}
