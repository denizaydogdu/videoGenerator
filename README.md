# YouTube Shorts Auto Generator

Otomatik YouTube Shorts video generator - AI kullanarak müzik, video ve metadata üretir.

## Özellikler

- **AI Müzik Üretimi**: Suno API ile telifsiz müzik oluşturma
- **AI Video Üretimi**: OpenAI Sora ile kısa video klipleri
- **Otomatik Metadata**: GPT-4 ile başlık, açıklama ve hashtag üretimi
- **YouTube Entegrasyonu**: Otomatik video yükleme
- **Zamanlama**: Günlük otomatik çalışma desteği
- **Pure Java SE**: Spring Boot bağımlılığı yok

## Gereksinimler

- Java 17+
- Maven 3.6+
- FFmpeg (sistem kurulumu)
- API Keys:
  - Suno API Key
  - OpenAI API Key
  - YouTube API Credentials (Client ID & Secret)

## Kurulum

### 1. FFmpeg Kurulumu

**macOS:**
```bash
brew install ffmpeg
```

**Ubuntu/Debian:**
```bash
sudo apt-get install ffmpeg
```

**Windows:**
[FFmpeg resmi sitesinden](https://ffmpeg.org/download.html) indirip PATH'e ekleyin.

### 2. API Keys Edinme

#### Suno API
1. [Suno API](https://sunoapi.org/) sitesine kayıt olun
2. API key alın
3. Aylık maliyet: ~$2-5

#### OpenAI API
1. [OpenAI Platform](https://platform.openai.com/) hesabı oluşturun
2. API key alın
3. Sora ve GPT-4 erişimi sağlayın

#### YouTube API
1. [Google Cloud Console](https://console.cloud.google.com/)
2. Yeni proje oluşturun
3. YouTube Data API v3'ü aktifleştirin
4. OAuth 2.0 credentials oluşturun (Desktop app)
5. `credentials.json` dosyasını indirip `config/` klasörüne koyun

### 3. Proje Yapılandırması

```bash
# Projeyi klonlayın
git clone <repository-url>
cd videoGenerator

# API keys'i ayarlayın
cp config/application.properties.example config/application.properties
# config/application.properties dosyasını düzenleyip API keys'leri girin

# Projeyi build edin
mvn clean package
```

## Yapılandırma

`config/application.properties` dosyasını düzenleyin:

```properties
# API Keys
suno.api.key=YOUR_SUNO_API_KEY
openai.api.key=YOUR_OPENAI_API_KEY
youtube.client.id=YOUR_YOUTUBE_CLIENT_ID
youtube.client.secret=YOUR_YOUTUBE_CLIENT_SECRET

# Zamanlama (Cron format: dakika saat gün ay haftanınGünü)
app.scheduler.cron=0 12 * * *  # Her gün saat 12:00
app.scheduler.timezone=UTC

# Video ayarları
video.width=720
video.height=1280
video.max.duration=59

# İçerik ayarları
music.default.genre=electronic
music.default.duration=20
video.default.mood=energetic

# YouTube ayarları
youtube.privacy.status=public  # public, unlisted, private
youtube.category.id=10  # 10 = Music
```

## Kullanım

### Tek Seferlik Çalıştırma

```bash
java -jar target/youtube-shorts-generator-1.0.0-SNAPSHOT.jar
```

### Otomatik Zamanlama Modunda Çalıştırma

```bash
# Arka planda çalıştırma
nohup java -jar target/youtube-shorts-generator-1.0.0-SNAPSHOT.jar &

# Logları takip etme
tail -f logs/application.log
```

### Systemd ile Servis Olarak Çalıştırma (Linux)

```bash
sudo cp youtube-shorts.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable youtube-shorts
sudo systemctl start youtube-shorts
sudo systemctl status youtube-shorts
```

## Proje Yapısı

```
videoGenerator/
├── src/main/java/com/videogenerator/
│   ├── main/          # Main application entry
│   ├── api/           # API clients (Suno, OpenAI, YouTube)
│   ├── service/       # Business logic services
│   ├── processor/     # Video/audio processing
│   ├── scheduler/     # Scheduling system
│   ├── model/         # Data models
│   ├── config/        # Configuration management
│   └── util/          # Utilities
├── config/            # Configuration files
├── output/            # Generated videos
├── temp/              # Temporary files
├── logs/              # Application logs
└── pom.xml            # Maven configuration
```

## API Kullanım Limitleri

- **Suno**: 50 müzik/gün (free tier)
- **YouTube**: 6 video/gün (quota: 10,000 units/gün)
- **OpenAI**: Rate limits (RPM)

## Maliyet Tahmini (Aylık)

- Suno API: ~$2.40 (30 müzik × $0.08)
- OpenAI Sora: ~$45.00 (30 video × 15sn × $0.10/sn)
- OpenAI GPT-4: ~$0.90 (30 metadata × $0.03)
- **Toplam: ~$48-50/ay**

## Sorun Giderme

### FFmpeg bulunamıyor
```bash
# FFmpeg yolunu kontrol edin
which ffmpeg

# Yapılandırmada doğru yolu belirtin
ffmpeg.path=/usr/local/bin/ffmpeg
```

### OAuth authentication hatası
1. `config/tokens.json` dosyasını silin
2. Uygulamayı yeniden başlatın
3. Browser'da açılan linke gidin ve izin verin

### API rate limit exceeded
- Günlük limitlere dikkat edin
- `application.properties` dosyasında scheduler sıklığını azaltın

### Video upload failed
- YouTube quota limitini kontrol edin
- İnternet bağlantısını kontrol edin
- Video boyutunu ve formatını kontrol edin (< 256GB, MP4)

## Geliştirme

### Build

```bash
mvn clean compile
```

### Test

```bash
mvn test
```

### Package

```bash
mvn clean package
```

## Katkıda Bulunma

1. Fork yapın
2. Feature branch oluşturun (`git checkout -b feature/amazing-feature`)
3. Commit edin (`git commit -m 'Add amazing feature'`)
4. Push edin (`git push origin feature/amazing-feature`)
5. Pull Request açın

## Lisans

MIT License - detaylar için LICENSE dosyasına bakın.

## Uyarılar

- YouTube'un AI-generated içerik politikalarına uyun
- API kullanım maliyetlerini takip edin
- Quota limitlerini aşmamaya dikkat edin
- Üretilen içeriklerin kalitesini düzenli kontrol edin

## Destek

Sorun bildirmek için [GitHub Issues](https://github.com/username/videoGenerator/issues) kullanın.
