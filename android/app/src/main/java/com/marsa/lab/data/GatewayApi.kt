package com.marsa.lab.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS) // gateway menunggu server lab maksimal 25 detik
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    val json = Json { ignoreUnknownKeys = true }
    val jsonOut = Json { encodeDefaults = true }
}

class ApiException(message: String, val status: Int = 0) : Exception(message)

private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()

private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            if (cont.isActive) cont.resume(response) else response.close()
        }
    })
}

/**
 * Klien untuk MarsaLab.Gateway.
 *  - /auth/login, /auth/logout, /auth/labs
 *  - /l/{lab}/api/...  (diteruskan gateway ke server guru di lab)
 */
class GatewayApi(baseUrl: String, private val token: String? = null) {
    private val base = baseUrl.trimEnd(/)

    private fun request(path: String): Request.Builder =
        Request.Builder().url(base + path).apply {
            token?.let { header("X-Marsa-Token", it) }
        }

    private suspend fun exec(r: Request): String = withContext(Dispatchers.IO) {
        val resp = try {
            Http.client.newCall(r).await()
        } catch (e: IOException) {
            throw ApiException(networkMessage(e))
        }
        resp.use {
            val body = it.body?.string().orEmpty()
            if (!it.isSuccessful) throw ApiException(errorMessage(it.code, body), it.code)
            body
        }
    }

    private fun networkMessage(e: IOException): String = when (e) {
        is UnknownHostException -> "Alamat server tidak ditemukan. Periksa alamat dan koneksi internet."
        is SocketTimeoutException -> "Server tidak merespons (waktu habis)."
        is SSLException -> "Koneksi aman (HTTPS) gagal. Periksa alamat server."
        else -> "Tidak dapat terhubung ke server. Periksa koneksi internet."
    }

    private fun errorMessage(code: Int, body: String): String {
        try {
            val msg = Http.json.decodeFromString<ErrorDto>(body).error
            if (!msg.isNullOrBlank()) return msg
        } catch (ignored: Exception) {
            // bukan JSON (misalnya halaman error Cloudflare)
        }
        return when (code) {
            401 -> "Sesi tidak valid. Silakan masuk lagi."
            403 -> "Peran Anda tidak diizinkan melakukan ini."
            429 -> "Terlalu banyak percobaan. Coba lagi beberapa menit lagi."
            502, 503, 504, in 520..530 -> "Gateway tidak dapat dihubungi (HTTP $code)."
            else -> "HTTP $code"
        }
    }

    // ===== Auth =====
    suspend fun login(username: String, password: String): LoginResponse {
        val body = buildJsonObject {
            put("username", username)
            put("password", password)
        }.toString().toRequestBody(JSON_TYPE)
        val text = exec(request("/auth/login").post(body).build())
        return Http.json.decodeFromString(text)
    }

    suspend fun logout() {
        exec(request("/auth/logout").post("{}".toRequestBody(JSON_TYPE)).build())
    }

    suspend fun labs(): List<LabDto> =
        Http.json.decodeFromString(exec(request("/auth/labs").build()))

    // ===== API lab (diteruskan gateway) =====
    private fun labPath(lab: String, rest: String) = "/l/" + Uri.encode(lab) + "/api/" + rest

    suspend fun clients(lab: String): List<ClientDto> =
        Http.json.decodeFromString(exec(request(labPath(lab, "clients")).build()))

    /** Meminta client mengirim gambar layar. mode: "thumb" (kecil) atau "full". */
    suspend fun watch(lab: String, mode: String, seconds: Int, clientIds: List<String>? = null) {
        val body = buildJsonObject {
            put("mode", mode)
            put("seconds", seconds)
            if (clientIds != null) put("clientIds", JsonArray(clientIds.map { JsonPrimitive(it) }))
        }.toString().toRequestBody(JSON_TYPE)
        exec(request(labPath(lab, "watch")).post(body).build())
    }

    suspend fun screens(lab: String): Map<String, ShotDto> =
        Http.json.decodeFromString(exec(request(labPath(lab, "screens")).build()))

    /** Gambar layar terbaru satu PC. Status 404 berarti belum ada gambar. */
    suspend fun screen(lab: String, clientId: String): ShotDto =
        Http.json.decodeFromString(exec(request(labPath(lab, "screens/" + Uri.encode(clientId))).build()))

    /** Kirim event input ke PC (maks 60 per permintaan). Hanya untuk peran controller. */
    suspend fun control(lab: String, clientId: String, events: List<InputEv>) {
        val body = Http.jsonOut
            .encodeToString(ListSerializer(InputEv.serializer()), events)
            .toRequestBody(JSON_TYPE)
        exec(request(labPath(lab, "control/" + Uri.encode(clientId))).post(body).build())
    }
}
