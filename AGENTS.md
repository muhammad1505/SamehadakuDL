# AGENTS.md — SamehadakuDL

Instruksi ini berlaku untuk semua agent yang bekerja di repo ini. Baca juga
`README.md`, `app/build.gradle.kts`, dan `.github/workflows/build.yml` sebelum
mengubah kode.

## 1. Lingkungan kerja

- Repo: `/sdcard/code/SamehadakuDL`, branch utama `main`.
- Android single-module `:app`, package `app.samehadaku.dl`.
- JDK 17, compileSdk/targetSdk 35, minSdk 26.
- Versi aplikasi ada di `app/build.gradle.kts` (`versionCode` dan `versionName`).
- Changelog aplikasi bersumber dari `README.md` dan dipaketkan sebagai
  `assets/changelog.md` saat build.
- Workflow CI/release: `.github/workflows/build.yml`.
- Alat yang tersedia: `git`, `gh`, `curl`. File sementara gunakan `/tmp/opencode`.
- **Jangan menjalankan Gradle, build, atau test secara lokal** kecuali diminta
  eksplisit. Validasi hanya lewat GitHub Actions.
- Jangan menyentuh atau menghapus perubahan pengguna yang tidak terkait.
- Jangan commit `local.properties`, `*.jks`, `*.keystore`,
  `keystore.properties`, `build/`, `.gradle/`, `app/build/`, atau file secret.
- Jangan pernah menampilkan isi secret di log atau jawaban.

## 2. Alur kerja

1. Baca file terkait, `git status`, dan `git log -5`.
2. Buat perubahan sekecil mungkin. Ikuti arsitektur, nama, dan gaya yang sudah
   ada. Jangan refactor di luar kebutuhan.
3. Cek statis: `git diff --check`, cari `TODO`/placeholder, dan pastikan tidak
   ada file yang tidak sengaja ikut berubah.
4. Commit dengan pesan bahasa Indonesia yang ringkas. Commit rilis memakai
   format `Rilis <versi>: <ringkasan>`.
5. Push ke `main`, lalu tunggu CI unit test hijau.
6. Jangan membuat tag sebelum test di commit yang sama hijau.
7. Jika perlu validasi build signed sebelum tag, jalankan
   `gh workflow run CI --ref main -f release=true` lalu tunggu job selesai.
8. Baru buat tag `v<versi>` dan push tag.
9. Tunggu job release hijau. Unduh dan verifikasi artefak.
10. Minta pengguna menguji di perangkat. Jangan klaim "sudah dites" jika belum.

## 3. Versi dan changelog

- Satu rilis = satu versi. Naikkan patch satu langkah setiap rilis.
- `versionCode` selalu naik dan tidak pernah dipakai ulang.
- Jangan memakai suffix `-fix`, `-patch`, atau tanggal.
- Jangan lompat ke minor/major (misalnya `2.8.x`) tanpa persetujuan eksplisit.
- `versionName` harus sama persis dengan tag tanpa prefix `v` dan heading
  changelog.
- Setiap rilis wajib punya heading persis `## Rilis <versi>` di `README.md`.
- Catatan rilis memakai bahasa Indonesia, menjelaskan dampak ke pengguna, bukan
  detail internal. Jangan menulis ulang catatan rilis lama; hanya boleh
  merapikan urutan atau typo.
- Section rilis disimpan urut naik (2.7.14, 2.7.15, ...). About screen membaca
  changelog dengan urutan terbalik, jadi rilis terbaru harus berada di akhir
  file agar tampil paling atas.
- Ringkasan perubahan harus jujur: jangan mengklaim fitur yang belum diuji di
  perangkat.

## 4. CI/CD dan signing

- Test berjalan pada push/PR non-tag.
- Job release berjalan pada tag `v*` atau `workflow_dispatch` dengan input
  `release=true`.
- Guard rilis memeriksa `versionName` == tag == heading README. Jangan
  menonaktifkan guard ini.
