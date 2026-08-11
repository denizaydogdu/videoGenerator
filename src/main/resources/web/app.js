/* Shorts Fabrikası backoffice — vanilla JS, framework yok */
"use strict";

const state = {
  channel: null,   // seçili kanal (null = tümü)
  status: "",      // seçili durum filtresi ("" = tümü)
  job: null,       // açık iş (detay görünümü)
  lang: null,      // seçili dil sekmesi
  view: "jobs",    // "jobs" | "pinterest"
};

const $ = (id) => document.getElementById(id);

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
async function loadChannels() {
  const channels = await api("/api/channels");
  state.channels = channels;
  const list = $("channel-list");
  list.innerHTML = "";
  const all = document.createElement("li");
  all.textContent = "Tüm kanallar";
  all.className = state.channel === null ? "active" : "";
  all.onclick = () => { state.channel = null; showList(); refresh(); };
  list.appendChild(all);
  for (const ch of channels) {
    const li = document.createElement("li");
    li.className = state.channel === ch.channelId ? "active" : "";
    const name = document.createElement("span");
    name.textContent = ch.displayName;
    li.appendChild(name);
    if (ch.pendingCount > 0) {
      const badge = document.createElement("span");
      badge.className = "badge";
      badge.textContent = ch.pendingCount;
      li.appendChild(badge);
    }
    li.onclick = () => { state.channel = ch.channelId; showList(); refresh(); };
    list.appendChild(li);
  }
  // Üretim dialogundaki kanal seçimi
  const sel = $("gen-channel");
  sel.innerHTML = "";
  for (const ch of channels) {
    const opt = document.createElement("option");
    opt.value = ch.channelId;
    opt.textContent = ch.displayName;
    sel.appendChild(opt);
  }
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

function showList() {
  // Detay panelini kapat (yenileme YAPMADAN) — kenar menü gezinmeleri
  // kendi refresh'ini çağırır, çift istek olmasın
  state.job = null;
  state.view = "jobs";
  $("job-detail").classList.add("hidden");
  $("pinterest-view").classList.add("hidden");
  $("job-grid").classList.remove("hidden");
  $("page-title").textContent = "İşler";
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
  $("job-grid").classList.add("hidden");
  $("job-detail").classList.add("hidden");
  $("pinterest-view").classList.remove("hidden");
  $("page-title").textContent = "Pinterest";
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

        if (pin.published && pin.url) {
          const link = document.createElement("a");
          link.className = "btn btn-secondary btn-small";
          link.textContent = "Pinterest'te gör ↗";
          link.href = pin.url;
          link.target = "_blank";
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
          btn.onclick = async () => {
            btn.disabled = true;
            btn.textContent = "Yayınlanıyor…";
            try {
              await api(`/api/pinterest/batches/${batch.id}/pins/${index}/publish`, {
                method: "POST",
              });
              toast("Pin yayınlandı");
              loadPinterestBatches();
            } catch (e) {
              toast(e.message, true);
              btn.disabled = false;
              btn.textContent = "Yayınla";
            }
          };
          card.appendChild(btn);
        }
        grid.appendChild(card);
      });
      section.appendChild(grid);
      container.appendChild(section);
    }
  } catch (e) {
    container.innerHTML = "";
    toast(e.message, true);
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
  $("nav-pinterest").onclick = () => {
    showPinterestView();
    loadPinterestBatches();
  };
  $("btn-pinterest-generate").onclick = openPinterestGenerateDialog;
  $("btn-pinterest-refresh").onclick = loadPinterestBatches;
  $("dlg-pinterest-generate").addEventListener("close", () => {
    if ($("dlg-pinterest-generate").returnValue === "ok") submitPinterestGenerate();
  });
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
  refresh();
  setInterval(() => {
    if (state.job) return;
    if (state.view === "pinterest") loadPinterestBatches();
    else refresh();
  }, 15000); // arka plan yenileme
});
