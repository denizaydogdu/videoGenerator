# Shorts Fabrikası — Tasarım Dokümanı

**Tarih:** 2026-08-01
**Durum:** Onaylandı (tasarım bölümleri kullanıcı ile tek tek gözden geçirildi)
**Proje:** videoGenerator (Java SE 17, Maven, FFmpeg)

## 1. Amaç ve Kapsam

Mevcut Sora-tabanlı Shorts pipeline'ını, görsel-tabanlı ucuz bir üretim hattına dönüştürmek:

- **İçerik tipi:** Hikâye/anlatı (suç, gizem, tarih) — 60–90 saniyelik dikey videolar
- **Maliyet hedefi:** ~$0.32–0.53 / yayınlanmış video (3 dil ortalaması)
- **Çok dilli:** Tek senaryo + tek görsel seti → N dilde seslendirme/altyazı/render
- **Çok kanallı:** Kanal profilleri (`channels/*.json`), portföy stratejisi
- **Onay kuyruğu:** Hiçbir video insan onayı olmadan yayınlanmaz; web backoffice üzerinden izle → düzenle → onayla/reddet
- **Çok platform (kademeli):** `Publisher` soyutlaması ilk sürümde, çalışan implementasyon yalnız YouTube; TikTok/Meta API onayları geldikçe eklenir

### Kapsam dışı (bu sürümde)

- TikTok/Instagram/Facebook publisher implementasyonları (arayüz hazır, sınıflar sonra)
- Sahne bazında yeniden üretim stüdyosu (backoffice yalnız izle+metadata+onay)
- Analitik/rapor ekranları (job.json'lar ileride SQLite'a beslenebilir)

## 2. Motivasyon (araştırma bulguları, Ağustos 2026)

| Bulgu | Kaynak | Etki |
|---|---|---|
| Sora API 24 Eylül 2026'da kapanıyor (`sora-2`, `sora-2-pro`, Videos API) | OpenAI resmî deprecations sayfası | `OpenAiSoraClient` silinir |
| `gpt-4` eski; güncel: `gpt-5.6-sol/terra/luna` | OpenAI | Senaryo/çeviri için `gpt-5.6-luna` ($0.20/$1.20 per 1M) |
| `eleven_v3` 70+ dil + karakter bazlı zaman damgası endpoint'i | ElevenLabs docs | TTS modeli + altyazı senkronu |
| Suno'nun resmî public API'si yok; ElevenLabs Music v2 lisanslı ve ticari kullanıma açık | ElevenLabs | `SunoApiClient` → `MusicApiClient` |
| Shorts limiti 180 sn, 1080×1920; TikTok Creator Rewards için video ≥60 sn | YouTube/TikTok | Hedef süre 60–90 sn |
| YouTube "inauthentic content" politikası: uyarı → 90 gün askı → YPP'den çıkarma | YouTube | Onay kuyruğu + sentetik içerik beyanı zorunlu |
| gpt-image-2: ~$0.05–0.08/dikey görsel (medium) | OpenAI | Görsel-tabanlı pipeline'ın ekonomik temeli |
| YouTube `videos.insert` ayrı günlük kova (~100 çağrı/gün) | Google docs | Günde ~9 yükleme sorunsuz |
| Shorts RPM $0.01–0.07; başabaş ~8.000 görüntülenme/video | Sektör verileri | Ekonomi ancak görsel-tabanlı maliyetle tutuyor |

## 3. Mimari

Mevcut katman deseni (`api/` → `service/` → `processor/`) korunur. İki yeni paket: `job/` (durum + orkestrasyon), `web/` (backoffice); ayrıca `channel/` ve `publish/`.

### Yeni sınıflar

