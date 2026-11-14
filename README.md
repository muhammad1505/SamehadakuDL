# Samehadaku DL

Aplikasi Android untuk mengunduh episode Samehadaku dengan antarmuka **Premium Glassmorphism**:
tema gelap, aksen cyan/ungu, Plus Jakarta Sans, dan wallpaper kosmik buatan AI yang dipakai
bersama di seluruh layar (splash, beranda, dan halaman episode).

## Fitur

- Deteksi episode dari URL daftar anime maupun episode tunggal.
- Terima link yang dibagikan dari browser/aplikasi lain lewat share sheet Android.
- Pemilihan episode cepat lewat daftar, pilih semua, atau rentang (`1-12, 15`).
- Pemilihan format (MKV/MP4/X265), resolusi, dan server (Pixeldrain/Mediafire/Gofile/Acefile/Filedon)
  dengan fallback.
- Satu layar Unduhan untuk antrean berjalan sekaligus riwayat (Semua/Selesai/Gagal),
  dengan konkurensi 1–3, jeda/lanjut per episode maupun massal.
- Progres, kecepatan, ukuran, server aktif, dan alasan kegagalan per task.
- Koleksi per judul anime: tampilan folder ringkas, ketuk untuk memutar file terakhir,
  tekan lama untuk aksi berbagi atau menghapus folder.
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

## Rilis 2.6.3

- Menampilkan thumbnail frame video pada setiap folder di Koleksi, dengan fallback ikon folder
  bila penyimpanan atau provider tidak menyediakan thumbnail.
- Membuat judul file anime yang panjang berjalan otomatis agar seluruh nama dapat terbaca.
- Menambahkan halaman Tentang dengan versi aplikasi dan changelog yang dibaca dinamis dari
  catatan rilis README saat proses build.

## Rilis 2.6.4

- Mengganti preview frame video di Koleksi dengan cover poster anime dari halaman detailnya.
- Menambahkan cache cover di memory, fallback lintas mirror, dan fallback ikon folder bila cover
  tidak tersedia atau provider penyimpanan menolak akses gambar.
- Menambahkan test parser cover untuk metadata sosial dan gambar lazy-load.

## Rilis 2.6.5

- Menerapkan cover anime yang sama pada daftar Langganan dan Unduhan agar seluruh daftar
  utama konsisten.
- Memoles navigasi bawah dengan state aktif glass-gradient, glow neon, indikator pilihan,
  dan animasi tekan yang lebih premium.

## Rilis 2.6.6

- Menambahkan cover anime pada setiap kartu hasil pencarian dengan cache yang sama seperti
  Koleksi, Langganan, dan Unduhan.
- Menjaga cover hasil pencarian tetap mengikuti query terbaru agar hasil lama tidak muncul
  pada pencarian baru.

## Rilis 2.6.7

- Menambahkan halaman detail dari setiap hasil pencarian dengan ringkasan anime, jumlah episode,
  cover, serta aksi Unduh dan Langganan dalam satu alur.
- Meminta episode terakhir secara langsung saat menambah langganan, termasuk dari halaman detail
  pencarian, sehingga pengguna tidak perlu bolak-balik mengatur progres.
- Menggunakan kualitas, resolusi, dan host dari Pengaturan secara otomatis pada alur unduhan
  berikutnya, termasuk unduhan dari Beranda dan Pencarian.
- Membuat kartu Unduhan lebih compact; file selesai dapat diputar dengan mengetuk kartunya tanpa
  tombol Play tambahan.
- Merapikan aksi massal Unduhan menjadi satu baris dan menghilangkan latar kotak pada item navigasi
  yang tidak aktif.
- Menambahkan ekspor backup langganan dalam format JSON yang kompatibel dengan fitur impor.

## Rilis 2.6.8

- Merapikan kartu Unduhan agar lebih compact dan memindahkan aksi utama ke sisi kanan judul,
  mengikuti pola kartu Koleksi; file selesai tetap bisa dibuka dengan mengetuk kartu.
- Menyamakan teks berjalan untuk judul/caption panjang di Koleksi, Pencarian, Langganan,
  Unduhan, dan ticker pengumuman.
- Memperjelas hasil verifikasi Cloudflare dengan spinner polos, dialog sukses, serta pesan gagal.
- Menampilkan changelog versi terbaru sekali setelah aplikasi diperbarui, tanpa harus membuka
  Pengaturan terlebih dahulu.
- Mengubah nama launcher menjadi Samehadaku Downloader.
- Menambahkan ringkasan detail anime dari pencarian berupa deskripsi, rating, genre, dan jumlah
  episode yang terbaca.
