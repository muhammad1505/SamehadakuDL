# PROMPT BUILD APK — "Samehadaku DL" (Kotlin + Jetpack Compose)

Cara pakai: kirim **MASTER PROMPT** dulu. Setelah model menjawab "READY", kirim **PHASE 1**, lalu PHASE 2, dst. satu per satu. Setiap fase harus dikompilasi dulu; kalau ada error build, tempel log error lengkap ke model dan minta perbaikan file yang terdampak saja (bukan seluruh proyek). Jangan minta seluruh proyek dalam satu jawaban: output akan terpotong.

---

# MASTER PROMPT (kirim pertama)

You are a senior Android engineer. Build a production-quality Android app, "Samehadaku DL", by porting the attached Python script (see APPENDIX at the end of this message) to native Kotlin. Deliver complete, compilable, ready-to-build files. No hand-holding, no tutorials, no explanations beyond one-line notes.

## Output rules (strict)
1. Every file is output as: a heading line `### path/relative/to/repo/root`, then ONE fenced code block with the FULL file content. Never use `...`, `// rest unchanged`, `TODO`, or pseudo-code. Never abbreviate.
2. Work in phases. I will tell you which phase to produce. Output only that phase's files. If you are about to run out of output space, stop cleanly at a file boundary and end with exactly `CONTINUE FROM: <next file path>`; I will reply "continue".
3. Code must compile on first try. Prefer boring, well-known APIs over clever ones. Do not invent library APIs; if unsure whether an API exists in the pinned version, use an alternative you are sure of.
4. All user-visible UI strings in Indonesian. Code, comments, identifiers in English.
5. At the end of each phase print a short "FILES" list and "NEXT" (one line). Nothing else.
6. Reply "READY" now, nothing else.

## Product
Personal-use downloader app for anime episodes from Samehadaku (any host containing "samehadaku", e.g. v2.samehadaku.how). User pastes an anime list URL (`/anime/<slug>/`) or a single episode URL. App detects episodes, shows available quality (format × resolution × host), lets the user pick episodes + quality + preferred host, then downloads in the background with automatic host fallback.

## Tech stack (pin these, use version catalog `gradle/libs.versions.toml`)
- Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21 with the `org.jetbrains.kotlin.plugin.compose` plugin, KSP matching Kotlin 2.0.21.
- compileSdk 35, targetSdk 35, minSdk 26. JDK 17.
- Compose BOM 2024.10.01, Material 3, Navigation Compose, Lifecycle (viewmodel-compose, runtime-compose).
- Hilt (with KSP) for DI, Coroutines + Flow, OkHttp 4.12.0, Jsoup 1.18.1, DataStore Preferences, `androidx.documentfile`, Timber optional.
- Tests: JUnit4 + Truth or plain asserts (JVM unit tests only, no instrumented tests).
- Package: `app.samehadaku.dl`. App name: "Samehadaku DL". Single module `:app`.

## Architecture: MVVM + Clean Architecture
```
app/src/main/java/app/samehadaku/dl/
  SamehadakuApp.kt            (@HiltAndroidApp)
  MainActivity.kt             (single activity, NavHost)
  di/                         (Hilt modules)
  core/                       (UrlUtils, SlugUtils, Result wrapper, Dispatchers)
  domain/
    model/                    (Episode, QualityOption, DownloadTask, DownloadState, Host, VideoFormat, Resolution)
    repository/               (interfaces)
    usecase/                  (DetectEpisodesUseCase, LoadQualityUseCase, EnqueueDownloadsUseCase, ...)
  data/
    network/                  (CloudflareSession, HtmlFetcher, HttpClientFactory)
    parser/                   (EpisodeListParser, DownloadLinkParser)
    host/                     (HostResolver: PixeldrainResolver, MediafireResolver)
    download/                 (Downloader, DownloadQueue, DownloadService, StorageWriter)
    settings/                 (SettingsRepository on DataStore)
  presentation/
    navigation/ theme/ home/ cloudflare/ episodes/ quality/ queue/ settings/
```
Pure-Kotlin parsers (no Android imports) so they are unit-testable on JVM.

## Cloudflare strategy (most important part)
The site is behind Cloudflare. Python `cloudscraper` no longer works; the app solves this with a WebView:
1. `CloudflareScreen`: a Compose-hosted `WebView` (JS enabled, DOM storage enabled, `CookieManager.setAcceptCookie(true)`, `setAcceptThirdPartyCookies(webView, true)`) loads the site. Detect challenge completion when the page title no longer contains "Just a moment" and `cf_clearance` exists in `CookieManager.getCookie(url)`. Then pop back automatically.
2. `CloudflareSession` stores: the exact WebView user-agent string (`WebSettings.userAgentString`) and cookie header for the host. cf_clearance is bound to the user-agent and IP, so ALL OkHttp requests to that host MUST send the identical UA and the cookie header (use an OkHttp Interceptor reading from CloudflareSession).
3. `HtmlFetcher.fetch(url)`: try OkHttp first (with session). If response is 403/503 or body looks like a challenge (contains "just a moment", "cf-browser-verification", "cf-challenge", "attention required" within the first 4000 chars, case-insensitive) → emit `NeedsVerification` so the UI opens CloudflareScreen, then retry. Retry up to 3 times with backoff for network errors, but do NOT retry a Cloudflare challenge (go straight to verification).
4. Fallback if OkHttp keeps getting challenged even with valid cookies (TLS fingerprint mismatch): fetch the page through an off-screen/visible WebView and read `document.documentElement.outerHTML` via `evaluateJavascript` (JSON-decode the returned string). Implement as `WebViewHtmlFetcher` used automatically as a second strategy.
5. Do NOT hardcode a User-Agent; use the WebView's.
6. Download hosts (Pixeldrain, Mediafire) do not need Cloudflare cookies; use a plain OkHttp client with a generic browser UA for them.

