# Scanner Dokumen

Aplikasi Android untuk memotret dokumen dan menyimpannya sebagai PDF dan PNG.

## Fitur
- Kamera pemindai dengan deteksi tepi kertas otomatis, potong, luruskan, putar, dan filter (hitam-putih, abu-abu, warna)
- Banyak halaman sekaligus (sampai 50) menjadi satu PDF
- Setiap halaman juga disimpan sebagai PNG
- Bisa ambil foto dari galeri
- Tombol Buka PDF, Kirim PDF, Kirim PNG (WA, email, dll.)
- PDF disimpan di Download/Scanner, PNG di Pictures/Scanner

## Syarat HP
- Android 10 ke atas
- Google Play Services aktif. Saat pertama kali scan, HP perlu internet untuk mengunduh modul pemindai.

## Build APK lewat GitHub
1. Buat repository baru di GitHub (boleh private), misalnya `scanner-dokumen`.
2. Upload seluruh isi folder ini, termasuk folder `.github`.
3. Buka tab **Actions** → workflow **Build APK** berjalan otomatis (atau klik **Run workflow**).
4. Setelah selesai (centang hijau), buka hasil run → bagian **Artifacts** → unduh `ScannerDokumen-apk`.
5. Ekstrak zip-nya, pindahkan `app-debug.apk` ke HP, lalu pasang (izinkan "instal dari sumber tidak dikenal").
