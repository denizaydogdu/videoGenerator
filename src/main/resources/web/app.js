/* Shorts Fabrikası backoffice — vanilla JS, framework yok */
"use strict";

const state = {
  channel: null,   // seçili video kanalı (null = tümü)
  brand: null,     // "velzon" = Velzon alt-menüsü açık, null = kapalı
  status: "",      // seçili durum filtresi ("" = tümü)
  job: null,       // açık iş (detay görünümü)
  lang: null,      // seçili dil sekmesi
  view: "jobs",    // "jobs" | "pinterest" | "velzon" | "velzon-instagram" | "velzon-youtube" | "velzon-tiktok"
};

const $ = (id) => document.getElementById(id);

// Velzon'un platformları — "Velzon" seçilince DOM'da hemen altına (aynı
// channel-list içine, Velzon'un hemen ardına) bu liste render edilir.
// Hiçbir platforma OTOMATİK geçilmez — X henüz Developer Portal kurulumu
// bekliyor, otomatik ona geçmek sürpriz "not configured" hatası veriyordu.
const VELZON_PLATFORMS = [
  { view: "velzon", label: "X (Twitter)",
    show: () => { showVelzonView(); loadVelzonBatches(); } },
  { view: "velzon-instagram", label: "Instagram",
    show: () => { showVelzonInstagramView(); loadVelzonInstagramBatches(); } },
  { view: "velzon-youtube", label: "YouTube",
    show: () => { showVelzonYoutubeView(); loadVelzonYoutubeBatches(); } },
  { view: "velzon-tiktok", label: "TikTok",
    show: () => { showVelzonTiktokView(); loadVelzonTiktokBatches(); } },
];

// ---------- API ----------
async function api(path, options = {}) {
  if (options.body) {
    options.headers = { "Content-Type": "application/json", ...(options.headers || {}) };
  }
  const res = await fetch(path, options);
  if (res.status === 204 || res.status === 202) return null;
  const body = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(body.error || ("HTTP " + res.status));
  return body;
}

// ---------- Toast ----------
let toastTimer;
function toast(message, isError = false) {
  const el = $("toast");
  el.textContent = message;
  el.className = "toast" + (isError ? " error" : "");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.add("hidden"), 3200);
}

// ---------- Sidebar ----------
/**
 * channel-list'i state.channels (önceden çekilmiş veri) + state'e göre
 * yeniden çizer — network isteği YAPMAZ. Velzon'un alt-menüsü (X/Instagram/
 * YouTube) Velzon li'sinin HEMEN ARDINA, aynı liste içine eklenir — ayrı
 * bir <ul> olarak DOM'un sonunda durmaz, doğru sırada görünür.
 */
function renderChannelList() {
  const channels = state.channels || [];
  const list = $("channel-list");
  list.innerHTML = "";

  const all = document.createElement("li");
  all.textContent = "Tüm kanallar";
  all.className = (state.channel === null && !state.brand && state.view === "jobs") ? "active" : "";
  all.onclick = () => { state.channel = null; state.brand = null; showList(); refresh(); };
  list.appendChild(all);

  for (const ch of channels) {
    const li = document.createElement("li");
    li.className = (state.channel === ch.channelId && !state.brand) ? "active" : "";
    const name = document.createElement("span");
    name.textContent = ch.displayName;
    li.appendChild(name);
    if (ch.pendingCount > 0) {
      const badge = document.createElement("span");
      badge.className = "badge";
      badge.textContent = ch.pendingCount;
      li.appendChild(badge);
    }
    li.onclick = () => { state.channel = ch.channelId; state.brand = null; showList(); refresh(); };
    list.appendChild(li);
  }

  // Velzon: marka girişi — tıklanınca (toggle) hemen altına X/Instagram/
  // YouTube açılır. Hiçbir platforma otomatik geçilmez, kullanıcı seçer.
  const velzonLi = document.createElement("li");
  velzonLi.textContent = "Velzon";
  velzonLi.className = state.brand === "velzon" ? "active" : "";
  velzonLi.onclick = () => {
    state.channel = null;
    if (state.brand === "velzon") {
      state.brand = null;
      showList();
      refresh();
      return;
    }
    state.brand = "velzon";
    showVelzonBrandView();
    renderChannelList();
  };
  list.appendChild(velzonLi);

  if (state.brand === "velzon") {
    for (const p of VELZON_PLATFORMS) {
      const li = document.createElement("li");
      li.textContent = p.label;
      li.className = "sub-item" + (state.view === p.view ? " active" : "");
      li.onclick = () => { p.show(); renderChannelList(); };
      list.appendChild(li);
    }
  }

  // Small Space Living: kişisel Pinterest hesabı (@denizaydogdu) —
  // Unsolved Files/Velzon'dan bağımsız yan proje. "Pinterest" jenerik
  // platform adı yerine gerçek marka adı kullanılır.
  const sslLi = document.createElement("li");
  sslLi.textContent = "Small Space Living";
  sslLi.className = (state.view === "pinterest" && !state.brand) ? "active" : "";
  sslLi.onclick = () => {
    state.channel = null;
    state.brand = null;
    showPinterestView();
    loadPinterestBatches();
    renderChannelList();
  };
  list.appendChild(sslLi);

  // Üretim dialogundaki kanal seçimi (yalnız gerçek video kanalları)
  const sel = $("gen-channel");
  sel.innerHTML = "";
  for (const ch of channels) {
    const opt = document.createElement("option");
    opt.value = ch.channelId;
    opt.textContent = ch.displayName;
    sel.appendChild(opt);
  }
}

async function loadChannels() {
  state.channels = await api("/api/channels");
  renderChannelList();
}

