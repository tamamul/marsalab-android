package com.marsa.lab.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.marsa.lab.data.PcCommand

/** Bilah aksi di bawah layar saat ada PC yang dipilih (hanya peran controller). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlBar(
    count: Int,
    onlineTotal: Int,
    busy: Boolean,
    onSelectAll: () -> Unit,
    onClear: () -> Unit,
    onPick: (PcCommand) -> Unit,
) {
    Surface(color = MarsaColors.CardAlt, tonalElevation = 4.dp) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$count PC dipilih",
                    color = MarsaColors.TextMain,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSelectAll, enabled = !busy) { Text("Semua online ($onlineTotal)") }
                TextButton(onClick = onClear, enabled = !busy) { Text("Batal") }
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (cmd in PcCommand.values()) {
                    AssistChip(onClick = { onPick(cmd) }, enabled = !busy, label = { Text(cmd.label) })
                }
            }
        }
    }
}

/** Dialog isian/konfirmasi untuk satu perintah. */
@Composable
fun CommandDialog(
    cmd: PcCommand,
    count: Int,
    onDismiss: () -> Unit,
    onSend: (String?) -> Unit,
) {
    var input by rememberSaveable(cmd) { mutableStateOf("") }
    val needsInput = cmd == PcCommand.MESSAGE || cmd == PcCommand.OPEN_URL
    val canSend = !needsInput || input.isNotBlank()

    val summary = when (cmd) {
        PcCommand.MESSAGE -> "Tampilkan pengumuman di $count PC."
        PcCommand.LOCK -> "Kunci layar $count PC."
        PcCommand.UNLOCK -> "Buka kunci layar $count PC."
        PcCommand.OPEN_URL -> "Buka alamat web di $count PC."
        PcCommand.RESTART -> "Restart $count PC sekarang? Pekerjaan yang belum disimpan di PC tersebut bisa hilang."
        PcCommand.SHUTDOWN -> "Matikan $count PC sekarang? Pekerjaan yang belum disimpan di PC tersebut bisa hilang."
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(cmd.label) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(summary)
                if (needsInput) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = if (cmd == PcCommand.MESSAGE) it.take(500) else it },
                        label = { Text(if (cmd == PcCommand.MESSAGE) "Isi pengumuman" else "Alamat web") },
                        singleLine = cmd == PcCommand.OPEN_URL,
                        minLines = if (cmd == PcCommand.MESSAGE) 3 else 1,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(if (needsInput) input else null) },
                enabled = canSend,
            ) {
                Text(
                    if (cmd.destructive) cmd.label else "Kirim",
                    color = if (cmd.destructive) MarsaColors.Danger else MarsaColors.Cyan,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
    )
}
