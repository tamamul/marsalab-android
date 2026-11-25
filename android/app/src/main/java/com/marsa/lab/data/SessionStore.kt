package com.marsa.lab.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme
import androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme
import androidx.security.crypto.MasterKey

/**
 * Menyimpan sesi (token) terenkripsi dengan Android Keystore.
 * Bila penyimpanan aman gagal dibuka, aplikasi tetap jalan tanpa menyimpan apa pun
 * (pengguna cukup login ulang) dan tidak pernah menyimpan token tanpa enkripsi.
 */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences? = openPrefs(context.applicationContext)

    fun loadSession(): Session? {
        val p = prefs ?: return null
        val token = p.getString(K_TOKEN, null) ?: return null
        val base = p.getString(K_BASE, null) ?: return null
        val exp = p.getLong(K_EXP, 0L)
        if (exp <= System.currentTimeMillis()) return null
        return Session(
            baseUrl = base,
            token = token,
            role = p.getString(K_ROLE, "viewer") ?: "viewer",
            user = p.getString(K_USER, "") ?: "",
            expiresAtMillis = exp,
        )
    }

    fun save(s: Session) {
        prefs?.edit()
            ?.putString(K_BASE, s.baseUrl)
            ?.putString(K_TOKEN, s.token)
            ?.putString(K_ROLE, s.role)
            ?.putString(K_USER, s.user)
            ?.putLong(K_EXP, s.expiresAtMillis)
            ?.apply()
    }

    /** Hapus token, tetapi ingat alamat server dan nama pengguna terakhir. */
    fun clearSession() {
        prefs?.edit()?.remove(K_TOKEN)?.remove(K_ROLE)?.remove(K_EXP)?.apply()
    }

    val lastBaseUrl: String? get() = prefs?.getString(K_BASE, null)
    val lastUsername: String? get() = prefs?.getString(K_USER, null)

    var showThumbs: Boolean
        get() = prefs?.getBoolean(K_THUMBS, true) ?: true
        set(v) { prefs?.edit()?.putBoolean(K_THUMBS, v)?.apply() }

    private companion object {
        const val FILE = "marsa_session"
        const val K_BASE = "base"
        const val K_TOKEN = "token"
        const val K_ROLE = "role"
        const val K_USER = "user"
        const val K_EXP = "exp"
        const val K_THUMBS = "thumbs"

        fun openPrefs(ctx: Context): SharedPreferences? {
            fun create(): SharedPreferences {
                val key = MasterKey.Builder(ctx)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                return EncryptedSharedPreferences.create(
                    ctx, FILE, key,
                    PrefKeyEncryptionScheme.AES256_SIV,
                    PrefValueEncryptionScheme.AES256_GCM,
                )
            }
            return try {
                create()
            } catch (ignored: Exception) {
                try {
                    ctx.deleteSharedPreferences(FILE)
                    create()
                } catch (ignored: Exception) {
                    null
                }
            }
        }
    }
}
