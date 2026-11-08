# Samehadaku DL

Aplikasi Android untuk mengunduh episode Samehadaku dengan antarmuka **Premium Glassmorphism**:
tema gelap, aksen cyan/ungu, Plus Jakarta Sans, dan wallpaper kosmik buatan AI yang dipakai
bersama di seluruh layar (splash, beranda, dan halaman episode).

## Fitur

- Deteksi episode dari URL daftar anime maupun episode tunggal.
- Terima link yang dibagikan dari browser/aplikasi lain lewat share sheet Android.
- Pemilihan episode cepat lewat daftar, pilih semua, atau rentang (`1-12, 15`).
- Pemilihan format (MKV/MP4/X265), resolusi, dan server (Pixeldrain/Mediafire/Gofile/Acefile)
  dengan fallback.
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

## Rilis 2.6.0

- Menyamakan bahasa visual semua menu dengan Beranda: brand anchor, hierarki judul,
  aksen neon, kartu glass, dan action bar yang konsisten.
- Bottom navigation menyesuaikan ukuran layar kecil agar tombol Beranda tidak meluber.
- Menambahkan state loading yang jelas pada Pusat Notifikasi.
- Mencegah hasil pencarian lama menimpa hasil dari query terbaru saat jaringan lambat.

## Rilis 2.6.1

- Memperbaiki pembacaan daftar episode dari struktur baru Samehadaku pada aksi Unduh
  dan Langganan.
- Menambahkan fallback nomor episode dari judul episode dan mirror ketika halaman utama
  gagal diparse.

## Rilis 2.6.2

- Memperbaiki unduh movie dari hasil pencarian yang memakai permalink bersih tanpa teks
  `episode-1`, termasuk pembacaan format, resolusi, dan host link.
- Menyesuaikan parser dengan layout link download baru yang tidak selalu memakai elemen `li`
  dan memastikan URL relatif dibaca sebagai URL absolut.
- Menambahkan tombol salin link pada setiap hasil pencarian untuk pengecekan mandiri.
- Merapikan aksi kartu pencarian, antrean, dan langganan menjadi ikon compact serta menghapus
  aksi yang berulang.

## Build

Prasyarat: JDK 17 dan Android SDK 35.

```bash
./gradlew assembleDebug
```

Jika shell Android/shared storage menolak menjalankan wrapper secara langsung, gunakan
`bash ./gradlew assembleDebug` (dan pola yang sama untuk perintah Gradle lainnya).

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
