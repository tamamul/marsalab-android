package com.marsa.lab.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun AppRoot(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val lab by vm.selectedLab.collectAsStateWithLifecycle()
    val selected = lab

    when {
        session == null -> LoginScreen(vm)
        selected == null -> LabsScreen(vm)
        else -> MonitorScreen(vm, selected)
    }
}
