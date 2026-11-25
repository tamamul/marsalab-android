package com.marsa.lab.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.marsa.lab.data.ClientDto
import com.marsa.lab.data.LabDto
import com.marsa.lab.data.PcCommand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorScreen(vm: AppViewModel, lab: LabDto) {
    val ui by vm.monitor.collectAsStateWithLifecycle()
    val ctl by vm.control.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val canControl = session?.canControl == true
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<PcCommand?>(null) }
    val owner = LocalLifecycleOwner.current
    var filter by rememberSaveable { mutableIntStateOf(0) } // 0 semua, 1 online, 2 offline

    // Tombol kembali: batalkan pilihan dulu, baru keluar dari lab.
    BackHandler { if (ctl.selected.isNotEmpty()) vm.clearSelection() else vm.backToLabs() }

    LaunchedEffect(ctl.notice) {
        ctl.notice?.let {
            snackbar.showSnackbar(it)
            vm.consumeNotice()
        }
    }

    // Polling hanya berjalan saat layar terlihat; berhenti otomatis di latar belakang.
    LaunchedEffect(lab.id) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.monitorLoop() }
    }

    val onlineCount = ui.pcs.count { it.isOnline }
    val offlineCount = ui.pcs.size - onlineCount
    val shown = remember(ui.pcs, filter) {
        when (filter) {
            1 -> ui.pcs.filter { it.isOnline }
            2 -> ui.pcs.filter { !it.isOnline }
            else -> ui.pcs
        }
    }

    Scaffold(
        containerColor = MarsaColors.Bg,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (canControl && ctl.selected.isNotEmpty()) {
                ControlBar(
                    count = ctl.selected.size,
                    onlineTotal = onlineCount,
                    busy = ctl.busy,
                    onSelectAll = { vm.selectAllOnline() },
                    onClear = { vm.clearSelection() },
                    onPick = { dialog = it },
                )
            }
        },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(lab.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (ui.updatedAtMillis > 0) {
                            Text(
                                "Diperbarui ${clockText(ui.updatedAtMillis)}",
                                color = MarsaColors.Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { vm.backToLabs() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.refreshNow() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Muat ulang")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MarsaColors.Bg,
                    titleContentColor = MarsaColors.TextMain,
                    navigationIconContentColor = MarsaColors.TextMain,
                    actionIconContentColor = MarsaColors.TextMain,
                ),
            )
        },
    ) { pad ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ui.error?.let { err ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    ErrorBanner(err, stale = ui.pcs.isNotEmpty(), modifier = Modifier.fillMaxWidth())
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Total", ui.pcs.size, Modifier.weight(1f))
                    StatBox("Online", onlineCount, Modifier.weight(1f), accent = MarsaColors.Online)
                    StatBox("Offline", offlineCount, Modifier.weight(1f))
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = filter == 0, onClick = { filter = 0 }, label = { Text("Semua") })
                        FilterChip(selected = filter == 1, onClick = { filter = 1 }, label = { Text("Online") })
                        FilterChip(selected = filter == 2, onClick = { filter = 2 }, label = { Text("Offline") })
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Tampilkan layar", color = MarsaColors.TextMain, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Matikan untuk menghemat kuota data.",
                                color = MarsaColors.Muted,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Switch(checked = ui.showThumbs, onCheckedChange = { vm.setShowThumbs(it) })
                    }
                    if (canControl && ctl.selected.isEmpty() && onlineCount > 0) {
                        Text(
                            "Tekan lama kartu PC untuk memilih dan mengirim perintah.",
                            color = MarsaColors.Muted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            if (ui.loading && ui.pcs.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (shown.isEmpty() && ui.error == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (ui.pcs.isEmpty()) "Belum ada komputer yang terdaftar di lab ini." else "Tidak ada komputer pada filter ini.",
                            color = MarsaColors.Muted,
                        )
                    }
                }
            }

            items(shown, key = { it.clientId }) { pc ->
                val picked = pc.clientId in ctl.selected
                PcCard(
                    pc = pc,
                    thumb = ui.thumbs[pc.clientId],
                    showThumbs = ui.showThumbs,
                    selected = picked,
                    onClick = { if (canControl && ctl.selected.isNotEmpty() && pc.isOnline) vm.toggleSelect(pc.clientId) },
                    onLongClick = { if (canControl && pc.isOnline) vm.toggleSelect(pc.clientId) },
                )
            }
        }
    }

    dialog?.let { cmd ->
        CommandDialog(
            cmd = cmd,
            count = ctl.selected.size,
            onDismiss = { dialog = null },
            onSend = { input ->
                dialog = null
                vm.sendCommand(cmd, input)
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PcCard(
    pc: ClientDto,
    thumb: Thumb?,
    showThumbs: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) MarsaColors.CardAlt else MarsaColors.Card),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MarsaColors.Cyan else MarsaColors.Outline),
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MarsaColors.Bg),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null && pc.isOnline) {
                Image(
                    bitmap = thumb.image,
                    contentDescription = "Layar ${pc.computerName}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = when {
                        !pc.isOnline -> "Offline"
                        showThumbs -> "Menunggu gambar…"
                        else -> "Layar dimatikan"
                    },
                    color = MarsaColors.Muted,
                    fontSize = 12.sp,
                )
            }
        }
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(pc.isOnline)
                RowGap(8)
                Text(
                    (if (selected) "✓ " else "") + pc.computerName,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                pc.ipAddress.ifBlank { "—" },
                color = MarsaColors.Muted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
            Text(
                if (pc.isOnline) "Aktif" else "Terakhir aktif ${agoText(pc.lastSeenUtc)}",
                color = MarsaColors.Muted,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
