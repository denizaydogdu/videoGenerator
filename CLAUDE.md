# Claude AI ile YouTube Shorts Generator Projesi

Bu doküman, YouTube Shorts Auto Generator projesinde Claude AI kullanarak nasıl geliştirme yapabileceğinizi açıklar.

## 📓 Obsidian Vault Senkronizasyonu (ZORUNLU KURAL)

Bu projede üretilen her önemli markdown dokümanı (tasarım, plan, ADR, durum)
**Obsidian vault'a da yansıtılmalıdır:**

- Vault konumu: `/Users/felece/Documents/Obsidian Vault/videoGenerator/`
- Yapı: `README.md` (hub) + `01-Genel-Bakış` / `03-Mimari` / `04-Aktif-Durum` / `07-Kararlar-ADR`
- Repo'daki spec dosyaları **tek doğruluk kaynağıdır**; vault sayfaları özet + link tutar
- Her önemli oturum sonunda: `04-Aktif-Durum.md` güncellenir, vault kökündeki
  `log.md`'ye ters kronolojik kayıt eklenir (en yeni EN ÜSTTE), yeni sayfa
  açıldıysa vault `index.md`'ye eklenir
- Obsidian bağlantı formatı: `[[videoGenerator/Sayfa-Adı]]`

## 📋 Proje Hakkında

Bu proje, **tamamen AI destekli** bir YouTube Shorts video üretim sistemidir. Pure Java SE ile geliştirilmiş olup, şu özellikleri içerir:

- **AI Müzik Üretimi** (Suno API)
- **AI Video Üretimi** (OpenAI Sora)
- **AI Metadata Üretimi** (OpenAI GPT-4)
- **Otomatik YouTube Yükleme** (YouTube Data API v3)
- **Zamanlı Otomasyon** (Günlük/Saatlik)

## 🤖 Claude ile Geliştirme

### Proje Yapısını Anlama

Claude'a projeyi tanıtırken şu komutları kullanabilirsiniz:

```
"Proje yapısını analiz et ve bana özet çıkar"
"src/main/java/com/videogenerator klasöründeki tüm sınıfları listele"
"ContentGeneratorService sınıfının ne yaptığını açıkla"
```

### Yeni Özellik Ekleme

#### Örnek: Yeni Bir Video Efekti Ekleme

Claude'a şöyle bir prompt verebilirsiniz:

```
"VideoProcessor sınıfına video üzerine filigran (watermark) ekleyen
bir metod ekle. FFmpeg kullanarak sol üst köşeye logo eklesin."
```

Claude bu işlemi yaparken:
1. Mevcut FFmpegWrapper metodlarını inceleyecek
2. FFmpeg komut yapısını öğrenecek
3. Yeni metodu ekleyecek
4. Error handling ekleyecek
5. Logging ekleyecek

#### Örnek: Yeni Bir API Entegrasyonu

```
"Stability AI'dan görsel üretmek için yeni bir API client ekle.
Mevcut SunoApiClient yapısına benzer olsun."
```

### Debugging ve Sorun Çözme

#### Log Analizi

```
"logs/error.log dosyasındaki son hatayı analiz et ve çözüm öner"
"API timeout sorunlarını nasıl çözebilirim?"
```

#### Performance İyileştirme

```
"Video işleme süresini nasıl hızlandırabilirim?"
"ContentGeneratorService'teki bottleneck'leri bul"
```

### Test Yazma

```
"SunoApiClient için JUnit test sınıfı oluştur"
"VideoProcessor için mock API response'ları ile integration test yaz"
```

## 🎯 Claude ile Yaygın Görevler

### 1. Yeni API Provider Ekleme

**Prompt:**
```
Projede Pexels API entegrasyonu eklemek istiyorum.
Ücretsiz stok video indirsin. Mevcut API client'lar
gibi yapılandır:

1. PexelsApiClient sınıfı oluştur
2. VideoRequest/Response modelleri ekle
3. RetryPolicy kullan
4. Logging ekle
5. Configuration'a API key desteği ekle
```