async function loadStats() {
  const s = await api("/api/stats");
  $("budget-text").textContent =
    `$${s.spentThisMonth.toFixed(2)} / $${s.monthlyBudget.toFixed(0)}`;
  const pct = Math.min(100, (s.spentThisMonth / s.monthlyBudget) * 100);
  const fill = $("budget-fill");
  fill.style.width = pct + "%";
  fill.className = "budget-fill" +
    (pct >= 100 ? " over" : pct >= 80 ? " warn" : "");
}

// ---------- Job list ----------
function fmtDuration(sec) {
  if (!sec) return "";
  const m = Math.floor(sec / 60), s = Math.round(sec % 60);
  return `${m}:${String(s).padStart(2, "0")}`;
}

async function loadJobs() {
  const params = new URLSearchParams();
  if (state.channel) params.set("channel", state.channel);
  if (state.status) params.set("status", state.status);
  const jobs = await api("/api/jobs?" + params);
  const grid = $("job-grid");
  grid.innerHTML = "";
  if (jobs.length === 0) {
    grid.innerHTML = '<div class="empty">Bu filtrede iş yok. "+ Üret" ile başlat.</div>';
    return;
  }
  for (const j of jobs) {
    const card = document.createElement("div");
    card.className = "card";
    const thumb = document.createElement("img");
    thumb.className = "card-thumb";
    thumb.loading = "lazy";
    thumb.src = `/api/jobs/${j.jobId}/scene/1`;
    thumb.onerror = () => { thumb.removeAttribute("src"); };
    const body = document.createElement("div");
    body.className = "card-body";
    const title = document.createElement("div");
    title.className = "card-title";
    title.textContent = j.title || j.jobId;
    const meta = document.createElement("div");
    meta.className = "card-meta";
    const pill = document.createElement("span");
    pill.className = "status-pill status-" + j.status;
    pill.textContent = j.status;
    meta.appendChild(pill);
    for (const lang of j.langs || []) {
      const chip = document.createElement("span");
      chip.className = "chip";
      chip.textContent = lang;
      meta.appendChild(chip);
    }
    const info = document.createElement("span");
    info.textContent =
      [fmtDuration(j.durationSeconds), j.costTotal ? "$" + j.costTotal.toFixed(2) : ""]
        .filter(Boolean).join(" · ");
    meta.appendChild(info);
    body.append(title, meta);
    card.append(thumb, body);
    card.onclick = () => openDetail(j.jobId);
    grid.appendChild(card);
  }
}

// ---------- Detail ----------
let detailRequestSeq = 0; // hızlı ardışık tıklamalarda eski yanıtı at

const PLATFORM_ICONS = { YOUTUBE: "▶", INSTAGRAM: "📷", FACEBOOK: "ⓕ" };

// ===== Kanal Ayarları =====
let settingsChannelId = null;

async function openChannelSettings(channelId) {
  try {
    const p = await api(`/api/channels/${channelId}`);
    settingsChannelId = channelId;
    $("ch-title").textContent = p.displayName || channelId;
    $("ch-name").value = p.displayName || "";
    $("ch-langs").value = (p.languages || []).join(", ");
    $("ch-duration").value = p.targetDurationSeconds;
    $("ch-scenes").value = p.sceneCount;
    $("ch-voice").value = p.voiceId || "";
    $("ch-style").value = p.stylePrefix || "";
    $("ch-enabled").checked = !!p.enabled;
    const plats = p.platforms || [];
    $("ch-platforms").querySelectorAll("input").forEach((i) => {
      i.checked = plats.includes(i.value);
    });
    $("ch-metalang").value = p.meta?.publishLang ?? "en";
    $("ch-metatoken").value = "";
    $("ch-metapage").value = p.meta?.pageId || "";
    $("ch-metaig").value = p.meta?.igUserId || "";
    $("dlg-channel").showModal();
  } catch (e) {
    toast(e.message, true);
  }
}

async function saveChannelSettings() {
  const meta = { publishLang: $("ch-metalang").value };
  if ($("ch-metatoken").value.trim()) meta.accessToken = $("ch-metatoken").value.trim();
  if ($("ch-metapage").value.trim()) meta.pageId = $("ch-metapage").value.trim();
  if ($("ch-metaig").value.trim()) meta.igUserId = $("ch-metaig").value.trim();
  const patch = {
    displayName: $("ch-name").value.trim(),
    languages: $("ch-langs").value.split(",").map((s) => s.trim()).filter(Boolean),
    targetDurationSeconds: Number($("ch-duration").value),
    sceneCount: Number($("ch-scenes").value),
    voiceId: $("ch-voice").value.trim(),
    stylePrefix: $("ch-style").value.trim(),
    enabled: $("ch-enabled").checked,
    platforms: [...$("ch-platforms").querySelectorAll("input:checked")].map((i) => i.value),
    meta,
  };
  try {
    await api(`/api/channels/${settingsChannelId}`, {
      method: "PATCH",
      body: JSON.stringify(patch),
    });
    toast("Kanal ayarları kaydedildi");
    refresh();
  } catch (e) {
    toast(e.message, true);
  }
}

async function loadJobStats() {
  const box = $("stats-box");
  box.textContent = "Yükleniyor…";
  try {
    const rows = await api(`/api/jobs/${state.job.jobId}/stats`);
    if (!rows.length) {
      box.textContent = "Yayınlanmış varyant yok";
      return;
    }
    // Pivot: satır = dil, sütun = platform
    const platforms = [...new Set(rows.map((r) => r.platform))];
    const langs = [...new Set(rows.map((r) => r.lang))];
    const cell = (lang, platform) =>
      rows.find((r) => r.lang === lang && r.platform === platform);
    box.innerHTML = "";
    const table = document.createElement("table");
    table.className = "stats-table";
    const head = document.createElement("tr");
    head.innerHTML = "<th></th>" + platforms.map((p) =>
      `<th>${PLATFORM_ICONS[p] || ""} ${p.charAt(0) + p.slice(1).toLowerCase()}</th>`).join("");
    table.appendChild(head);
    for (const lang of langs) {
      const tr = document.createElement("tr");
      let html = `<td>${lang}</td>`;
      for (const p of platforms) {
        const r = cell(lang, p);
        const views = !r || r.views == null ? "—" : r.views.toLocaleString("tr-TR");
        html += `<td class="stats-views${r ? " stats-link" : ""}" ` +
          (r ? `data-url="${r.url}"` : "") + `>${views}</td>`;
      }
      tr.innerHTML = html;
      table.appendChild(tr);
    }
    table.querySelectorAll(".stats-link").forEach((td) => {
      td.onclick = () => window.open(td.dataset.url, "_blank");
    });
    box.appendChild(table);
  } catch (e) {
    box.textContent = e.message;
  }
}

