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
- Pemutar internal Media3/ExoPlayer dengan resume posisi tonton, tanda tonton otomatis
  setelah 90% video, playlist episode otomatis, kecepatan 0.5x–2x, mode layar
  FIT/ZOOM/FILL, siklus decoder Auto/HW/SW, Picture-in-Picture, pemutar langsung
  terbuka singkat dalam landscape lalu rotasi mengikuti arah HP (portrait maupun
  landscape) walau auto-rotate HP dimatikan, gesture
  kecerahan/volume/scrub, smart screen lock,
  sleep timer, equalizer + loudness boost, serta mode “Jangan ganggu” selama menonton.
  Background play memakai MediaSessionService foreground dengan kontrol di
  notifikasi dan jendela PiP, lalu berhenti saat aplikasi digeser keluar dari Recents.
  Subtitle bawaan dan file eksternal SRT/VTT/SSA-ASS
  digabung dalam satu daftar dengan pemilihan track presisi dan sinkronisasi
  ±0.1s; pemutar eksternal tetap tersedia dari menu folder.
- Smart Storage opsional: menandai episode yang sudah ditonton dan menghapus videonya
  otomatis setelah masa tenggang 1/3/7/14 hari, atau langsung lewat tombol manual
  dengan konfirmasi. Batas penyimpanan 5/10/20 GB dapat diaktifkan, dan layar
  “Kelola episode” menampilkan alasan file yang tertahan agar bisa ditindaklanjuti.
- Panel latar belakang & autostart yang ringkas: ringkasan “x/y siap” dan status
  per baris, baris izin bisa diketuk langsung ke pengaturan sistem, dan panduan OEM
  dibuka hanya saat dibutuhkan.
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

## Rilis 2.7.2

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
- Menyesuaikan alur izin dengan perangkat: Realme/Oppo dengan ColorOS lama
  (Android 10 ke bawah, termasuk Realme 3) diarahkan ke Info aplikasi karena izin
  latar belakang dan autostart berada di sana. Perangkat lain tetap memakai layar
  baterai atau pengelola autostart OEM yang sesuai.

## Rilis 2.7.3

- Memastikan notifikasi hasil unduhan benar-benar menggantikan notifikasi progres:
  kartu progres dihapus lebih dulu, lalu kartu selesai dipasang, sehingga keduanya
  tidak pernah tampil bersamaan.
- Menghapus laporan selesai dari sesi sebelumnya saat sesi unduhan baru dimulai.
- Panel “Latar belakang & autostart” kini menampilkan ringkasan “x/y siap” dan status
  per baris (“Sudah”/“Belum”) yang dibaca ulang setiap kembali ke layar ini, ditambah
  tombol “Periksa ulang”, sehingga jelas apakah pengecualian baterai sudah disetel.
