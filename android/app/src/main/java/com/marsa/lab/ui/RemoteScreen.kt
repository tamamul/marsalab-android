package com.marsa.lab.ui

import android.app.Activity
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.marsa.lab.data.InputEv
import kotlinx.coroutines.delay
import kotlin.math.abs

// Sentinel agar tombol hapus di keyboard tetap terdeteksi walau kolom teks tersembunyi kosong.
private const val SENTINEL = " "

private const val OUT_PENDING = 0
private const val OUT_TAP = 1
private const val OUT_DRAG = 2
private const val OUT_MULTI = 3
private const val OUT_NONE = 4

private class KeyDef(val label: String, val vk: Int, val mods: List<String> = emptyList())

// Kode tombol virtual Windows. Tombol Windows sengaja tidak ada (ditolak oleh client).
private val KEYS = listOf(
    KeyDef("Esc", 0x1B), KeyDef("Tab", 0x09), KeyDef("Enter", 0x0D), KeyDef("⌫", 0x08), KeyDef("Del", 0x2E),
    KeyDef("←", 0x25), KeyDef("↑", 0x26), KeyDef("↓", 0x28), KeyDef("→", 0x27),
    KeyDef("Home", 0x24), KeyDef("End", 0x23), KeyDef("PgUp", 0x21), KeyDef("PgDn", 0x22),
    KeyDef("Ctrl+A", 0x41, listOf("ctrl")), KeyDef("Ctrl+C", 0x43, listOf("ctrl")),
    KeyDef("Ctrl+V", 0x56, listOf("ctrl")), KeyDef("Ctrl+X", 0x58, listOf("ctrl")),
    KeyDef("Ctrl+Z", 0x5A, listOf("ctrl")), KeyDef("Alt+Tab", 0x09, listOf("alt")),
)