async function openDetail(jobId) {
  const seq = ++detailRequestSeq;
  const job = await api("/api/jobs/" + jobId);
  if (seq !== detailRequestSeq) return; // daha yeni bir istek kazandı
  state.job = job;
  state.lang = state.job.variants?.[0]?.lang || null;
  $("job-grid").classList.add("hidden");
  $("job-detail").classList.remove("hidden");
  $("page-title").textContent = state.job.story?.title || jobId;
  renderDetail();
  loadJobStats().catch(() => {}); // detay açılınca izlenmeler otomatik gelsin
}

/** Tüm ana içerik görünümlerini gizler — her show*View() bunu çağırıp
 * kendi görünümünü açar, böylece yeni bir görünüm eklenince tek yerden
 * yönetilir ve eski bir görünümün "unutulup" ekranda kalması imkansızlaşır. */
function hideAllViews() {
  $("job-grid").classList.add("hidden");
  $("job-detail").classList.add("hidden");
  $("pinterest-view").classList.add("hidden");
  $("velzon-brand-view").classList.add("hidden");
  $("velzon-view").classList.add("hidden");
  $("velzon-instagram-view").classList.add("hidden");
  $("velzon-youtube-view").classList.add("hidden");
  $("velzon-tiktok-view").classList.add("hidden");
}

function showList() {
  // Detay panelini kapat (yenileme YAPMADAN) — kenar menü gezinmeleri
  // kendi refresh'ini çağırır, çift istek olmasın
  state.job = null;
  state.view = "jobs";
  // Vurgu: renderChannelList() (refresh() üzerinden) state.view'a göre kendi hesaplar
  hideAllViews();
  $("job-grid").classList.remove("hidden");
  $("page-title").textContent = "İşler";
  $("player").pause?.();
}

/** "Velzon" marka girişine tıklanınca (alt-menü açılırken) gösterilir —
 * hiçbir platforma otomatik geçilmez, kullanıcı X/Instagram/YouTube'dan
 * birini seçmeden önce boş bir yönlendirme ekranı görür. */
function showVelzonBrandView() {
  state.job = null;
  state.view = "velzon-brand";
  hideAllViews();
  $("velzon-brand-view").classList.remove("hidden");
  $("page-title").textContent = "Velzon";
  $("player").pause?.();
}

function closeDetail() {
  showList();
  refresh();
}

function currentVariant() {
  return state.job?.variants?.find((v) => v.lang === state.lang) || null;
}

function renderDetail() {
  const job = state.job;
  // Dil sekmeleri
  const tabs = $("lang-tabs");
  tabs.innerHTML = "";
  for (const v of job.variants || []) {
    const b = document.createElement("button");
    b.className = "tab" + (v.lang === state.lang ? " active" : "");
    b.textContent = v.lang;
    b.onclick = () => { state.lang = v.lang; renderDetail(); };
    tabs.appendChild(b);
  }
  // Oynatıcı
  const player = $("player");
  if (state.lang) {
    player.src = `/api/jobs/${job.jobId}/render/${state.lang}`;
  } else {
    player.removeAttribute("src");
  }
  // Sahne şeridi
  const strip = $("scene-strip");
  strip.innerHTML = "";
  const sceneCount = job.story?.scenes?.length || 0;
  for (let i = 1; i <= sceneCount; i++) {
    const img = document.createElement("img");
    img.loading = "lazy";
    img.src = `/api/jobs/${job.jobId}/scene/${i}`;
    strip.appendChild(img);
  }
  // Metadata formu
  const v = currentVariant();
  $("f-title").value = v?.metadata?.title || "";
  $("f-description").value = v?.metadata?.description || "";
  $("f-hashtags").value = (v?.metadata?.hashtags || []).join(", ");
  // Aksiyon durumu
  const reviewable = job.status === "PENDING_REVIEW";
  $("btn-approve").disabled = !reviewable;
  $("btn-reject").disabled = !reviewable;
  $("btn-save-meta").disabled = !reviewable;
  $("detail-info").textContent =
    `${job.jobId} · ${job.status} · maliyet $${(job.cost ? (job.cost.images + job.cost.tts + job.cost.music + job.cost.llm) : 0).toFixed(2)}` +
    (job.error ? ` · HATA: ${job.error}` : "");
}

async function saveMetadata() {
  const v = currentVariant();
  if (!v) return;
  const metadata = {
    title: $("f-title").value.trim(),
    description: $("f-description").value.trim(),
    hashtags: $("f-hashtags").value.split(",").map((h) => h.trim()).filter(Boolean),
  };
  try {
    await api(`/api/jobs/${state.job.jobId}/variants/${state.lang}`, {
      method: "PATCH",
      body: JSON.stringify(metadata),
    });
    v.metadata = metadata;
    toast("Metadata kaydedildi");
  } catch (e) {
    toast(e.message, true);
  }
}