- Pengecualian baterai disetel manual oleh pengguna: baris “Optimasi baterai”
  membuka daftar optimasi sistem, lalu pengguna memilih Samehadaku DL dan “Jangan
  optimalkan”. Aplikasi tidak memakai izin `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
  yang dibatasi Google Play dan memicu peringatan Play Protect pada APK sideload,
  serta tidak pernah meminta pengecualian otomatis.
- Baris Autostart kini berlabel “Tidak terbaca” karena Android tidak menyediakan API
  untuk membaca status autostart; pengguna tetap diarahkan ke pengelola OEM terkait.
- Menyesuaikan alur Realme/Oppo ColorOS lama agar tetap membuka Info aplikasi,
  dengan baris izin latar belakang dan autostart yang terpisah.
- Pemutar internal menambahkan kontrol kecepatan 0.75x–2x, mode layar Pas/Zoom/Penuh,
  penyembunyian system bar selama pemutaran, serta pesan kegagalan pemutaran yang
  menyediakan tombol “Coba lagi” tanpa harus keluar dari video.

## Rilis 2.7.4

- Pemutar internal mengikuti rotasi perangkat: memutar HP ke landscape langsung
  membuat video memenuhi layar, dengan tombol kunci landscape untuk perangkat yang
  rotasinya dikunci. Perubahan orientasi ditangani tanpa membuat ulang aktivitas,
  jadi video tidak terputus atau kembali ke posisi awal.
- Tombol “Jangan ganggu” di pemutar menonaktifkan notifikasi selama menonton lewat
  akses kebijakan notifikasi (`ACCESS_NOTIFICATION_POLICY`) yang diberikan pengguna
  sendiri di pengaturan sistem; filter yang aktif sebelumnya dipulihkan saat pemutar
  ditutup atau aplikasi ditinggalkan, sehingga HP tidak tertinggal dalam mode senyap.
- Kontrol pemutaran baru: tombol putar/jeda dan slider posisi dengan waktu berjalan,
  ketuk dua kali di sisi kiri/kanan untuk mundur/maju 10 detik, serta cubit dan geser
  pada video untuk zoom hingga 4x. Layar gelap sendiri setelah 3,5 detik berjalan dan
  muncul lagi saat diketuk.
- Subtitle eksternal bisa dimuat dari penyimpanan (SRT, WebVTT, SSA/ASS) tanpa
  meninggalkan video; mode gestur subtitle mengatur ukuran teks lewat cubit dan
  posisi vertikalnya lewat geseran. Pemutar juga mengaktifkan fallback dekoder
  perangkat lunak saat dekoder perangkat keras tidak mendukung video.

## Rilis 2.7.5

- Aturan pemutar tidak lagi kembali ke awal setiap episode. Mode layar
  Pas/Zoom/Penuh, kecepatan putar, serta ukuran dan posisi subtitle disimpan,
  lalu dipakai lagi saat membuka episode berikutnya.
- “Jangan ganggu” memakai filter prioritas, bukan total senyap, sehingga
  notifikasi tetap ditahan tetapi audio video terus berbunyi. Filter yang aktif
  sebelum menonton tetap dipulihkan saat pemutar ditutup.
- Bilah progres digambar ulang: track tipis, bagian yang sudah dimuat lebih
  terang, dan titik geser yang membesar saat ditarik. Waktu berjalan dan durasi
  mengapit bilah, jadi posisi video lebih mudah dibaca.
- Folder episode kini menjadi playlist pemutar: tombol episode sebelumnya dan
  berikutnya, label “Ep x dari y”, panel daftar episode untuk melompat langsung,
  serta overlay akhir episode dengan pilihan putar ulang atau lanjut. Posisi
  tiap episode disimpan saat berpindah.
- Tombol subtitle membuka panel opsi lebih dulu — pilih atau ganti file, lepas
  subtitle, atur ukuran teks dan posisi, atau aktifkan mode gestur — bukan
  langsung membuka pemilih file.

## Rilis 2.7.6

- Pemutar memakai tata letak empat zona: top bar (kembali, judul, decoder,
  playlist, PiP), transport tengah (±10 detik), bottom bar (kunci, waktu, CC,
  audio/EQ, rasio, setelan, layar penuh), dan panel kaca 285dp dengan tab
  Episode, Subtitle, Audio, dan Setelan. Semua tombol pengaturan memakai badge
  status dan banner aksi yang menjelaskan mode aktif.
- Gesture pemutar: geser vertikal kiri untuk kecerahan, kanan untuk volume,
  geser horizontal untuk scrub dengan pratinjau waktu, tap untuk HUD, dan
  double-tap sisi untuk ±10 detik. Smart screen lock membekukan seluruh sentuhan
  sampai kapsul “Buka kunci” ditekan.
- Mesin pemutaran menambahkan siklus decoder Auto/HW/SW, resolusi mengikuti track
  yang tersedia, pemilihan audio track, EQ (Normal, Cinema Bass, Vocal Boost,
  Night Mode), loudness boost sampai +150%, sleep timer Off/15/30/60 menit, dan
  auto-play episode berikutnya dengan hitungan yang bisa dibatalkan.
- Trek subtitle bawaan dibaca dari video dan digabung dengan file eksternal dalam
  satu daftar; pemilihan memakai `TrackSelectionOverride` sehingga dua track
  berbahasa sama tetap bisa dibedakan. Ikon CC disembunyikan bila video tidak
  punya subtitle sama sekali, dan tab Subtitle tetap menyediakan tombol memuat
  file SRT/WebVTT/SSA-ASS.
- Background play dan PiP kini didukung `MediaSessionService` foreground: audio
  tetap berjalan saat aplikasi di latar belakang, dan notifikasi/PiP punya
  kontrol putar, jeda, serta seek. Pemutar, dekoder, dan efek audio tetap memakai
  instance ExoPlayer yang sama.
- Panel Optimasi Sistem memakai status sebelum/sesudah yang jelas (amber “Belum
  disetting” dan hijau “Sudah disetting”), badge `PERLU ATUR (n/3)` atau
  `✓ OPTIMAL (3/3)`, dan tombol “Atur →” menuju Info aplikasi di semua versi
  Android. Panduan menyesuaikan Android 6–11, 12–13, 14–16+, serta menu khas
  Xiaomi/Redmi/POCO, Samsung, OPPO/Realme, dan Vivo/iQOO.
- Autostart bawaan pabrik tidak bisa dibaca Android, jadi kunjungan ke menu OEM
  dicatat dan aplikasi menampilkan konfirmasi satu ketuk “Sudah mengaktifkan
  Mulai Otomatis?” saat kembali. Status izin lain diperbarui otomatis saat
  aplikasi kembali ke depan, dan kartu pengingat hanya muncul kontekstual saat
  Background play diaktifkan.

## Rilis 2.7.7

- Kontrol pemutar kini berupa pill berlabel pendek yang menyatu dengan ikonnya
  (AUTO/HW/SW, NORM/BASS/VOCL/NITE, FIT/ZOOM/FILL, dan ON/OFF/FILE/HARD untuk
  subtitle). Ketuk pill langsung beralih — decoder berganti, preset audio berputar,
  rasio berganti, subtitle hidup/mati — tanpa membuka panel, dan umpan baliknya
  berupa notifikasi satu baris di bawah judul, bukan kartu di tengah video.
  Tekan lama pill membuka panel terkait (Audio atau Subtitle) untuk pilihan
  lengkap.
- Deteksi softsub dan hardsub: trek teks bawaan dibaca lengkap dengan bahasa,
  format (SRT/WebVTT/SSA-ASS/TTML), dan status track default. Tab Subtitle
  menampilkan ringkasan “Softsub • n trek teks terbaca” atau “Hardsub • tidak ada
  trek teks di file” beserta metadata tiap track, dan file tanpa trek teks ditandai
  HARD pada pill CC karena teksnya menyatu di gambar.
- Tombol fullscreen/rotasi dilepas; pemutar memakai orientasi sensor selama
  dibuka sehingga memutar HP langsung memenuhi layar, lalu orientasi sebelumnya
  dipulihkan saat pemutar ditutup. Loudness boost memakai bilah geser bergaya
  seek bar agar seragam dengan kontrol lain.
- Menghapus episode lama dari instalasi sebelumnya kini benar-benar berjalan.
  Android 10 memakai legacy storage (`WRITE_EXTERNAL_STORAGE` +
  `requestLegacyExternalStorage`) sehingga `Download/SamehadakuDL` bisa dibaca dan
  dihapus lewat File API tanpa pemilih folder, sedangkan Android 11+ meminta satu
  dialog sistem untuk seluruh batch baris MediaStore, bukan mencoba baris per baris.
- Koleksi menawarkan izin folder sekali saat dibuka bila masih ada episode milik
  instalasi lama, jadi penghapusan tidak gagal diam-diam lebih dulu. Pemilih folder
  terbuka langsung di `Download/SamehadakuDL` (juga dari Pengaturan) dan pilihan
  folder divalidasi memuat episode yang dituju; folder yang salah tidak lagi
  disimpan sebagai lokasi unduhan.

## Rilis 2.7.8

- Cek episode otomatis tidak lagi berjalan ganda. Pemicu saat aplikasi dibuat
  dihapus (cek saat kembali ke depan sudah cukup), antrean “Cek sekarang”
  memakai `KEEP` sehingga tidak membatalkan cek yang sedang berjalan, dan worker
  periodik serta manual berbagi satu gerbang proses. Pemeriksaan kedua langsung
  selesai tanpa mengulang gelombang “Detect episodes”. Tombol “Periksa” di
  Langganan memakai gerbang yang sama dan mencatat waktu cek, jadi kembali ke
  aplikasi tidak langsung memicu pemeriksaan yang sama lagi.
- Saat perangkat offline atau DNS gagal, waktu percobaan tetap dicatat. Membuka
  aplikasi berulang kali tidak lagi memicu deretan percobaan DNS baru; WorkManager
  periodik tetap mencoba lagi sesuai interval, dan tombol “Cek sekarang” tersedia
  setelah koneksi kembali.
- Pemutar lebih hemat baterai: layar tidak lagi terus menyala saat video dijeda
  atau sudah berakhir (layar hanya ditahan selama putar atau buffering singkat),
  dan UI pemutar berhenti memperbarui posisi tiga kali per detik saat aplikasi
  berada di latar belakang. Posisi tonton disimpan tiap 10 detik, bukan 5 detik.
- Audio latar lebih andal: ExoPlayer memegang wake lock lokal hanya selama
  pemutaran, sehingga layar mati tidak menghentikan dekode file yang sudah
  diunduh. Masuk PiP tidak lagi dijeda oleh `ON_STOP` pada ROM yang memanggilnya,
  dan rasio PiP ekstrem dijepit ke rentang yang didukung Android.
- Layar akhir episode tetap menampilkan bar atas (kembali, playlist, PiP), dan
  notifikasi pemutaran memakai ikon play sendiri, bukan ikon unduhan.

## Rilis 2.7.13

- Menekan tombol putar di tab Unduhan sekarang membuka pemutar internal seperti
  Koleksi, bukan lagi menyerahkan file ke aplikasi lain.
- Daftar unduhan diberi pemisah tanggal bergaris (“Hari ini”, “22 Nov 2026”, …)
  lengkap dengan jumlah episode per hari, dan setiap kartu menampilkan tanggal
  unduhan selesai agar riwayat mudah dibaca.
- Cinema DND kini memakai filter “alarm saja”, sehingga notifikasi WhatsApp,
  pesan, dan panggilan tidak lagi muncul saat menonton; alarm tetap berbunyi dan
  audio video tidak terpengaruh.
- DND bertahan selama pemutaran latar berjalan (sebelumnya dilepas begitu
  aplikasi ke latar), dan pilihan DND diingat: episode berikutnya mengaktifkannya
  kembali otomatis tanpa perlu membuka panel. Mematikan manual menghapus memori
  itu. Ikon DND juga tersedia langsung di bar atas pemutar.
- Equalizer dan loudness boost lebih andal: efek dipasang ulang saat pemutar siap
  dan selama pemutaran, sehingga preset tidak lagi diam-diam tidak aktif, dan
  perangkat tanpa EQ memberi tahu pengguna alih-alih tampak rusak.

## Rilis 2.7.12

- Rotasi pemutar kembali mengikuti arah HP seperti 2.7.7, termasuk pada HP yang
  auto-rotate-nya dimatikan. Landscape tetap jadi pembuka singkat saat episode
  diputar, lalu orientasi memakai sensor murni sehingga HP yang diputar tegak
  langsung portrait dan diputar menyamping kembali landscape. Aturan 2.7.9 yang
  menyerahkan orientasi ke kunci rotasi sistem membuat pemutar diam di satu
  orientasi pada HP dengan auto-rotate mati.

## Rilis 2.7.11

- Rotasi pemutar sekarang sepenuhnya mengikuti pengguna. Landscape hanya dipakai
  sebagai pembuka singkat (±1,5 detik) saat episode diputar, lalu orientasi
  diserahkan ke sensor dan kunci rotasi sistem: memutar HP ke tegak langsung
  membuat pemutar portrait, memutar ke samping kembali penuh landscape, berulang
  sebanyak yang diinginkan tanpa menutup pemutar. Sebelumnya pemutar bertahan
  landscape selama HP belum pernah diputar ke samping, sehingga HP yang sejak awal
  dipegang tegak tidak pernah bisa portrait.

## Rilis 2.7.10

- Pemutar sekarang langsung terbuka dalam landscape saat episode diputar, jadi
  pengguna tidak perlu memutar HP lebih dulu untuk mendapat gambar penuh. Setelah
  menonton dalam landscape lalu HP diputar tegak, rotasi diserahkan kembali ke
  sensor dan kunci rotasi sistem, sehingga video portrait serta daftar episode
  tetap bisa dibaca tegak. Perilaku ini menggantikan aturan 2.7.9 yang menunggu
  HP diputar lebih dulu sebelum layar berubah orientasi.

## Rilis 2.7.9

- “Jangan ganggu” sekarang dipulihkan otomatis saat aplikasi dibuka lagi setelah
  proses mati atau force-stop ketika menonton, sehingga filter prioritas tidak
  tertinggal aktif. Filter tetap menyala selama video hanya dijeda di pemutar,
  dan dilepas saat pemutar atau aplikasi benar-benar ditinggalkan. Jika pengguna
  mengubah mode jangan ganggu sendiri, pilihan pengguna tidak ditimpa. Aktivitas
  yang dibuat ulang karena perubahan tema atau bahasa juga tidak melepas filter,
  karena pemutaran masih berjalan.
- Orientasi pemutar mengikuti kunci rotasi sistem. Dengan auto-rotate aktif layar
  mengikuti sensor; dengan auto-rotate mati pemutar tidak memutar sendiri seperti
  versi sebelumnya.
- Arti background play dibuat konsisten dan didokumentasikan: audio tetap berjalan
  saat aplikasi di rumah atau layar mati bila opsi aktif, sedangkan menggeser
  aplikasi keluar dari Recents selalu menghentikan pemutaran dan notifikasi media.
  Teks bantuan di panel Setelan pemutar ikut diperbarui.
- Waktu “Terakhir dicoba” dan “Terakhir berhasil” dipisahkan. Percobaan yang
  offline, gagal, atau berhenti di verifikasi Cloudflare hanya memperbarui waktu
  percobaan, sehingga membuka aplikasi tidak mengulang pemeriksaan yang sama
  terus-menerus, sementara waktu berhasil tetap menunjukkan cek terakhir yang
  benar-benar sampai ke daftar episode.
- Pada Realme/Oppo ColorOS lama (Android 10 ke bawah), baterai kini meminta
  konfirmasi manual setelah pengguna kembali dari Info aplikasi, sama seperti
  autostart. Status 3/3 tidak lagi terus kuning ketika sakelar aktivitas latar
  pabrik sudah aktif tetapi tidak terbaca oleh status baterai Android.
- Pemutar otomatis mencoba dekoder software satu kali ketika dekoder perangkat
  gagal membuka video, lalu melanjutkan dari posisi terakhir, termasuk posisi
  resume yang tersimpan bila kegagalan terjadi sebelum video mulai. Jika tetap
  gagal, pesan kesalahan menjelaskan masalah file, izin, jaringan, atau format
  beserta kode Media3-nya, bukan hanya pesan sistem yang samar. Pilihan audio dan
  subtitle dari pemutar lama ikut direset karena treknya sudah dilepas bersama
  pemutar itu, dan episode berikutnya kembali memakai dekoder pilihan pengguna.
- Target sentuh tombol pemutar, pill, dan chip dinaikkan ke minimal 48dp. Banner
  tips sekali muncul untuk memberi tahu bahwa menekan lama pill CC atau Audio
  membuka opsi lengkap, sehingga kontrol lanjutan lebih mudah ditemukan.
- Tombol Picture-in-Picture disembunyikan bila perangkat tidak mendukungnya, dan
  kegagalan membuka mode kecil sekarang menampilkan banner penjelasan. Rasio
  jendela tetap dijepit ke rentang yang diterima Android, termasuk video vertikal
  dan metadata ukuran yang rusak.
- Pesan log layanan pemutaran tidak lagi menyiratkan bahwa audio terus berbunyi
  setelah aplikasi digeser keluar; pesan berhenti sekarang bersifat informasional.
  Komentar dan dokumentasi pemutar juga diselaraskan dengan perilaku sebenarnya.
- CI memeriksa bahwa tag rilis cocok dengan `versionName` dan README memiliki
  bagian `## Rilis x.y.z` sebelum APK ditandatangani. Unit test baru menjaga
  perhitungan jadwal cek otomatis, gerbang cek tunggal, dan penjepitan rasio PiP.
  Changelog di aplikasi berhenti pada judul bagian berikutnya sehingga catatan
  rilis tidak lagi mencampur daftar dari bagian Penyimpanan atau Smart Storage.

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

