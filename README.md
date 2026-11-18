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
- Satu notifikasi unduhan yang berubah dari progres ke jeda lalu selesai, lengkap
  dengan judul anime, episode, kualitas, server, ukuran, kecepatan, dan alasan gagal.
- Koleksi per judul anime: tampilan folder ringkas, ketuk untuk memutar file terakhir,
  tekan lama untuk aksi berbagi atau menghapus folder.
- Pemberitahuan saat file episode yang sama sudah ada sehingga tidak diunduh ulang.
- Aksi terpisah untuk membersihkan riwayat (file tetap ada) dan menghapus file permanen,
  masing-masing dengan konfirmasi.
- Penyimpanan fleksibel: folder pilihan (SAF), MediaStore, atau penyimpanan aplikasi.
- Pemutar internal Media3/ExoPlayer dengan resume posisi tonton dan tanda tonton
  otomatis setelah 90% video diputar; pemutar eksternal tetap tersedia dari menu folder.
- Smart Storage opsional: menandai episode yang sudah ditonton dan menghapus videonya
  otomatis setelah masa tenggang 1/3/7/14 hari, atau langsung lewat tombol manual
  dengan konfirmasi. Batas penyimpanan 5/10/20 GB dapat diaktifkan, dan layar
  “Kelola episode” menampilkan alasan file yang tertahan agar bisa ditindaklanjuti.
- Panel latar belakang & autostart yang ringkas: baris izin bisa diketuk langsung ke
  pengaturan sistem, dan panduan OEM dibuka hanya saat dibutuhkan.
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

## Rilis 2.6.16

- Menambahkan Filedon sebagai server unduhan: halaman `filedon.co/view/...` dibaca
  otomatis, termasuk sesi dan token berbaginya, lalu link unduhan langsung dipakai.
- Menampilkan daftar server yang didukung di Pengaturan agar pilihan host lebih jelas;
  server pilihan tetap dipakai lebih dulu dengan fallback otomatis ke server lain.

## Rilis 2.6.17

- Pilihan server di Pengaturan memakai chip yang membungkus sehingga semua host,
  termasuk Filedon, tetap terlihat dan bisa dipilih di layar sempit.
- Parser menyatukan link server di dalam dan di luar daftar `<li>`, jadi tidak ada
  host yang terlewat saat halaman memakai dua bentuk markup.
- Unduhan yang gagal di semua server kini punya tombol "Buka halaman episode di
  browser" sebagai jalan terakhir, termasuk untuk host yang meminta captcha.

## Rilis 2.6.18

- Server yang dicoret situs (mis. `Filedon` pada episode lama) kini tetap muncul di
  daftar server sebagai chip nonaktif "tidak tersedia", jadi jelas kenapa tidak bisa
  dipilih alih-alih menghilang dari daftar.

## Rilis 2.6.19

- Resolver Filedon memakai cookie jar sehingga cookie sesi dan XSRF dari respons
  redirect tetap terkirim ke halaman unduhan.
- Link Filedon yang kedaluwarsa atau sudah mencapai batas unduhan dikenali sebagai
  kegagalan permanen, bukan dicoba ulang tanpa akhir.
- Parsing link Filedon diperketat ke domain resmi, dan pembacaan props halaman
  dipisahkan dari alur jaringan agar lebih mudah dirawat.

## Rilis 2.6.20

- Resolusi Filedon disimpan sebentar, jadi preflight dan unduhan tidak mengulang
  GET halaman plus POST download untuk link yang sama.

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

## Rilis 2.6.22

- Menambahkan panel collapsible “Link diambil dari mirror” pada kartu unduhan. Panel
  menjelaskan bahwa link di domain utama mati, menyebut situs mirror yang dipakai, host
  penyedia (mis. Gofile), serta tipe dan kualitas/resolusi yang tetap sama.
- Menyimpan asal mirror bersama task unduhan, sehingga keterangan tetap tampil setelah
  aplikasi ditutup dan antrean dibuka kembali.