## Parsing behavior (port EXACTLY from the Python source in the appendix)
### Episode list (`/anime/<slug>/` page) — function `extract_episode_links`
- Resolve every href against the page URL (`URI.resolve`/Jsoup `absUrl`). Accept only http(s) and hosts equal to the page host or containing "samehadaku". Reject paths containing `/anime/`, `/category/`, `/tag/`. Drop the page itself. Normalize URLs: strip `#` and `?`, ensure trailing `/`.
- Episode number rules (IMPORTANT — slugs are inconsistent on this site): `baseSlug` = last path segment of the list page URL.
  - path matches regex `episode[-_/]?(\d+)` (case-insensitive) → number = that digit group, group prefix = text before it with `-_ ` trimmed.
  - else slug == baseSlug → episode 1.
  - else slug fully matches `<baseSlug>-(\d+)` → that number (episode 2, 3, …).
  - else not an episode.
- First try container selectors in this order and stop at the first that yields results: `.lstepsiode a[href]`, `.listeps a[href]`, `.epsright a[href]`, `.epsleft a[href]`, `#episode a[href]`, `.episodelist a[href]`.
- Fallback: scan all anchors, group candidates by prefix, pick the group whose prefix is most similar to baseSlug (similarity ratio, tie-break by group size). Implement a normalized similarity (e.g. 1 - levenshtein/maxLen) since Java has no difflib.
- Output: URLs de-duplicated, sorted ascending by episode number then URL.
- A user-pasted URL under `/anime/` is a list page; any other path is a single-episode page.

### Download links (episode page) — function `get_download_links`
- Handle `<meta http-equiv="refresh" content="...url=...">` by fetching the target once.
- For every `<li>` containing at least one anchor whose text or href contains "pixeldrain" or "mediafire": resolution = first match of `(360|480|720|1080)P?` in the li's uppercase text → "<n>p"; else "FULLHD" → 1080p; else "HD" → 720p; else "HD". Format: walk backwards through up to 30 previous text nodes from the enclosing `<ul>`; if text contains MP4 → MP4; X265 or HEVC → X265; MKV → MKV; default MKV (check in that order per node).
- Result: `Map<Format, Map<Resolution, Map<Host, Url>>>`; drop empty entries; null/empty if nothing found.
- Quality preview in the app uses the first episode (in the selected list) that returns any links.

### Host selection with fallback — function `find_target_links`
- Given chosen format (substring match, case-insensitive), resolution, preferred host: return an ordered list of (host, url): preferred host first, then the other hosts available for the same format+resolution. De-duplicate URLs.
- If the chosen format/resolution does not exist for an episode → mark that episode `SKIPPED` with reason "Kualitas tidak tersedia" (no automatic quality downgrade in v1; add a Settings toggle "Turun ke kualitas terdekat" default OFF, optional).

### Resolving direct URLs
- Pixeldrain: URL contains `pixeldrain.com/u/<id>` → direct = `https://pixeldrain.com/api/file/<id>?download` (strip query/trailing slash from id).
- Mediafire: candidates = [url] plus `URLDecoder.decode(url)` if url contains `%25` (site emits double-encoded links like `%255B`). For each candidate: GET; non-200 → remember reason; parse `a#downloadButton`: prefer `data-scrambled-url` (Base64 decode → URL), else `href` if it starts with http; else regex `https?://download\d*\.mediafire\.com/[^\s"'<>]+` over the body. If body contains "invalid or deleted file"/"has been removed"/"file removed"/"key you provided" → reason "file sudah dihapus". If nothing works → null with a human-readable reason.
- Extension: "mp4" if chosen format contains mp4 else "mkv"; override to "rar" if direct URL contains ".rar", "zip" if ".zip". Filename: `<episodeSlug>_<format>_<resolution>.<ext>` where episodeSlug = last path segment of the episode URL. Output folder name = anime slug (last segment of the list URL).
- Known host errors to show with clear Indonesian reasons: HTTP 451 "diblokir/dihapus oleh host (takedown), bukan masalah aplikasi"; 404/410 "file sudah dihapus dari host"; 429 "terkena limit host, coba lagi nanti". On any host failure: delete the partial file, log reason on the task, try the next host in the ordered list. If all hosts fail → task `FAILED` with the per-host reasons listed.

