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

/** Event input untuk kontrol jarak jauh. Koordinat x,y dinormalisasi 0..1 terhadap layar PC. */
@Serializable
data class InputEv(
    val t: String,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val b: String? = null,
    val vk: Int = 0,
    val s: String? = null,
    val mods: List<String>? = null,
    val d: Int = 0,
)

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