## Rilis 2.6.23

- Memperbaiki pemilihan episode dari mirror yang bisa mengambil episode anime lain.
  Hasil pencarian sekarang harus cocok dengan judul/slug anime yang diminta sebelum
  dipakai, termasuk saat slug berbeda antar mirror.
- Mengurutkan percobaan unduhan: format dan resolusi dari Pengaturan di domain utama,
  lalu kualitas yang sama di semua mirror. Format lain baru dicoba setelah itu, tetap
  pada resolusi yang sama (X265 → MKV → MP4 untuk pilihan X265 480p). Resolusi tidak
  lagi berubah diam-diam ke 720p/HD.
- Host pilihan di Pengaturan tetap dicek lebih dulu; host lain hanya dipakai bila link
  mati. Antrean mencatat host dan situs mirror yang benar-benar dipakai.
- Menambahkan menu “Log aplikasi” di Pengaturan untuk memantau proses pencarian,
  mirror, pemilihan kualitas/host, dan unduhan. Log bisa disalin, dibagikan, atau
  dihapus langsung dari aplikasi.

## Rilis 2.6.24

- Pembatalan cek otomatis (misalnya saat batas waktu WorkManager habis) tidak lagi
  dilaporkan sebagai “Auto check failed”; proses berhenti bersih tanpa melanjutkan sisa
  langganan yang hasilnya sudah tidak bisa dipakai.
- Kegagalan DNS kini berhenti cepat dengan pesan koneksi yang jelas, tanpa mengulang
  permintaan tiga kali sampai menghabiskan waktu cek otomatis.
- Deteksi episode mencoba mirror yang dikenal ketika host utama tidak dapat dihubungi,
  bukan hanya ketika halaman utama berhasil dibuka tetapi gagal diparse.
- Cek otomatis berhenti lebih awal jika semua host tidak dapat dihubungi, dan waktu cek
  terakhir tidak diperbarui agar percobaan berikutnya tidak menunggu jadwal penuh.

## Rilis 2.6.25

- Menambahkan Smart Storage opsional untuk menandai episode yang sudah ditonton dan
  menghapus videonya otomatis setelah masa tenggang 1/3/7/14 hari (default 3 hari).
- Deteksi tonton memakai durasi `MediaMetadataRetriever` saat video dibuka lewat pemutar
  eksternal dan aplikasi kembali aktif. Tanda tonton juga bisa diatur manual dari Koleksi.
- Pembersihan berjalan harian lewat WorkManager dengan catch-up saat aplikasi dibuka,
  serta tombol “Bersihkan sekarang” di Pengaturan.
- Hanya file hasil unduhan aplikasi yang dikelola, dan ukuran file diverifikasi ulang
  sebelum dihapus. Konfirmasi atau penolakan penyimpanan tidak memaksa penghapusan.

## Rilis 2.6.27

- Tombol “Bersihkan sekarang” kini benar-benar menghapus episode yang ditandai
  tonton tanpa menunggu masa tenggang, setelah dialog konfirmasi. Masa tenggang
  1/3/7/14 hari hanya berlaku untuk pembersihan otomatis di latar belakang.
- Pesan hasil pembersihan sekarang membedakan tidak ada episode tonton, file yang
  dilewati karena ukuran atau bentuknya berubah, dan file yang menunggu izin
  penyimpanan.
- Pembatalan normal saat memuat pengumuman situs, misalnya ketika layar
  ditinggalkan, tidak lagi dicatat sebagai error di Log aplikasi.
- Verifikasi ukuran byte dan kepemilikan file hasil unduhan tetap dilakukan
  sebelum penghapusan, baik untuk aksi manual maupun otomatis.

## Rilis 2.6.26-fix

