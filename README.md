# Samehadaku DL

Aplikasi Android untuk mengunduh episode Samehadaku dengan antarmuka **Premium Glassmorphism**:
tema gelap, aksen cyan/ungu, Plus Jakarta Sans, dan wallpaper kosmik buatan AI yang dipakai
bersama di seluruh layar (splash, beranda, dan halaman episode).

## Fitur

- Deteksi episode dari URL daftar anime maupun episode tunggal.
- Terima link yang dibagikan dari browser/aplikasi lain lewat share sheet Android.
- Pemilihan episode cepat lewat daftar, pilih semua, atau rentang (`1-12, 15`).
- Pemilihan format (MKV/MP4/X265), resolusi, dan server (Pixeldrain/Mediafire) dengan fallback.
- Satu layar Unduhan untuk antrean berjalan sekaligus riwayat (Semua/Selesai/Gagal),
  dengan konkurensi 1–3, jeda/lanjut per episode maupun massal.
- Progres, kecepatan, ukuran, server aktif, dan alasan kegagalan per task.
- Koleksi per judul anime: tampilan folder ringkas, tekan lama untuk membuka episode
  terakhir, mengulangi yang gagal, atau menghapus folder dari koleksi.
- Pemberitahuan saat file episode yang sama sudah ada sehingga tidak diunduh ulang.
- Aksi terpisah untuk membersihkan riwayat (file tetap ada) dan menghapus file permanen,
  masing-masing dengan konfirmasi.
- Penyimpanan fleksibel: folder pilihan (SAF), MediaStore, atau penyimpanan aplikasi.
- Sesi Cloudflare dengan WebView verifikasi, reset sesi, dan verifikasi ulang otomatis.
- Pengaturan unduhan paralel dan fallback kualitas terdekat.
- Splash screen atmosferik, bottom navigation, dan transisi antar layar.

## Build

Prasyarat: JDK 17 dan Android SDK 35.

```bash
./gradlew assembleDebug
```

APK: `app/build/outputs/apk/debug/app-debug.apk`.

Release signed memakai `keystore.properties` di root (tidak di-commit):

```properties
storeFile=keystore/samehadaku-release.jks
storePassword=...
keyAlias=samehadaku
keyPassword=...
```

```bash
./gradlew assembleRelease
```

APK: `app/build/outputs/apk/release/app-release.apk`.

Install:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Test

```bash
./gradlew testDebugUnitTest
```

## Lisensi font

Plus Jakarta Sans disertakan di `app/src/main/res/font/plus_jakarta_sans.ttf` di bawah
SIL Open Font License. Salinan lisensi ada di `licenses/PlusJakartaSans-OFL.txt`.

## Catatan

Aplikasi ini hanya untuk penggunaan pribadi. Aplikasi tidak meng-host konten apa pun;
hormati hak cipta pemilik konten.
