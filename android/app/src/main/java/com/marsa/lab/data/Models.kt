package com.marsa.lab.data

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String,
    val role: String,
    val user: String,
    val hours: Int = 8,
)

@Serializable
data class LabDto(
    val id: String,
    val name: String,
    val online: Boolean = false,
)

@Serializable
data class ClientDto(
    val clientId: String,
    val computerName: String,
    val ipAddress: String = "",
    val lastSeenUtc: String = "",
    val status: String = "Offline",
) {
    val isOnline: Boolean get() = status.equals("Online", ignoreCase = true)
}

@Serializable
data class ShotDto(
    val t: String = "",
    val w: Int = 0,
    val h: Int = 0,
    val mode: String? = null,
    val jpg: String,
)

/** Perintah ke PC lab (tahap 2). `wire` = nilai yang dikirim ke server. */
enum class PcCommand(val wire: String, val label: String, val destructive: Boolean = false) {
    MESSAGE("message", "Pengumuman"),
    LOCK("lock", "Kunci layar"),
    UNLOCK("unlock", "Buka kunci"),
    OPEN_URL("openurl", "Buka URL"),
    RESTART("restart", "Restart", destructive = true),
    SHUTDOWN("shutdown", "Matikan", destructive = true),
}

@Serializable
data class CommandResult(val sent: Int = 0)

@Serializable
data class ErrorDto(val error: String? = null)

/** Sesi login ke gateway. Kata sandi tidak pernah disimpan. */
data class Session(
    val baseUrl: String,
    val token: String,
    val role: String,
    val user: String,
    val expiresAtMillis: Long,
) {
    val canControl: Boolean get() = role == "controller"
}