- Menghapus izin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` yang dapat membuat
  Google Play Protect menandai APK sebagai aplikasi tidak aman saat dipasang
  dari luar Play Store.
- Tombol optimasi baterai tetap membuka daftar optimasi sistem; pengguna memilih
  Samehadaku DL lalu “Jangan optimalkan/Tanpa batasan” secara manual.
- Status pengecualian baterai, pembatasan latar belakang, hemat data, notifikasi,
  dan autostart OEM tidak berubah.

## Rilis 2.6.26

- Menambahkan panel “Latar belakang & autostart” di Pengaturan agar cek episode
  otomatis tidak mudah ditunda oleh Doze atau pengelola daya OEM.
- Menampilkan status dan tombol perbaikan untuk optimasi baterai (Android 6+),
  pembatasan latar belakang (Android 9+), dan hemat data (Android 7+).
- Menambahkan status notifikasi dan permintaan izin `POST_NOTIFICATIONS` pada
  Android 13+, dengan pengaturan notifikasi aplikasi sebagai fallback.
- Tombol Autostart membuka pengelola OEM yang terdeteksi—MIUI/HyperOS,
  ColorOS/Realme/OPPO, OxygenOS, iManager, Device Care, Mobile Manager,
  Phone Master, dan lainnya—lalu fallback ke optimasi baterai dan Info aplikasi.
- Semua izin bersifat opsional dan hanya dibuka lewat tindakan pengguna; cek
  susulan saat aplikasi dibuka tetap menjadi jaring pengaman.

## Rilis 2.6.25-fix

- File unduhan lama atau sebelum pembaruan Smart Storage kini tetap dikenali dari
  Koleksi, termasuk setelah riwayat unduhan dibersihkan.
- Pengenalan memakai lokasi unduhan aplikasi, pola nama file downloader, dan ukuran
  byte yang sama; file yang lolos dicatat ulang agar pengelolaan berikutnya tidak
  bergantung pada riwayat.
- Alasan file diterima atau ditolak sekarang ditulis ke Log aplikasi, sehingga file
  yang tidak bisa ditandai tonton bisa diperiksa langsung dari ponsel.
- Penghapusan dari folder SAF pilihan kini juga menangani file di dalam subfolder
  `SamehadakuDL`, bukan hanya folder anime di level teratas.

## Rilis 2.7.0

- Menambahkan pemutar internal berbasis Media3/ExoPlayer sebagai cara default membuka
  episode dari Koleksi. Posisi tonton disimpan dan dilanjutkan otomatis, dan episode
  ditandai tonton setelah 90% video benar-benar diputar. Menu folder tetap menyediakan
  “Putar dengan aplikasi lain”.
- Menghitung waktu tonton pemutar eksternal per sesi dan mengakumulasikannya, sehingga
  jeda panjang atau menonton dalam dua kesempatan tidak menghapus progres. Hanya episode
  terakhir yang dilacak, dan sesi yang jauh melebihi durasi video diabaikan.
- Menambahkan layar “Kelola episode” untuk melihat tanda tonton, jatuh tempo, playback
  yang sedang dilacak, serta file yang tertahan karena ukuran berubah, menunggu
  konfirmasi penyimpanan, atau ditolak. Setiap file bisa dihapus atau dibatalkan
  satu per satu.
- Menampilkan notifikasi hasil pembersihan latar belakang dengan ringkasan jumlah file,
  ruang yang kembali, dan file yang tertahan. Menyentuh notifikasi membuka layar Smart
  Storage; mengaktifkan fitur juga langsung memicu pemeriksaan susulan.
- Menambahkan batas penyimpanan opsional (tanpa batas, 5, 10, atau 20 GB). Jika total
  episode tonton melebihi batas, episode tertua dihapus lebih awal; default tetap
  tanpa batas.

## Rilis 2.7.1

- Menghentikan pencarian mirror tambahan ketika semua mirror utama gagal DNS, sehingga
  cek otomatis tidak tertahan beberapa menit saat koneksi internet bermasalah.
- Meringkas kegagalan DNS di Log aplikasi menjadi satu baris penyebab; stack trace
  berulang tidak lagi memenuhi riwayat log.
- Memperjelas catatan Smart Storage saat sesi pemutar eksternal diabaikan: durasi
  ditampilkan dalam jam/menit dan file video ditegaskan tetap ada.
- Mencatat jadwal pembersihan Smart Storage hanya ketika WorkManager benar-benar
  memasang ulang jadwalnya.

## Rilis 2.8.0

- Menyatukan notifikasi unduhan menjadi satu alur dengan satu ID: progres, dijeda,
  lalu selesai. Ringkasan selesai tidak lagi muncul sebagai notifikasi kedua, dan
  notifikasi akhir tetap tersedia sampai diketuk atau ditutup pengguna.
- Melengkapi isi notifikasi: judul anime, episode, format dan resolusi, server yang
  dipakai, ukuran file, persen, kecepatan, status, serta alasan kegagalan. Data ini
  dipakai sama pada progres, jeda, dan ringkasan selesai.
- Membatasi hitungan notifikasi ke sesi unduhan yang sedang berjalan agar riwayat
  episode lama tidak ikut terhitung.
- Menambahkan aksi “Lanjut semua” pada notifikasi yang dijeda, berdampingan dengan
  “Batalkan semua”.
- Menyederhanakan panel “Latar belakang & autostart”: status baterai dan autostart
  menjadi baris ringkas yang bisa diketuk, baris notifikasi hanya muncul saat
  diblokir, dan panduan OEM bisa dibuka-tutup. Panel tidak lagi meminta izin
  notifikasi sendiri; permintaan awal tetap dilakukan setelah splash dan
  pengguna yang menolak diarahkan ke pengaturan sistem.

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

## Latar belakang dan autostart

Cek otomatis dijalankan WorkManager, sehingga Android dapat menundanya saat
perangkat masuk Doze atau OEM membatasi aktivitas latar belakang. Panel
“Latar belakang & autostart” di Pengaturan dibuat ringkas: setiap baris bisa
diketuk dan langsung membuka layar pengaturan yang sesuai.

- Baris “Optimasi baterai” menunjukkan apakah aplikasi sudah dikecualikan.
  Mengetuknya membuka daftar optimasi; pengguna memilih Samehadaku DL lalu
  “Jangan optimalkan”. Aplikasi tidak mendeklarasikan
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` karena izin itu dibatasi Google Play
  dan dapat memicu peringatan Play Protect saat APK dipasang dari luar Play Store.
