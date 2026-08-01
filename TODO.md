# 📋 Yapılacak İşler — Shorts Fabrikası

> Güncelleme: 2026-08-02 gecesi. Kaynak: 5-ajan denetimi + ilk gün deneyimi.
> Kural: bir iş bitince `[x]` işaretle + tarihi yaz. Sıra önemlidir.

## 1️⃣ ŞİMDİ — Deniz'in Studio görevleri (~15 dk, tek seferlik)

- [ ] **Telefon doğrulaması** → https://youtube.com/verify
      *(özel küçük resim, 15dk+ video, Content ID itirazı açılır)*
- [ ] **Kanal kitlesi:** Studio → Ayarlar → Kanal → Gelişmiş ayarlar →
      Kitle → **"Hayır, çocuklara yönelik değil"**
- [x] **Ülke seçimi: Türkiye** ✅ (2026-08-02)
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

- [x] **Hook ters çevirme** ✅ (2026-08-02 — StoryWriter 2.0 promptu)
- [x] **İlk karede ekran yazısı** ✅ (2026-08-02 — hookText: üretim+çeviri+ASS overlay 2.2sn)
- [x] **Süre hedefi 75→60 sn** ✅ (2026-08-02 — profiller güncellendi)
- [x] **Görsel tempo** ✅ (2026-08-02 — sahne başına 2 görsel, 01a/01b, süre bölüşümü)
- [x] **Karaoke altyazı** ✅ (2026-08-02 — kelime başına event, aktif kelime altın sarısı)
- [x] **Loop sonu** ✅ (2026-08-02 — StoryWriter LOOP RULE)
- [x] **Telifsiz müzik yatağı (kod)** ✅ (2026-08-02 — LocalMusicLibrary, deterministik)
      - [ ] Deniz: assets/music/ altına 3-5 mp3 indir (README'de kaynaklar)

## 4️⃣b BÜYÜME PLAYBOOK ENTEGRASYONU — 5-ajan araştırmasından
> Detay: `thoughts/research/2026-08-02-buyume-playbook.md`

- [ ] **İçerik stratejisi TR-merkezli:** %70 Türk vakası / %30 uluslararası;
      mikro-niş kilidi ("çözülmemiş kayıp/faili meçhul") ilk 15-20 video
- [x] **StoryWriter'a yasal korkuluklar** ✅ (2026-08-02 — FACTUAL RULES: gerçek+kesinleşmiş/10yıl, alleged dili, likeness yasağı; dipnot F6'da)
- [x] **Başlık şablonu bankası** ✅ (2026-08-02 — TR/EN/ES kalıpları çeviri promptunda)
- [ ] **Seri mekaniği:** Bölüm 1/2 cliffhanger + "Bölüm 2 yazın"
      yorum-kapısı + Dosya #N numaralama
- [x] **Anti-slop çeşitlendirme** ✅ (2026-08-02 — 5 tempo varyantı rotasyonu + birincil-kaynak zorunluluğu promptta)
- [ ] **Video-başı operasyon rutini** (playbook §B: pinned soru, 24h
      swipe-oranı kontrolü, flop=yeni-hook-yeni-video, haftalık arşiv tarama)
- [ ] TR yayın saatleri scheduler'a: hafta içi 18:00-20:00 TRT

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

## 7️⃣ İLERİSİ — Büyüme (YPP SONRASI — karar 2026-08-02)

> **STRATEJİ KARARI (Deniz):** YPP eşikleri kanal başına → tek kanala
> konsantrasyon. Portföy ARDIŞIK işler: kanal 1 para kazanınca kanal 2.
> Dil-başına-kanal fikri İPTAL (abone havuzunu böler) — yerine
> multi-audio track araştırması öncelikli.

- [ ] **Evidence Lab (2. kanal)** — YPP SONRASINA park edildi; profil +
      44 fikir tohumu hazır bekliyor
- [ ] **Multi-audio track POC** (öncelik yükseldi): tek video + 3 ses
      kanalı = tek abone havuzu + temiz dil hedeflemesi
- [ ] **TikTok/Meta API başvuruları** (onay haftalar sürüyor — erken başvur)
- [ ] OAuth kalıcılığı: Cloud Console → "Publish app" *(yoksa 7 günde bir
      yeniden izin — ilk hatırlatıcı: ~8 Ağustos)*
- [ ] Multi-audio track araştırması (tek video + 6 dublaj kanalı — pipeline
      yeniden mimarisi gerektirir, veri toplandıktan sonra değerlendir)

---
**Bitenler arşivi:** Plan 1-2-3 ✅ · kanal kimliği ✅ · 6 video yayında ✅ ·
dil/kategori/hashtag/kitle backfill ✅ · madeForKids+limit+temizlik kodu ✅
