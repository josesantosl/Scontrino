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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pepesantos.scontrino.data.AppDatabase
import com.pepesantos.scontrino.data.repository.ItemRepository
import com.pepesantos.scontrino.data.repository.LoyaltyCardRepository
import com.pepesantos.scontrino.data.repository.ProductRepository
import com.pepesantos.scontrino.data.repository.ReceiptRepository
import com.pepesantos.scontrino.data.repository.StoreRepository
import com.pepesantos.scontrino.ui.screens.ReceiptsScreen
import com.pepesantos.scontrino.ui.screens.StatsScreen
import com.pepesantos.scontrino.ui.screens.WalletScreen
import com.pepesantos.scontrino.ui.theme.ScontrinoTheme
import com.pepesantos.scontrino.ui.viewmodel.ReceiptViewModel
import com.pepesantos.scontrino.ui.viewmodel.ViewModelFactory
import com.pepesantos.scontrino.ui.viewmodel.WalletViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = AppDatabase.getDatabase(applicationContext)
        val factory = ViewModelFactory(
            receiptRepository = ReceiptRepository(database.receiptDao(), database.itemDao()),
            storeRepository = StoreRepository(database.storeDao()),
            productRepository = ProductRepository(database.productDao()),
            loyaltyCardRepository = LoyaltyCardRepository(database.loyaltyCardDao()),
            itemRepository = ItemRepository(database.itemDao()),
        )
        setContent {
            ScontrinoTheme {
                ScontrinoApp(factory = factory)
            }
        }
    }
}

@Composable
fun ScontrinoApp(factory: ViewModelFactory) {
    val receiptViewModel: ReceiptViewModel = viewModel(factory = factory)
    val walletViewModel: WalletViewModel = viewModel(factory = factory)

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
            AppDestinations.SCONTRINI -> ReceiptsScreen(viewModel = receiptViewModel)
            AppDestinations.STATS -> StatsScreen()
            AppDestinations.WALLET -> WalletScreen(viewModel = walletViewModel)
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
}