- Baris “Autostart” membuka pengelola autostart OEM yang terdeteksi—MIUI/HyperOS,
  ColorOS/Realme/OPPO, OxygenOS, iManager, Device Care, Mobile Manager,
  Phone Master, dan lainnya—dengan fallback ke optimasi baterai dan Info aplikasi
  bila komponen tidak tersedia. Intent dicoba satu per satu agar tetap bekerja
  pada Android 11+ yang membatasi visibilitas paket.
- Baris pembatasan latar belakang dan hemat data hanya muncul saat sistem
  benar-benar membatasi aplikasi, sehingga panel tidak penuh status yang sudah
  aman.
- Baris “Notifikasi” hanya muncul saat notifikasi diblokir dan mengetuknya membuka
  pengaturan notifikasi aplikasi. Pada Android 13+ aplikasi tetap meminta izin
  `POST_NOTIFICATIONS` setelah splash; panel tidak meminta izin sendiri.
  Pengguna yang menolak bisa mengaktifkannya lagi lewat pengaturan tersebut.
- Panduan langkah per OEM tersembunyi di balik baris “Panduan …” dan hanya
  ditampilkan saat dibuka.

Semua tindakan berasal dari pengguna; aplikasi tidak meminta pengecualian baterai
atau autostart secara otomatis. Doze tetap dapat menunda WorkManager, tetapi
aplikasi menjalankan pemeriksaan susulan saat dibuka.

## Smart Storage