## Download engine
- Foreground service (`foregroundServiceType="dataSync"`, permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`, `POST_NOTIFICATIONS` requested at runtime on API 33+, `INTERNET`). Persistent notification with overall progress + actions Pause all / Cancel all.
- `DownloadQueue` (singleton, `StateFlow<List<DownloadTask>>`): concurrency configurable 1–3 (default 1). States: QUEUED, RESOLVING, DOWNLOADING, PAUSED, DONE, FAILED, SKIPPED, CANCELED. Per-task: bytesDone, totalBytes, speed, current host, failure reasons.
- Downloader: OkHttp streaming with 64 KB buffer; writes to `<cacheDir or filesDir>/downloads/<name>.part`; supports resume via `Range: bytes=<size>-` (handle 206 vs 200 correctly; if server returns 200 restart from 0). Timeouts: connect 30 s, read 60 s. If a final file with identical size already exists in the destination → mark DONE ("sudah ada"). Cancel/pause must be cooperative (coroutine cancellation) and not corrupt files.
- Storage: user picks a destination folder with the Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), persist with `takePersistableUriPermission`. On completion, create `<animeSlug>/` subfolder via `DocumentFile` and copy the `.part` to the final file there, then delete the cache copy. If no folder chosen, fall back to MediaStore `Downloads/SamehadakuDL/<animeSlug>/`.
- A task's `DONE` notification shows count of successes/failures at the end of the batch. `.rar` files are saved as-is (no extraction in v1).

## UI (Compose, Material 3, dark/light + dynamic color, Indonesian text)
1. **Home**: URL text field + "Tempel" button, "Deteksi" button, recent URLs (DataStore, last 10), banner when Cloudflare verification is needed with a button "Verifikasi".
2. **Cloudflare verify**: WebView screen as described; top bar with "Selesai" button and auto-close on success.
3. **Episode list**: LazyColumn of episodes with checkboxes, "Pilih semua", a range text field (accepts `1-12`, `3,5`, `1,4,7-9`; same semantics as Python `parse_range`, 1-based, clamp to list size), count header.
4. **Quality picker**: chips for Format (MKV/MP4/X265), Resolution, preferred Host; only show options that exist (from the sample episode); summary line; "Mulai Unduh" button.
5. **Queue**: per-task card with progress bar, speed, host, status text, failure reasons expandable, buttons retry/cancel; top actions pause all/resume all/clear finished; "Coba ulang yang gagal".
6. **Settings**: destination folder, concurrency, optional quality downgrade, "Reset sesi Cloudflare" (clear cookies + stored UA), about.
Navigation via a typed NavHost; ViewModels expose a single `UiState` StateFlow each; one-shot events via `Channel`/`SharedFlow`.

## Manifest/security
`usesCleartextTraffic=false`; no unnecessary permissions; WebView debugging only in debug builds; `android:allowBackup="false"`; adaptive launcher icon (simple vector, original design, no copyrighted characters or logos).

## Tests (JVM)
Provide unit tests with inline HTML fixtures for `EpisodeListParser` and `DownloadLinkParser`, including these cases that MUST pass:
- List page `/anime/diamond-no-ace-act-ii-season-2-cour-2/` where episode links are `/diamond-no-ace-act-ii-season-2-cour-2/` (ep 1), `/diamond-no-ace-act-ii-season-2-cour-2-2/` (ep 2), `/…-cour-2-episode-3/`, `/…-cour-2-episode-4/` → exactly 4 episodes, sorted 1..4, with sidebar links to other anime (`/one-piece-episode-1100/`, `/…-cour-1-episode-12/`) excluded.
- Normal list `/anime/ao-ashi-s2/` with `/ao-ashi-s2-episode-1/`, `-episode-2/`, including relative hrefs and `#fragment` variants.
- Host ordering: preferred first, fallback second; no match → empty list.
- Mediafire double-encoded URL candidate generation.
- Range parser cases: "", "3", "2-5", "1,4,7-9", out-of-range, garbage.

## CI/CD (GitHub Actions)
`.github/workflows/build.yml`: on push/PR/workflow_dispatch; JDK 17 (temurin), `gradle/actions/setup-gradle@v4` with `gradle-version: 8.9` (do NOT depend on a committed gradle-wrapper.jar), run `gradle testDebugUnitTest assembleDebug --stacktrace`, upload `app/build/outputs/apk/debug/*.apk` as artifact `samehadaku-dl-debug`. Add an optional manual `release` job that signs with keystore from secrets (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and uploads the signed APK.

## Acceptance criteria
- `gradle assembleDebug` succeeds from a clean checkout with the files you output.
- Pasting a list URL → Cloudflare verification if needed → episodes detected (including inconsistent slugs) → quality list → queue downloads with host fallback and resume.
- No crashes on: empty page, challenge page, malformed HTML, no network, host 451/404/429, user cancels, process death mid-download (queue state restored from a small persisted store or tasks marked PAUSED on restart).

Reply with exactly `READY` and nothing else.

---

# PHASE 1 — Skeleton & CI
Produce: `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `AndroidManifest.xml` (all permissions + service declaration with foregroundServiceType), `SamehadakuApp.kt`, `MainActivity.kt`, theme files (Color/Type/Theme with dynamic color), Navigation graph with empty placeholder screens for Home / Cloudflare / Episodes / Quality / Queue / Settings, Hilt `AppModule`, adaptive icon resources, `res/values/strings.xml` (Indonesian), `res/xml` backup/data-extraction rules, `.github/workflows/build.yml`, `.gitignore`, `README.md` (3 lines: how to build). The empty app must build.

# PHASE 2 — Domain, parsers, tests
Produce: all `domain/model`, `core/*` (UrlUtils, SlugUtils, similarity function, range parser), `data/parser/EpisodeListParser.kt`, `data/parser/DownloadLinkParser.kt`, `find_target_links` equivalent (`HostSelector`), `data/host/*Resolver` interfaces + pure parsing parts, and ALL unit tests listed above. Parsers must be pure Kotlin + Jsoup.

# PHASE 3 — Network & Cloudflare
Produce: `HttpClientFactory`, `CloudflareSession` (+ persistence in DataStore), the OkHttp interceptor, `HtmlFetcher` (OkHttp strategy + WebView fallback strategy), `CloudflareScreen` + `CloudflareViewModel`, repository implementations for "detect episodes" and "load download links", `MediafireResolver` / `PixeldrainResolver` implementations with the error-reason mapping, and Hilt bindings.

# PHASE 4 — Download engine
Produce: `Downloader` (resume/range/.part), `DownloadQueue`, `DownloadService` (foreground notification, actions), `StorageWriter` (SAF + MediaStore fallback), `SettingsRepository`, use cases (`EnqueueDownloadsUseCase`, retry, cancel, pause), and persisted queue state.

# PHASE 5 — UI wiring
Produce: all screens + ViewModels (Home, Episodes, Quality, Queue, Settings), notification permission flow, SAF folder picker, snackbars for errors, empty/loading/error states, and final wiring in the NavHost. Then a final checklist table: each acceptance criterion → file(s) that implement it.

# PHASE 6 — Self-audit (send last)
Re-read every file you produced. List, then FIX by re-outputting only the affected files in full: (1) compile errors or unresolved references, (2) mismatched versions in the version catalog, (3) missing manifest entries, (4) Hilt graph holes, (5) places where Python behavior was not ported exactly (compare with the appendix), (6) any `TODO`/placeholder. If none, reply `AUDIT CLEAN`.

---

# APPENDIX — Python source to port (authoritative behavior reference)

```python
#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SAMEHADAKU SMART CONTROL DOWNLOADER v11

Perbaikan utama dari v10:
- Deteksi episode jauh lebih tahan banting (selector khusus + fallback regex,
  link relatif di-resolve dengan urljoin, link sidebar anime lain difilter).
- Status HTTP / halaman challenge Cloudflare diperiksa, bukan diabaikan.
- Jika deteksi gagal, HTML halaman disimpan ke debug_page.html untuk diagnosa.
- Episode diurutkan berdasarkan nomor episode, bukan sekadar dibalik.
- Bisa pilih rentang episode (mis. 1-12) atau Enter untuk semua.
- Unduhan memakai file .part, bisa dilewati kalau sudah lengkap, ada timeout.
- Mediafire: mendukung tombol dengan data-scrambled-url (base64).

Dependensi: pip install cloudscraper beautifulsoup4 requests tqdm
Termux    : pkg install nodejs   (dibutuhkan cloudscraper untuk challenge JS)
"""

import base64
import difflib
import os
import re
import sys
import time
from urllib.parse import urlparse, urljoin, unquote

import json

import cloudscraper
import requests
from bs4 import BeautifulSoup
from tqdm import tqdm

try:  # opsional: meniru TLS fingerprint Chrome (lebih ampuh lawan Cloudflare)
    from curl_cffi import requests as cffi_requests
except Exception:  # pragma: no cover
    cffi_requests = None

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
)
EP_RE = re.compile(r"episode[-_/]?(\d+)", re.I)
DEBUG_FILE = "debug_page.html"
SESSION_FILE = ".samehadaku_session.json"

HTTP_REASONS = {
    404: "file tidak ada / sudah dihapus dari host",
    410: "file sudah dihapus dari host",
    429: "terkena limit dari host, coba lagi nanti",
    451: "file diblokir/dihapus oleh host karena permintaan hukum (takedown), bukan masalah skrip",
}

EPISODE_SELECTORS = [
    ".lstepsiode a[href]",
    ".listeps a[href]",
    ".epsright a[href]",
    ".epsleft a[href]",
    "#episode a[href]",
    ".episodelist a[href]",
]


# ----------------------------------------------------------------------------
# Jaringan
# ----------------------------------------------------------------------------
def is_challenge(text):
    head = text[:4000].lower()
    return any(
        k in head
        for k in ("just a moment", "cf-browser-verification", "attention required", "cf-challenge")
    )


def save_debug(text):
    try:
        with open(DEBUG_FILE, "w", encoding="utf-8") as f:
            f.write(text)
        print(f"[i] HTML halaman disimpan ke ./{DEBUG_FILE} untuk diagnosa.")
    except OSError:
        pass


def make_scraper():
    """Pakai curl_cffi (impersonate Chrome) kalau terpasang, jika tidak cloudscraper."""
    if cffi_requests is not None:
        try:
            return cffi_requests.Session(impersonate="chrome")
        except Exception:
            pass
    return cloudscraper.create_scraper(
        browser={"browser": "chrome", "platform": "windows", "desktop": True}, delay=10
    )


def apply_session(scraper, host, cf_clearance, user_agent):
    """Pasang cookie cf_clearance + User-Agent dari browser pengguna."""
    global UA
    if user_agent:
        UA = user_agent
    if cf_clearance:
        try:
            scraper.cookies.set("cf_clearance", cf_clearance, domain=host)
        except Exception:
            pass


def load_session(scraper, host):
    try:
        with open(SESSION_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
    except (OSError, ValueError):
        return False
    if data.get("host") != host or not data.get("cf_clearance"):
        return False
    apply_session(scraper, host, data["cf_clearance"], data.get("user_agent"))
    print("[i] Memakai sesi tersimpan (cf_clearance) dari sebelumnya.")
    return True


def prompt_cf_cookie(scraper, host):
    """Minta cf_clearance + User-Agent dari browser; simpan untuk run berikutnya."""
    print("\n[!] Cloudflare memblokir akses otomatis. Solusi: pakai sesi browser kamu.")
    print("    1. Di HP/PC YANG SAMA (jaringan sama), buka https://" + host + " di browser,")
    print("       tunggu sampai lolos pengecekan dan halaman terbuka.")
    print("    2. Salin cookie bernama  cf_clearance  (DevTools > Application/Storage > Cookies,")
    print("       atau ekstensi Cookie-Editor di Kiwi Browser / Firefox).")
    print("    3. Salin juga User-Agent browser itu (ketik 'my user agent' di Google).")
    print("    Cookie berlaku ±30 menit - 1 hari; kalau kedaluwarsa ulangi langkah ini.\n")
    cf = input("Tempel nilai cf_clearance (Enter = batal): ").strip()
    if not cf:
        return False
    ua = input("Tempel User-Agent browser (Enter = pakai default): ").strip()
    apply_session(scraper, host, cf, ua)
    try:
        with open(SESSION_FILE, "w", encoding="utf-8") as f:
            json.dump({"host": host, "cf_clearance": cf, "user_agent": ua or UA}, f)
        try:
            os.chmod(SESSION_FILE, 0o600)
        except OSError:
            pass
    except OSError:
        pass
    return True


def fetch_page(scraper, url, referer=None, retries=3):
    """GET halaman dengan retry; kembalikan Response atau None."""
    headers = {"User-Agent": UA}
    if referer:
        headers["Referer"] = referer
    last_note = ""
    last_text = ""
    for attempt in range(1, retries + 1):
        try:
            resp = scraper.get(url, headers=headers, timeout=45)
            if resp.status_code == 200 and not is_challenge(resp.text):
                return resp
            blocked = is_challenge(resp.text)
            last_note = f"HTTP {resp.status_code}" + (" (challenge Cloudflare)" if blocked else "")
            last_text = resp.text
            if resp.status_code in (403, 503) and blocked:
                print(f"[!] Gagal: {last_note}")
                break  # mengulang tidak akan membantu
        except Exception as e:
            last_note = str(e)
            last_text = ""
        print(f"[!] Percobaan {attempt}/{retries} gagal: {last_note}")
        time.sleep(2 * attempt)
    if last_text:
        save_debug(last_text)
    return None


# ----------------------------------------------------------------------------
# Deteksi episode
# ----------------------------------------------------------------------------
def _norm_url(u):
    return u.split("#")[0].split("?")[0].rstrip("/") + "/"


def _slug(path):
    return [p for p in path.split("/") if p][-1] if path.strip("/") else ""


def _episode_prefix(slug):
    return EP_RE.split(slug)[0].strip("-_ ")


def extract_episode_links(soup, page_url):
    """Kembalikan list URL episode (unik) diurutkan berdasarkan nomor episode."""
    base = urlparse(page_url)
    page_norm = _norm_url(page_url)
    base_slug = _slug(base.path)  # mis. 'judul-anime-season-2' dari /anime/judul-anime-season-2/

    def candidate(href):
        if not href:
            return None
        full = urljoin(page_url, href.strip())
        p = urlparse(full)
        if p.scheme not in ("http", "https"):
            return None
        if p.netloc != base.netloc and "samehadaku" not in p.netloc:
            return None
        if "/anime/" in p.path or "/category/" in p.path or "/tag/" in p.path:
            return None
        slug = _slug(p.path)
        m = EP_RE.search(p.path)
        if m:
            num = int(m.group(1))
            prefix = _episode_prefix(slug)
        elif base_slug and slug == base_slug:
            # slug tanpa 'episode' sama persis dengan judul = episode 1
            num, prefix = 1, base_slug
        else:
            # slug berakhiran angka: judul-2, judul-3, ... = episode 2, 3, ...
            m2 = re.fullmatch(re.escape(base_slug) + r"-(\d+)", slug) if base_slug else None
            if not m2:
                return None
            num, prefix = int(m2.group(1)), base_slug
        norm = _norm_url(full)
        if norm == page_norm:
            return None
        return norm, num, prefix

    # 1) Selector khusus daftar episode (paling presisi)
    found = {}
    for sel in EPISODE_SELECTORS:
        for a in soup.select(sel):
            c = candidate(a.get("href"))
            if c:
                found.setdefault(c[0], c[1])
        if found:
            break

    # 2) Fallback: semua anchor, lalu pilih grup judul yang paling mirip slug halaman
    if not found:
        groups = {}
        for a in soup.find_all("a", href=True):
            c = candidate(a["href"])
            if not c:
                continue
            groups.setdefault(c[2], {}).setdefault(c[0], c[1])
        if groups:
            page_slug = _slug(base.path)
            best = max(
                groups,
                key=lambda k: (difflib.SequenceMatcher(None, k, page_slug).ratio(), len(groups[k])),
            )
            found = groups[best]

    return [u for u, _ in sorted(found.items(), key=lambda kv: (kv[1], kv[0]))]


# ----------------------------------------------------------------------------
# Parsing halaman episode
# ----------------------------------------------------------------------------
def get_download_links(episode_url, scraper):
    parsed = urlparse(episode_url)
    base_domain = f"{parsed.scheme}://{parsed.netloc}/"

    resp = fetch_page(scraper, episode_url, referer=base_domain)
    if resp is None:
        return None

    try:
        soup = BeautifulSoup(resp.text, "html.parser")

        meta_refresh = soup.find("meta", attrs={"http-equiv": re.compile(r"refresh", re.I)})
        if meta_refresh:
            m = re.search(r"url=['\"]?([^'\">]+)", meta_refresh.get("content", ""), re.I)
            if m:
                resp2 = fetch_page(scraper, urljoin(base_domain, m.group(1)), referer=base_domain)
                if resp2 is not None:
                    soup = BeautifulSoup(resp2.text, "html.parser")

        link_data = {}
        for li in soup.find_all("li"):
            anchors = li.find_all("a")
            if not anchors:
                continue

            valid = False
            for a in anchors:
                txt = a.get_text(strip=True).lower()
                href = a.get("href", "").lower()
                if "pixeldrain" in txt or "pixeldrain.com" in href or "mediafire" in txt or "mediafire.com" in href:
                    valid = True
                    break
            if not valid:
                continue

            li_text = li.get_text(separator=" ", strip=True).upper()
            res_match = re.search(r"(360|480|720|1080)P?", li_text)
            if res_match:
                resolusi = f"{res_match.group(1)}p"
            elif "FULLHD" in li_text:
                resolusi = "1080p"
            elif "HD" in li_text:
                resolusi = "720p"
            else:
                resolusi = "HD"

            fmt = "MKV"
            ul = li.find_parent("ul")
            if ul:
                prev = ul.previous_element
                for _ in range(30):
                    if prev is None:
                        break
                    if isinstance(prev, str):
                        t = prev.upper()
                        if "MP4" in t:
                            fmt = "MP4"
                            break
                        if "X265" in t or "HEVC" in t:
                            fmt = "X265"
                            break
                        if "MKV" in t:
                            fmt = "MKV"
                            break
                    prev = prev.previous_element

            hosts = link_data.setdefault(fmt, {}).setdefault(resolusi, {})
            for a in anchors:
                txt = a.get_text(strip=True).lower()
                href = a.get("href", "")
                if not href:
                    continue
                if "pixeldrain" in txt or "pixeldrain.com" in href.lower():
                    hosts["Pixeldrain"] = href
                elif "mediafire" in txt or "mediafire.com" in href.lower():
                    hosts["Mediafire"] = href

        link_data = {f: {r: h for r, h in res.items() if h} for f, res in link_data.items()}
        link_data = {f: res for f, res in link_data.items() if res}
        return link_data or None
    except Exception as e:
        print(f"[!] Gagal parsing halaman episode: {e}")
        return None


def find_target_links(link_data, pilih_fmt, pilih_res, pilih_host):
    """Daftar (host, link) berurutan: host pilihan dulu, lalu host lain yang tersedia
    pada format + resolusi yang sama."""
    preferred, others, seen = [], [], set()
    for fmt_key, resolutions in link_data.items():
        if pilih_fmt not in fmt_key.lower():
            continue
        for res_key, hosts in resolutions.items():
            if pilih_res not in res_key.lower():
                continue
            for host_key, link_val in hosts.items():
                if link_val in seen:
                    continue
                seen.add(link_val)
                bucket = preferred if pilih_host in host_key.lower() else others
                bucket.append((host_key, link_val))
    return preferred + others


def find_target_link(link_data, pilih_fmt, pilih_res, pilih_host):
    """Kompatibilitas lama: satu link saja (host pilihan dulu, jika tak ada host lain)."""
    t = find_target_links(link_data, pilih_fmt, pilih_res, pilih_host)
    return t[0][1] if t else None


# ----------------------------------------------------------------------------
# Unduhan
# ----------------------------------------------------------------------------
def download_file(url, filename):
    print(f"\n[↓] Memulai unduhan: {os.path.basename(filename)}")
    part = filename + ".part"
    try:
        headers = {"User-Agent": UA}
        with requests.get(url, stream=True, headers=headers, timeout=60) as response:
            response.raise_for_status()
            total = int(response.headers.get("content-length", 0))

            if os.path.exists(filename) and total and os.path.getsize(filename) == total:
                print("[✓] File sudah ada dan ukurannya sama, dilewati.\n")
                return True

            with open(part, "wb") as f, tqdm(
                desc=os.path.basename(filename),
                total=total or None,
                unit="iB",
                unit_scale=True,
                unit_divisor=1024,
            ) as bar:
                for chunk in response.iter_content(1024 * 64):
                    if chunk:
                        f.write(chunk)
                        bar.update(len(chunk))
        os.replace(part, filename)
        print(f"\n[✓] Unduhan selesai: {os.path.basename(filename)}\n")
        return True
    except Exception as e:
        why = str(e)
        if isinstance(e, requests.HTTPError) and e.response is not None:
            why = f"HTTP {e.response.status_code} - " + HTTP_REASONS.get(e.response.status_code, str(e))
        print(f"\n[!] Gagal mengunduh {os.path.basename(filename)}: {why}\n")
        try:
            if os.path.exists(part):
                os.remove(part)
        except OSError:
            pass
        return False


DEAD_MARKERS = ("invalid or deleted file", "has been removed", "file removed", "key you provided")


def resolve_mediafire(target_link, scraper):
    """Kembalikan link unduhan langsung Mediafire, atau None (alasan dicetak)."""
    candidates = [target_link]
    if "%25" in target_link:  # link double-encoded di situs (%255B -> %5B)
        candidates.append(unquote(target_link))

    reason = "tombol unduh tidak ditemukan (kemungkinan file sudah dihapus)"
    for link in candidates:
        res = scraper.get(link, headers={"User-Agent": UA}, timeout=45)
        if res.status_code != 200:
            reason = f"HTTP {res.status_code}"
            continue

        soup = BeautifulSoup(res.text, "html.parser")
        btn = soup.find("a", id="downloadButton")
        if btn:
            scrambled = btn.get("data-scrambled-url")
            if scrambled:
                try:
                    return base64.b64decode(scrambled).decode()
                except Exception:
                    pass
            href = btn.get("href", "")
            if href.startswith("http"):
                return href
        m = re.search(r"https?://download\d*\.mediafire\.com/[^\s\"'<>]+", res.text)
        if m:
            return m.group(0)
        if any(k in res.text.lower() for k in DEAD_MARKERS):
            reason = "file sudah dihapus / link tidak valid di Mediafire"

    print(f"[!] Mediafire: {reason}")
    return None


def resolve_direct(target_link, scraper):
    """Ubah link halaman host menjadi link unduhan langsung (None jika mati/tidak valid)."""
    if "pixeldrain.com/u/" in target_link:
        file_id = target_link.split("/u/")[-1].split("?")[0].strip("/")
        return f"https://pixeldrain.com/api/file/{file_id}?download"
    if "mediafire.com" in target_link:
        print("[*] Mengekstrak link asli dari Mediafire...")
        try:
            return resolve_mediafire(target_link, scraper)
        except Exception as e:
            print(f"[!] Gagal ekstrak Mediafire: {e}")
    return None


def process_and_download(targets, pilih_fmt, pilih_res, episode_url, scraper, save_folder="."):
    """targets: list (host, link) dari find_target_links. Coba satu per satu sampai
    ada yang berhasil. Kembalikan True jika file berhasil diunduh."""
    if isinstance(targets, str):  # kompatibel dengan pemanggil lama yang kirim 1 link
        targets = [("Link", targets)]
    if not targets:
        print("[!] Tautan tidak ditemukan.")
        return False

    for n, (host, link) in enumerate(targets, 1):
        more = n < len(targets)
        print(f"\n[*] Mencoba {host} ({n}/{len(targets)}): {link}")

        direct = resolve_direct(link, scraper)
        if not direct:
            print(f"[!] Tautan langsung dari {host} mati/tidak valid." + (" Beralih ke host lain..." if more else ""))
            continue

        ext = "mp4" if "mp4" in pilih_fmt.lower() else "mkv"
        if ".rar" in direct.lower():
            ext = "rar"
        elif ".zip" in direct.lower():
            ext = "zip"

        ep_name = episode_url.strip("/").split("/")[-1]
        filename = f"{ep_name}_{pilih_fmt}_{pilih_res}.{ext}"
        if download_file(direct, os.path.join(save_folder, filename)):
            return True
        if more:
            print(f"[!] Unduhan dari {host} gagal. Beralih ke host berikutnya...")

    print("[!] Semua host yang tersedia gagal untuk episode ini.")
    return False


# ----------------------------------------------------------------------------
# Util input
# ----------------------------------------------------------------------------
def parse_range(text, total):
    """'' -> semua, '3' -> [3], '2-5' -> [2..5], '1,4,7-9' -> campuran (1-based)."""
    text = text.strip()
    if not text:
        return list(range(1, total + 1))
    idx = set()
    for part in text.split(","):
        part = part.strip()
        if "-" in part:
            a, _, b = part.partition("-")
            if a.strip().isdigit() and b.strip().isdigit():
                idx.update(range(int(a), int(b) + 1))
        elif part.isdigit():
            idx.add(int(part))
    return sorted(i for i in idx if 1 <= i <= total)


def ask_quality():
    pilih_fmt = input("Pilih Format (mkv/mp4/x265)         : ").strip().lower()
    pilih_res = input("Pilih Resolusi (360p/480p/720p/1080p): ").strip().lower()
    if pilih_res.isdigit():
        pilih_res += "p"
    pilih_host = input("Pilih Server (pixeldrain/mediafire)  : ").strip().lower()
    return pilih_fmt, pilih_res, pilih_host


def print_options(link_data):
    for fmt, resolutions in link_data.items():
        print(f"🎬 ❯❯ {fmt.upper()} ❮❮")
        for res, hosts in resolutions.items():
            print(f"   [{res}] via {', '.join(hosts.keys())}")


# ----------------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------------
def main():
    print("=" * 50)
    print("🤖 SAMEHADAKU SMART CONTROL DOWNLOADER v11")
    print("=" * 50)

    target_url = input("Masukkan URL Samehadaku (Episode / Halaman Utama):\n👉 ").strip()
    if not target_url:
        return
    if not target_url.startswith("http"):
        target_url = "https://" + target_url

    scraper = make_scraper()
    host = urlparse(target_url).netloc
    load_session(scraper, host)

    print("\nMemeriksa akses ke situs...")
    resp = fetch_page(scraper, target_url, retries=2)
    if resp is None:
        if prompt_cf_cookie(scraper, host):
            resp = fetch_page(scraper, target_url, retries=1)
        if resp is None:
            print("\n[!] Halaman tetap tidak bisa diambil.")
            print("    Pastikan cf_clearance baru, User-Agent sama persis dengan browser,")
            print("    dan skrip dijalankan dari jaringan/IP yang sama dengan browser tadi.")
            try:
                os.remove(SESSION_FILE)
            except OSError:
                pass
            return

    print("\nMendeteksi tipe link secara otomatis...")
    path = urlparse(target_url).path
    # Halaman daftar episode selalu di /anime/<judul>/; selain itu dianggap episode satuan
    # (slug episode bisa tanpa kata 'episode', mis. /judul/ atau /judul-2/).
    is_batch = "/anime/" in path

    if is_batch:
        print("[✨ DETEKSI] Tipe: Halaman Utama Anime (Batch List).")

        soup = BeautifulSoup(resp.text, "html.parser")
        ep_list = extract_episode_links(soup, resp.url or target_url)

        if not ep_list:
            print("[!] Gagal mendeteksi link episode di halaman ini.")
            save_debug(resp.text)
            return

        print(f"\n[✓] Berhasil mendeteksi total {len(ep_list)} Episode!")
        print(f"    Pertama : {ep_list[0]}")
        print(f"    Terakhir: {ep_list[-1]}")

        print("\n[*] Memeriksa opsi kualitas yang tersedia di server...")
        sample = None
        for test_url in ep_list:
            sample = get_download_links(test_url, scraper)
            if sample:
                break

        if sample:
            print("\n=========================================")
            print("📋 PILIHAN KUALITAS YANG TERSEDIA:")
            print("=========================================")
            print_options(sample)
            print("=========================================")

        pilih_fmt, pilih_res, pilih_host = ask_quality()
        rng = input(f"Rentang episode 1-{len(ep_list)} (mis. 1-12 / 3,5 / Enter = semua): ")
        chosen = parse_range(rng, len(ep_list))
        if not chosen:
            print("[!] Rentang episode tidak valid.")
            return
        print("=========================================")

        folder = target_url.strip("/").split("/")[-1]
        os.makedirs(folder, exist_ok=True)
        print(f"[*] File tersimpan di folder: ./{folder}/\n")

        failed = []
        for n, i in enumerate(chosen, 1):
            ep_url = ep_list[i - 1]
            print("\n--------------------------------------------------")
            print(f"[{n}/{len(chosen)}] Memeriksa: {ep_url}")
            print("--------------------------------------------------")

            link_data = get_download_links(ep_url, scraper)
            if not link_data:
                print("[!] Link episode tidak ditemukan / belum rilis. Dilewati.")
                failed.append(ep_url)
                continue

            targets = find_target_links(link_data, pilih_fmt, pilih_res, pilih_host)
            if targets:
                print(f"[*] Sesuai pilihan: {pilih_fmt.upper()} | {pilih_res} | host: {', '.join(h for h, _ in targets)}")
                if not process_and_download(targets, pilih_fmt, pilih_res, ep_url, scraper, save_folder=folder):
                    failed.append(ep_url)
            else:
                print("[!] Format/Resolusi pilihan tidak tersedia di episode ini. Dilewati.")
                failed.append(ep_url)
            time.sleep(1)

        if failed:
            print(f"\n[!] {len(failed)} episode gagal / dilewati:")
            for u in failed:
                print(f"    - {u}")
        print("\n=========================================")
        print("🎉 SEMUA BATCH UNDUHAN SELESAI!")
        print(f"📁 Cek folder: {folder}/")
        print("=========================================")

    else:
        print("[✨ DETEKSI] Tipe: Link Episode Satuan.")
        link_data = get_download_links(target_url, scraper)
        if not link_data:
            print("[!] Link download tidak ditemukan.")
            return

        print()
        print_options(link_data)
        print("\n" + "=" * 50)
        pilih_fmt, pilih_res, pilih_host = ask_quality()
        print("=" * 50)

        targets = find_target_links(link_data, pilih_fmt, pilih_res, pilih_host)
        if not targets:
            print("[!] Format/Resolusi pilihan tidak tersedia di episode ini.")
            return
        process_and_download(targets, pilih_fmt, pilih_res, target_url, scraper)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        # tanpa sys.exit: di Jupyter/Colab SystemExit memunculkan traceback IPython yang rusak
        print("\n[!] Dibatalkan oleh pengguna.")

```