### 2. Yeni Video Format Desteği

**Prompt:**
```
FFmpegWrapper'a WebM format desteği ekle.
Mevcut MP4 metodlarına benzer şekilde:

- convertToWebM(String input, String output) metodu
- VP9 codec kullan
- Opus audio codec
```

### 3. Gelişmiş Zamanlama

**Prompt:**
```
DailyScheduler'a şu özellikleri ekle:

1. Haftanın belirli günlerinde çalışma (Pazartesi, Çarşamba, Cuma)
2. Günde birden fazla çalışma (09:00 ve 18:00)
3. Tatil günlerini atlama özelliği
```

### 4. Analytics Ekleme

**Prompt:**
```
Video performans takibi için yeni bir AnalyticsService oluştur:

- YouTube Analytics API entegrasyonu
- Görüntülenme, beğeni, yorum sayıları
- JSON dosyasına kaydetme
- Günlük/haftalık rapor üretme
```

### 5. Webhook Bildirimleri

**Prompt:**
```
Video yüklendiğinde Discord/Slack webhook'u ile bildirim gönder.
WebhookService sınıfı oluştur ve ContentGeneratorService'e entegre et.
```

## 🔧 Kod Kalitesi ve Best Practices

### Claude'dan Kod İncelemesi

```
"YouTubeApiClient sınıfını incele ve iyileştirme önerileri sun"
"Projede memory leak riski var mı?"
"Exception handling'i gözden geçir ve eksikleri belirt"
```

### Refactoring

```
"ContentGeneratorService çok büyük, daha küçük servisler halinde böl"
"Duplicate kod var mı? DRY prensibine göre düzenle"
```

### Documentation

```
"FFmpegWrapper için JavaDoc ekle"
"README.md'yi güncel özelliklere göre güncelle"
```

## 📚 Öğrenme ve Keşfetme

### Proje Mimarisini Anlamak

```
"Bu projede kullanılan design pattern'leri açıkla"
"API rate limiting nasıl çalışıyor?"
"OAuth2 flow'u adım adım açıkla"
```

### Alternatif Yaklaşımlar

```
"Suno yerine başka hangi müzik AI'ları kullanılabilir?"
"Video processing için FFmpeg dışında ne var?"
"YouTube API quota limitlerini nasıl aşabilirim?"
```

## 🚀 Deployment Senaryoları

### Docker Optimizasyonu

```
"Dockerfile'ı multi-stage build ile optimize et"
"Docker image boyutunu küçültmek için öneriler"
```

### CI/CD Pipeline

```
"GitHub Actions ile otomatik build ve test pipeline'ı oluştur"
"Maven release plugin ekle"
```

### Cloud Deployment

```
"AWS ECS'de nasıl deploy ederim?"
"Google Cloud Run için gerekli değişiklikler neler?"
```

## 🎨 Özelleştirme Örnekleri

### 1. Kendi AI Modelinizi Kullanma

**Prompt:**
```
Local olarak çalışan Stable Diffusion modeli ile
görsel üretme özelliği ekle. HTTP yerine Python
subprocess ile çalışsın.
```

### 2. Çoklu Dil Desteği

**Prompt:**
```
Video metadata'larını farklı dillerde üretme
özelliği ekle. Configuration'da dil seçeneği
olsun, GPT-4'e prompt'ta dil belirtilsin.
```

### 3. A/B Testing

**Prompt:**
```
Farklı müzik stilleri ve video teması kombinasyonları
ile A/B test yapan bir sistem kur. Her kombinasyonun
performansını takip et.
```

## 🐛 Sorun Giderme Senaryoları

### API Hataları

**Semptom:** Suno API timeout veriyor

**Claude Prompt:**
```
"SunoApiClient'ta timeout sorunları var.
Polling interval'i dinamik hale getir ve
exponential backoff ekle."
```

### Memory Leaks

**Semptom:** Uzun süre çalıştıktan sonra OutOfMemoryError