async function approve() {
  const btn = $("btn-approve");
  if (btn.disabled) return;
  btn.disabled = true; // çift tıklama = çift POST önlemi
  const platforms = [...$("platform-checks").querySelectorAll("input:checked")]
    .map((i) => i.value);
  try {
    await api(`/api/jobs/${state.job.jobId}/approve`, {
      method: "POST",
      body: JSON.stringify({ platforms }),
    });
    toast("Onaylandı — yayın kuyruğunda");
    closeDetail();
  } catch (e) {
    toast(e.message, true);
    btn.disabled = false;
  }
}

async function reject() {
  const btn = $("btn-reject");
  if (btn.disabled) return;
  btn.disabled = true;
  try {
    await api(`/api/jobs/${state.job.jobId}/reject`, { method: "POST" });
    toast("Reddedildi");
    closeDetail();
  } catch (e) {
    toast(e.message, true);
    btn.disabled = false;
  }
}

/** Backend'in "not configured" (503, henüz kurulum bekleniyor — ör. X
 * Developer Portal) yanıtını sakin bir bilgi kutusuyla gösterir; gerçek
 * hatalarda (network, sunucu hatası) mevcut kırmızı toast'a düşer. */
function renderLoadError(container, e) {
  if (/not configured/i.test(e.message)) {
    container.innerHTML = '<div class="empty">Bu platform henüz yapılandırılmadı — kurulum tamamlanınca burada görünecek.</div>';
  } else {
    container.innerHTML = "";
    toast(e.message, true);
  }
}

// ---------- Ortak post/pin/tweet detay penceresi ----------
// Pinterest/Velzon X/Instagram/YouTube kartlarının hepsi bu tek modalı
// kullanır — büyük görsel + tam (kesilmemiş) metin + yayınla/link.
function openPostDetail({ imageUrl, title, text, meta, published, url, onPublish, publishingLabel }) {
  const dlg = $("dlg-post-detail");
  const img = $("post-detail-image");
  if (imageUrl) {
    img.src = imageUrl;
    img.classList.remove("hidden");
  } else {
    img.classList.add("hidden");
  }
  const titleEl = $("post-detail-title");
  titleEl.textContent = title || "";
  titleEl.style.display = title ? "" : "none";
  $("post-detail-text").textContent = text || "";
  const metaEl = $("post-detail-meta");
  metaEl.textContent = meta || "";
  metaEl.style.display = meta ? "" : "none";

  const linkEl = $("post-detail-link");
  const publishBtn = $("post-detail-publish");
  if (published) {
    publishBtn.classList.add("hidden");
    if (url) {
      linkEl.href = url;
      linkEl.classList.remove("hidden");
    } else {
      linkEl.classList.add("hidden");
    }
  } else {
    linkEl.classList.add("hidden");
    publishBtn.classList.remove("hidden");
    publishBtn.disabled = false;
    publishBtn.textContent = "Yayınla";
    publishBtn.onclick = async () => {
      publishBtn.disabled = true;
      publishBtn.textContent = publishingLabel || "Yayınlanıyor…";
      try {
        await onPublish();
        dlg.close();
      } catch (e) {
        toast(e.message, true);
        publishBtn.disabled = false;
        publishBtn.textContent = "Yayınla";
      }
    };
  }
  dlg.showModal();
}

// ---------- Generate ----------
function openGenerateDialog() {
  const dlg = $("dlg-generate");
  dlg.returnValue = ""; // önceki "ok" değeri sonraki iptali tetiklemesin
  dlg.showModal();
}

async function submitGenerate() {
  const channelId = $("gen-channel").value;
  if (!channelId) return;
  try {
    await api("/api/jobs/generate", {
      method: "POST",
      body: JSON.stringify({ channelId }),
    });
    toast(`Üretim kuyruğa alındı: ${channelId}`);
    setTimeout(refresh, 1500);
  } catch (e) {
    toast(e.message, true);
  }
}

// ---------- Pinterest ----------
function showPinterestView() {
  state.job = null;
  state.view = "pinterest";
  hideAllViews();
  $("pinterest-view").classList.remove("hidden");
  $("page-title").textContent = "Small Space Living";
  $("player").pause?.();
}

async function loadPinterestBatches() {
  const container = $("pinterest-batches");
  try {
    const batches = await api("/api/pinterest/batches");
    container.innerHTML = "";
    if (!batches.length) {
      container.innerHTML = '<div class="empty">Henüz parti yok. "+ Yeni parti üret" ile başlat.</div>';
      return;
    }
    for (const batch of batches) {
      const section = document.createElement("div");
      section.className = "pinterest-batch";
      const heading = document.createElement("div");
      heading.className = "pinterest-batch-title";
      const publishedCount = batch.pins.filter((p) => p.published).length;
      heading.textContent = `${batch.id} — ${publishedCount}/${batch.pins.length} yayında`;
      section.appendChild(heading);

      const grid = document.createElement("div");
      grid.className = "pinterest-pin-grid";
      batch.pins.forEach((pin, index) => {
        const card = document.createElement("div");
        card.className = "pinterest-pin-card";
        const img = document.createElement("img");
        img.loading = "lazy";
        img.src = `/api/pinterest/batches/${batch.id}/images/${pin.file}`;
        const title = document.createElement("div");
        title.className = "pinterest-pin-title";
        title.textContent = pin.title;
        const desc = document.createElement("div");
        desc.className = "pinterest-pin-desc";
        desc.textContent = pin.description;
        card.append(img, title, desc);

        const doPublish = async () => {
          await api(`/api/pinterest/batches/${batch.id}/pins/${index}/publish`, {
            method: "POST",
          });
          toast("Pin yayınlandı");
          loadPinterestBatches();
        };

        if (pin.published && pin.url) {
          const link = document.createElement("a");
          link.className = "btn btn-secondary btn-small";
          link.textContent = "Pinterest'te gör ↗";
          link.href = pin.url;
          link.target = "_blank";
          link.onclick = (e) => e.stopPropagation();
          card.appendChild(link);
        } else if (pin.published) {
          const badge = document.createElement("span");
          badge.className = "chip";
          badge.textContent = "Yayında";
          card.appendChild(badge);
        } else {
          const btn = document.createElement("button");
          btn.className = "btn btn-primary btn-small";
          btn.textContent = "Yayınla";
          btn.onclick = async (e) => {
            e.stopPropagation();
            btn.disabled = true;
            btn.textContent = "Yayınlanıyor…";
            try {
              await doPublish();
            } catch (err) {
              toast(err.message, true);
              btn.disabled = false;
              btn.textContent = "Yayınla";
            }
          };
          card.appendChild(btn);
        }

        card.onclick = () => openPostDetail({
          imageUrl: img.src,
          title: pin.title,
          text: pin.description,
          published: pin.published,
          url: pin.url,
          onPublish: doPublish,
        });
        grid.appendChild(card);
      });
      section.appendChild(grid);
      container.appendChild(section);
    }
  } catch (e) {
    renderLoadError(container, e);
  }
}