Smart Storage nonaktif secara default dan baru bekerja setelah diaktifkan di
Pengaturan. Fitur ini hanya mengelola video yang diunduh aplikasi ke folder
`SamehadakuDL/<judul anime>/` atau folder pilihan; arsip `.rar`/`.zip`, file
pengguna lain, dan video di luar folder unduhan tidak pernah disentuh.

- Episode ditandai tonton secara manual dari Koleksi, otomatis oleh pemutar
  internal setelah 90% video benar-benar diputar, atau lewat pemutar eksternal
  lalu aplikasi kembali visible. Durasi pemutar eksternal dibaca dengan
  `MediaMetadataRetriever`.
- Video bisa diputar di dalam aplikasi dengan pemutar Media3: posisi tonton
  disimpan dan ditawarkan lagi saat episode dibuka berikutnya, sehingga sinyal
  selesai berasal dari posisi nyata, bukan perkiraan waktu. Menu folder di
  Koleksi tetap menyediakan “Putar dengan aplikasi lain”.
- Untuk pemutar eksternal, waktu tonton dihitung per sesi dan diakumulasi: jeda
  panjang atau menonton dalam dua kesempatan tidak lagi menghapus progres, dan
  hanya episode yang terakhir dibuka yang dilacak. Satu sesi yang jauh lebih lama
  dari durasi video (misalnya pemutar ditinggal atau di-pause) diabaikan agar
  tidak menjadi tanda tonton palsu. Ambangnya 80% durasi.
- File baru dihapus setelah masa tenggang 1, 3, 7, atau 14 hari (default 3 hari),
  bukan langsung setelah ditonton. Pemeriksaan berjalan harian lewat WorkManager
  dan bisa dipicu manual dengan “Bersihkan sekarang”.
- Sebelum menghapus, aplikasi memastikan foldernya masih ada di lokasi unduhan
  dan ukuran file sama persis dengan ukuran saat ditandai. Jika penyimpanan
  meminta konfirmasi sistem atau menolak, file dan tanda tontonnya dipertahankan
  agar bisa dihapus manual dari Koleksi.
- Unduhan lama yang dibuat sebelum pencatatan Smart Storage tetap dikenali dari
  file yang tampil di Koleksi. Aplikasi memeriksa lokasi unduhan
  (`Download/SamehadakuDL`, folder privat aplikasi, atau folder pilihan), pola
  nama file yang dibuat downloader (`<episode>_<FORMAT>_<resolusi>.<ext>`), dan
  ukuran file yang sama persis. File yang lolos dicatat ulang agar pengelolaan
  berikutnya tidak bergantung pada riwayat unduhan.
- File yang tidak lolos verifikasi (misalnya video yang disalin sendiri ke
  folder pilihan) tidak akan ditandai otomatis dan tidak dikelola Smart Storage.
- Setiap keputusan Smart Storage—diterima sebagai unduhan lama atau ditolak
  beserta alasannya—ditulis ke Log aplikasi di Pengaturan, sehingga masalah file
  yang tidak bisa ditandai tonton bisa diperiksa tanpa komputer.
- Batas penyimpanan opsional (tanpa batas, 5, 10, atau 20 GB) bisa diaktifkan.
  Jika total episode tonton melebihi batas, episode tertua dihapus lebih awal
  tanpa menunggu masa tenggang; default tetap tanpa batas.
- Layar “Kelola episode” di Pengaturan menampilkan semua tanda tonton beserta
  tanggal jatuh tempo, sisa masa tenggang, playback yang sedang dilacak, dan file
  yang tertahan. File yang menunggu konfirmasi sistem, ukurannya berubah, atau
  ditolak penyimpanan ditandai dengan alasannya dan bisa dihapus atau dibatalkan
  satu per satu.
- Menghapus file atau folder secara manual juga membersihkan tanda tontonnya,
  sehingga Smart Storage tidak mencoba menghapusnya lagi.

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
