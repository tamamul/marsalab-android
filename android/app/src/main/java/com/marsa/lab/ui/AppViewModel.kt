package com.marsa.lab.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marsa.lab.data.ApiException
import com.marsa.lab.data.ClientDto
import com.marsa.lab.data.GatewayApi
import com.marsa.lab.data.InputEv
import com.marsa.lab.data.LabDto
import com.marsa.lab.data.Session
import com.marsa.lab.data.SessionStore
import com.marsa.lab.data.ShotDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerializationException
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class LoginUi(
    val baseUrl: String,
    val username: String,
    val busy: Boolean = false,
    val error: String? = null,
)

data class LabsUi(
    val items: List<LabDto> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class Thumb(val image: ImageBitmap, val t: String)

data class MonitorUi(
    val pcs: List<ClientDto> = emptyList(),
    val thumbs: Map<String, Thumb> = emptyMap(),
    val loading: Boolean = true,
    val error: String? = null,
    val showThumbs: Boolean = true,
    val updatedAtMillis: Long = 0L,
)

data class RemoteUi(
    val pc: ClientDto,
    val frame: ImageBitmap? = null,
    val frameT: String = "",
    val frameReceivedAt: Long = 0L,
    val error: String? = null,
    val control: Boolean = false,
    val controlError: String? = null,
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)

    private val _session = MutableStateFlow(store.loadSession())
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _login = MutableStateFlow(
        LoginUi(baseUrl = store.lastBaseUrl ?: DEFAULT_SERVER, username = store.lastUsername ?: "")
    )
    val login: StateFlow<LoginUi> = _login.asStateFlow()

    private val _labs = MutableStateFlow(LabsUi())
    val labs: StateFlow<LabsUi> = _labs.asStateFlow()

    private val _selectedLab = MutableStateFlow<LabDto?>(null)
    val selectedLab: StateFlow<LabDto?> = _selectedLab.asStateFlow()

    private val _monitor = MutableStateFlow(MonitorUi(showThumbs = store.showThumbs))
    val monitor: StateFlow<MonitorUi> = _monitor.asStateFlow()

    private val kick = Channel<Unit>(Channel.CONFLATED)

    private fun api(): GatewayApi? = _session.value?.let { GatewayApi(it.baseUrl, it.token) }

    // ===================== Login / logout =====================
    fun login(serverInput: String, username: String, password: String) {
        val url = normalizeUrl(serverInput)
        val name = username.trim()
        if (url == null) {
            _login.update { it.copy(error = "Alamat server tidak valid. Gunakan alamat https://, misalnya $DEFAULT_SERVER") }
            return
        }
        if (name.isEmpty() || password.isEmpty()) {
            _login.update { it.copy(error = "Nama pengguna dan kata sandi wajib diisi.") }
            return
        }
        _login.update { it.copy(baseUrl = url, username = name, busy = true, error = null) }
        viewModelScope.launch {
            try {
                val r = GatewayApi(url).login(name, password)
                val s = Session(
                    baseUrl = url,
                    token = r.token,
                    role = r.role,
                    user = r.user,
                    expiresAtMillis = System.currentTimeMillis() + r.hours * 3_600_000L,
                )
                store.save(s)
                _labs.value = LabsUi()
                _selectedLab.value = null
                _session.value = s
                _login.update { it.copy(busy = false, error = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _login.update { it.copy(busy = false, error = messageOf(e)) }
            }
        }
    }

    fun signOut(message: String? = null, notifyServer: Boolean = true) {
        val s = _session.value
        if (s != null && notifyServer) {
            viewModelScope.launch {
                try { GatewayApi(s.baseUrl, s.token).logout() } catch (ignored: Exception) { /* abaikan */ }
            }
        }
        store.clearSession()
        _session.value = null
        _selectedLab.value = null
        _labs.value = LabsUi()
        _monitor.value = MonitorUi(showThumbs = store.showThumbs)
        _remote.value = null
        inputBuf.clear()
        _login.update { it.copy(busy = false, error = message) }
    }

    // ===================== Daftar lab =====================
    fun loadLabs() {
        val api = api() ?: return
        _labs.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val list = api.labs()
                _labs.value = LabsUi(items = list)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isUnauthorized(e)) return@launch expireSession()
                _labs.update { it.copy(loading = false, error = messageOf(e)) }
            }
        }
    }

    fun selectLab(lab: LabDto) {
        _monitor.value = MonitorUi(showThumbs = store.showThumbs)
        _selectedLab.value = lab
    }

    fun backToLabs() {
        _selectedLab.value = null
        _monitor.value = MonitorUi(showThumbs = store.showThumbs)
        loadLabs()
    }

    // ===================== Pemantauan =====================
    fun setShowThumbs(on: Boolean) {
        store.showThumbs = on
        _monitor.update { it.copy(showThumbs = on, thumbs = if (on) it.thumbs else emptyMap()) }
        if (on) refreshNow()
    }

    fun refreshNow() {
        kick.trySend(Unit)
    }

    /** Dipanggil dari UI selama layar terlihat (berhenti otomatis saat aplikasi di latar belakang). */
    suspend fun monitorLoop() {
        val lab = _selectedLab.value ?: return
        while (true) {
            refreshOnce(lab)
            withTimeoutOrNull(POLL_MS) { kick.receive() }
        }
    }

    private suspend fun refreshOnce(lab: LabDto) {
        val api = api() ?: return
        try {
            val list = api.clients(lab.id)
            _monitor.update {
                it.copy(pcs = list, loading = false, error = null, updatedAtMillis = System.currentTimeMillis())
            }
            val onlineIds = list.filter { it.isOnline }.map { it.clientId }
            if (_monitor.value.showThumbs && onlineIds.isNotEmpty()) {
                api.watch(lab.id, "thumb", WATCH_SECONDS, onlineIds.take(200))
                updateThumbs(api.screens(lab.id))
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (isUnauthorized(e)) return expireSession()
            _monitor.update { it.copy(loading = false, error = messageOf(e)) }
        }
    }

    private suspend fun updateThumbs(shots: Map<String, ShotDto>) {
        val old = _monitor.value.thumbs
        val next = withContext(Dispatchers.Default) {
            val m = HashMap<String, Thumb>()
            for ((id, shot) in shots) {
                val prev = old[id]
                if (prev != null && prev.t == shot.t) {
                    m[id] = prev
                } else {
                    decode(shot.jpg)?.let { m[id] = Thumb(it, shot.t) }
                }
            }
            m
        }
        _monitor.update { if (it.showThumbs) it.copy(thumbs = next) else it }
    }

    // ===================== Layar penuh & kontrol jarak jauh =====================
    private val _remote = MutableStateFlow<RemoteUi?>(null)
    val remote: StateFlow<RemoteUi?> = _remote.asStateFlow()

    private val inputBuf = ArrayDeque<InputEv>()

    fun openRemote(pc: ClientDto) {
        inputBuf.clear()
        _remote.value = RemoteUi(pc = pc)
    }

    fun closeRemote() {
        inputBuf.clear()
        _remote.value = null
    }

    /** Kontrol hanya boleh dinyalakan oleh peran controller. */
    fun setControl(on: Boolean) {
        if (on && _session.value?.canControl != true) return
        if (!on) inputBuf.clear()
        _remote.update { it?.copy(control = on, controlError = null) }
    }

    fun sendInput(ev: InputEv) {
        if (_remote.value?.control != true) return
        // gabungkan gerakan beruntun agar antrean tidak menumpuk
        if (ev.t == "move" && inputBuf.lastOrNull()?.t == "move") inputBuf.removeLast()
        if (inputBuf.size >= 300) inputBuf.removeFirst()
        inputBuf.addLast(ev)
    }

    /**
     * Dipanggil dari UI selama layar kontrol terlihat. Saat berhenti (aplikasi ke latar belakang
     * atau layar ditutup) kontrol otomatis dimatikan.
     */
    suspend fun remoteLoop() {
        val lab = _selectedLab.value ?: return
        val id = _remote.value?.pc?.clientId ?: return
        try {
            coroutineScope {
                launch { frameLoop(lab, id) }
                launch { flushLoop(lab, id) }
            }
        } finally {
            inputBuf.clear()
            _remote.update { it?.copy(control = false) }
        }
    }

    private suspend fun frameLoop(lab: LabDto, id: String) {
        var lastWatch = 0L
        while (true) {
            var gotNew = false
            var failed = false
            val api = api() ?: return
            try {
                val now = SystemClock.elapsedRealtime()
                if (now - lastWatch >= WATCH_REFRESH_MS) {
                    api.watch(lab.id, "full", 15, listOf(id))
                    lastWatch = now
                }
                val shot = api.screen(lab.id, id)
                // frame kecil (thumb) dari mode sebelumnya diabaikan sampai frame penuh tiba
                if (shot.mode == "full" && shot.t != _remote.value?.frameT) {
                    val img = withContext(Dispatchers.Default) { decode(shot.jpg) }
                    if (img != null) {
                        _remote.update {
                            it?.copy(frame = img, frameT = shot.t, frameReceivedAt = SystemClock.elapsedRealtime(), error = null)
                        }
                        gotNew = true
                    }
                }
                if (_remote.value?.error != null) _remote.update { it?.copy(error = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isUnauthorized(e)) return expireSession()
                if (e is ApiException && e.status == 404 && e.message == "HTTP 404") {
                    // belum ada gambar dari PC; tunggu saja
                } else {
                    failed = true
                    _remote.update { it?.copy(error = messageOf(e)) }
                }
            }
            delay(if (failed) 1_500L else if (gotNew) 450L else 250L)
        }
    }

    private suspend fun flushLoop(lab: LabDto, id: String) {
        while (true) {
            delay(FLUSH_MS)
            if (inputBuf.isEmpty()) continue
            val api = api() ?: return
            val batch = ArrayList<InputEv>()
            while (batch.size < 60 && inputBuf.isNotEmpty()) batch.add(inputBuf.removeFirst())
            try {
                api.control(lab.id, id, batch)
                if (_remote.value?.controlError != null) _remote.update { it?.copy(controlError = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isUnauthorized(e)) return expireSession()
                val msg = if (e is ApiException && e.status == 429) "antrean penuh, tunggu sebentar" else messageOf(e)
                _remote.update { it?.copy(controlError = "Kontrol gagal: $msg") }
            }
        }
    }

    // ===================== Bantu =====================
    private fun expireSession() = signOut("Sesi berakhir. Silakan masuk lagi.", notifyServer = false)

    private fun isUnauthorized(e: Throwable) = e is ApiException && e.status == 401

    private fun messageOf(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "Terjadi kesalahan."
        is SerializationException ->
            "Respons server tidak dikenali. Pastikan alamat adalah gateway MARSA Lab."
        else -> "Terjadi kesalahan: ${e.javaClass.simpleName}"
    }

    private fun normalizeUrl(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        if (!s.startsWith("https://", ignoreCase = true)) return null
        s = s.trimEnd('/')
        return if (s.toHttpUrlOrNull() != null) s else null
    }

    private fun decode(b64: String): ImageBitmap? = try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (ignored: Exception) {
        null
    }

    private companion object {
        const val DEFAULT_SERVER = "https://relay.marsa9.com"
        const val POLL_MS = 5_000L
        const val WATCH_SECONDS = 20
        const val WATCH_REFRESH_MS = 4_000L
        const val FLUSH_MS = 120L
    }
}