function openPinterestGenerateDialog() {
  const dlg = $("dlg-pinterest-generate");
  dlg.returnValue = "";
  dlg.showModal();
}

async function submitPinterestGenerate() {
  const niche = $("pin-niche").value.trim();
  const count = Number($("pin-count").value) || 10;
  if (!niche) return;
  try {
    await api("/api/pinterest/generate", {
      method: "POST",
      body: JSON.stringify({ niche, count }),
    });
    toast(`Parti kuyruğa alındı (${count} pin) — birkaç dakika sürebilir, sonra Yenile'ye bas`);
  } catch (e) {
    toast(e.message, true);
  }
}

// ---------- Velzon Knowledge Base (paylaşılan makale seçici) ----------
// X/Instagram/YouTube üç ayrı diyalog kullanır ama aynı makale listesini
// paylaşır — tek fetch, tek in-memory title->path map yeterli.
let velzonArticlesPromise = null;
let velzonArticleTitleToPath = new Map();

async function loadVelzonArticles() {
  if (!velzonArticlesPromise) {
    velzonArticlesPromise = api("/api/velzon-knowledge-base/articles")
      .then((articles) => {
        velzonArticleTitleToPath = new Map(articles.map((a) => [a.title, a.path]));
        return articles;
      })
      .catch((e) => {
        velzonArticlesPromise = null; // sonraki açılışta tekrar denensin
        throw e;
      });
  }
  return velzonArticlesPromise;
}

function populateVelzonArticleDatalist(datalistId, articles) {
  const dl = $(datalistId);
  dl.innerHTML = "";
  for (const a of articles) {
    const opt = document.createElement("option");
    opt.value = a.title;
    dl.appendChild(opt);
  }
}

/** Diyalogdaki input'a yazılan başlığı gerçek makale yoluna çözer; eşleşme yoksa null. */
function resolveVelzonArticlePath(typedTitle) {
  return velzonArticleTitleToPath.get((typedTitle || "").trim()) || null;
}

// ---------- Velzon X ----------
function showVelzonView() {
  state.job = null;
  state.view = "velzon";
  hideAllViews();
  $("velzon-view").classList.remove("hidden");
  $("page-title").textContent = "Velzon X";
  $("player").pause?.();
}

async function loadVelzonBatches() {
  const container = $("velzon-batches");
  try {
    const batches = await api("/api/velzon/batches");
    container.innerHTML = "";
    if (!batches.length) {
      container.innerHTML = '<div class="empty">Henüz parti yok. "+ Yeni parti üret" ile başlat.</div>';
      return;
    }
    for (const batch of batches) {
      const section = document.createElement("div");
      section.className = "pinterest-batch";
      const heading = document.createElement("div");
      heading.className = "pinterest-batch-title";
      const publishedCount = batch.tweets.filter((t) => t.published).length;
      heading.textContent = `${batch.id} — ${publishedCount}/${batch.tweets.length} yayında`;
      section.appendChild(heading);

      const grid = document.createElement("div");
      grid.className = "velzon-tweet-list";
      batch.tweets.forEach((tw, index) => {
        const card = document.createElement("div");
        card.className = "velzon-tweet-card";
        const text = document.createElement("div");
        text.className = "velzon-tweet-text";
        text.textContent = tw.text;
        const meta = document.createElement("div");
        meta.className = "velzon-tweet-meta";
        meta.textContent = `${tw.topic} · ${tw.text.length}/280`;
        card.append(text, meta);

        const doPublish = async () => {
          await api(`/api/velzon/batches/${batch.id}/tweets/${index}/publish`, {
            method: "POST",
          });
          toast("Tweet yayınlandı");
          loadVelzonBatches();
        };

        if (tw.published && tw.url) {
          const link = document.createElement("a");
          link.className = "btn btn-secondary btn-small";
          link.textContent = "X'te gör ↗";
          link.href = tw.url;
          link.target = "_blank";
          link.onclick = (e) => e.stopPropagation();
          card.appendChild(link);
        } else if (tw.published) {
          const badge = document.createElement("span");
          badge.className = "chip";
          badge.textContent = "Yayında";
          card.appendChild(badge);
        } else {
          const btn = document.createElement("button");
          btn.className = "btn btn-primary btn-small";
          btn.textContent = "Yayınla";
          btn.onclick = async (e) => {
            e.stopPropagation();
            btn.disabled = true;
            btn.textContent = "Yayınlanıyor…";
            try {
              await doPublish();
            } catch (err) {
              toast(err.message, true);
              btn.disabled = false;
              btn.textContent = "Yayınla";
            }
          };
          card.appendChild(btn);
        }

        card.onclick = () => openPostDetail({
          text: tw.text,
          meta: `${tw.topic} · ${tw.text.length}/280`,
          published: tw.published,
          url: tw.url,
          onPublish: doPublish,
        });
        grid.appendChild(card);
      });
      section.appendChild(grid);
      container.appendChild(section);
    }
  } catch (e) {
    renderLoadError(container, e);
  }
}

