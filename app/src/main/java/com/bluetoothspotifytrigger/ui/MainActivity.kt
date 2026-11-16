package com.bluetoothspotifytrigger.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Android 15+ (targetSdk 35) impose l'affichage edge-to-edge : le contenu
        // passe SOUS la barre d'état et la barre de navigation. On l'active
        // explicitement (comportement identique sur toutes les versions) et on
        // décale le contenu via safeDrawingPadding() dans AppRoot.
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

private val tabs = listOf("Permissions", "Appareils", "Service")

@Composable
private fun AppRoot() {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // safeDrawingPadding : marge = barres système + encoche + clavier, pour que
    // les onglets ne soient pas recouverts (et restent cliquables).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding(),
    ) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) },
                )
            }
        }
        when (selectedTab) {
            0 -> PermissionsScreen(Modifier.fillMaxSize())
            1 -> DeviceListScreen(Modifier.fillMaxSize())
            2 -> ServiceScreen(Modifier.fillMaxSize())
        }
    }
}
