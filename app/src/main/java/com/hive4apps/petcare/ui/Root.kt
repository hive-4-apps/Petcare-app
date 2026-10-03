package com.hive4apps.petcare.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hive4apps.petcare.R
import com.hive4apps.petcare.ui.screens.DocumentScreen
import com.hive4apps.petcare.ui.screens.EditTreatmentScreen
import com.hive4apps.petcare.ui.screens.NewCareScreen
import com.hive4apps.petcare.ui.screens.PetDetailScreen
import com.hive4apps.petcare.ui.screens.PetsScreen
import com.hive4apps.petcare.ui.screens.SettingsScreen
import com.hive4apps.petcare.ui.screens.TodayScreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetCareRoot(vm: MainViewModel) {
    val screen = vm.screen
    // Late doses turn red without leaving the screen.
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            vm.tick()
        }
    }
    BackHandler(enabled = vm.backStack.size > 1 || (screen == Screen.Home && vm.tab != Tab.TODAY)) {
        if (!vm.back()) vm.tab = Tab.TODAY
    }

    Scaffold(
        topBar = {
            if (screen != Screen.Home) {
                TopAppBar(
                    title = {
                        Text(
                            when (screen) {
                                is Screen.NewCare -> stringResource(R.string.new_follow_up)
                                is Screen.Document -> stringResource(R.string.document)
                                else -> ""
                            },
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { vm.back() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (screen == Screen.Home) {
                NavigationBar {
                    listOf(
                        Triple(Tab.TODAY, Icons.Outlined.Today, R.string.tab_today),
                        Triple(Tab.PETS, Icons.Outlined.Pets, R.string.tab_pets),
                        Triple(Tab.SETTINGS, Icons.Outlined.Settings, R.string.tab_settings),
                    ).forEach { (tab, icon, label) ->
                        NavigationBarItem(
                            selected = vm.tab == tab,
                            onClick = { vm.tab = tab },
                            icon = { Icon(icon, null) },
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (screen == Screen.Home && vm.tab != Tab.SETTINGS) {
                ExtendedFloatingActionButton(
                    onClick = { vm.startNewCare() },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text(stringResource(R.string.new_follow_up)) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.Home -> when (vm.tab) {
                    Tab.TODAY -> TodayScreen(vm, onOpenSettings = { vm.tab = Tab.SETTINGS })
                    Tab.PETS -> PetsScreen(vm)
                    Tab.SETTINGS -> SettingsScreen(vm)
                }
                is Screen.PetDetail -> PetDetailScreen(vm, screen.petId)
                is Screen.EditTreatment -> EditTreatmentScreen(vm, screen.treatmentId, screen.petId)
                is Screen.Document -> DocumentScreen(vm, screen.documentId)
                Screen.NewCare -> vm.flow?.let { NewCareScreen(vm, it) } ?: LaunchedEffect(Unit) { vm.back() }
            }
        }
    }
}