| Paket | Sınıf | Sorumluluk |
|---|---|---|
| `api` | `ImageApiClient` | gpt-image-2 ile sahne görseli (prompt + boyut → PNG) |
| `api` | `MusicApiClient` | ElevenLabs Music v2 (`music_v2`) |
| `service` | `StoryWriter` | `ContentIdea` → `StoryScript`: **tek LLM çağrısında** N sahne (anlatı + görsel prompt birlikte) |
| `service` | `TranslationService` | `StoryScript` → hedef dilde anlatı + metadata |
| `service` | `SceneImageService` | stylePrefix + sahne promptu → `ImageApiClient`, sıralı üretim |
| `processor` | `KenBurnsRenderer` | zoompan + xfade + concat → sahne görsellerinden video |
| `processor` | `SubtitleRenderer` | karakter zaman damgaları → kelime grupları → ASS → burn-in |
| `job` | `Job`, `JobStore` | job.json şeması, atomik oku/yaz (temp + rename) |
| `job` | `JobPipeline` | Uçtan uca orkestrasyon (ContentGeneratorService'in yerine) |
| `channel` | `ChannelProfile`, `ChannelStore` | `channels/*.json` şema + doğrulama |
| `publish` | `Publisher` (arayüz), `YouTubePublisher` | Platform başına yayın; idempotent |
| `web` | `BackofficeServer` | JDK `com.sun.net.httpserver.HttpServer`, REST + statik dosya + Range |

### Değişen sınıflar

- `ElevenLabsClient`: `generateWithTimestamps()` — `POST /v1/text-to-speech/{voice_id}/with-timestamps`; yanıt: `audio_base64` + `alignment.characters` / `character_start_times_seconds` / `character_end_times_seconds`
- `YouTubeApiClient`: profil başına OAuth token (tek global token varsayımı kalkar); sentetik içerik beyanı; varyant başına metadata
- `FFmpegWrapper`: `zoompan`/`xfade`/`concat`/`subtitles` filtreleri; stderr'in yüzeye çıkarılması
- `Configuration`: yeni anahtarlar (aşağıda)
- `Main`: menü ve CLI komutları `JobPipeline` + `BackofficeServer`'a bağlanır (`generate <channelId>`, `serve`, `validate`)
- `DailyScheduler`: `ContentGeneratorService` yerine etkin her kanal için `JobPipeline` tetikler; üretir ama yayınlamaz (onay kuyruğuna düşer)

### Silinen sınıflar

`OpenAiSoraClient`, `SunoApiClient`, `ContentGeneratorService` (iki eski pipeline metoduyla birlikte)

### Dokunulmayanlar

`NicheFinder`, `IdeaGenerator`, `TrendAnalyzer`, SQLite repo'ları, `AudioProcessor`, OAuth akış mekanizması

## 4. Veri Modeli

### Kanal profili — `channels/<id>.json`

```json
{
  "channelId": "truecrime-en",
  "displayName": "Unsolved Files",
  "niche": { "topic": "unsolved crime", "keywords": ["cold case", "mystery"] },
  "stylePrefix": "1970s film grain, desaturated, cinematic, no human faces",
  "voiceId": "...",
  "languages": ["en", "es", "tr"],
  "platforms": ["YOUTUBE"],
  "targetDurationSeconds": 75,
  "sceneCount": 6,
  "youtubeTokenFile": "config/tokens/truecrime-en.json",
  "enabled": true
}
```

Yeni kanal = yeni JSON + OAuth akışı. Kod değişikliği yok.

### İş — `output/jobs/<jobId>/`

```
job.json
scenes/01.png … 0N.png
audio/music.mp3, <lang>.mp3, <lang>.alignment.json
subs/<lang>.ass
renders/<lang>.mp4
```

`job.json` alanları: `jobId`, `channelId`, `status`, `story` (title, stylePrefix, scenes[index, narration, imagePrompt, imageFile]), `musicFile`, `variants[]` (lang, metadata{title, description, hashtags}, audioFile, alignmentFile, renderFile, durationSeconds, publications[platform, status, url]), `cost` (images, tts, music, llm, total), hata durumunda `error`.

### Durum makinesi

```
DRAFTING → RENDERING → PENDING_REVIEW → APPROVED → PUBLISHING → PUBLISHED
                ↓              ↓
             FAILED        REJECTED
```

- Onay **hikâye seviyesinde**: tek onay tüm dil varyantlarını seçili platformlara gönderir
- Yayın **idempotent**: `PUBLISHED` işaretli varyant+platform çifti tekrar yüklenmez

## 5. Pipeline Akışı

### Faz 1 — dilden bağımsız (kanal profili girdi)

1. `IdeaGenerator` → profil nişine göre `ContentIdea`
2. `StoryWriter` → tek çağrıda N sahne (anlatı + görsel prompt); ayrı çağrı görsel-anlatı uyumsuzluğu yaratır, bu yüzden tek çağrı
3. `SceneImageService` → `scenes/*.png` (stylePrefix önek)
4. `MusicApiClient` → `audio/music.mp3`

### Faz 2 — dil başına

5. `TranslationService` → yerelleştirilmiş anlatı + metadata
6. `ElevenLabsClient.generateWithTimestamps()` → `<lang>.mp3` + `<lang>.alignment.json`
7. **Sahne kesim noktaları:** anlatım tek parça seslendirilir (sahnelerin birleşimi); sahne N'in bitişi = son karakterinin `character_end_times_seconds` değeri. Görsel süresi ve altyazı zamanlaması aynı veriden türetildiği için senkron kayması yapısal olarak imkânsız. Her dilin sahne süreleri kendi seslendirmesinden otomatik çıkar.
8. `SubtitleRenderer` → kelime grupları → `subs/<lang>.ass`
9. `KenBurnsRenderer` + `AudioProcessor` (ducking ile müzik miksajı) → `renders/<lang>.mp4` (1080×1920, H.264/AAC)

### Görsel stil kuralı

Yüz gösterilmez: mekân, nesne, belge, silüet, el çekimi, kuşbakışı. `stylePrefix` her prompta eklenir. Karakter tutarlılığı problemi bu tercihle yapısal olarak ortadan kalkar.

## 6. Backoffice

### Sunucu

JDK `HttpServer`, `localhost:8080` (config'ten). Yeni bağımlılık yok.

**Zorunlu detay:** `HttpServer` Range isteklerini desteklemez; `Range` başlığı elle parse edilip `206 Partial Content` dönülür — aksi halde `<video>` içinde ileri sarma çalışmaz.

### API

```
GET    /                                   index.html
GET    /api/channels                       kanal listesi + bekleyen sayıları
GET    /api/jobs?channel=&status=          iş özetleri
GET    /api/jobs/{id}                      detay
GET    /api/jobs/{id}/render/{lang}        mp4 (Range destekli)
GET    /api/jobs/{id}/scene/{n}            png
PATCH  /api/jobs/{id}/variants/{lang}      metadata güncelle
POST   /api/jobs/{id}/approve              { platforms: [...] }
POST   /api/jobs/{id}/reject
POST   /api/jobs/generate                  { channelId }
GET    /api/stats                          aylık maliyet / bütçe
```

### UI

- Vanilla HTML/CSS/JS, build adımı yok; `src/main/resources/web/` altından statik servis
- Layout: 240px kanal sidebar'ı + kart ızgarası (AwesomeDesign.md: 8px grid, 12px radius, gölge sistemi)
- **Koyu tema varsayılan** (video thumbnail'ları koyu zeminde bütünleşir; Linear referansı), açık tema desteklenir
- Kart: thumbnail, başlık, dil rozetleri, süre, maliyet
- Detay: dil sekmeli oynatıcı (soL) + metadata formu ve platform seçimi (sağ); "Yayınla" hikâye seviyesinde onaydır
- Sidebar altında aylık bütçe çubuğu

## 7. Hata Yönetimi ve Bütçe Koruması

- **Atomik durum:** job.json her adımdan sonra temp+rename ile yazılır. Pipeline yeniden başlatıldığında tamamlanmış adımlar atlanır (ör. 6 görselin 5'i varsa yalnız 6.sı üretilir).
- **Geçici hata (5xx/timeout):** mevcut `RetryPolicy`, exponential backoff
- **Kalıcı hata (4xx):** job → `FAILED`, sebep job.json'a, kısmi çıktılar korunur
- **FFmpeg:** stderr yakalanır, job.json'a yazılır, backoffice'te gösterilir
- **Kısmi yayın:** platform başına durum; yalnız başarısız olan yeniden denenir
- **Bütçe:** aylık tavan config'te; harcama `output/costs/<yıl-ay>.json`. Kontrol **çağrıdan önce**: tahmini maliyet + ay içi harcama > tavan → pipeline başlamaz. Amaç tasarruf değil, hata döngüsüne karşı sigorta.

## 8. Konfigürasyon (yeni/değişen anahtarlar)

```properties
llm.model=gpt-5.6-luna
image.model=gpt-image-2
image.quality=medium
tts.model=eleven_v3
music.model=music_v2
video.width=1080
video.height=1920
video.target.duration=75      # kanal profili geçersiz kılabilir
budget.monthly.usd=100
backoffice.port=8080
```

Kanal-özel değerler (`voiceId`, diller, stylePrefix, sceneCount, süre) profil dosyasından gelir; Configuration yalnız global varsayılanları tutar.

## 9. Test Stratejisi

**Birim (API'siz, hızlı):**
- `SubtitleRenderer`: alignment fixture → kelime grupları → ASS (bölme, satır kırma, süre eşikleri)
- Sahne kesim hesabı: karakter indeksi → zaman; sınır durumları (tek/boş/çok uzun sahne)
- `JobStore`: atomik yazma, yarım dosyadan kurtarma, durum geçişleri
- `ChannelStore`: şema doğrulama
- Maliyet hesaplayıcı + bütçe kontrolü

**Sahte client'lar:** kaydedilmiş gerçek JSON yanıtlarıyla `JobPipeline` uçtan uca sıfır maliyetle koşar.

**FFmpeg testleri:** gerçek ffmpeg, küçük fixture'lar; ayrı JUnit tag'i (normal `mvn test` dışında).

**Kapsam dışı:** gerçek API çağrıları, OAuth, YouTube upload (mevcut `validate` komutu yeterli).

## 10. Kararlar Kaydı

| Karar | Seçim | Gerekçe |
|---|---|---|
| İçerik | Hikâye/anlatı | İzlenme süresi yüksek; yaratıcı katkı politika riskini düşürür |
| Yayın | Onay kuyruğu + backoffice | Politika riski (3 aşamalı ceza); kalite kontrolü |
| Backoffice kapsamı | İzle + metadata + onayla/reddet | Dengeli; sahne stüdyosu sonraya |
| Görsel | Atmosferik, yüz yok, stil kilidi | Tutarlılık problemi yapısal çözülür; en ucuz |
| Dil | Çok dilli, görsel paylaşımlı | Ek dil ~$0.10; düşük rekabetli pazarlar |
| Altyazı | Burn-in, ElevenLabs timestamps | Whisper'a gerek yok; senkron mükemmel; ek maliyet sıfır |
| Durum | Dosya-tabanlı iş klasörü (A) | Çıktılar zaten dosya; çökme kurtarması bedava; SQLite yalnız niş/trend verisi için kalır |
| Platform | Soyutlama şimdi, YouTube önce, kademeli | API onayları haftalar sürüyor; arayüzü sonradan sokmak pahalı |
| Süre | 60–90 sn | TikTok ≥60 sn kuralı + Shorts ≤180 sn |
| Kanal | Profil dosyaları, 2-3 kanalla başla | Portföy stratejisi; pipeline zaten parametrik |
| Onay seviyesi | Hikâye (render değil) | Günde 9 değil 3 inceleme; varyantlar aynı kaynaktan |
| UI stack | Vanilla, build yok | Maven'e npm sokmamak; sıfır-bağımlılık felsefesi |