function openVelzonGenerateDialog() {
  const dlg = $("dlg-velzon-generate");
  dlg.returnValue = "";
  loadVelzonArticles()
    .then((articles) => populateVelzonArticleDatalist("velzon-article-list", articles))
    .catch((e) => toast(e.message, true));
  dlg.showModal();
}

async function submitVelzonGenerate() {
  const articleTitle = $("velzon-article").value.trim();
  const count = Number($("velzon-count").value) || 5;
  if (!articleTitle) return;
  const articlePath = resolveVelzonArticlePath(articleTitle);
  if (!articlePath) {
    toast("Listeden gerçek bir makale seçmelisiniz", true);
    return;
  }
  try {
    await api("/api/velzon/generate", {
      method: "POST",
      body: JSON.stringify({ articlePath, count }),
    });
    toast(`Parti kuyruğa alındı (${count} tweet) — birkaç dakika sürebilir, sonra Yenile'ye bas`);
  } catch (e) {
    toast(e.message, true);
  }
}

// ---------- Velzon Instagram ----------
function showVelzonInstagramView() {
  state.job = null;
  state.view = "velzon-instagram";
  hideAllViews();
  $("velzon-instagram-view").classList.remove("hidden");
  $("page-title").textContent = "Velzon Instagram";
  $("player").pause?.();
}

async function loadVelzonInstagramBatches() {
  const container = $("velzon-instagram-batches");
  try {
    const batches = await api("/api/velzon-instagram/batches");
    container.innerHTML = "";
    if (!batches.length) {
      container.innerHTML = '<div class="empty">Henüz parti yok. "+ Yeni parti üret" ile başlat.</div>';
      return;
    }
    for (const batch of batches) {
      const section = document.createElement("div");
      section.className = "pinterest-batch";
      const heading = document.createElement("div");
      heading.className = "pinterest-batch-title";
      const publishedCount = batch.posts.filter((p) => p.published).length;
      heading.textContent = `${batch.id} — ${publishedCount}/${batch.posts.length} yayında`;
      section.appendChild(heading);

      const grid = document.createElement("div");
      grid.className = "pinterest-pin-grid";
      batch.posts.forEach((post, index) => {
        const card = document.createElement("div");
        card.className = "pinterest-pin-card";
        const img = document.createElement("img");
        img.loading = "lazy";
        img.src = `/api/velzon-instagram/batches/${batch.id}/images/${post.file}`;
        const desc = document.createElement("div");
        desc.className = "pinterest-pin-desc";
        desc.textContent = post.caption;
        card.append(img, desc);

        const doPublish = async () => {
          await api(`/api/velzon-instagram/batches/${batch.id}/posts/${index}/publish`, {
            method: "POST",
          });
          toast("Gönderi yayınlandı");
          loadVelzonInstagramBatches();
        };

        if (post.published && post.url) {
          const link = document.createElement("a");
          link.className = "btn btn-secondary btn-small";
          link.textContent = "Instagram'da gör ↗";
          link.href = post.url;
          link.target = "_blank";
          link.onclick = (e) => e.stopPropagation();
          card.appendChild(link);
        } else if (post.published) {
          const badge = document.createElement("span");
          badge.className = "chip";
          badge.textContent = "Yayında";
          card.appendChild(badge);
        } else {
          const btn = document.createElement("button");
          btn.className = "btn btn-primary btn-small";
          btn.textContent = "Yayınla";
          btn.onclick = async (e) => {
            e.stopPropagation();
            btn.disabled = true;
            btn.textContent = "Yayınlanıyor…";
            try {
              await doPublish();
            } catch (err) {
              toast(err.message, true);
              btn.disabled = false;
              btn.textContent = "Yayınla";
            }
          };
          card.appendChild(btn);
        }

        card.onclick = () => openPostDetail({
          imageUrl: img.src,
          text: post.caption,
          published: post.published,
          url: post.url,
          onPublish: doPublish,
        });
        grid.appendChild(card);
      });
      section.appendChild(grid);
      container.appendChild(section);
    }
  } catch (e) {
    renderLoadError(container, e);
  }
}

function openVelzonInstagramGenerateDialog() {
  const dlg = $("dlg-velzon-instagram-generate");
  dlg.returnValue = "";
  loadVelzonArticles()
    .then((articles) => populateVelzonArticleDatalist("velzon-instagram-article-list", articles))
    .catch((e) => toast(e.message, true));
  dlg.showModal();
}

async function submitVelzonInstagramGenerate() {
  const articleTitle = $("velzon-instagram-article").value.trim();
  const count = Number($("velzon-instagram-count").value) || 5;
  if (!articleTitle) return;
  const articlePath = resolveVelzonArticlePath(articleTitle);
  if (!articlePath) {
    toast("Listeden gerçek bir makale seçmelisiniz", true);
    return;
  }
  try {
    await api("/api/velzon-instagram/generate", {
      method: "POST",
      body: JSON.stringify({ articlePath, count }),
    });
    toast(`Parti kuyruğa alındı (${count} gönderi) — birkaç dakika sürebilir, sonra Yenile'ye bas`);
  } catch (e) {
    toast(e.message, true);
  }
}

// ---------- Velzon YouTube ----------
function showVelzonYoutubeView() {
  state.job = null;
  state.view = "velzon-youtube";
  hideAllViews();
  $("velzon-youtube-view").classList.remove("hidden");
  $("page-title").textContent = "Velzon YouTube";
  $("player").pause?.();
}

