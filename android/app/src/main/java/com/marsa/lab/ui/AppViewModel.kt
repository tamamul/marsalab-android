package com.marsa.lab.ui

import android.app.Application
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.marsa.lab.data.ApiException
import com.marsa.lab.data.ClientDto
import com.marsa.lab.data.GatewayApi
import com.marsa.lab.data.LabDto
import com.marsa.lab.data.PcCommand
import com.marsa.lab.data.Session
import com.marsa.lab.data.SessionStore
import com.marsa.lab.data.ShotDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
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

data class ControlUi(
    val selected: Set<String> = emptySet(),
    val busy: Boolean = false,
    val notice: String? = null,
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

    private val _control = MutableStateFlow(ControlUi())
    val control: StateFlow<ControlUi> = _control.asStateFlow()

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
        _control.value = ControlUi()
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
        _control.value = ControlUi()
        _monitor.value = MonitorUi(showThumbs = store.showThumbs)
        _selectedLab.value = lab
    }

    fun backToLabs() {
        _control.value = ControlUi()
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
            _control.update { c -> c.copy(selected = c.selected.filter { it in onlineIds }.toSet()) }
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

    // ===================== Kontrol PC (tahap 2) =====================
    fun toggleSelect(id: String) {
        if (_session.value?.canControl != true) return
        _control.update { c ->
            c.copy(selected = if (id in c.selected) c.selected - id else c.selected + id)
        }
    }

    fun selectAllOnline() {
        if (_session.value?.canControl != true) return
        val ids = _monitor.value.pcs.filter { it.isOnline }.map { it.clientId }.toSet()
        _control.update { it.copy(selected = ids) }
    }

    fun clearSelection() = _control.update { it.copy(selected = emptySet()) }

    fun consumeNotice() = _control.update { it.copy(notice = null) }

    private fun notice(msg: String) = _control.update { it.copy(notice = msg) }

    /** [input]: isi pengumuman (MESSAGE) atau alamat web (OPEN_URL); diabaikan untuk perintah lain. */
    fun sendCommand(type: PcCommand, input: String? = null) {
        val s = _session.value ?: return
        val lab = _selectedLab.value ?: return
        if (!s.canControl) return notice("Peran Anda tidak diizinkan mengirim perintah.")
        val c = _control.value
        if (c.busy) return
        val ids = c.selected.toList()
        if (ids.isEmpty()) return notice("Pilih minimal satu PC online.")

        val text: String? = if (type == PcCommand.MESSAGE) input?.trim()?.take(MAX_MESSAGE) else null
        if (type == PcCommand.MESSAGE && text.isNullOrEmpty()) return notice("Isi pengumuman tidak boleh kosong.")
        val url: String? = if (type == PcCommand.OPEN_URL) normalizeWebUrl(input.orEmpty()) else null
        if (type == PcCommand.OPEN_URL && url == null) return notice("Alamat web tidak valid. Gunakan http:// atau https://.")

        _control.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val r = GatewayApi(s.baseUrl, s.token).command(lab.id, type, ids, text, url)
                _control.value = ControlUi(notice = "${type.label}: terkirim ke ${r.sent} PC.")
                refreshNow()
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (isUnauthorized(e)) return@launch expireSession()
                _control.update { it.copy(busy = false, notice = messageOf(e)) }
            }
        }
    }

    private fun normalizeWebUrl(input: String): String? {
        var s = input.trim()
        if (s.isEmpty()) return null
        if (!s.contains("://")) s = "https://$s"
        val ok = s.startsWith("https://", ignoreCase = true) || s.startsWith("http://", ignoreCase = true)
        return if (ok && s.toHttpUrlOrNull() != null) s else null
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
        const val MAX_MESSAGE = 500
    }
}