- Build release ditandatangani dengan secret:
  - `KEYSTORE_BASE64`
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`
- Artefak rilis: `samehadaku-dl-${TAG}.apk` dan `SHA256SUMS`.
- Jangan publikasikan APK unsigned.
- Verifikasi rilis dengan `sha256sum -c SHA256SUMS`, cek signature, dan catat
  ukuran byte APK.
- Tag push melewati job test, jadi test harus hijau di `main` sebelum tag dibuat.
- Workflow menyimpan 3 rilis terbaru; jangan mengubah kebijakan prune tanpa
  diminta.
- Jika CI gagal: baca log lengkap, perbaiki file terkait saja, push ulang, dan
  ulangi. Jangan menurunkan versi atau menghapus test hanya agar hijau.

## 5. Build release dan ukuran APK

- Release memakai R8: `isMinifyEnabled = true` dan `isShrinkResources = true`.
- Jangan mengembalikan aturan ProGuard yang terlalu luas seperti
  `-keep class org.jsoup.**`, `okhttp3.**`, atau `okio.**`; library tersebut
  sudah punya consumer rules.
- Aturan keep yang wajib dipertahankan:
  - Constructor `AutoCheckWorker` dan `SmartStorageWorker` untuk WorkManager.
  - Nama enum di `app.samehadaku.dl.**` karena disimpan sebagai JSON.
- Jika menambah kode yang diakses lewat reflection, tambahkan keep rule
  spesifik untuk class/constructor tersebut, bukan seluruh package.
- `resourceConfigurations` dibatasi ke `in`/`id`. Jangan menambah bahasa baru
  tanpa permintaan.
- Metadata dependensi tidak ditulis ke APK (`dependenciesInfo`).
- Baseline v2.7.15: APK signed sekitar 3,2 MB (3.303.342 byte). Jika ukuran
  melonjak, periksa `classes.dex`, `resources.arsc`, dan aset besar sebelum
  menambah dependency baru.
- Jangan mengorbankan kompatibilitas data pengguna demi mengecilkan APK.

## 6. Kompatibilitas data

- Riwayat unduhan, langganan, notifikasi, dan Smart Storage disimpan sebagai
  JSON. Jangan mengganti nama enum atau key JSON tanpa migrasi.
- Data lama harus tetap terbaca setelah update. Jika format berubah, tulis
  migrasi dan unit test untuk data versi lama.
- File video pengguna tidak boleh dihapus atau dipindahkan tanpa aksi eksplisit
  dari pengguna.

## 7. Gaya komunikasi

- Bahasa Indonesia, ringkas, langsung ke hasil.
- Laporkan: apa yang berubah, bukti CI (run ID/status), hash dan ukuran APK,
  serta hal yang perlu dites.
- Jangan mengklaim sukses tanpa bukti CI. Jika belum bisa diverifikasi,
  katakan terus terang.
- Jika ada keputusan desain yang berisiko, jelaskan pilihan dan konsekuensinya
  sebelum menerapkannya.

## 8. Checklist rilis

- [ ] Perubahan sesuai permintaan, tanpa regresi yang diketahui.
- [ ] `git diff --check` bersih.
- [ ] Tidak ada secret atau artefak build yang ikut ter-commit.
- [ ] `versionCode` dan `versionName` dinaikkan.
- [ ] Heading `## Rilis <versi>` ada di `README.md` dan urutannya benar.
- [ ] Unit test CI hijau di `main`.
- [ ] Build signed manual hijau (disarankan sebelum tag).
- [ ] Tag `v<versi>` dibuat dan job release hijau.
- [ ] APK dan `SHA256SUMS` tersedia; hash cocok; ukuran dilaporkan.
- [ ] Pengguna diberi checklist tes perangkat.

## 9. Perintah referensi

```bash
# Cek perubahan
git status
git diff --check

# Push dan tunggu test
git push origin main
gh run list --workflow=CI --branch=main --limit 3
gh run watch <run-id> --exit-status

# Validasi build signed sebelum tag
gh workflow run CI --ref main -f release=true
gh run watch <run-id> --exit-status

# Rilis
git tag -a v<versi> -m "Rilis <versi>: <ringkasan>"
git push origin v<versi>

# Verifikasi rilis
gh release view v<versi> --json tagName,assets
sha256sum -c SHA256SUMS
```
