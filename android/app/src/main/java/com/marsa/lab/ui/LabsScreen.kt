package com.marsa.lab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabsScreen(vm: AppViewModel) {
    val session by vm.session.collectAsStateWithLifecycle()
    val labs by vm.labs.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadLabs() }

    Scaffold(
        containerColor = MarsaColors.Bg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Pilih Lab", fontWeight = FontWeight.Bold)
                        session?.let {
                            Text(
                                "${it.user} · ${roleLabel(it.role)}",
                                color = MarsaColors.Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { vm.loadLabs() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
                    }
                    TextButton(onClick = { vm.signOut() }) { Text("Keluar") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MarsaColors.Bg,
                    titleContentColor = MarsaColors.TextMain,
                    actionIconContentColor = MarsaColors.TextMain,
                ),
            )
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when {
                labs.loading && labs.items.isEmpty() ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                labs.error != null && labs.items.isEmpty() ->
                    Text(
                        labs.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )

                labs.items.isEmpty() ->
                    Text(
                        "Belum ada lab yang dikonfigurasi di gateway.",
                        color = MarsaColors.Muted,
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(labs.items, key = { it.id }) { lab ->
                        Card(
                            onClick = { vm.selectLab(lab) },
                            colors = CardDefaults.cardColors(containerColor = MarsaColors.Card),
                            border = BorderStroke(1.dp, MarsaColors.Outline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(lab.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                    Text(lab.id, color = MarsaColors.Muted, style = MaterialTheme.typography.bodySmall)
                                }
                                StatusLabel(lab.online)
                            }
                        }
                    }
                }
            }
        }
    }
}
