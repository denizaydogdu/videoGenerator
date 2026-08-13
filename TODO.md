# 📋 Yapılacak İşler — Shorts Fabrikası

> Güncelleme: 2026-08-12. Kaynak: 5-ajan denetimi + ilk hafta deneyimi.
> Kural: bir iş bitince `[x]` işaretle + tarihi yaz. Sıra önemlidir.

## 1️⃣2️⃣ Velzon çoklu-platform genişlemesi (2026-08-12 başladı)

**DÜZELTME (2026-08-12, oturum ilerlerken fark edildi):** Velzon
e-fatura/muhasebe şirketi DEĞİL — **BIST borsa analiz terminali**
(endeksler, hisse skorları, AI tahminleri, teknik göstergeler, Pine
Script eğitimi, TradingView/Fintables benzeri). Erken bir varsayım
hatası aşağıdaki maddelerde "e-fatura" diye geçiyor olabilir — o kısımlar
tarihsel kayıt olarak bırakıldı, düzeltme detayı için "Velzon içerik
kaynağı" bölümüne bak. Kullanıcının kendi şirketi Velzon için görünürlük
artırma işi büyüdü: X'ten sonra Instagram/YouTube/TikTok de eklenecek. Ayrıca
**yeni bir 3. firma: Scyborsa** (borsa uygulaması, iOS+Android canlı,
mevcut hesaplar: https://x.com/scyborsa, https://www.tiktok.com/@scyborsa,
https://www.instagram.com/scyborsa/, web: scyborsa.com) — aynı desenle
(AI metin+görsel, backoffice'ten tek tık yayın) ileride eklenecek.

Sıra: Velzon IG → Velzon YouTube → **Velzon TikTok (kod hazır, kullanıcı
Developer Portal kurulumu bekliyor)** → Velzon X (kod zaten hazır, sadece
Developer Portal kurulumu bekleniyor) → sonra Scyborsa.

**Scyborsa veri entegrasyonu ERTELENDİ** (2026-08-12 araştırıldı,
kullanıcı kararıyla) — otomatik DB/API entegrasyonu şu an kapalı yol:
Scyborsa'nın Postgres'i ayrı sunucuda + localhost-only, "yayına hazır
bulgu" endpoint'i yok, `docs/kaynak/olcum/*.md` dosyaları yapısal değil
(serbest metin, insan yargısı gerektiriyor). Ayrıca Scyborsa'nın kendi
yayın rehberi (`olcum-bulgulari-yayin-rehberi.md`) çok katı: hisse kodu +
güncel veri, kurum adı ASLA yayınlanamaz — sadece kurumsuz/istatistiksel
bulgular (T+2 mutabakatı, taban oranı vb.) güvenli. Detay: [[scyborsa-data-integration]].
Gerçekçi yol otomatik entegrasyon değil, manuel bir "bulgu ekle" arayüzü
— sıra gelince değerlendirilecek.

### Velzon içerik kaynağı — DÜZELTİLDİ: BIST terminali + gerçek makale kaynağı ✅ (2026-08-12)
- [x] **Kritik yanlış varsayım düzeltildi:** Velzon = e-fatura/muhasebe
      DEĞİL, BIST borsa analiz terminali (kanıt: velzon.tr/bilgi-merkezi/
      — 434 gerçek makale, endeksler/hisse skorları/AI tahminleri/teknik
      göstergeler/Pine Script eğitimi). Hata, erken bir oturumdaki yanlış
      özetin sorgulanmadan tekrar kullanılmasından kaynaklandı —
      `velzon-django` kodunda `gate.velzon.tr/api/chart/v2` (TradingView
      borsa verisi) zaten görülmüştü ama çelişki fark edilmemişti. Ders
      hafızaya kaydedildi ([[velzon-is-bist-terminal]]).
- [x] **`VelzonKnowledgeBaseClient` yazıldı** — Jsoup ile Bilgi Merkezi'ni
      (`velzon.tr/bilgi-merkezi/`) tarar, gerçek makale listesi + tam
      metin çeker. `VelzonTweetGenerator`/`VelzonInstagramPostGenerator`/
      `VelzonYoutubeScriptGenerator` yeniden temalandırıldı (BIST/yatırım
      eğitimi, "yatırım tavsiyesi/garanti getiri/uydurma istatistik yok"
      sert kuralları) — serbest metin "konu" kutusu yerine backoffice'te
      gerçek makale seçici (datalist). Üretici imzaları değişmedi, sadece
      LLM'e giden metin artık gerçek makale içeriği. 233 test yeşil (11 yeni).
- [x] **Adversarial review + kritik bug düzeltildi** ✅ — bağımsız review
      agent'ı canlı siteyi bizzat çekip test etti: `ARTICLE_PATH` regex'i
      sadece 2 kategoriyi (borsa-terimleri, baslangic) sabit listelemiş,
      canlı sitede olan 12+ kategoriyi (derecelendirmeler, indikatörler,
      velzon-script-seviye-1..10) SESSİZCE dışlıyordu — "gerçek makale
      havuzu kullan" hedefini yarı yarıya boşa çıkaran bir bug. Düzeltme:
      kategori adı yerine yapısal kural (`/bilgi-merkezi/<kategori>/<slug>/`
      = tam 2 segment). Canlı doğrulama: düzeltmeden önce ~50 makale,
      sonra **434 makale, 14 kategori** doğru yakalanıyor. Yeni test
      eklendi, 233/233 yeşil.
- [ ] Prod sunucusuna deploy + canlı ilk içerik üretimi denemesi (yeni
      makale seçici ile, gerçek bir Velzon makalesinden tweet/post/video).

### Velzon YouTube — OAuth tamamlandı, video pipeline sırada
- [x] `velzon-youtube-auth` CLI komutu eklendi (Main.java) — YouTube'un
      OAuth akışı TikTok/Pinterest'ten farklı: Google'ın kütüphanesi
      tarayıcıyı otomatik açar, yerel sunucuyla (port 8888) callback'i
      yakalar, kod kopyala-yapıştır yok ✅ (2026-08-12)
- [x] **Doğru Google hesabı: `info@velzon.tr`** (kullanıcının kişisel
      Gmail'i DEĞİL — bu karışıklık oturumda birkaç kez düzeltildi,
      hafızaya kaydedildi) — "Google doğrulamadı" uyarısı normal
      (youtube.upload sensitive scope), "Gelişmiş → git (güvenli değil)"
      ile geçildi
- [x] **Bug düzeltildi:** `runVelzonYouTubeAuth` ilk yazımda token'ı
      yanlış dizine (`config/velzon-youtube/`) kaydediyordu — diğer tüm
      kanallarla tutarlı olması gereken yer `config/tokens/velzon-youtube/`
      (`"tokens/" + id` deseni, Main.java'daki diğer YouTubeApiClient
      kullanımlarıyla aynı). Dosya taşındı + kod düzeltildi.
- [x] **Doğrulandı:** `getMyChannelTitle()` eklendi, token'ın gerçekten
      "Velzon" kanalına bağlı olduğu teyit edildi (tarayıcı açılmadan,
      kayıtlı token ile).
- [x] **Video üretim pipeline'ı** ✅ (2026-08-12, TDD) —
      `VelzonYoutubeScriptGenerator` (LLM ile anlatım/başlık/açıklama/
      hashtag/görsel-prompt üretir, TEMBEL: parti üretiminde görsel/ses/
      video ÜRETMEZ) + `VelzonYoutubeVideoBuilder` (KenBurnsRenderer'ı
      TEK SAHNE modunda kullanır: görsel+TTS+boş .ass ile render,
      "Yayınla" tıklanana kadar çalışmaz, dosya varlığına bakarak
      idempotent) + `VelzonYoutubePublishService` (upload sonrası
      published=true HEMEN kalıcı — Instagram'daki emsal duplicate-publish
      bug fix'iyle aynı sıralama). Backoffice: yeni "Velzon YouTube"
      sekmesi (sidebar + parti üretim diyaloğu + yayın butonu). 221 test
      yeşil (18 yeni). AÇIK RİSK: boş `.ass` dosyasının ffmpeg
      `subtitles=` filtresini bozmadığı gerçek ffmpeg ile doğrulanmadı —
      canlı ilk denemede kontrol edilmeli.
- [x] **Adversarial review + kritik bug düzeltildi** ✅ (2026-08-12) —
      bağımsız review agent'ı (Codex hâlâ limitli): `Main.java`'daki
      backoffice wiring, gerçek OAuth token'ının bulunduğu
      `config/tokens/velzon-youtube/` yerine yanlışlıkla var olmayan
      `config/velzon-youtube/`'a bakıyordu (`"tokens/"` öneki eksikti) —
      prod'da headless sunucuda ya tarayıcı açmaya çalışıp donma/çökme,
      ya da sessizce "yapılandırılmamış" görünme riski. Düzeltildi +
      canlı doğrulandı (`serve` başlatılıp log'da "OAuth2 authorization
      successful" + "Velzon YouTube backoffice section enabled" görüldü,
      tarayıcı hiç açılmadı — mevcut token sessizce yüklendi). 221/221 yeşil.
- [x] **Canlı ilk video denemesi denendi, ElevenLabs'ta durdu, sonra
      düzeltildi** ✅ (2026-08-13) — "Momentum Skoru" makalesinden parti
      üretildi (GPT script + gpt-image görsel sorunsuz çalıştı),
      "Yayınla" tıklanınca TTS adımında `402 payment_required: "Free
      users cannot use library voices via the API"` hatası alındı.
      **Kök neden kod bug'ı değildi, yanlış ses ID'siydi:** Velzon
      YouTube varsayılan `new VoiceConfig()` kullanıyordu, bu da
      `VOICE_RACHEL` (21m00...) demek — bu ses hesaba hiç "eklenmemiş"
      bir kütüphane sesi, free planda API'den kullanılamıyor. Ana
      truecrime-en kanalının kendi `channels/truecrime-en.json`'da
      `voiceId: JBFqnCBsd6RMkjVDRZzb` (George) tanımlı ve bu ses zaten
      hesaba ekli, API'den çalışıyor — canlı curl testiyle doğrulandı.
      Düzeltme: `VoiceConfig.VOICE_GEORGE` sabiti eklendi, Velzon
      YouTube wiring'i (`Main.java`) George'u kullanacak şekilde
      değiştirildi (kullanıcının kendi ElevenLabs hesap/plan tercihine
      dokunulmadı, sadece doğru sesi seçtik). 238/238 yeşil, prod'a
      deploy edildi.
- [x] **Canlı doğrulama tamamlandı** ✅ (2026-08-13) — aynı script
      tekrar "Yayınla" ile denendi, uçtan uca başarılı: TTS (George) →
      ffmpeg render (boş `.ass` riski de burada netleşti, sorun
      çıkarmadı) → YouTube upload. `published: true`,
      `url: youtube.com/shorts/knDUv2ajbZk`. oEmbed ile canlı
      doğrulandı: kanal **@Velzon-tr**, başlık/thumbnail doğru geliyor.

### Velzon TikTok (@velzon_tr) — kod tamamlandı, kullanıcı kurulumu bekliyor
- [x] **Kod tarafı tamamlandı ve canlıda tarayıcıyla test edildi** ✅
      (2026-08-13, adım adım — kullanıcı isteğiyle küçük parçalara
      bölündü) — **video üretmez**, Velzon YouTube batch'inin zaten
      render ettiği `video-XX.mp4`'ü ikinci bir hesaba (@velzon_tr)
      postlar. Tasarım kararı: fotoğraf/carousel modu yerine mevcut
      video pipeline'ı yeniden kullanıldı (ElevenLabs artık çalışıyor,
      TikTok'ta yeni bir PHOTO API yüzeyi yazmaktan daha az risk).
      `VelzonTiktokPublishService` (yeni, kendi `tiktok.json` durum
      dosyası — mevcut YouTube `manifest.json` şemasına HİÇ dokunmadı),
      mevcut `TikTokApiClient.directPost()` (ana kanalla aynı sınıf,
      ayrı client key/token dosyasıyla ikinci kez instantiate edildi),
      backoffice route'ları (`/api/velzon-tiktok/*`, "generate" yok),
      `velzon-tiktok-auth` CLI komutu, sidebar'da Velzon alt-menüsüne
      4. sekme. 249/249 test yeşil. Puppeteer ile canlı tarayıcı testi:
      batch listeleme, video-hazır-değil rozeti, detay modalı, ve sahte
      anahtarla uçtan uca yayın denemesinin hatayı çökmeden toast olarak
      gösterdiği doğrulandı. Adversarial review: 0 bulgu.
- [ ] **Kullanıcı: developer.tiktok.com'da @velzon_tr için sandbox app
      kur** — mevcut Unsolved Files uygulamasından TAMAMEN ayrı bir kayıt
      (farklı hesap/marka). Content Posting API + video.publish scope,
      callback: `https://videogenerator.velzon.tr/velzon-tiktok-callback.html`
      (bu callback sayfası da GitHub Pages'e eklenmeli, mevcut
      tiktok-callback.html'e benzer).
- [ ] Client key/secret gelince config'e ekle (`velzon.tiktok.sandbox.client.key/secret`)
      → `velzon-tiktok-auth` ile tek seferlik yetkilendirme (@velzon_tr
      hesabıyla tarayıcıda onay)
- [ ] İlk gerçek TikTok post denemesi (backoffice'ten, Velzon YouTube'da
      zaten yayınlanmış bir videoyu seçip "TikTok'a yayınla") — post
      TikTok API'sinden `publish_id` döner ama public URL dönmez, post
      @velzon_tr hesabının TikTok uygulamasındaki gelen kutusuna düşer
      (uygulama onaylanana kadar SELF_ONLY, elle incelenip yayınlanması
      gerekebilir) — bu davranış TikTok'un kendi Direct Post akışının
      normal parçası, bug değil.

### Velzon Instagram — kurulum tamamlandı, kod agent'ta
- [x] Meta app "Velzon Social Publisher" oluşturuldu (App ID 1984849792233808,
      use case: Manage messaging & content on Instagram) ✅ (2026-08-12)
- [x] **"Instagram API with Instagram Login" akışı kullanıldı — Facebook Page
      GEREKMEDİ** (Velzon'un Facebook hesabı yok, bu yüzden bilinçli seçim)
- [x] `instagram_business_content_publish` izni eklendi (varsayılan listede
      yoktu, Permissions and features sayfasından elle eklendi)
- [x] **"Insufficient developer role" hatası çözüldü** — hem Facebook hem
      Instagram hesabında 2FA açılması + @velzontr'nin App roles → Roles'te
      "Instagram Tester" davetini instagram.com/accounts/manage_access/
      üzerinden elle kabul etmesi gerekti (otomatik "Add account" akışı
      arka planda bu adımı tamamlayamıyor, bilinen bir Meta aksaklığı)
- [x] Access token üretildi ve doğrulandı (curl ile canlı test, @velzontr
      BUSINESS hesap dönüyor) — config'e kaydedildi (`velzon.ig.*` anahtarları)
- [x] **Kod tarafı tamamlandı** ✅ (2026-08-12) — `VelzonInstagramApiClient`
      (graph.instagram.com, media container → publish → permalink, 3 ayrı
      metod), `VelzonInstagramPostGenerator` (Pinterest deseninde AI
      görsel+caption, fintech güvenlik kuralları), `VelzonInstagramPublishService`
      (idempotent manifest), backoffice entegrasyonu (Pinterest/Velzon-X ile
      aynı desen, "Velzon Instagram" bölümü). 195 yeni test (163→203 toplam,
      market data client dahil).
- [x] **Adversarial review + kritik bug düzeltildi** ✅ (2026-08-12) — Codex
      hâlâ kullanılamıyor (limit 10 Eylül), bağımsız review agent'ı kullanıldı.
      Bulgu: `publishContainer` başarılı olup (post gerçek hesapta canlıya
      çıkıp) `getPermalink` ya da manifest yazma başarısız olursa,
      `published` flag `false` kalıyordu → kullanıcı tekrar "Yayınla" derse
      **duplicate post** riski (Pinterest'teki emsal bug'ın aynısı). Düzeltme:
      `publishContainer` sonrası `published=true` (url=null) hemen kalıcı
      hale getiriliyor, permalink ayrı try/catch'te deneniyor. Yeni testle
      doğrulandı, 203/203 yeşil.
- [x] **Canlı ilk post denemesi + container-ready bug düzeltildi** ✅
      (2026-08-13) — kullanıcı gerçek bir postu "Yayınla" ile denedi,
      Instagram `HTTP 400 "Media ID is not available"` (subcode 2207027)
      döndü. Kök neden: container oluşturulduktan hemen sonra publish
      denenmiş, Instagram tarafında container henüz FINISHED durumuna
      gelmemiş. Düzeltme: `waitUntilContainerReady()` eklendi
      (`VelzonInstagramApiClient`) — `status_code` FINISHED olana kadar
      polling (max 10 deneme × 2sn, ERROR/EXPIRED'de hemen fırlatır,
      timeout'ta da fırlatır — sessizce publish'e geçmez),
      `VelzonInstagramPublishService.publishPost()` içinde
      `publishContainer()`'dan önce çağrılıyor. Adversarial review'da
      polling mantığı, thread-blocking (BackofficeServer'ın diğer
      publish endpoint'leriyle aynı senkron desende, kabul edilebilir),
      idempotent-publish sıralaması ve test kapsamı doğrulandı — 238/238
      yeşil, prod'a deploy edildi.
- [x] **Düzeltme sonrası canlı doğrulama** ✅ (2026-08-13) — aynı RSI
      postu tekrar "Yayınla" ile denendi, bu sefer başarılı: `published:
      true`, `url: instagram.com/p/Db-KSgjjHoF/`, canlıda 200 dönüyor.
      Container-ready polling gerçek koşulda doğrulandı.
- [ ] **Gerçek piyasa verisi entegrasyonu (yeni, 2026-08-12 başladı)** —
      `VelzonMarketDataClient` yazıldı (gate.velzon.tr/api/chart/v2/{symbol},
      X-API-KEY ile — Velzon'un kendi Django frontend'inin de kullandığı
      aynı backend, Django aradan çıkarılıyor). **Not:** kullanılan
      API key (`prod-DE6D594B-...`), velzon-django'nun kendi
      ADR-005-frontend-apikey-proxy-public-veri.md dosyasında "rotate
      edilmeli" diye işaretli eski bir anahtar — kullanıcı onayıyla
      şimdilik bu anahtarla devam ediliyor, rotate edilirse
      `velzon.market.api.key` burada da güncellenmeli. Henüz içerik
      üretimine (VelzonInstagramPostGenerator/VelzonTweetGenerator)
      bağlanmadı — sıradaki adım bu.

### Backoffice UX — sol menü + detay modalı ✅ (2026-08-12/13, kullanıcı geri bildirimiyle 4-5 tur)
- [x] **Sol menü kanala-özel dinamik alt-menüye çevrildi** — eski statik
      "Diğer kanallar / Pinterest / Velzon X / Velzon Instagram / Velzon
      YouTube" listesi kaldırıldı. Artık sadece 2 kanal adı görünüyor
      (Unsolved Files, "Small Space Living" — Pinterest'in kişisel marka
      adı, jenerik "Pinterest" değil), Velzon'a tıklanınca X/Instagram/
      YouTube alt-menüsü DOM'da hemen altına (aynı liste içine, ayrı
      `<ul>` değil) açılıyor. `state.brand`, `VELZON_PLATFORMS` dizisi,
      `renderChannelList()` (sadece render, network isteği yok — veri
      `loadChannels()`'ta ayrı çekiliyor), `hideAllViews()`,
      `showVelzonBrandView()` (Velzon'a tıklayınca otomatik bir platfoma
      geçmeden "platform seçin" boş ekranı) eklendi.
- [x] **"Velzon not configured" kırmızı hata → sakin bilgi kutusu** —
      henüz kurulmamış platforma (X) girince artık kırmızı toast yerine
      "Bu platform henüz yapılandırılmadı" mesajı (`renderLoadError()`,
      503 durumunu diğer hatalardan ayırıyor).
- [x] **Kart tıklayınca detay modalı** — daha önce onaylanmış ama
      kodlanmamış bir özellik: kullanıcı "içine girip izleyemiyorum ne
      yaptığını, yayınlandı diyor ne yayınlandı" diye bildirdi. 4
      platformun (Pinterest, Velzon X/Instagram/YouTube) ortak kart
      tıklama davranışı: `openPostDetail()` — görsel/başlık/metin/meta +
      yayınlanmışsa "Yayında, görüntüle ↗" linki, değilse "Yayınla"
      butonu (kartın kendi butonuyla aynı yayın mantığı, `stopPropagation`
      ile korunmuş çift-tıklama riski yok).
- [x] **Adversarial review + bug düzeltildi** ✅ (2026-08-13) — bağımsız
      review agent'ı: modal içindeki "Yayınlanıyor…" metni
      `publishingLabel` parametresini yok sayıyordu, Velzon YouTube'un
      (dakikalar sürebilen video render+upload) "Video oluşturuluyor ve
      yükleniyor…" özel mesajı yerine hep genel "Yayınlanıyor…"
      gösteriyordu — kullanıcıya app'in donmuş gibi görünme riski.
      Düzeltildi (`publishBtn.textContent = publishingLabel ||
      "Yayınlanıyor…"`), XSS/idempotency/dead-code kontrolleri temiz
      çıktı. 238/238 yeşil, prod'a deploy edildi.

## ✅ OAuth kalıcılığı çözüldü

- [x] **Google Cloud Console → Google Auth Platform → Audience → Publish app**
      ✅ (2026-08-12) — proje `gen-lang-client-0951563693`, durum artık
      "In production" (Testing'ten çıktı). YouTube token'ı artık 7 günde
      bir kesilme riski taşımıyor. `youtube.upload` sensitive scope
      olduğu için gelecekteki re-auth'larda "Google hasn't verified this
      app" uyarısı çıkabilir — sorun değil, "Advanced → Go to (unsafe)"
      ile geçilir; tek kullanıcı (owner) kullandığı için doğrulama
      (verification) gerekmez.

## ✅ 1️⃣ Deniz'in Studio görevleri — TAMAMLANDI (2026-08-12)

- [x] **Telefon doğrulaması** ✅ (2026-08-12) — özel küçük resim, 15dk+
      video, Content ID itirazı artık açık
- [x] **Kanal kitlesi: "Hayır, çocuklara yönelik değil"** ✅ (2026-08-12)
- [x] **Ülke seçimi: Türkiye** ✅ (2026-08-02)
- [x] **Advanced features doğrulaması** ✅ (2026-08-12) — kontrol edildi,
      Standart/Orta/Gelişmiş özelliklerin üçü de zaten Etkin, ek işlem
      gerekmedi
- [x] **Handle değişikliği: `@UnsolvedFilesShow`** ✅ (2026-08-12) —
      `Unsolved_Files_TV`, `UnsolvedFilesTV` denendi, ikisi de doluydu;
      `UnsolvedFilesShow` müsait çıktı, yayınlandı
- [x] **About linkleri + iletişim maili** ✅ (2026-08-12) — Instagram
      (instagram.com/unsolvedfiles007) + Facebook (facebook.com/
      unsolvedfiles007) linkleri, iletişim maili denizaydogdu@gmail.com

## 2️⃣ Lisans — ElevenLabs

- [x] **Karar verildi (2026-08-02):** Starter yerine yeni ücretsiz hesap
      açıldı (kullanıcı riskleri bilerek kabul etti). YPP/gelir öncesi
      tekrar gündeme getirilmeyecek — kaynak fiilen bitince tek hatırlatma.

## 3️⃣ KARAR — Kurgu mu, gerçek vakalar mı? ✅ VERİLDİ

- [x] **Seçenek A: Sadece gerçek vakalar** (2026-08-02) — Zodiac, Çağla
      Tuğaltay, Los Galindos hepsi bu kuralla üretildi: kesinleşmiş
      hüküm ya da 10+ yıllık faili meçhul, "reportedly/alleged" dili,
      yaşayan şüpheli ismi yok. StoryWriter promptunda kalıcı kural.
- [ ] Mara Vale (kurgu, 3 video, eski format) — başlıkları hâlâ gözden
      geçirilmedi; düşük öncelik (izlenmesi zaten YouTube'da devam ediyor)

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
- [x] **Meta API** ✅ (2026-08-02) — IG Reels + FB Reels canlı, ilk yayın yapıldı
- [~] **TikTok API** — app kuruldu, domain doğrulandı, sandbox'ta ilk
      gönderi başarılı (2026-08-03). Kalan: demo video çek → app review'a
      gönder (onay 1-4 hafta bekleniyor, henüz gönderilmedi)
- [ ] Multi-audio track araştırması (tek video + 6 dublaj kanalı — pipeline
      yeniden mimarisi gerektirir, veri toplandıktan sonra değerlendir)

## 8️⃣ TikTok — App review

- [x] Demo video çekildi (73sn ekran kaydı, 160MB→3.4MB sıkıştırıldı) ✅ (2026-08-11)
- [x] Production sekmesinde Products/Scopes/App review dolduruldu, video
      yüklendi, Submit for review yapıldı ✅ (2026-08-11) — "gönderildi,
      yakında dönüş yapılacak" onayı alındı. Bekleniyor: 1-4 hafta.
- [ ] **Onay gelince (ayrı oturum):** hesabı tekrar herkese açık yap
      (Özel Hesap'ı kapat) + config'te `tiktok.privacy.level=PUBLIC_TO_EVERYONE`
      + `tiktok.use.sandbox=false` + prod client key (`awwq0ly3g4sgjjng`)
      ile `tiktok-auth` tekrar koştur (prod token ayrı, henüz yok)

## 1️⃣1️⃣ Backoffice production deploy ✅ (2026-08-12)

- [x] **Backoffice DigitalOcean droplet'e (Velzon'un prod sunucusu,
      209.38.247.159) deploy edildi** — `https://shorts.velzon.tr`
      canlı, HTTPS (Let's Encrypt, certbot otomatik yenileme), Cloudflare
      proxied. Servis: `shorts-backoffice.service` (systemd, kullanıcı
      `shortsgen`, dizin `/opt/shorts-backoffice/`, port 8096 → nginx
      reverse proxy). `enabled` — reboot'ta otomatik kalkar.
- [x] Java 17 + ffmpeg sunucuya kuruldu, jar + `config/application.properties`
      + `google_credentials.json` + TikTok sandbox token + YouTube
      `StoredCredential` + kanal profilleri (`forensic-lab-en`,
      `truecrime-en`) + `assets/` (müzik) kopyalandı. `ffmpeg.path`/
      `ffprobe.path` Linux yollarına (`/usr/bin/*`) güncellendi.
      `backoffice.port=8096` eklendi.
- [x] Doğrulama: `/api/jobs` → `[]`, `/api/pinterest/batches` → `[]`,
      `/api/velzon/batches` → 503 (x.client.id boş, beklenen).
- [ ] **TEKNİK BORÇ — kimlik doğrulama yok:** kullanıcının açık talimatıyla
      auth eklenmedi ("login vb koyma ilerleyen zamanlarda teknik borç
      olarak ekleriz", 2026-08-11). Backoffice şu an URL'yi bilen herkese
      açık — TikTok/Pinterest/Velzon X'e gerçek içerik yayınlayabilir,
      OpenAI/ElevenLabs bütçesi tüketebilir. Cloudflare Access (email/Google
      login duvarı, kod değişikliği gerektirmez) en hızlı çözüm — ne zaman
      gündeme gelirse onunla başla.
- [ ] `output/pinterest/batch1` (zaten manuel yayınlanmış 10 pin) ve eski
      video job'ları sunucuya kopyalanmadı — dashboard bundan sonra üretilen
      yeni işleri gösterecek, geçmiş sıfırdan. İstenirse sonradan senkronize
      edilebilir.
- [ ] Yerel Mac'teki `serve` artık gereksiz — ileride sadece geliştirme/test
      için kullanılacak, gerçek üretim artık sunucuda.

## 🔟 Velzon X (Twitter) otomasyonu (2026-08-11 başladı, kullanıcının kendi şirketi)

- [x] **Kod + backoffice entegrasyonu tamamlandı** ✅ (commit 471f2c2, 163 test)
      — `XApiClient` (OAuth2 PKCE, Pinterest'ten farklı: X'in tek desteklediği
      grant type), `VelzonTweetGenerator` (Türkçe taslak üretimi, fintech
      güvenlik kuralları: KDV/vergi rakamı yok, kesin hukuki tavsiye yok,
      rakip adı yok), backoffice'te "Velzon X" bölümü (Pinterest ile aynı yerde)
- [ ] **Kullanıcı: developer.x.com'da uygulama kur** — ÖNEMLİ: mutlaka bir
      **Project** altında oluştur (standalone app'lerde Pay-Per-Use planında
      bilinen bir 403 hatası var), Permissions: Read and Write, callback:
      `https://videogenerator.velzon.tr/x-callback.html`, Pay-Per-Use'a
      kredi yükle (~$5 yeterli, tweet başı ~$0.015)
- [ ] Client ID + Secret gelince config'e ekle (`x.client.id`, `x.client.secret`)
      → `velzon-x-auth` ile tek seferlik yetkilendirme
- [ ] İlk gerçek tweet testi (backoffice'ten "Yayınla") — 403 hatası çıkarsa
      app'in Project'e bağlı olduğunu ve kredi bakiyesini kontrol et

## 9️⃣ Pinterest yan deneyi (2026-08-10 başladı, Unsolved Files'tan ayrı)

- [x] Pinterest işletme hesabı kuruldu (@denizaydogdu, marka adı "Small
      Space Living", AI-üretimi profil fotoğrafı + bio) ✅ (2026-08-11)
- [x] batch1'in 10 pini de "Small Space Living" panosuna yayınlandı ✅
      (2026-08-11) — her pinde "Mark as AI-Modified" işaretlendi (şeffaflık),
      profil herkese açık + aranabilir (Private/Search privacy kapalı doğrulandı)
- [x] **Backoffice'e Pinterest bölümü eklendi** ✅ (2026-08-11, commit a92ce4d,
      144 test) — parti listesi + tek tık "Yayınla" + "Yeni parti üret" hepsi
      aynı ekranda (video işleriyle yan yana), artık Pinterest.com'a gidip
      gelmeye gerek yok. Pinterest Developer app kuruldu (App id: 1599926,
      site: videogenerator.velzon.tr/pinterest-callback.html) ama **App
      secret "Trial access pending" — henüz alınamıyor**, o yüzden yayınlama
      butonu şimdilik çalışmaz (parti üretimi + liste görüntüleme çalışıyor,
      secret gelince `pinterest-auth` ile tek seferlik yetkilendirme yapılacak)
- [ ] **1 hafta bekle (~18 Ağustos), Pinterest Analytics'i kontrol et:**
      hangi format (öncesi/sonrası vs "en iyi 5" vs "N ürün") daha çok
      impression/save/outbound click alıyor
- [ ] Amazon Associates onayını takip et (kullanıcı başvurdu)
- [ ] Onay gelince: gerçek ürünleri SiteStripe ile seç, linkleri pinlere ekle
- [ ] Sıradaki parti: Pinterest Trends'teki gerçek arama terimlerine göre
      üret (kör GPT tahmini değil) — `pinterest-batch <count> <niş>` komutu hazır
- [ ] Sinyal olumluysa (gerçek tıklama/kayıt varsa) otomasyon değerlendir;
      olumsuzsa bırak — büyük yatırım yapılmadı, kayıp minimal

---
**Bitenler arşivi:** Plan 1-2-3 ✅ · kanal kimliği ✅ · vaka rotasyonu
(ABD→TR→ES) 3 platformda yayında ✅ · dil/kategori/hashtag/kitle backfill ✅ ·
madeForKids+limit+temizlik kodu ✅ · Meta entegrasyonu ✅ · TikTok sandbox ✅ ·
izlenme tablosu backoffice'te ✅ · Kanal Ayarları sayfası ✅ · fal.ai
değerlendirildi ve rafa kaldırıldı (2026-08-10, gelir sonrası B planı)
