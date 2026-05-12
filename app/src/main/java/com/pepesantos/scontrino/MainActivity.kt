package com.pepesantos.scontrino

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.pepesantos.scontrino.ui.screens.ReceiptsScreen
import com.pepesantos.scontrino.ui.screens.WalletScreen
import com.pepesantos.scontrino.ui.screens.StatsScreen
import com.pepesantos.scontrino.ui.theme.ScontrinoTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ScontrinoTheme {
                ScontrinoApp()
            }
        }
    }
}

@Composable
fun ScontrinoApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.SCONTRINI) }
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            imageVector = it.icon,
                            contentDescription = stringResource(it.label)
                        )
                    },
                    label = { Text(stringResource(it.label)) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestinations.SCONTRINI -> ReceiptsScreen()
            AppDestinations.STATS -> StatsScreen()
            AppDestinations.WALLET -> WalletScreen()
        }
    }
}

enum class AppDestinations(
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    SCONTRINI(R.string.nav_receipts, Icons.Filled.ListAlt),
    STATS(R.string.nav_stats, Icons.Filled.BarChart),
    WALLET(R.string.nav_settings, Icons.Filled.CreditCard),
}