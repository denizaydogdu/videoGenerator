# 📋 Yapılacak İşler — Shorts Fabrikası

> Güncelleme: 2026-08-02 gecesi. Kaynak: 5-ajan denetimi + ilk gün deneyimi.
> Kural: bir iş bitince `[x]` işaretle + tarihi yaz. Sıra önemlidir.

## 1️⃣ ŞİMDİ — Deniz'in Studio görevleri (~15 dk, tek seferlik)

- [ ] **Telefon doğrulaması** → https://youtube.com/verify
      *(özel küçük resim, 15dk+ video, Content ID itirazı açılır)*
- [ ] **Kanal kitlesi:** Studio → Ayarlar → Kanal → Gelişmiş ayarlar →
      Kitle → **"Hayır, çocuklara yönelik değil"**
- [ ] **Ülke seçimi:** aynı sayfada → İkamet ülkesi *(para kazanma için zorunlu)*
- [ ] **Advanced features doğrulaması:** Ayarlar → Kanal → Özellik uygunluğu →
      Gelişmiş özellikler → kimlik/video ile doğrula
      *(Topluluk sekmesini açar; 48 saate kadar sürebilir — erken başlat)*
- [ ] **Handle değişikliği:** Özelleştirme → Profil → Tanıtıcı →
      `@Unsolved_Files_007` yerine temiz bir isim (öneri: `@UnsolvedFilesTV`)
      *(dış linkler birikmeden şimdi — sonra maliyetli)*
- [ ] **About linkleri + iletişim maili** (Özelleştirme → Profil)

## 2️⃣ ŞİMDİ — Lisans (para kazanma öncesi tek engel)

- [ ] **ElevenLabs Starter ($5/ay)** → elevenlabs.io/pricing
      *(free tier ticari kullanım YASAK + atıf zorunlu; Starter kalıcı ticari
      hak verir. Sonra `music.enabled=true` yapılıp müzik de açılabilir)*

## 3️⃣ KARAR — Kurgu mu, gerçek vakalar mı? (video #3'ten önce şart)

- [ ] Seçenek A (ÖNERİLEN): **Sadece gerçek vakalar** — D.B. Cooper modeli.
      Risk sıfırlanır, arama trafiği hazır. Emsal: "True Crime Case Files"
      kanalı kurgu→gerçek sunumdan KAPATILDI (Şubat 2025).
- [ ] Seçenek B: Kurguya devam ama **başlıkta/ekranda** kurgu ibaresi
      (açıklamadaki uyarı YETMEZ)
- [ ] Karar sonrası: Mara Vale (3 video) başlıkları karara göre güncellenir

## 4️⃣ FORMAT 2.0 — Claude'un kod işleri (video #3+ için, ~yarım gün)

- [ ] **Hook ters çevirme:** StoryWriter promptu — şok edici sonuçla açıl,
      tarih/kurulumla değil ("In October 1975..." → "She vanished 40 seconds
      after this photo was taken")
- [ ] **İlk karede ekran yazısı:** 4-7 kelimelik hook overlay (sessiz izleyici
      için; drawtext/ASS ile ilk 2 saniye)
- [ ] **Süre hedefi 75→60 sn** (profil + kelime bütçesi otomatik uyar)
- [ ] **Görsel tempo:** sahne başına 2-3 görsel → her 3-6 sn'de görüntü
      değişimi (maliyet ~$0.96/hikâye olur, değer)
- [ ] **Karaoke altyazı:** kelime-kelime vurgu, aksan renkli (alignment
      verisi hazır; SubtitleRenderer güncellemesi)
- [ ] **Loop sonu:** son cümle açılışı yeniden anlamlandırsın (StoryWriter
      promptu) — tekrar izleme = en güçlü 2026 sinyali
- [ ] **Telifsiz müzik yatağı:** Pixabay/ZapSplat'ten 3-5 karanlık ambient
      mp3 → assets/music/ → rastgele seçilip mikslenir (ElevenLabs beklemeden)

## 5️⃣ OPERASYON — Ritim kurulumu

- [ ] **`schedule` daemon'u başlat** — günde 1 üretim, sabit saat
      (cron config: `app.scheduler.cron`; öneri: TR akşam piki öncesi ~16:00 UTC)
- [ ] **Dil playlistleri oluştur** (EN/ES/TR + seri bazlı) — kanal oturumu
      görüntülemelerini ~%22 artırıyor; API ile otomatikleştirilebilir
- [ ] **Pinned comment akışı:** her videoya soru/teori yorumu
      ("Sence gerçekten kaçtı mı?") — şimdilik elle, sonra API

## 6️⃣ KOD KUYRUĞU — Öncelikli olmayan (sıra gelince)

- [ ] Backoffice: FAILED işlere "Yeniden dene" + "Sil" butonları
- [ ] İzlenme verisini job.json'a çekme (getVideoStatistics var, bağlı değil)
- [ ] Playlist otomasyonu (yayın sonrası playlistItems.insert)
- [ ] `notifySubscribers` config anahtarı
- [ ] job.json'larından SQLite'a analitik beslemesi (hangi niş tutuyor)

## 7️⃣ İLERİSİ — Büyüme (bu hafta değil)

- [ ] **Evidence Lab (2. kanal)** aktivasyonu — profil hazır
      (`channels/forensic-lab-en.json`, enabled:false) + 44 fikir tohumu
      (`thoughts/ideas/`) · YouTube kanalı + OAuth gerekir
- [ ] **Dil-başına-kanal kararı** — 1 haftalık izlenme verisiyle
      (TR baskınsa: Unsolved Files→TR, EN/ES'e ayrı kanallar)
- [ ] **TikTok/Meta API başvuruları** (onay haftalar sürüyor — erken başvur)
- [ ] OAuth kalıcılığı: Cloud Console → "Publish app" *(yoksa 7 günde bir
      yeniden izin — ilk hatırlatıcı: ~8 Ağustos)*
- [ ] Multi-audio track araştırması (tek video + 6 dublaj kanalı — pipeline
      yeniden mimarisi gerektirir, veri toplandıktan sonra değerlendir)

---
**Bitenler arşivi:** Plan 1-2-3 ✅ · kanal kimliği ✅ · 6 video yayında ✅ ·
dil/kategori/hashtag/kitle backfill ✅ · madeForKids+limit+temizlik kodu ✅