- Menambahkan ikon kalender di Beranda untuk membuka jadwal rilis anime mingguan.
- Menambahkan ticker pengumuman di Beranda untuk informasi delay/libur yang tersedia di situs.

## Rilis 2.6.9

- Menyesuaikan parser jadwal dengan halaman `/jadwal/` terbaru Samehadaku: jadwal per hari,
  cover, jam, dan rating kini dipetakan ke kalender bulanan sesuai tanggalnya.
- Membuat kalender rilis menampilkan penanda tanggal, detail anime terpilih, cover, jam, dan
  navigasi bulan.
- Ketuk folder anime di Koleksi kini langsung memutar file terbaru; tombol expand dipisahkan
  khusus untuk melihat daftar episode.
- Memprioritaskan sinopsis panjang dari halaman detail anime dan menampilkan metadata seperti
  status, tipe, sumber, durasi, studio, produser, dan tanggal rilis jika tersedia.
- Meningkatkan akurasi pencarian melalui selector kartu tema terbaru dan pemeringkatan hasil
  berdasarkan kecocokan judul.
- Menampilkan status eksplisit di halaman Cloudflare ketika tantangan ditemukan, belum ditemukan,
  berhasil disimpan, atau belum dapat diverifikasi.

## Rilis 2.6.10

- Mengembalikan fungsi klik kartu folder Koleksi sebagai expand; klik item file di dalam folder
  kini langsung memutar episode.
- Menyeragamkan aksi file selesai di Unduhan menjadi Play, Bagikan, dan Hapus.
- Memfilter hasil pencarian yang tidak relevan dan menempatkan judul yang paling cocok di urutan pertama.
- Mengubah aksi halaman Cloudflare menjadi Selesai saat verifikasi terdeteksi dan Batal saat tidak ada.
- Menambahkan teks berjalan pada judul jadwal panjang agar jam dan rating tetap terbaca.
- Menyamakan caption aplikasi menjadi Samehadaku Downloader di Tentang, logo, dan notifikasi.

## Rilis 2.6.12

- Koleksi tidak lagi meminta izin baca seluruh video/galeri.
- Koleksi hanya membaca file yang ditulis aplikasi ke `Download/SamehadakuDL`
  atau folder yang dipilih lewat SAF di Pengaturan.
- Subfolder per judul anime tetap terbaca, termasuk arsip `.rar`/`.zip`.
- Setelah install ulang, file lama bisa dibuka dengan memilih folder
  `Download/SamehadakuDL` di Pengaturan › Penyimpanan.

## Rilis 2.6.13

- Memperbaiki Koleksi yang kosong setelah izin media dihapus: file di
  `Download/SamehadakuDL` sekarang dikenali lewat path folder, bukan hanya
  metadata pemilik file.
- Izin media tetap tidak diminta; file lama yang tidak lagi dikaitkan dengan
  aplikasi tetap bisa dibuka dengan memilih folder lewat SAF.

## Rilis 2.6.14

- Koleksi kembali meminta izin baca video/penyimpanan agar episode lama di
  `Download/SamehadakuDL` otomatis terbaca, termasuk setelah uninstall lalu
  install ulang.
- Izin hanya dipakai untuk memindai folder unduhan aplikasi, bukan seluruh
  galeri. File baru milik instalasi ini tetap terbaca walau izin ditolak.
- Folder pilihan lewat SAF tetap tersedia sebagai cadangan, terutama untuk
  arsip `.rar`/`.zip` lama yang bukan file media.
- Konsekuensi: APK kembali memuat `READ_MEDIA_VIDEO` dan
  `READ_EXTERNAL_STORAGE`, sehingga peringatan izin di Play Protect/VirusTotal
  bisa muncul lagi. Verifikasi hash dan appeal Play Protect tetap dilakukan.

## Rilis 2.6.15

- Menghapus folder atau file di Koleksi tidak lagi dilaporkan "sudah tidak ada" ketika
  penyimpanan sebenarnya menolak menghapus. Hasil MediaStore diverifikasi ulang per baris
  dan sisa yang gagal dihapus dicoba satu per satu.
- Menampilkan konfirmasi sistem saat Android mengizinkan penghapusan lewat dialog izin,
  dan menandai penghapusan yang ditolak penyimpanan sebagai gagal, bukan sebagai berhasil.
- Android 10 tidak menyediakan dialog konfirmasi untuk file milik instalasi sebelumnya,
  jadi Koleksi kini menawarkan pemilihan folder `Download/SamehadakuDL` sekali, lalu
  melanjutkan penghapusan secara otomatis.
