package com.pepesantos.scontrino

import android.content.Context
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pepesantos.scontrino.data.AppDatabase
import com.pepesantos.scontrino.data.repository.CategoryRepository
import com.pepesantos.scontrino.data.repository.ItemRepository
import com.pepesantos.scontrino.data.repository.LoyaltyCardRepository
import com.pepesantos.scontrino.data.repository.ProductRepository
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import com.pepesantos.scontrino.data.settings.AppLocale
import com.pepesantos.scontrino.data.settings.SettingsRepository
import com.pepesantos.scontrino.ui.components.LocalCurrency
import com.pepesantos.scontrino.ui.screens.ReceiptsScreen
import com.pepesantos.scontrino.ui.screens.SettingsScreen
import com.pepesantos.scontrino.ui.screens.StatsScreen
import com.pepesantos.scontrino.ui.screens.WalletScreen
import com.pepesantos.scontrino.ui.theme.ScontrinoTheme
import com.pepesantos.scontrino.ui.viewmodel.ReceiptViewModel
import com.pepesantos.scontrino.ui.viewmodel.ViewModelFactory
import com.pepesantos.scontrino.ui.viewmodel.WalletViewModel

import com.pepesantos.scontrino.ui.viewmodel.StatsViewModel
import com.pepesantos.scontrino.ui.viewmodel.PriceComparisonViewModel
import com.pepesantos.scontrino.ui.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {

    // The language chosen in the settings, applied before any resource is read.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLocale.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.getDatabase(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)
        val factory = ViewModelFactory(
            receiptRepository = ReceiptRepository(database.receiptDao(), database.itemDao()),
            storeRepository = StoreRepository(database.storeDao()),
            productRepository = ProductRepository(database.productDao()),
            loyaltyCardRepository = LoyaltyCardRepository(database.loyaltyCardDao()),
            itemRepository = ItemRepository(database.itemDao()),
            categoryRepository = CategoryRepository(database.categoryDao()),
            settingsRepository = settingsRepository,
        )
        setContent {
            val currency by settingsRepository.currency.collectAsState()
            ScontrinoTheme {
                CompositionLocalProvider(LocalCurrency provides currency) {
                    ScontrinoApp(factory = factory, onLanguageChanged = { recreate() })
                }
            }
        }
    }
}

@Composable
fun ScontrinoApp(factory: ViewModelFactory, onLanguageChanged: () -> Unit = {}) {
    val receiptViewModel: ReceiptViewModel = viewModel(factory = factory)
    val walletViewModel: WalletViewModel = viewModel(factory = factory)
    val statsViewModel: StatsViewModel = viewModel(factory = factory)
    val priceComparisonViewModel: PriceComparisonViewModel = viewModel(factory = factory)
    val settingsViewModel: SettingsViewModel = viewModel(factory = factory)

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
                    onClick = {
                        when (it) {
                            AppDestinations.STATS -> statsViewModel.loadStats()
                            // The currency may have changed in the settings.
                            AppDestinations.SCONTRINI -> receiptViewModel.loadReceipts()
                            else -> Unit
                        }
                        currentDestination = it
                    }
                )
            }
        }
    ) {
        when (currentDestination) {
            AppDestinations.SCONTRINI -> ReceiptsScreen(viewModel = receiptViewModel)
            AppDestinations.STATS -> StatsScreen(
                viewModel = statsViewModel,
                priceComparisonViewModel = priceComparisonViewModel
            )
            AppDestinations.WALLET -> WalletScreen(viewModel = walletViewModel)
            AppDestinations.SETTINGS -> SettingsScreen(
                viewModel = settingsViewModel,
                onLanguageChanged = onLanguageChanged
            )
        }
    }
}

enum class AppDestinations(
    @StringRes val label: Int,
    val icon: ImageVector,
) {
    SCONTRINI(R.string.nav_receipts, Icons.Filled.ListAlt),
    STATS(R.string.nav_stats, Icons.Filled.BarChart),
    WALLET(R.string.nav_wallet, Icons.Filled.CreditCard),
    SETTINGS(R.string.nav_settings, Icons.Filled.Settings),
}
