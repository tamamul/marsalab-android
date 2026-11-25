# MARSA Lab Android (native Kotlin)

Aplikasi Android untuk memantau lab lewat **MarsaLab.Gateway** (login akun guru, lalu
permintaan diteruskan gateway ke server guru di lab). Tidak ada sambungan langsung ke PC lab.

## Tahap 1 (versi 0.1.0) — pemantauan
- Login ke gateway (`/auth/login`), token disimpan terenkripsi (Android Keystore). Kata sandi tidak disimpan.
- Daftar lab beserta status online (`/auth/labs`).
- Pantau PC per lab: total/online/offline, filter, thumbnail layar (`/l/{lab}/api/clients|watch|screens`).
- Polling tiap 5 detik hanya saat aplikasi terlihat; sakelar "Tampilkan layar" untuk hemat kuota.
- Sesi habis (401) otomatis kembali ke halaman login.
- Dapat dipakai peran `viewer` maupun `controller` (tahap 1 hanya membaca).

## Tahap 2 (versi 0.2.0) — kontrol layar/PC
- Peran `controller`: tekan lama kartu PC online untuk memilih (bisa banyak), lalu pilih aksi di bilah bawah.
- Aksi: pengumuman, kunci layar, buka kunci, buka URL, restart, matikan. Restart/matikan meminta konfirmasi.
- Tombol "Semua online" memilih semua PC online; tombol kembali membatalkan pilihan lebih dulu.
- Peran `viewer` tetap hanya memantau (tidak ada pilihan/aksi); server juga menolak dengan 403.
- Perintah dikirim `POST /l/{lab}/api/commands` berisi `{type, clientIds, text?, url?}`.
  Jalur ada di `GatewayApi.COMMAND_PATH` bila kontrak server guru berbeda.
- Perbaikan tahap 1: `trimEnd('/')` yang tidak bisa dikompilasi.

## Rencana berikutnya
- Tahap 3: tampilan layar penuh + kontrol jarak jauh (sentuh, scroll, keyboard) — peran `controller`.

## Build
Android Studio (Koala atau lebih baru, JDK 17): buka folder `android/`, Sync, lalu Run.
Tanpa Android Studio: jalankan workflow **Build MARSA-Lab Android** di GitHub Actions, unduh
artefak `MarsaLab-Android-debug` (APK debug, perlu izin pasang dari sumber tak dikenal).

## Struktur
- `data/GatewayApi.kt`  klien HTTP gateway (OkHttp + kotlinx.serialization)
- `data/SessionStore.kt` penyimpanan sesi terenkripsi
- `ui/AppViewModel.kt`  status aplikasi dan loop pemantauan
- `ui/*Screen.kt`       Login, Daftar Lab, Pemantau (Jetpack Compose)

Alamat server bawaan: `https://relay.marsa9.com` (ubah di layar login). Hanya HTTPS yang diterima.