async function loadVelzonYoutubeBatches() {
  const container = $("velzon-youtube-batches");
  try {
    const batches = await api("/api/velzon-youtube/batches");
    container.innerHTML = "";
    if (!batches.length) {
      container.innerHTML = '<div class="empty">Henüz parti yok. "+ Yeni parti üret" ile başlat.</div>';
      return;
    }
    for (const batch of batches) {
      const section = document.createElement("div");
      section.className = "pinterest-batch";
      const heading = document.createElement("div");
      heading.className = "pinterest-batch-title";
      const publishedCount = batch.scripts.filter((s) => s.published).length;
      heading.textContent = `${batch.id} — ${publishedCount}/${batch.scripts.length} yayında`;
      section.appendChild(heading);

      const grid = document.createElement("div");
      grid.className = "velzon-tweet-list";
      batch.scripts.forEach((s, index) => {
        const card = document.createElement("div");
        card.className = "velzon-tweet-card";
        const title = document.createElement("div");
        title.className = "pinterest-pin-title";
        title.textContent = s.title;
        const narration = document.createElement("div");
        narration.className = "velzon-tweet-text";
        narration.textContent = s.narration;
        const meta = document.createElement("div");
        meta.className = "velzon-tweet-meta";
        meta.textContent = (s.hashtags || []).join(" ");
        card.append(title, narration, meta);

        const doPublish = async () => {
          await api(`/api/velzon-youtube/batches/${batch.id}/scripts/${index}/publish`, {
            method: "POST",
          });
          toast("Video yayınlandı");
          loadVelzonYoutubeBatches();
        };

        if (s.published && s.url) {
          const link = document.createElement("a");
          link.className = "btn btn-secondary btn-small";
          link.textContent = "YouTube'da gör ↗";
          link.href = s.url;
          link.target = "_blank";
          link.onclick = (e) => e.stopPropagation();
          card.appendChild(link);
        } else if (s.published) {
          const badge = document.createElement("span");
          badge.className = "chip";
          badge.textContent = "Yayında";
          card.appendChild(badge);
        } else {
          const btn = document.createElement("button");
          btn.className = "btn btn-primary btn-small";
          btn.textContent = "Yayınla";
          btn.onclick = async (e) => {
            e.stopPropagation();
            btn.disabled = true;
            // Görsel + seslendirme + render + upload burada gerçekleşir —
            // Pinterest/Instagram'ın anlık yayınından çok daha uzun sürebilir
            btn.textContent = "Video oluşturuluyor ve yükleniyor…";
            try {
              await doPublish();
            } catch (err) {
              toast(err.message, true);
              btn.disabled = false;
              btn.textContent = "Yayınla";
            }
          };
          card.appendChild(btn);
        }

        card.onclick = () => openPostDetail({
          title: s.title,
          text: s.narration,
          meta: (s.hashtags || []).join(" "),
          published: s.published,
          url: s.url,
          onPublish: doPublish,
          publishingLabel: "Video oluşturuluyor ve yükleniyor…",
        });
        grid.appendChild(card);
      });
      section.appendChild(grid);
      container.appendChild(section);
    }
  } catch (e) {
    renderLoadError(container, e);
  }
}

// ---------- Velzon TikTok ----------
// "+ Yeni parti üret" YOK — bu sekme yeni içerik üretmez, Velzon YouTube
// batch'inin zaten render ettiği videoyu ikinci bir hesaba (@velzon_tr)
// postlar (bkz. VelzonTiktokPublishService javadoc'u).
function showVelzonTiktokView() {
  state.job = null;
  state.view = "velzon-tiktok";
  hideAllViews();
  $("velzon-tiktok-view").classList.remove("hidden");
  $("page-title").textContent = "Velzon TikTok";
  $("player").pause?.();
}

async function loadVelzonTiktokBatches() {
  const container = $("velzon-tiktok-batches");
  try {
    const batches = await api("/api/velzon-tiktok/batches");
    container.innerHTML = "";
    if (!batches.length) {
      container.innerHTML = '<div class="empty">Henüz parti yok — önce Velzon YouTube\'da bir video yayınla.</div>';
      return;
    }
    for (const batch of batches) {
      const section = document.createElement("div");
      section.className = "pinterest-batch";
      const heading = document.createElement("div");
      heading.className = "pinterest-batch-title";
      const publishedCount = batch.scripts.filter((s) => s.tiktokPublished).length;
      heading.textContent = `${batch.id} — ${publishedCount}/${batch.scripts.length} TikTok'ta yayında`;
      section.appendChild(heading);

      const grid = document.createElement("div");
      grid.className = "velzon-tweet-list";
      batch.scripts.forEach((s, index) => {
        const card = document.createElement("div");
        card.className = "velzon-tweet-card";
        const title = document.createElement("div");
        title.className = "pinterest-pin-title";
        title.textContent = s.title;
        const meta = document.createElement("div");
        meta.className = "velzon-tweet-meta";
        meta.textContent = (s.hashtags || []).join(" ");
        card.append(title, meta);

        const doPublish = async () => {
          await api(`/api/velzon-tiktok/batches/${batch.id}/scripts/${index}/publish`, {
            method: "POST",
          });
          toast("TikTok'a gönderildi — @velzon_tr hesabının TikTok uygulamasındaki gelen kutusunda incele");
          loadVelzonTiktokBatches();
        };

        if (s.tiktokPublished) {
          const badge = document.createElement("span");
          badge.className = "chip";
          badge.textContent = "TikTok'ta yayında";
          card.appendChild(badge);
        } else if (!s.videoReady) {
          const badge = document.createElement("span");
          badge.className = "chip";
          badge.textContent = "Önce Velzon YouTube'da yayınla";
          card.appendChild(badge);
        } else {
          const btn = document.createElement("button");
          btn.className = "btn btn-primary btn-small";
          btn.textContent = "TikTok'a yayınla";
          btn.onclick = async (e) => {
            e.stopPropagation();
            btn.disabled = true;
            btn.textContent = "Gönderiliyor…";
            try {
              await doPublish();
            } catch (err) {
              toast(err.message, true);
              btn.disabled = false;
              btn.textContent = "TikTok'a yayınla";
            }
          };
          card.appendChild(btn);
        }

        if (s.videoReady && !s.tiktokPublished) {
          card.onclick = () => openPostDetail({
            title: s.title,
            text: (s.hashtags || []).join(" "),
            published: false,
            onPublish: doPublish,
            publishingLabel: "Gönderiliyor…",
          });
        }
        grid.appendChild(card);
      });
      section.appendChild(grid);
      container.appendChild(section);
    }
  } catch (e) {
    renderLoadError(container, e);
  }
}