- Ringkasan “Status izin sistem” menghitung tiga hal yang menentukan pekerjaan
  latar: baterai, notifikasi, dan autostart. Dua di antaranya bisa dibaca Android;
  autostart dan sakelar aktivitas latar ColorOS lama dilengkapi konfirmasi manual.
  Status dibaca ulang saat kembali dari pengaturan sistem, dan tombol “Periksa
  ulang” memperbaruinya tanpa meninggalkan layar.
- Baris “Baterai tanpa batas” menunjukkan apakah aplikasi sudah dikecualikan dari
  optimasi baterai. Mengetuknya membuka Info aplikasi → Baterai/Penggunaan baterai,
  karena dialog pengecualian langsung membutuhkan izin
  `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` yang dibatasi Google Play dan memicu
  peringatan Play Protect pada APK sideload. Aplikasi tidak pernah meminta
  pengecualian secara otomatis.
- Baris “Autostart” diberi label “Tidak terbaca” karena Android tidak menyediakan
  cara membacanya otomatis, lalu membuka Info aplikasi yang menjadi titik awal
  menu pabrik; panduan OEM menjelaskan nama submenu yang harus dicari. Setelah
  kembali, aplikasi menampilkan konfirmasi satu ketuk “Sudah mengaktifkan Mulai
  Otomatis?”. Status hanya berubah hijau setelah dikonfirmasi.
- Pada Realme/Oppo dengan ColorOS lama (Android 10 ke bawah), baris baterai juga
  meminta konfirmasi manual setelah pengguna kembali dari Info aplikasi. ColorOS
  menyimpan sakelar “Izinkan aktivitas latar belakang” terpisah dari status
  optimasi baterai Android, sehingga tanpa konfirmasi hitungan 3/3 bisa terus
  berwarna kuning meskipun izinnya sudah aktif.
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