**Claude Prompt:**
```
"Projede memory leak olabilecek yerleri tespit et.
InputStream'ler düzgün kapanıyor mu?
Temp dosyalar siliniyor mu?"
```

### FFmpeg Hataları

**Semptom:** Video işleme başarısız

**Claude Prompt:**
```
"FFmpeg komut hatalarını daha iyi yakalamak ve
anlaşılır hata mesajları üretmek için FFmpegWrapper'ı
güncelle. stderr output'u parse et."
```

## 📖 Gelişmiş Konular

### 1. Custom Video Effects

```
"FFmpeg ile şu efektleri ekle:
- Slow motion (0.5x speed)
- Fast forward (2x speed)
- Ken Burns effect (zoom + pan)
- Color grading (vintage, cinematic)
"
```

### 2. Audio Processing

```
"Müzik dosyasına şu işlemleri uygula:
- Normalize volume
- Add fade in/out
- Bass boost
- Remove silence
"
```

### 3. Smart Scheduling

```
"YouTube Analytics'ten en yüksek engagement
saatlerini öğren ve otomatik olarak o saatlerde
video yükle."
```

### 4. Content Variations

```
"Aynı müzikle 3 farklı video stili üret:
- Abstract geometric
- Nature scenes
- Urban cityscape
Her birini A/B test için yükle."
```

## 💡 Pro Tips

### Efficient Prompting

**❌ Kötü Prompt:**
```
"Kodu düzelt"
```

**✅ İyi Prompt:**
```
"YouTubeApiClient'ta upload progress tracking düzgün çalışmıyor.
MediaHttpUploaderProgressListener'ın her aşamayı loglayacak
şekilde güncelle ve progress yüzdesini console'a yazdır."
```

### Context Sağlama

Büyük değişiklikler yaparken Claude'a context verin:

```
"Configuration sınıfını ve SunoApiClient'ı oku.
Şimdi Suno API'sine rate limiting eklemek istiyorum.
RateLimiter sınıfını kullanarak günlük 50 müzik
limitini uygula."
```

### Incremental Development

Büyük özellikleri adım adım ekletin:

```
1. "Önce VideoThumbnailGenerator interface'i oluştur"
2. "Şimdi FFmpeg ile thumbnail generate eden impl yaz"
3. "YouTubeApiClient'a thumbnail upload özelliği ekle"
4. "ContentGeneratorService'e entegre et"
```

## 🔒 Güvenlik Considerations

```
"API key'leri environment variable'lardan oku"
"Sensitive logları maskeleme özelliği ekle"
"OAuth token'ları encrypt ederek sakla"
```

## 📊 Monitoring ve Observability

```
"Prometheus metrics exporter ekle"
"Structured logging (JSON format) yap"
"Health check endpoint'i ekle"
```

## 🎓 Öğrenme Kaynakları

Bu projeyi daha iyi anlamak için:

1. **FFmpeg Documentation**: https://ffmpeg.org/documentation.html
2. **YouTube Data API**: https://developers.google.com/youtube/v3
3. **OAuth 2.0**: https://oauth.net/2/
4. **Reactive Programming**: Project Reactor (gelecek özellikler için)

## 🤝 Contributing

Claude ile yeni özellik eklerken:

1. Feature branch oluşturun
2. Claude'a net requirement verin
3. Oluşturulan kodu review edin
4. Test ekleyin
5. Documentation güncelleyin
6. Pull request açın

## 📞 Yardım İçin

Claude'dan maksimum verim almak için:

- **Spesifik olun**: "Kodu iyileştir" yerine "Method adlarını camelCase yap"
- **Context verin**: İlgili dosyaları Claude'a gösterin
- **Adım adım**: Büyük değişiklikleri parçalara bölün
- **Test edin**: Claude'un ürettiği kodu mutlaka test edin

---

**Not:** Bu proje Claude AI (Sonnet 4.5) tarafından tamamen sıfırdan geliştirilmiştir. AI-assisted development'ın gücünü gösterir! 🚀