function openVelzonYoutubeGenerateDialog() {
  const dlg = $("dlg-velzon-youtube-generate");
  dlg.returnValue = "";
  loadVelzonArticles()
    .then((articles) => populateVelzonArticleDatalist("velzon-youtube-article-list", articles))
    .catch((e) => toast(e.message, true));
  dlg.showModal();
}

async function submitVelzonYoutubeGenerate() {
  const articleTitle = $("velzon-youtube-article").value.trim();
  const count = Number($("velzon-youtube-count").value) || 5;
  if (!articleTitle) return;
  const articlePath = resolveVelzonArticlePath(articleTitle);
  if (!articlePath) {
    toast("Listeden gerçek bir makale seçmelisiniz", true);
    return;
  }
  try {
    await api("/api/velzon-youtube/generate", {
      method: "POST",
      body: JSON.stringify({ articlePath, count }),
    });
    toast(`Parti kuyruğa alındı (${count} senaryo) — birkaç dakika sürebilir, sonra Yenile'ye bas`);
  } catch (e) {
    toast(e.message, true);
  }
}

// ---------- Wiring ----------
function refresh() {
  loadChannels().catch((e) => toast(e.message, true));
  loadStats().catch(() => {});
  loadJobs().catch((e) => toast(e.message, true));
}

document.addEventListener("DOMContentLoaded", () => {
  $("status-list").querySelectorAll("li").forEach((li) => {
    li.onclick = () => {
      $("status-list").querySelectorAll("li").forEach((x) => x.classList.remove("active"));
      li.classList.add("active");
      state.status = li.dataset.status;
      showList();
      refresh();
    };
  });
  $("brand-home").onclick = () => {
    // Logo = ana sayfa: tüm kanallar + "Tümü" filtresi
    state.channel = null;
    state.status = "";
    $("status-list").querySelectorAll("li").forEach((x) =>
      x.classList.toggle("active", x.dataset.status === ""));
    showList();
    refresh();
  };
  // nav-pinterest/nav-velzon/nav-velzon-instagram/nav-velzon-youtube linkleri
  // kaldırıldı — navigasyon artık renderChannelList() içinde dinamik olarak
  // kuruluyor (Velzon/Small Space Living marka girişleri + Velzon'un
  // X/Instagram/YouTube alt-menüsü). Generate/refresh butonları ve dialog
  // kapanış dinleyicileri kalıcı elementler, burada kalmaya devam ediyor.
  $("btn-pinterest-generate").onclick = openPinterestGenerateDialog;
  $("btn-pinterest-refresh").onclick = loadPinterestBatches;
  $("dlg-pinterest-generate").addEventListener("close", () => {
    if ($("dlg-pinterest-generate").returnValue === "ok") submitPinterestGenerate();
  });
  $("btn-velzon-generate").onclick = openVelzonGenerateDialog;
  $("btn-velzon-refresh").onclick = loadVelzonBatches;
  $("dlg-velzon-generate").addEventListener("close", () => {
    if ($("dlg-velzon-generate").returnValue === "ok") submitVelzonGenerate();
  });
  $("btn-velzon-instagram-generate").onclick = openVelzonInstagramGenerateDialog;
  $("btn-velzon-instagram-refresh").onclick = loadVelzonInstagramBatches;
  $("dlg-velzon-instagram-generate").addEventListener("close", () => {
    if ($("dlg-velzon-instagram-generate").returnValue === "ok") submitVelzonInstagramGenerate();
  });
  $("btn-velzon-youtube-generate").onclick = openVelzonYoutubeGenerateDialog;
  $("btn-velzon-youtube-refresh").onclick = loadVelzonYoutubeBatches;
  $("dlg-velzon-youtube-generate").addEventListener("close", () => {
    if ($("dlg-velzon-youtube-generate").returnValue === "ok") submitVelzonYoutubeGenerate();
  });
  $("btn-velzon-tiktok-refresh").onclick = loadVelzonTiktokBatches;
  $("nav-settings").onclick = () => {
    // Seçili kanal (yoksa ilk kanal) için ayarları aç
    const ch = state.channel
        || (state.channels && state.channels[0]?.channelId);
    if (ch) openChannelSettings(ch);
    else toast("Kanal bulunamadı", true);
  };
  $("btn-back").onclick = closeDetail;
  $("btn-save-meta").onclick = saveMetadata;
  $("btn-stats").onclick = loadJobStats;
  $("btn-approve").onclick = approve;
  $("btn-reject").onclick = reject;
  $("btn-generate").onclick = openGenerateDialog;
  $("dlg-generate").addEventListener("close", () => {
    if ($("dlg-generate").returnValue === "ok") submitGenerate();
  });
  $("dlg-channel").addEventListener("close", () => {
    if ($("dlg-channel").returnValue === "ok") saveChannelSettings();
  });
  $("post-detail-close-x").onclick = () => $("dlg-post-detail").close();
  refresh();
  setInterval(() => {
    if (state.job) return;
    if (state.view === "pinterest") loadPinterestBatches();
    else if (state.view === "velzon") loadVelzonBatches();
    else if (state.view === "velzon-instagram") loadVelzonInstagramBatches();
    else if (state.view === "velzon-youtube") loadVelzonYoutubeBatches();
    else if (state.view === "velzon-tiktok") loadVelzonTiktokBatches();
    else refresh();
  }, 15000); // arka plan yenileme
});
