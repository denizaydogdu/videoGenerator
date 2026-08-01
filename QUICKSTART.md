# YouTube Shorts Auto Generator - Quick Start Guide

Bu kılavuz, projeyi 10 dakikada çalıştırmanızı sağlayacak.

## ✅ Ön Gereksinimler

1. **Java 17+** kurulu olmalı
```bash
java -version  # 17 veya üzeri olmalı
```

2. **Maven 3.6+** kurulu olmalı
```bash
mvn -version
```

3. **FFmpeg** kurulu olmalı
```bash
# macOS
brew install ffmpeg

# Ubuntu/Debian
sudo apt-get install ffmpeg

# Windows
# https://ffmpeg.org/download.html adresinden indirin
```

4. **API Keys** - Aşağıdaki servislere kayıt olup API key alın:
   - Suno API: https://sunoapi.org/
   - OpenAI API: https://platform.openai.com/
   - YouTube API: https://console.cloud.google.com/

## 🚀 Hızlı Kurulum (3 Adım)

### Adım 1: API Keys'leri Ayarla

`config/application.properties` dosyasını düzenleyin:

```properties
suno.api.key=GERÇEK_SUNO_API_KEY_BURAYA
openai.api.key=GERÇEK_OPENAI_API_KEY_BURAYA
youtube.client.id=GERÇEK_YOUTUBE_CLIENT_ID_BURAYA
youtube.client.secret=GERÇEK_YOUTUBE_CLIENT_SECRET_BURAYA
```

**ÖNEMLİ:** "YOUR_" ile başlayan değerleri gerçek API key'lerinizle değiştirin!

### Adım 2: Build

```bash
mvn clean package
```

### Adım 3: Çalıştır

```bash
# İnteraktif mod (menü ile)
java -jar target/youtube-shorts-generator-1.0.0-SNAPSHOT.jar

# Veya run.sh ile
./run.sh
```

## 📋 İlk Kullanım

### Manuel Video Üret

```bash
java -jar target/youtube-shorts-generator-1.0.0-SNAPSHOT.jar generate
```

Bu komut:
1. AI ile müzik üretir (Suno)
2. AI ile video üretir (Sora)
3. Metadata oluşturur (GPT-4)
4. Video işler ve birleştirir (FFmpeg)
5. YouTube'a yükler

Süre: ~3-5 dakika

### Otomatik Zamanlama Başlat

```bash
java -jar target/youtube-shorts-generator-1.0.0-SNAPSHOT.jar schedule
```

Bu komut uygulamayı daemon modda çalıştırır. Her gün belirlediğiniz saatte otomatik video üretir.

## ⚙️ Temel Ayarlar

### Zamanlama Ayarı

`config/application.properties`:

```properties
# Her gün saat 12:00'da çalış
app.scheduler.cron=0 12 * * *

# Her gün saat 09:00'da çalış
app.scheduler.cron=0 9 * * *

# Her gün saat 18:30'da çalış
app.scheduler.cron=30 18 * * *
```

### Video Ayarları

```properties
# Video çözünürlüğü (YouTube Shorts için)
video.width=720
video.height=1280

# Maksimum süre (saniye)
video.max.duration=59

# YouTube privacy (public, unlisted, private)
youtube.privacy.status=public
```

## 🔍 Sorun Giderme

### FFmpeg bulunamıyor

```bash
# FFmpeg yolunu kontrol edin
which ffmpeg

# Eğer farklı bir yoldaysa, config'de belirtin
ffmpeg.path=/usr/local/bin/ffmpeg
```

### OAuth Hatası

İlk çalıştırmada tarayıcı açılır ve YouTube'a izin vermeniz istenir:

1. Tarayıcıda açılan linke gidin
2. Google hesabınızla giriş yapın
3. İzin verin
4. Token otomatik kaydedilir

### API Key Hataları

Tüm servisleri test edin:

```bash
java -jar target/youtube-shorts-generator-1.0.0-SNAPSHOT.jar validate
```

## 💰 Tahmini Maliyetler

| Servis | Günlük | Aylık (30 gün) |
|--------|--------|----------------|
| Suno API | $0.08 | $2.40 |
| OpenAI Sora | $1.50 | $45.00 |
| OpenAI GPT-4 | $0.03 | $0.90 |
| YouTube API | Ücretsiz | Ücretsiz |
| **TOPLAM** | **$1.61** | **~$48/ay** |

## 📊 İlk Başarı Kontrolü

Video başarıyla yüklendikten sonra:

1. Konsol çıktısında şu bilgileri görmelisiniz:
   ```
   Video uploaded successfully: https://www.youtube.com/watch?v=...
   Shorts URL: https://www.youtube.com/shorts/...
   ```

2. YouTube Studio'ya gidin: https://studio.youtube.com
3. Yeni videonuz "Shorts" bölümünde görünmelidir

## 🎯 Sonraki Adımlar

1. **İçerik Kalitesini İyileştir**: `config/application.properties`'de müzik tarzı ayarları
2. **Zamanlama Optimize Et**: En yüksek engagement saatlerinde yayınla
3. **Hashtag Stratejisi**: GPT-4 promptlarını özelleştir
4. **Analytics**: Video performansını takip et

## 🆘 Yardım

- Log dosyaları: `logs/application.log`
- Hata logları: `logs/error.log`
- API logları: `logs/api.log`

Sorun mu yaşıyorsunuz? README.md dosyasındaki detaylı dokümantasyona bakın.

---

**Tebrikler! İlk otomatik YouTube Short'unuzu oluşturmaya hazırsınız!** 🎉