/** Status zoom/geser tampilan. Semua koordinat sentuh dihitung terhadap kontainer. */
private class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    var container: IntSize = IntSize.Zero

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    private fun clampOffset(o: Offset, s: Float): Offset {
        val mx = (s - 1f) * container.width / 2f
        val my = (s - 1f) * container.height / 2f
        return Offset(o.x.coerceIn(-mx, mx), o.y.coerceIn(-my, my))
    }

    fun panBy(d: Offset) {
        offset = clampOffset(offset + d, scale)
    }

    fun zoomAround(c: Offset, factor: Float, pan: Offset) {
        val old = scale
        val ns = (old * factor).coerceIn(1f, 6f)
        val center = Offset(container.width / 2f, container.height / 2f)
        val no = c + pan - center - (c - center - offset) * (ns / old)
        scale = ns
        offset = clampOffset(no, ns)
    }

    /** Titik sentuh -> koordinat layar PC 0..1. Null bila di luar gambar (dan clamp = false). */
    fun toNorm(p: Offset, frame: IntSize, clamp: Boolean): Offset? {
        if (container.width == 0 || container.height == 0 || frame.width == 0 || frame.height == 0) return null
        val center = Offset(container.width / 2f, container.height / 2f)
        val q = center + (p - center - offset) / scale
        val f = minOf(container.width.toFloat() / frame.width, container.height.toFloat() / frame.height)
        val w = frame.width * f
        val h = frame.height * f
        val left = (container.width - w) / 2f
        val top = (container.height - h) / 2f
        val nx = (q.x - left) / w
        val ny = (q.y - top) / h
        if (!clamp && (nx < 0f || nx > 1f || ny < 0f || ny > 1f)) return null
        return Offset(nx.coerceIn(0f, 1f), ny.coerceIn(0f, 1f))
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RemoteScreen(vm: AppViewModel, state: RemoteUi) {
    val session by vm.session.collectAsStateWithLifecycle()
    val canControl = session?.canControl == true
    val owner = LocalLifecycleOwner.current
    val zoom = remember { ZoomState() }
    var scrollMode by remember { mutableStateOf(false) }
    var kb by remember { mutableStateOf(false) }
    var nowTick by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var tf by remember { mutableStateOf(TextFieldValue(SENTINEL, TextRange(SENTINEL.length))) }
    val focusReq = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    ImmersiveEffect()
    BackHandler { if (kb) kb = false else vm.closeRemote() }

    LaunchedEffect(state.pc.clientId) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.remoteLoop() }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowTick = SystemClock.elapsedRealtime()
        }
    }
    LaunchedEffect(state.control) { if (!state.control) kb = false }
    LaunchedEffect(kb) {
        if (kb) {
            focusReq.requestFocus()
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    fun key(vk: Int, mods: List<String> = emptyList()) =
        vm.sendInput(InputEv(t = "key", vk = vk, mods = mods.ifEmpty { null }))

    fun text(s: String) {
        s.filter { !it.isISOControl() }.chunked(32).forEach { vm.sendInput(InputEv(t = "text", s = it)) }
    }

    val status = if (state.frameReceivedAt == 0L) "menunggu gambar…"
    else "diperbarui ${maxOf(0L, (nowTick - state.frameReceivedAt) / 1000)} dtk lalu"

    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .displayCutoutPadding()
            .imePadding()
    ) {
        RemoteTopBar(
            name = state.pc.computerName,
            status = status,
            canControl = canControl,
            control = state.control,
            onBack = { vm.closeRemote() },
            onToggle = { vm.setControl(!state.control) },
        )

        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            RemoteViewport(
                frame = state.frame,
                zoom = zoom,
                control = state.control,
                scrollMode = scrollMode,
                send = { vm.sendInput(it) },
                modifier = Modifier.fillMaxSize(),
            )

            if (state.frame == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        "Menunggu gambar layar… bisa sampai beberapa detik.",
                        color = MarsaColors.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 10.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }

            if (state.control) {
                Surface(
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 6.dp),
                    color = Color(0xCCB3261E),
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(
                        if (scrollMode) "KONTROL AKTIF · mode gulir: geser 1 jari" else "KONTROL AKTIF · ketuk=klik · tahan=klik kanan · geser=seret",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            val msg = state.error ?: state.controlError
            if (msg != null) {
                ErrorBanner(msg, stale = false, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
            }

            if (state.control) {
                // Kolom teks tersembunyi hanya untuk memunculkan keyboard dan menangkap ketikan.
                BasicTextField(
                    value = tf,
                    onValueChange = { nv ->
                        val old = tf.text
                        val new = nv.text
                        if (new != old) {
                            val common = old.commonPrefixWith(new).length
                            repeat(old.length - common) { key(0x08) }
                            val added = new.substring(common)
                            if (added.isNotEmpty()) text(added)
                        }
                        tf = TextFieldValue(SENTINEL, TextRange(SENTINEL.length))
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { key(0x0D) }),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(1.dp)
                        .alpha(0f)
                        .focusRequester(focusReq)
                        .onFocusChanged { if (!it.isFocused && kb) kb = false },
                )
            }
        }

        RemoteToolsRow(
            control = state.control,
            scrollMode = scrollMode,
            kb = kb,
            onResetZoom = { zoom.reset() },
            onScrollMode = { scrollMode = it },
            onKb = { kb = it },
            onKey = { key(it.vk, it.mods) },
        )
    }
}

@Composable
private fun ImmersiveEffect() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

@Composable
private fun RemoteTopBar(
    name: String,
    status: String,
    canControl: Boolean,
    control: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(MarsaColors.Bg).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali", tint = MarsaColors.TextMain)
        }
        Column(Modifier.weight(1f)) {
            Text(
                name,
                color = MarsaColors.TextMain,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(status, color = MarsaColors.Muted, style = MaterialTheme.typography.labelSmall)
        }
        if (canControl) {
            if (control) {
                Button(
                    onClick = onToggle,
                    colors = ButtonDefaults.buttonColors(containerColor = MarsaColors.Danger, contentColor = Color.Black),
                ) { Text("Kontrol: AKTIF") }
            } else {
                OutlinedButton(onClick = onToggle) { Text("Kontrol: Mati") }
            }
        } else {
            Text(
                "Hanya memantau",
                color = MarsaColors.Muted,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(end = 12.dp),
            )
        }
    }
}

@Composable
private fun RemoteToolsRow(
    control: Boolean,
    scrollMode: Boolean,
    kb: Boolean,
    onResetZoom: () -> Unit,
    onScrollMode: (Boolean) -> Unit,
    onKb: (Boolean) -> Unit,
    onKey: (KeyDef) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MarsaColors.Bg)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(onClick = onResetZoom, label = { Text("Reset zoom") })
        if (control) {
            FilterChip(selected = scrollMode, onClick = { onScrollMode(!scrollMode) }, label = { Text("Gulir") })
            FilterChip(selected = kb, onClick = { onKb(!kb) }, label = { Text("Keyboard") })
            KEYS.forEach { k ->
                AssistChip(onClick = { onKey(k) }, label = { Text(k.label) })
            }
        }
    }
}

@Composable
private fun RemoteViewport(
    frame: ImageBitmap?,
    zoom: ZoomState,
    control: Boolean,
    scrollMode: Boolean,
    send: (InputEv) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frameNow by rememberUpdatedState(frame)
    val controlNow by rememberUpdatedState(control)
    val scrollNow by rememberUpdatedState(scrollMode)
    val sendNow by rememberUpdatedState(send)

    Box(
        modifier
            .onSizeChanged { zoom.container = it }
            .pointerInput(Unit) {
                remoteGestures(
                    zoom = zoom,
                    frameSize = { IntSize(frameNow?.width ?: 0, frameNow?.height ?: 0) },
                    control = { controlNow },
                    scrollMode = { scrollNow },
                    send = { sendNow(it) },
                )
            }
    ) {
        if (frame != null) {
            Image(
                bitmap = frame,
                contentDescription = "Layar PC",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.Medium,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom.scale
                        scaleY = zoom.scale
                        translationX = zoom.offset.x
                        translationY = zoom.offset.y
                    },
            )
        }
    }
}