- Tab Unduhan memakai alur hapus yang sama: konfirmasi, status hasil yang jujur, dan
  percobaan ulang setelah izin diberikan.

## Rilis 2.6.21

- Menyamakan integrasi server di semua menu dan notifikasi: riwayat Unduhan kini
  menyimpan server yang benar-benar dipakai, sedangkan notifikasi progres, ringkasan,
  dan episode baru menampilkan format, resolusi, serta server termasuk Filedon.
- Membersihkan daftar mirror dari domain mati/placeholder dan menerjemahkan permalink
  episode `.li` (`-subtitle-indonesia`) ke bentuk kanonik, sehingga link lama tetap bisa
  diunduh.
- Mencari anime lewat pencarian mirror ketika slug berbeda (`ao-ashi-s2` vs
  `ao-ashi-season-2`) dan memakai halaman kanonik yang lebih lengkap saat URL berasal
  dari `.li`.
- Fallback mirror tidak lagi berhenti di halaman pertama yang bisa diparse: semua set
  link dikumpulkan dan dicoba sampai ada server yang benar-benar hidup. Pencarian juga
  tidak lagi berhenti di mirror yang mengembalikan halaman kosong.

## Rilis 2.6.20

- Resolusi Filedon disimpan sebentar, jadi preflight dan unduhan tidak mengulang
  GET halaman plus POST download untuk link yang sama.

## Rilis 2.6.19

- Resolver Filedon memakai cookie jar sehingga cookie sesi dan XSRF dari respons
  redirect tetap terkirim ke halaman unduhan.
- Link Filedon yang kedaluwarsa atau sudah mencapai batas unduhan dikenali sebagai
  kegagalan permanen, bukan dicoba ulang tanpa akhir.
- Parsing link Filedon diperketat ke domain resmi, dan pembacaan props halaman
  dipisahkan dari alur jaringan agar lebih mudah dirawat.

## Rilis 2.6.18

- Server yang dicoret situs (mis. `Filedon` pada episode lama) kini tetap muncul di
  daftar server sebagai chip nonaktif "tidak tersedia", jadi jelas kenapa tidak bisa
  dipilih alih-alih menghilang dari daftar.

## Rilis 2.6.17

- Pilihan server di Pengaturan memakai chip yang membungkus sehingga semua host,
  termasuk Filedon, tetap terlihat dan bisa dipilih di layar sempit.
- Parser menyatukan link server di dalam dan di luar daftar `<li>`, jadi tidak ada
  host yang terlewat saat halaman memakai dua bentuk markup.
- Unduhan yang gagal di semua server kini punya tombol "Buka halaman episode di
  browser" sebagai jalan terakhir, termasuk untuk host yang meminta captcha.

## Rilis 2.6.16

- Menambahkan Filedon sebagai server unduhan: halaman `filedon.co/view/...` dibaca
  otomatis, termasuk sesi dan token berbaginya, lalu link unduhan langsung dipakai.
- Menampilkan daftar server yang didukung di Pengaturan agar pilihan host lebih jelas;
  server pilihan tetap dipakai lebih dulu dengan fallback otomatis ke server lain.

## Penyimpanan dan izin

Koleksi memakai tiga sumber:

- File di `Download/SamehadakuDL/<judul anime>/` lewat MediaStore. Pada
  Android 13+ aplikasi meminta `READ_MEDIA_VIDEO`; Android 10–12 memakai
  `READ_EXTERNAL_STORAGE` (dibatasi sampai SDK 32). Izin ini membuat file dari
  instalasi sebelumnya ikut terbaca tanpa SAF.
- File milik instalasi yang sedang berjalan. Android selalu mengizinkan
  aplikasi membaca file yang ia tulis sendiri, bahkan tanpa izin media.
- Folder yang dipilih pengguna lewat SAF di Pengaturan › Penyimpanan. Akses SAF
  bersifat rekursif, jadi semua subfolder anime ikut terbaca.

Izin media hanya dipakai untuk memindai folder `SamehadakuDL`; aplikasi tidak
menampilkan galeri atau file di luar folder tersebut. Jika izin ditolak, unduhan
baru tetap muncul, sedangkan file dari instalasi lama dan arsip non-video perlu
dibuka lewat SAF.

## Verifikasi APK

Setiap rilis menyertakan file `SHA256SUMS`. Cocokkan hash APK yang diunduh dengan:

```bash
sha256sum -c SHA256SUMS
```

Signature APK juga dapat diperiksa dengan `apksigner verify --print-certs`.

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
