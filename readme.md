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

## Tahap 3 (versi 0.3.0) — layar penuh + kontrol jarak jauh
Ketuk kartu PC yang online di layar pemantau.
- Tampilan layar penuh (frame 1280 px dari client, sekitar 1-2 gambar/detik), mode imersif.
- Semua peran dapat melihat. Tombol **Kontrol** hanya untuk peran `controller`, default mati.
- Gestur 1 jari: ketuk = klik kiri, tahan = klik kanan, geser = seret. Tombol **Gulir** mengubah geser jadi scroll.
- 2 jari: zoom dan geser tampilan (tanpa kontrol, 1 jari juga menggeser).
- Tombol **Keyboard** memunculkan keyboard HP; baris tombol khusus: Esc, Tab, Enter, Backspace, Del, panah,
  Home/End/PgUp/PgDn, Ctrl+A/C/V/X/Z, Alt+Tab.
- Kontrol otomatis mati saat aplikasi ke latar belakang atau layar ditutup.

Batasan: tombol Windows ditolak oleh client; Ctrl+C/V memakai clipboard PC, bukan HP; keyboard fisik belum
didukung; input baru sampai ke PC saat client mengirim frame berikutnya (tunda sekitar 0,25-0,7 detik);
keyboard memakai mode kata sandi agar tanpa prediksi teks, sehingga huruf non-Latin/emoji belum didukung.
Perkiraan data: sekitar 5-15 MB per menit saat layar penuh dibuka.

## Rencana berikutnya
- Perintah ke PC terpilih (pengumuman, kunci, restart, matikan, buka URL) — dilewati dulu atas keputusan pemilik proyek.

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