// ===================== Gestur =====================

private fun mouse(t: String, n: Offset, b: String? = null, d: Int = 0) =
    InputEv(t = t, x = n.x.toDouble(), y = n.y.toDouble(), b = b, d = d)

/**
 * 1 jari: ketuk = klik kiri, tahan = klik kanan, geser = seret (atau gulir bila mode gulir).
 * Tanpa kontrol, geser 1 jari menggeser tampilan yang di-zoom.
 * 2 jari: zoom dan geser tampilan.
 */
private suspend fun PointerInputScope.remoteGestures(
    zoom: ZoomState,
    frameSize: () -> IntSize,
    control: () -> Boolean,
    scrollMode: () -> Boolean,
    send: (InputEv) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    val longPressMs = viewConfiguration.longPressTimeoutMillis

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val ctl = control()
        val scroll = scrollMode()
        val fr = frameSize()

        var outcome = OUT_PENDING
        val completed = withTimeoutOrNull(longPressMs) {
            while (outcome == OUT_PENDING) {
                val ev = awaitPointerEvent()
                if (ev.changes.count { it.pressed } > 1) {
                    outcome = OUT_MULTI
                } else {
                    val c = ev.changes.firstOrNull { it.id == down.id }
                    outcome = when {
                        c == null -> OUT_NONE
                        !c.pressed -> OUT_TAP
                        (c.position - down.position).getDistance() > slop -> OUT_DRAG
                        else -> OUT_PENDING
                    }
                }
            }
            true
        }

        if (completed == null) {
            // tekan lama = klik kanan
            if (ctl) {
                zoom.toNorm(down.position, fr, false)?.let { n ->
                    send(mouse("down", n, "right"))
                    send(mouse("up", n, "right"))
                }
            }
            waitAllUp()
            return@awaitEachGesture
        }

        when (outcome) {
            OUT_TAP -> if (ctl) {
                zoom.toNorm(down.position, fr, false)?.let { n ->
                    send(mouse("down", n, "left"))
                    send(mouse("up", n, "left"))
                }
            }
            OUT_DRAG -> dragSingle(down, zoom, fr, ctl, scroll, send)
            OUT_MULTI -> panZoomUntilUp(zoom)
            else -> Unit
        }
    }
}

private suspend fun AwaitPointerEventScope.dragSingle(
    down: PointerInputChange,
    zoom: ZoomState,
    fr: IntSize,
    ctl: Boolean,
    scroll: Boolean,
    send: (InputEv) -> Unit,
) {
    val pressLeft = ctl && !scroll
    val wheelStep = 32.dp.toPx()
    var wheelAcc = 0f
    var lastPos = down.position
    var lastMoveAt = 0L
    var multi = false

    if (pressLeft) zoom.toNorm(down.position, fr, true)?.let { send(mouse("down", it, "left")) }

    while (true) {
        val ev = awaitPointerEvent()
        if (ev.changes.count { it.pressed } > 1) {
            multi = true
            break
        }
        val c = ev.changes.firstOrNull { it.id == down.id } ?: break
        if (!c.pressed) break

        val p = c.position
        val delta = p - c.previousPosition
        lastPos = p
        when {
            !ctl -> zoom.panBy(delta)
            scroll -> {
                wheelAcc += delta.y
                while (abs(wheelAcc) >= wheelStep) {
                    // jari turun = isi bergeser turun = roda ke atas (positif)
                    val up = wheelAcc > 0
                    zoom.toNorm(p, fr, true)?.let { send(mouse("wheel", it, d = if (up) 120 else -120)) }
                    wheelAcc += if (up) -wheelStep else wheelStep
                }
            }
            else -> {
                val now = SystemClock.uptimeMillis()
                if (now - lastMoveAt >= 80) {
                    lastMoveAt = now
                    zoom.toNorm(p, fr, true)?.let { send(mouse("move", it)) }
                }
            }
        }
        c.consume()
    }

    if (pressLeft) zoom.toNorm(lastPos, fr, true)?.let { send(mouse("up", it, "left")) }
    if (multi) panZoomUntilUp(zoom)
}

private suspend fun AwaitPointerEventScope.panZoomUntilUp(zoom: ZoomState) {
    while (true) {
        val ev = awaitPointerEvent()
        val pressed = ev.changes.count { it.pressed }
        if (pressed == 0) break
        if (pressed >= 2) {
            val z = ev.calculateZoom()
            val pan = ev.calculatePan()
            val centroid = ev.calculateCentroid(useCurrent = true)
            if (centroid.isSpecified) zoom.zoomAround(centroid, z, pan)
        }
    }
}

private suspend fun AwaitPointerEventScope.waitAllUp() {
    while (true) {
        val ev = awaitPointerEvent()
        if (ev.changes.none { it.pressed }) break
    }
}
