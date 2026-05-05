package com.pepesantos.scontrino

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import com.pepesantos.scontrino.ui.theme.ScontrinoTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ListAlt

//screens de los 3 tabs
import com.pepesantos.scontrino.ui.screens.ReceiptsScreen
import com.pepesantos.scontrino.ui.screens.StatsScreen
import com.pepesantos.scontrino.ui.screens.SettingsScreen
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

@PreviewScreenSizes
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
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Greeting(
                name = "Android",
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
    when (currentDestination) {
        AppDestinations.SCONTRINI -> ReceiptsScreen()
        AppDestinations.STATS -> StatsScreen()
        AppDestinations.SETTINGS -> SettingsScreen()
    }
}

enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    SCONTRINI("Scontrini", Icons.Filled.ListAlt),
    STATS("Statistiche", Icons.Filled.BarChart),
    SETTINGS("Impostazioni", Icons.Filled.Settings),
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ScontrinoTheme {
        Greeting("Android")
    }
}