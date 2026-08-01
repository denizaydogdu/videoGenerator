# Shorts Fabrikası — Plan 2/3: Backoffice

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `serve` komutu ile localhost'ta çalışan onay ekranı: işleri listele, videoyu izle (Range/206), metadata düzenle, hikâye seviyesinde onayla/reddet, yeni üretim tetikle, bütçe durumunu gör.

**Architecture:** JDK `com.sun.net.httpserver.HttpServer` (127.0.0.1'e bağlı, yeni bağımlılık yok). Domain işlemleri `JobService`'te (HTTP'siz test edilir); HTTP katmanı ince. UI: `src/main/resources/web/` altından classpath'ten servis edilen vanilla HTML/CSS/JS, koyu tema varsayılan (AwesomeDesign tokenları).

**Tech Stack:** Java SE 17, JDK HttpServer, Gson, JUnit 5; UI vanilla (build adımı yok).

**Spec:** `docs/superpowers/specs/2026-08-01-shorts-fabrikasi-design.md` §6

## Global Constraints

- Sunucu **yalnız 127.0.0.1**'e bağlanır (kimlik doğrulama yok; localhost aracı)
- Video endpoint'i **Range/206** desteklemek zorunda (yoksa `<video>` sarma çalışmaz)
- Onay **hikâye seviyesinde**: tek approve tüm dil varyantlarını seçili platformlara işaretler
- Approve publisher'ı ÇAĞIRMAZ (Plan 3); yalnız `APPROVED` + `approvedPlatforms` kaydeder
- Metadata güncellemesi `VideoMetadata.isValid()` geçmek zorunda
- Job dosya yolları `JobStore.dirFor()` üzerinden (path-traversal koruması orada)
- Commit'lerde Claude attribution YOK

---

### Task B1: JobService — backoffice domain işlemleri

**Files:**
- Create: `src/main/java/com/videogenerator/web/JobService.java`
- Create: `src/main/java/com/videogenerator/web/JobSummary.java`
- Modify: `src/main/java/com/videogenerator/job/Job.java` (+`List<String> approvedPlatforms` alanı, getter/setter)
- Test: `src/test/java/com/videogenerator/web/JobServiceTest.java`

**Interfaces:**
- `JobSummary`: `String jobId, channelId, title; JobStatus status; List<String> langs; double durationSeconds, costTotal; int sceneCount`
- `JobService(JobStore, ChannelStore, CostTracker, double monthlyBudget)`:
  - `List<JobSummary> listJobs(String channelFilter, String statusFilter)` — null filtre = hepsi; title = ilk varyantın metadata.title'ı, varyant yoksa story.title
  - `Job detail(String jobId)`
  - `void updateMetadata(String jobId, String lang, VideoMetadata md)` — `isValid()` değilse `IllegalArgumentException`; bilinmeyen lang → `IllegalArgumentException`
  - `void approve(String jobId, List<String> platforms)` — yalnız `PENDING_REVIEW`'dan; platforms boşsa `IllegalArgumentException`; `APPROVED` + `approvedPlatforms` kaydeder
  - `void reject(String jobId)` — yalnız `PENDING_REVIEW`'dan → `REJECTED`
  - `Stats stats()` — `record Stats(double spentThisMonth, double monthlyBudget)`
- Yanlış durum geçişleri `IllegalStateException`

**Steps:** failing testler (listeleme+filtre, metadata validasyonu, approve/reject durum makinesi, yanlış durumdan geçiş reddi) → FAIL doğrula → implement → PASS → review → commit.

### Task B2: RangeSupport — HTTP Range parse

**Files:**
- Create: `src/main/java/com/videogenerator/web/RangeSupport.java`
- Test: `src/test/java/com/videogenerator/web/RangeSupportTest.java`

**Interfaces:**
- `record ByteRange(long start, long end)` (end dahil)
- `static ByteRange parse(String header, long fileLength)` — `bytes=0-499`, `bytes=500-` (dosya sonuna), `bytes=-500` (son 500 bayt); geçersiz/karşılanamaz → `null` (çağıran 200-full veya 416 döner); çoklu range desteklenmez → ilkini al

**Steps:** TDD döngüsü + review + commit.

### Task B3: BackofficeServer — JSON API

**Files:**
- Create: `src/main/java/com/videogenerator/web/BackofficeServer.java`
- Test: `src/test/java/com/videogenerator/web/BackofficeServerTest.java` (gerçek HttpServer, port 0 = ephemeral, `java.net.http.HttpClient` ile)

**Interfaces:**
- `interface JobLauncher { void launch(String channelId); }` (Main, executor+JobPipeline'a bağlar)
- `BackofficeServer(JobService, JobLauncher, int port)`: `int start()` (dinlenen portu döner), `void stop()`
- Rotalar:

```
GET    /api/channels                → [{channelId, displayName, pendingCount}]
GET    /api/jobs?channel=&status=   → [JobSummary]
GET    /api/jobs/{id}               → Job (ham job.json)
PATCH  /api/jobs/{id}/variants/{lang} → body: VideoMetadata → 204
POST   /api/jobs/{id}/approve       → body: {"platforms":["YOUTUBE"]} → 204
POST   /api/jobs/{id}/reject        → 204
POST   /api/jobs/generate           → body: {"channelId":"..."} → 202
GET    /api/stats                   → {spentThisMonth, monthlyBudget}
```

- Hata eşlemesi: `IllegalArgumentException`→400, `IllegalStateException`→409, bulunamadı→404, diğer→500; gövde `{"error":"..."}`
- Tek `/api/` context'i içinde path-segment yönlendirme; JSON Gson ile

**Steps:** TDD (testler: kanal listesi, job listesi+filtre, detay, approve 204 + yeniden approve 409, generate 202 → launcher çağrıldı, stats) → implement → PASS → review → commit.

### Task B4: Medya endpoint'leri — Range'li video + sahne görseli

**Files:**
- Modify: `src/main/java/com/videogenerator/web/BackofficeServer.java`
- Test: `src/test/java/com/videogenerator/web/MediaEndpointTest.java`

**Interfaces:**
```
GET /api/jobs/{id}/render/{lang}  → video/mp4; Accept-Ranges: bytes;
                                    Range yoksa 200-full, varsa 206 + Content-Range
GET /api/jobs/{id}/scene/{n}      → image/png (n = 1..sceneCount, %02d.png)
```
- lang/n segmentleri dosya adına ham geçmez: lang `[a-z]{2,3}` regex, n integer — aksi 400

**Steps:** TDD (206 + Content-Range doğrulaması, Range'siz 200, geçersiz lang 400) → implement → PASS → review → commit.

### Task B5: Web UI — koyu temalı onay ekranı

**Files:**
- Create: `src/main/resources/web/index.html`
- Create: `src/main/resources/web/style.css`
- Create: `src/main/resources/web/app.js`
- Modify: `BackofficeServer` (statik servis: `/` → classpath `/web/*`, content-type map, `..` reddi)
- Test: `BackofficeServerTest`'e statik servis testi (GET / → 200 text/html)

**UI yapısı (spec §6 ekran taslağı):**
- Sol sidebar 240px: kanal listesi (pendingCount rozetli), durum filtreleri, altta bütçe çubuğu (`/api/stats`)
- Ana alan: kart ızgarası (`grid-3`) — thumbnail (`scene/1`), başlık, dil rozetleri, süre, maliyet
- Karta tıkla → detay: sol dil sekmeli `<video controls>` (render/{lang}), sahne şeridi; sağ metadata formu (title/description/hashtags), platform checkbox'ları, Yayınla (approve) / Reddet
- `[+ Üret]` butonu → kanal seç → POST /api/jobs/generate → toast "Üretim kuyruğa alındı"
- AwesomeDesign tokenları; koyu tema varsayılan (gray-900 zemin, gray-800 kart, primary-500 accent), 8px grid, 12px radius
- JS: tek `app.js`, fetch tabanlı, framework yok; durum = son seçilen kanal/filtre/job (module-level)

**Steps:** statik servis testi → handler implement → UI dosyalarını yaz → smoke (curl /) → review → commit.

### Task B6: Main `serve` komutu + kablolama

**Files:**
- Modify: `src/main/java/com/videogenerator/main/Main.java`

**Interfaces:**
- `serve` komutu (argümansız, legacy stack'siz): JobService + BackofficeServer'ı gerçek store'larla kurar; JobLauncher = tek thread'li executor içinde `JobPipeline.run(channelId)` (hata loglanır, süreç düşmez); `System.out`'a URL basar; Ctrl-C'ye kadar bloklar
- help metnine `serve` eklenir

**Steps:** implement → `mvn package` + `java -jar ... serve` smoke (curl /api/stats) → full suite → review → commit.

## Self-Review Sonucu

- Spec §6 kapsaması: tüm rotalar + Range + UI blokları görev eşli; approve→publisher bağlantısı bilinçli olarak Plan 3'te.
- Tip tutarlılığı: `JobLauncher` B3'te tanımlı, B6 kullanıyor; `ByteRange` B2'de, B4 kullanıyor; `approvedPlatforms` B1'de eklenip B3 approve gövdesinde kullanılıyor.
- Placeholder yok; UI yapısı somut komponent ağacı olarak verildi.
