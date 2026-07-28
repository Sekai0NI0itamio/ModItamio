"use strict";

let currentMod = null;
let versions = [];
let issues = [];
let activeTab = "description";
let issueFilter = "all";
let expandedIssue = null;
let issueComments = {};
let vFilter = { loader: "", version: "" };
let sbLoader = "";
let sbGameVer = "";
let modId = "";

const ISSUE_STATUS = {
  queued: { label: "Queued", color: "var(--clay)", bg: "var(--clay-soft)", icon: "clock" },
  "in-progress": { label: "In Progress", color: "var(--moss)", bg: "var(--moss-soft)", icon: "loader" },
  complete: { label: "Complete", color: "var(--leaf)", bg: "var(--sage-soft)", icon: "check" },
  wontfix: { label: "Won't Fix", color: "var(--faint)", bg: "var(--surface-2)", icon: "x" },
};

function getIssueStatus(issue) {
  const labels = (issue.labels || []).map(l => (typeof l === "string" ? l : l.name).toLowerCase());
  if (labels.some(l => l.includes("wontfix") || l.includes("unfixable") || l === "invalid" || l === "duplicate")) return "wontfix";
  if (labels.some(l => l.includes("status:complete") || l.includes("status:done") || l.includes("status:fixed") || l.includes("status:resolved"))) return "complete";
  if (issue.state === "closed") return "complete";
  if (labels.some(l => l.includes("in-progress") || l.includes("inprogress") || l.includes("status:progress") || l.includes("status:working"))) return "in-progress";
  return "queued";
}

function getIssueType(issue) {
  const labels = (issue.labels || []).map(l => (typeof l === "string" ? l : l.name).toLowerCase());
  if (labels.some(l => l.includes("bug") || l.includes("crash"))) return "bug";
  if (labels.some(l => l.includes("enhancement") || l.includes("feature"))) return "feature";
  if (labels.some(l => l.includes("question") || l.includes("help"))) return "question";
  return "issue";
}

function loaderName(l) { return LOADER_NAMES[normLoader(l)] || l; }
function loaderColor(l) { return LOADER_COLORS[normLoader(l)] || "var(--color-text-dim)"; }
function loadersInclude(arr, l) { return (arr || []).some(x => normLoader(x) === normLoader(l)); }

function syncUrl() {
  const p = new URLSearchParams();
  if (modId) p.set("id", modId);
  if (activeTab && activeTab !== "description") p.set("tab", activeTab);
  if (sbLoader) p.set("l", sbLoader);
  if (sbGameVer) p.set("g", sbGameVer);
  if (vFilter.loader) p.set("vfl", vFilter.loader);
  if (vFilter.version) p.set("vfv", vFilter.version);
  if (activeTab === "issues" && issueFilter !== "all") p.set("if", issueFilter);
  const qs = p.toString();
  const newUrl = location.pathname + (qs ? "?" + qs : "");
  history.replaceState(null, "", newUrl);
}

async function init() {
  const params = new URLSearchParams(location.search);
  modId = params.get("id");
  if (!modId) { document.getElementById("app").innerHTML = '<div class="empty"><p>Missing mod ID.</p></div>'; return; }

  sbLoader = normLoader(params.get("l") || "");
  sbGameVer = params.get("g") || "";
  activeTab = params.get("tab") || "description";
  vFilter.loader = normLoader(params.get("vfl") || "");
  vFilter.version = params.get("vfv") || "";
  issueFilter = params.get("if") || "all";

  const validTabs = ["description", "gallery", "versions", "changelog", "issues"];
  if (!validTabs.includes(activeTab)) activeTab = "description";
  const validFilters = ["all", "queued", "in-progress", "complete", "wontfix"];
  if (!validFilters.includes(issueFilter)) issueFilter = "all";

  renderNavbar("mods");
  const root = document.getElementById("app");
  root.innerHTML = '<div class="loading">Loading…</div>';

  try {
    currentMod = await fetchJSON(CONFIG.dataUrl + "/" + modId + ".json");
    versions = currentMod.versions || [];
    try {
      const allIssues = [];
      for (let page = 1; page <= 3; page++) {
        const r = await fetch(ISSUES_API + "?state=all&labels=mod:" + encodeURIComponent(modId) + "&per_page=100&page=" + page + "&sort=updated&direction=desc");
        if (!r.ok) break;
        const pageIssues = await r.json();
        if (!pageIssues.length) break;
        allIssues.push(...pageIssues);
        if (pageIssues.length < 100) break;
      }
      issues = allIssues;
    } catch(e) { issues = []; }

    if (!sbLoader) sbLoader = pickDefaultLoader();
    if (!sbGameVer) sbGameVer = pickDefaultGameVer(sbLoader);

    const availLoaders = getAvailableLoaders();
    const availVers = getAvailableGameVers(sbLoader);
    if (sbLoader && !availLoaders.includes(sbLoader)) sbLoader = availLoaders[0] || "";
    if (sbGameVer && !availVers.includes(sbGameVer)) sbGameVer = availVers[0] || "";

    if (activeTab === "changelog" && !versions.some(v => v.changelog)) activeTab = "description";
    if (activeTab === "gallery" && !(currentMod.gallery || []).length) activeTab = "description";

    document.title = escapeHtml(currentMod.name) + " — ModItamio";
    syncUrl();
    render();
  } catch (e) {
    root.innerHTML = '<div class="empty"><p>Mod not found: ' + escapeHtml(e.message) + '</p><a class="btn" href="mods.html">' + ICONS.chevron_left + ' Back to mods</a></div>';
  }
}

function pickDefaultLoader() {
  const all = [...new Set(versions.flatMap(v => v.loaders || []).map(normLoader))];
  if (all.length === 0) return "";
  const priority = ["forge", "neoforge", "fabric", "quilt"];
  for (const p of priority) {
    if (all.includes(p)) return p;
  }
  return all[0];
}

function pickDefaultGameVer(loader) {
  let pool = versions;
  if (loader) pool = pool.filter(v => loadersInclude(v.loaders, loader));
  const all = [...new Set(pool.flatMap(v => v.game_versions || []))].sort().reverse();
  return all[0] || "";
}

function getFilteredVersionsForSidebar() {
  let list = [...versions];
  if (sbLoader) list = list.filter(v => loadersInclude(v.loaders, sbLoader));
  if (sbGameVer) list = list.filter(v => (v.game_versions || []).includes(sbGameVer));
  list.sort((a, b) => (b.date_published || "").localeCompare(a.date_published || ""));
  return list;
}

function getSelectedFile() {
  const filtered = getFilteredVersionsForSidebar();
  for (const v of filtered) {
    const files = v.files || [];
    const primary = files.find(f => f.primary);
    if (primary) return { file: primary, version: v };
    if (files[0]) return { file: files[0], version: v };
  }
  return null;
}

function getAvailableLoaders(gameVer) {
  let pool = versions;
  if (gameVer) pool = pool.filter(v => (v.game_versions || []).includes(gameVer));
  return [...new Set(pool.flatMap(v => v.loaders || []).map(normLoader))];
}

function getAvailableGameVers(loader) {
  let pool = versions;
  if (loader) pool = pool.filter(v => loadersInclude(v.loaders, loader));
  return [...new Set(pool.flatMap(v => v.game_versions || []))].sort().reverse();
}

function render() {
  const root = document.getElementById("app");
  const iconUrl = currentMod.icon_url || CONFIG.modsAssetBase + "/" + currentMod.mod_id + "/icon.png";
  const letter = (currentMod.name || "M")[0].toUpperCase();
  const gallery = currentMod.gallery || [];
  const selected = getSelectedFile();
  const primaryFile = selected ? selected.file : getPrimaryFile();

  const links = [];
  if (currentMod.source_url) links.push({ icon: ICONS.github, label: "Source", url: currentMod.source_url });
  links.push({ icon: ICONS.alert, label: "Issues", url: ISSUES_API.replace("api.github.com/repos", "github.com") });
  if (currentMod.wiki_url) links.push({ icon: ICONS.book, label: "Wiki", url: currentMod.wiki_url });
  if (currentMod.discord_url) links.push({ icon: ICONS.discord, label: "Discord", url: currentMod.discord_url });

  const issueCount = issues.length;
  const openCount = issues.filter(i => getIssueStatus(i) !== "complete" && getIssueStatus(i) !== "wontfix").length;

  root.innerHTML =
  '<div class="proj-header">' +
    '<div class="proj-header__main">' +
      '<div class="proj-icon-wrap">' +
        '<img class="proj-header__icon" src="' + escapeHtml(iconUrl) + '" alt="" loading="lazy" onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'flex\'">' +
        '<div class="proj-icon-fallback" style="display:none">' + escapeHtml(letter) + '</div>' +
      '</div>' +
      '<div class="proj-header__info">' +
        '<h1 class="proj-header__title">' + escapeHtml(currentMod.name) + '</h1>' +
        '<div class="proj-header__desc">' + escapeHtml(currentMod.summary || "") + '</div>' +
        '<div class="proj-header__tags">' +
          renderLoaderTags(currentMod.loaders) + renderCategoryTags(currentMod.categories) +
          (currentMod.license ? '<span class="tag tag--cat license-tag">' + escapeHtml(currentMod.license) + '</span>' : "") +
        '</div>' +
      '</div>' +
    '</div>' +
    '<div class="proj-header__actions">' +
      (primaryFile ? '<a class="btn btn--lg btn--primary" id="header-download-btn" href="' + escapeHtml(primaryFile.url) + '" download><span class="btn__icon">' + ICONS.download + '</span><span class="btn__label" id="header-download-label">' + ((sbLoader || sbGameVer) ? "Download" + (sbLoader ? " for " + (loaderName(sbLoader) || sbLoader) : "") + (sbGameVer ? " " + sbGameVer : "") : "Download") + '</span></a>' : "") +
      '<a class="btn btn--lg" href="' + ISSUES_NEW_URL + '?labels=mod:' + escapeHtml(currentMod.mod_id) + '&title=' + encodeURIComponent("[" + currentMod.name + "] ") + '" target="_blank" rel="noopener"><span class="btn__icon">' + ICONS.alert + '</span><span class="btn__label">Report issue</span></a>' +
    '</div>' +
  '</div>' +

  '<div class="proj-meta-row">' +
    '<span class="proj-meta__item">' + ICONS.download + ' ' + formatNumber(currentMod.downloads) + ' downloads</span>' +
    '<span class="proj-meta__item">' + ICONS.heart + ' ' + formatNumber(currentMod.followers) + ' followers</span>' +
    '<span class="proj-meta__item">' + ICONS.clock + ' Updated ' + timeAgo(currentMod.updated || currentMod.date_published) + '</span>' +
    '<span class="proj-meta__item">' + ICONS.alert + ' ' + openCount + ' open / ' + issueCount + ' total issues</span>' +
    (currentMod.author ? '<span class="proj-meta__item">by <strong>' + escapeHtml(currentMod.author) + '</strong></span>' : "") +
    links.map(l => '<a class="proj-meta__link" href="' + escapeHtml(l.url) + '" target="_blank" rel="noopener">' + l.icon + ' ' + escapeHtml(l.label) + ' ' + ICONS.external + '</a>').join("") +
  '</div>' +

  '<div class="proj-tabs">' +
    renderTab("description", "Description") +
    (gallery.length ? renderTab("gallery", "Gallery (" + gallery.length + ")") : "") +
    renderTab("versions", "Versions (" + versions.length + ")") +
    (versions.some(v => v.changelog) ? renderTab("changelog", "Changelog") : "") +
    renderTab("issues", "Issues" + (issueCount ? " (" + openCount + ")" : "")) +
  '</div>' +

  '<div class="proj-body" id="proj-body"></div>';

  renderTabContent();
}

function renderTab(id, label) {
  return '<button class="proj-tab' + (activeTab === id ? " proj-tab--active" : "") + '" data-tab="' + id + '" onclick="switchTab(\'' + id + '\')">' + label + '</button>';
}

function switchTab(tab) {
  activeTab = tab;
  document.querySelectorAll(".proj-tab").forEach(t => t.classList.toggle("proj-tab--active", t.dataset.tab === tab));
  syncUrl();
  renderTabContent();
}

function renderTabContent() {
  const body = document.getElementById("proj-body");
  if (!body) return;
  switch (activeTab) {
    case "description": body.innerHTML = renderDescription(); bindSidebarSelectors(); break;
    case "gallery": body.innerHTML = renderGallery(); break;
    case "versions": body.innerHTML = renderVersions(); break;
    case "changelog": body.innerHTML = renderChangelog(); break;
    case "issues": body.innerHTML = renderIssues(); bindIssueEvents(); break;
  }
}

function onSidebarLoaderChange(val) {
  sbLoader = normLoader(val);
  const availVers = getAvailableGameVers(sbLoader);
  if (sbGameVer && !availVers.includes(sbGameVer)) {
    sbGameVer = availVers[0] || "";
  }
  syncUrl();
  updateSidebarDownload();
  updateHeaderDownload();
}

function onSidebarVerChange(val) {
  sbGameVer = val;
  const availLoaders = getAvailableLoaders(sbGameVer);
  if (sbLoader && !availLoaders.includes(sbLoader)) {
    sbLoader = availLoaders[0] || "";
  }
  syncUrl();
  updateSidebarDownload();
  updateHeaderDownload();
}

function bindSidebarSelectors() {
  const loaderSel = document.getElementById("sb-loader");
  const verSel = document.getElementById("sb-version");
  if (loaderSel) loaderSel.addEventListener("change", (e) => onSidebarLoaderChange(e.target.value));
  if (verSel) verSel.addEventListener("change", (e) => onSidebarVerChange(e.target.value));
}

function updateSidebarDownload() {
  const loaderSel = document.getElementById("sb-loader");
  const verSel = document.getElementById("sb-version");
  const downloadBtn = document.getElementById("sb-download-btn");
  const fileInfo = document.getElementById("sb-file-info");
  const noMatch = document.getElementById("sb-no-match");

  if (!downloadBtn) return;

  if (loaderSel) {
    const availLoaders = getAvailableLoaders(sbGameVer);
    loaderSel.innerHTML = '<option value="">Any loader</option>' + availLoaders.map(l =>
      '<option value="' + l + '"' + (l === sbLoader ? ' selected' : '') + '>' + escapeHtml(loaderName(l) || l) + '</option>'
    ).join("");
  }
  if (verSel) {
    const availVers = getAvailableGameVers(sbLoader);
    verSel.innerHTML = '<option value="">Any version</option>' + availVers.map(v =>
      '<option value="' + escapeHtml(v) + '"' + (v === sbGameVer ? ' selected' : '') + '>' + escapeHtml(v) + '</option>'
    ).join("");
  }

  const sel = getSelectedFile();
  if (sel && downloadBtn) {
    downloadBtn.href = sel.file.url;
    downloadBtn.style.display = "";
    const sizeStr = sel.file.size ? formatBytes(sel.file.size) : "";
    const verName = sel.version.name || sel.version.version_number;
    const loaderList = (sel.version.loaders || []).map(l => loaderName(l) || l).join(", ");
    const gameList = (sel.version.game_versions || []).join(", ");
    const btnLabel = (sbLoader || sbGameVer)
      ? "Download" + (sbLoader ? " for " + (loaderName(sbLoader) || sbLoader) : "") + (sbGameVer ? " " + sbGameVer : "")
      : "Download latest";
    const labelEl = document.getElementById("sb-download-label");
    if (labelEl) labelEl.textContent = btnLabel;
    if (fileInfo) fileInfo.innerHTML =
      '<div class="sidebar-card__file-ver">' + versionBadge(sel.version.version_type) + ' ' + escapeHtml(verName) + '</div>' +
      '<div class="sidebar-card__file-meta">' + ICONS.download + ' ' + (sizeStr ? sizeStr + ' • ' : '') + escapeHtml(loaderList) + ' • ' + escapeHtml(gameList) + '</div>' +
      '<div class="sidebar-card__file-date">' + ICONS.clock + ' Released ' + formatDate(sel.version.date_published) + '</div>';
    if (noMatch) noMatch.style.display = "none";
  } else {
    if (downloadBtn) downloadBtn.style.display = "none";
    if (fileInfo) fileInfo.innerHTML = "";
    if (noMatch) noMatch.style.display = "block";
  }
}

function updateHeaderDownload() {
  const btn = document.getElementById("header-download-btn");
  if (!btn) return;
  const sel = getSelectedFile();
  if (sel) {
    btn.href = sel.file.url;
    btn.style.display = "";
    const label = document.getElementById("header-download-label");
    if (label) {
      if (sbLoader || sbGameVer) {
        label.textContent = "Download" + (sbLoader ? " for " + (loaderName(sbLoader) || sbLoader) : "") + (sbGameVer ? " " + sbGameVer : "");
      } else {
        label.textContent = "Download";
      }
    }
    btn.title = (sel.version.name || sel.version.version_number) + " - " + (sel.file.filename || "");
  } else {
    btn.style.display = "none";
  }
}

function renderDescription() {
  const desc = currentMod.body || currentMod.description || currentMod.summary || "_No description provided._";
  const availLoaders = getAvailableLoaders(sbGameVer);
  const availVers = getAvailableGameVers(sbLoader);
  const recentVersions = versions.slice(0, 5);
  const sel = getSelectedFile();

  const loaderOptions = '<option value="">Any loader</option>' + availLoaders.map(l =>
    '<option value="' + l + '"' + (l === sbLoader ? ' selected' : '') + '>' + escapeHtml(loaderName(l) || l) + '</option>'
  ).join("");

  const verOptions = '<option value="">Any version</option>' + availVers.map(v =>
    '<option value="' + escapeHtml(v) + '"' + (v === sbGameVer ? ' selected' : '') + '>' + escapeHtml(v) + '</option>'
  ).join("");

  let downloadSection = "";
  if (sel) {
    const sizeStr = sel.file.size ? formatBytes(sel.file.size) : "";
    const verName = sel.version.name || sel.version.version_number;
    const loaderList = (sel.version.loaders || []).map(l => loaderName(l) || l).join(", ");
    const gameList = (sel.version.game_versions || []).join(", ");
    const btnLabel = (sbLoader || sbGameVer)
      ? "Download" + (sbLoader ? " for " + (loaderName(sbLoader) || sbLoader) : "") + (sbGameVer ? " " + sbGameVer : "")
      : "Download latest";
    downloadSection =
      '<a class="btn btn--lg btn--primary btn--block" id="sb-download-btn" href="' + escapeHtml(sel.file.url) + '" download>' +
        '<span class="btn__icon">' + ICONS.download + '</span>' +
        '<span class="btn__label" id="sb-download-label">' + btnLabel + '</span>' +
      '</a>' +
      '<div id="sb-file-info" class="sidebar-card__file-info">' +
        '<div class="sidebar-card__file-ver">' + versionBadge(sel.version.version_type) + ' ' + escapeHtml(verName) + '</div>' +
        '<div class="sidebar-card__file-meta">' + ICONS.download + ' ' + (sizeStr ? sizeStr + ' • ' : '') + escapeHtml(loaderList) + ' • ' + escapeHtml(gameList) + '</div>' +
        '<div class="sidebar-card__file-date">' + ICONS.clock + ' Released ' + formatDate(sel.version.date_published) + '</div>' +
      '</div>' +
      '<div id="sb-no-match" class="sidebar-card__nomatch" style="display:none">' +
        ICONS.alert + ' No file matches your selection. Try different options.' +
      '</div>';
  } else {
    downloadSection =
      '<a class="btn btn--lg btn--primary btn--block" id="sb-download-btn" href="#" style="display:none" download></a>' +
      '<div id="sb-file-info"></div>' +
      '<div id="sb-no-match" class="sidebar-card__nomatch">' +
        ICONS.alert + ' No file matches your selection. Try different options.' +
      '</div>';
  }

  return '<div class="proj-split">' +
    '<div class="proj-split__main">' +
      '<div class="panel"><div class="panel__body prose">' + renderMarkdown(desc) + '</div></div>' +
    '</div>' +
    '<div class="proj-split__sidebar">' +
      '<div class="panel sidebar-card">' +
        '<div class="sidebar-card__section">' +
          '<div class="sidebar-card__label">Choose your setup</div>' +
          '<div class="sidebar-card__selectors">' +
            '<div class="sidebar-card__select-wrap">' +
              '<label class="sidebar-card__select-label">Loader</label>' +
              '<select class="form-select sidebar-card__select" id="sb-loader">' + loaderOptions + '</select>' +
            '</div>' +
            '<div class="sidebar-card__select-wrap">' +
              '<label class="sidebar-card__select-label">Minecraft version</label>' +
              '<select class="form-select sidebar-card__select" id="sb-version">' + verOptions + '</select>' +
            '</div>' +
          '</div>' +
        '</div>' +
        '<div class="sidebar-card__section">' +
          downloadSection +
          '<div class="sidebar-card__meta">' + ICONS.download + ' ' + formatNumber(currentMod.downloads) + ' total downloads</div>' +
        '</div>' +
        (recentVersions.length ? '<div class="sidebar-card__section sidebar-card__section--bordered">' +
          '<div class="sidebar-card__label">Recent versions</div>' +
          '<div class="sidebar-card__vlist">' + recentVersions.map(v =>
            '<a class="sidebar-card__vrow" href="version.html?id=' + encodeURIComponent(currentMod.mod_id) + '&v=' + encodeURIComponent(v.version_number) + '&l=' + encodeURIComponent((v.loaders||[])[0]||"") + '">' +
              '<span class="sidebar-card__vname">' + versionBadge(v.version_type) + ' ' + escapeHtml(v.name || v.version_number) + '</span>' +
              '<span class="sidebar-card__vdate">' + formatDate(v.date_published) + '</span>' +
            '</a>'
          ).join("") + '</div>' +
          '<a class="sidebar-card__all" href="#" onclick="switchTab(\'versions\');return false;">' + ICONS.chevron_right + ' View all versions</a>' +
        '</div>' : "") +
      '</div>' +
    '</div>' +
  '</div>';
}

function renderGallery() {
  const g = currentMod.gallery || [];
  if (!g.length) return '<div class="empty"><p>No gallery images.</p></div>';
  return '<div class="gallery-grid">' + g.map((img, i) =>
    '<a href="' + escapeHtml(img.url) + '" target="_blank" rel="noopener" class="gallery-item"><img src="' + escapeHtml(img.url) + '" alt="' + escapeHtml(img.title || "Gallery image " + (i+1)) + '" loading="lazy"></a>'
  ).join("") + '</div>';
}

function getPrimaryFile() {
  const allFiles = versions.flatMap(v => (v.files || []).map(f => ({ ...f, version: v })));
  const releaseVers = versions.filter(v => (v.version_type || "release") === "release");
  const src = releaseVers.length ? releaseVers : versions;
  for (const v of src) {
    const files = v.files || [];
    const primary = files.find(f => f.primary);
    if (primary) return { ...primary, version: v };
    if (files[0]) return { ...files[0], version: v };
  }
  return allFiles[0] || null;
}

function getFilteredVersions() {
  let list = [...versions];
  if (vFilter.loader) list = list.filter(v => loadersInclude(v.loaders, vFilter.loader));
  if (vFilter.version) list = list.filter(v => (v.game_versions || []).includes(vFilter.version));
  list.sort((a, b) => (b.date_published || "").localeCompare(a.date_published || ""));
  return list;
}

function onVerFilterLoader(val) { vFilter.loader = normLoader(val); syncUrl(); renderTabContent(); }
function onVerFilterVersion(val) { vFilter.version = val; syncUrl(); renderTabContent(); }

function renderVersions() {
  const allLoaders = [...new Set(versions.flatMap(v => v.loaders || []).map(normLoader))];
  const allVersions = [...new Set(versions.flatMap(v => v.game_versions || []))].sort().reverse();
  const filtered = getFilteredVersions();

  return '<div class="version-filters">' +
    '<select class="form-select" onchange="onVerFilterLoader(this.value)"><option value="">All loaders</option>' +
      allLoaders.map(l => '<option value="' + l + '"' + (vFilter.loader === l ? " selected" : "") + '>' + escapeHtml(loaderName(l) || l) + '</option>').join("") +
    '</select>' +
    '<select class="form-select" onchange="onVerFilterVersion(this.value)"><option value="">All versions</option>' +
      allVersions.map(v => '<option value="' + escapeHtml(v) + '"' + (vFilter.version === v ? " selected" : "") + '>' + escapeHtml(v) + '</option>').join("") +
    '</select>' +
  '</div>' +
  (!filtered.length ? '<div class="empty"><p>No versions match the filters.</p></div>' :
  '<div class="vlist">' + filtered.map(v => renderVersionRow(v)).join("") + '</div>');
}

function renderVersionRow(v) {
  const files = v.files || [];
  const primary = files.find(f => f.primary) || files[0];
  return '<div class="vrow">' +
    versionBadge(v.version_type) +
    '<div class="vrow__info">' +
      '<div class="vrow__name"><a href="version.html?id=' + encodeURIComponent(currentMod.mod_id) + '&v=' + encodeURIComponent(v.version_number) + '&l=' + encodeURIComponent((v.loaders||[])[0]||"") + '">' + escapeHtml(v.name || v.version_number) + '</a></div>' +
      '<div class="vrow__tags">' + renderVersionTags(v.game_versions) + renderLoaderTags(v.loaders) + '</div>' +
    '</div>' +
    '<div class="vrow__meta">' +
      '<span class="vrow__date">' + formatDate(v.date_published) + '</span>' +
      '<span class="vrow__dl">' + ICONS.download + ' ' + formatNumber(v.downloads) + '</span>' +
    '</div>' +
    '<div class="vrow__actions">' +
      (primary ? '<a class="btn btn--sm btn--primary" href="' + escapeHtml(primary.url) + '" download title="Download">' + ICONS.download + '</a>' : "") +
      (files.length > 1 ? '<div class="btn-group"><button class="btn btn--sm" onclick="this.nextElementSibling.style.display=this.nextElementSibling.style.display===\'none\'?\'block\':\'none\'">' + ICONS.chevron_down + '</button><div class="menu" style="display:none;right:0;top:110%">' + files.map(f => '<a class="menu__item" href="' + escapeHtml(f.url) + '" download>' + escapeHtml(f.filename || "file") + ' (' + formatBytes(f.size) + ')</a>').join("") + '</div></div>' : "") +
    '</div>' +
  '</div>';
}

function renderChangelog() {
  const versionsWithChangelog = versions.filter(v => v.changelog).sort((a, b) => (b.date_published || "").localeCompare(a.date_published || ""));
  if (!versionsWithChangelog.length) return '<div class="panel"><div class="panel__body prose"><p><em>No changelog entries available.</em></p></div></div>';
  return '<div class="panel"><div class="panel__body">' + versionsWithChangelog.map(v =>
    '<div class="changelog-entry">' +
      '<div class="changelog-entry__header">' +
        '<h4 style="margin:0;font-size:var(--fs-base);font-weight:700;color:var(--color-text-bright)">' + escapeHtml(v.name || v.version_number) + '</h4>' +
        '<span style="font-size:var(--fs-xs);color:var(--color-text-dim)">' + formatDate(v.date_published) + '</span>' +
      '</div>' +
      '<div class="prose changelog-text">' + renderMarkdown(v.changelog) + '</div>' +
    '</div>'
  ).join("") + '</div></div>';
}

function getFilteredIssues() {
  const modIssues = issues.filter(issue => {
    const labels = (issue.labels || []).map(l => typeof l === "string" ? l : l.name);
    return labels.some(l => l === "mod:" + currentMod.mod_id);
  });
  if (issueFilter === "all") return modIssues;
  return modIssues.filter(i => getIssueStatus(i) === issueFilter);
}

function renderStatusBadge(status) {
  const s = ISSUE_STATUS[status];
  if (!s) return "";
  return '<span class="issue-status issue-status--' + status + '" style="--status-color:' + s.color + ';--status-bg:' + s.bg + '">' +
    ICONS[s.icon] + ' ' + s.label +
  '</span>';
}

function setIssueFilter(filter) {
  issueFilter = filter;
  syncUrl();
  renderTabContent();
}

function toggleIssueExpand(issueNumber) {
  if (expandedIssue === issueNumber) {
    expandedIssue = null;
  } else {
    expandedIssue = issueNumber;
    if (!issueComments[issueNumber]) {
      loadIssueComments(issueNumber);
    }
  }
  renderTabContent();
}

async function loadIssueComments(issueNumber) {
  try {
    const issue = issues.find(i => i.number === issueNumber);
    if (!issue || !issue.comments_url) return;
    const r = await fetch(issue.comments_url);
    if (r.ok) {
      issueComments[issueNumber] = await r.json();
      const el = document.getElementById("issue-comments-" + issueNumber);
      if (el) el.innerHTML = renderIssueComments(issueComments[issueNumber]);
    }
  } catch(e) {
    issueComments[issueNumber] = [];
  }
}

function renderIssueComments(comments) {
  if (!comments || !comments.length) {
    return '<div class="issue-comments__empty"><em>No comments yet.</em></div>';
  }
  return comments.map(c =>
    '<div class="issue-comment">' +
      '<div class="issue-comment__header">' +
        '<img class="issue-comment__avatar" src="' + escapeHtml(c.user?.avatar_url || "") + '" alt="" loading="lazy" onerror="this.style.display=\'none\'">' +
        '<span class="issue-comment__author">' + escapeHtml(c.user?.login || "unknown") + '</span>' +
        '<span class="issue-comment__date">' + timeAgo(c.created_at) + '</span>' +
      '</div>' +
      '<div class="prose issue-comment__body">' + renderMarkdown(c.body || "") + '</div>' +
    '</div>'
  ).join("");
}

function renderIssues() {
  const filtered = getFilteredIssues();
  const allModIssues = issues.filter(i => {
    const labels = (i.labels||[]).map(l=>typeof l==='string'?l:l.name);
    return labels.some(l=>l==='mod:'+currentMod.mod_id);
  });
  const counts = { all: allModIssues.length, queued: 0, "in-progress": 0, complete: 0, wontfix: 0 };
  allModIssues.forEach(i => { const s = getIssueStatus(i); counts[s] = (counts[s]||0) + 1; });

  const allLoaders = [...new Set(versions.flatMap(v => v.loaders || []).map(normLoader))];
  const allVersions = [...new Set(versions.flatMap(v => v.game_versions || []))].sort().reverse();

  const filterTabs = [
    { key: "all", label: "All", count: counts.all },
    { key: "queued", label: "Queued", count: counts.queued },
    { key: "in-progress", label: "In Progress", count: counts["in-progress"] },
    { key: "complete", label: "Complete", count: counts.complete },
    { key: "wontfix", label: "Won't Fix", count: counts.wontfix },
  ];

  return '<div class="issues-layout">' +
    '<div>' +
      '<div class="issue-filters">' +
        filterTabs.map(t =>
          '<button class="issue-filter' + (issueFilter === t.key ? ' issue-filter--active' : '') + '" onclick="setIssueFilter(\'' + t.key + '\')">' +
            escapeHtml(t.label) + ' <span class="issue-filter__count">' + t.count + '</span>' +
          '</button>'
        ).join("") +
      '</div>' +
      '<div class="panel"><div class="panel__body">' +
        (filtered.length === 0 ? '<div class="empty" style="padding:var(--space-8)"><p style="margin-bottom:var(--space-2)">No ' + (issueFilter !== "all" ? issueFilter + " " : "") + 'issues for this mod.</p></div>' :
        '<div class="issue-list">' + filtered.map(renderIssueRow).join("") + '</div>') +
      '</div></div>' +
    '</div>' +
    '<div class="issues-sidebar">' +
      '<div class="panel"><div class="panel__body" style="padding:var(--space-4)">' +
        '<h3 style="font-size:var(--fs-base);font-weight:700;margin:0 0 var(--space-3);color:var(--color-text-bright)">Report an issue</h3>' +
        '<form id="quick-issue-form" onsubmit="submitQuickIssue(event)">' +
          '<label style="display:block;font-size:var(--fs-sm);font-weight:600;color:var(--color-text-bright);margin-bottom:var(--space-1)">Issue type</label>' +
          '<select class="form-select" id="qi-type" style="width:100%;margin-bottom:var(--space-3)">' +
            '<option value="bug">Bug / Crash</option>' +
            '<option value="enhancement">Feature Request</option>' +
            '<option value="question">Question / Help</option>' +
            '<option value="">Other</option>' +
          '</select>' +
          '<label style="display:block;font-size:var(--fs-sm);font-weight:600;color:var(--color-text-bright);margin-bottom:var(--space-1)">Loader</label>' +
          '<select class="form-select" id="qi-loader" style="width:100%;margin-bottom:var(--space-3)">' +
            '<option value="">Any / Not applicable</option>' +
            allLoaders.map(l => '<option value="' + l + '"' + (l === sbLoader ? ' selected' : '') + '>' + escapeHtml(loaderName(l) || l) + '</option>').join("") +
          '</select>' +
          '<label style="display:block;font-size:var(--fs-sm);font-weight:600;color:var(--color-text-bright);margin-bottom:var(--space-1)">Minecraft version</label>' +
          '<select class="form-select" id="qi-version" style="width:100%;margin-bottom:var(--space-3)">' +
            '<option value="">Any / Not applicable</option>' +
            allVersions.map(v => '<option value="' + escapeHtml(v) + '"' + (v === sbGameVer ? ' selected' : '') + '>' + escapeHtml(v) + '</option>').join("") +
          '</select>' +
          '<label style="display:block;font-size:var(--fs-sm);font-weight:600;color:var(--color-text-bright);margin-bottom:var(--space-1)">Title</label>' +
          '<input class="form-input" type="text" id="qi-title" placeholder="Brief summary of the issue..." required style="width:100%;margin-bottom:var(--space-3)">' +
          '<label style="display:block;font-size:var(--fs-sm);font-weight:600;color:var(--color-text-bright);margin-bottom:var(--space-1)">Description</label>' +
          '<textarea class="form-input" id="qi-body" rows="5" placeholder="Steps to reproduce, expected behavior, crash reports, screenshots, etc..." style="width:100%;margin-bottom:var(--space-3);resize:vertical"></textarea>' +
          '<button type="submit" class="btn btn--primary" style="width:100%"><span class="btn__icon">' + ICONS.send + '</span><span class="btn__label">Create on GitHub</span></button>' +
        '</form>' +
        '<div id="qi-result" style="margin-top:var(--space-2);font-size:var(--fs-sm)"></div>' +
        '<p style="margin:var(--space-3) 0 0;font-size:var(--fs-xs);color:var(--faint);line-height:1.5">' + ICONS.github + " You'll be taken to GitHub to submit. Issues are tracked with the <code>mod:" + escapeHtml(currentMod.mod_id) + '</code> label.</p>' +
      '</div></div>' +
      '<div class="panel"><div class="panel__body" style="padding:var(--space-4)">' +
        '<h3 style="font-size:var(--fs-base);font-weight:700;margin:0 0 var(--space-2);color:var(--color-text-bright)">Status guide</h3>' +
        '<div class="status-legend">' +
          Object.entries(ISSUE_STATUS).map(([key, s]) =>
            '<div class="status-legend__row">' +
              renderStatusBadge(key) +
              '<span class="status-legend__desc">' +
                (key === "queued" ? "Reported, waiting to be reviewed" :
                 key === "in-progress" ? "Actively being worked on" :
                 key === "complete" ? "Fixed and resolved" :
                 "Cannot be fixed or won't be addressed") +
              '</span>' +
            '</div>'
          ).join("") +
        '</div>' +
      '</div></div>' +
    '</div>' +
  '</div>';
}

function renderIssueRow(issue) {
  const status = getIssueStatus(issue);
  const statusInfo = ISSUE_STATUS[status];
  const labels = (issue.labels || []).filter(l => {
    const name = typeof l === "string" ? l : l.name;
    return !name.startsWith("mod:") && !name.startsWith("status:") && !["wontfix","unfixable","invalid","duplicate"].includes(name.toLowerCase());
  }).map(l => {
    const name = typeof l === "string" ? l : l.name;
    return '<span class="issue-label" style="--label:#' + (typeof l === "string" ? "8b949e" : l.color || "8b949e") + '">' + escapeHtml(name) + '</span>';
  }).join("");

  const isOpen = status === "queued" || status === "in-progress";
  const isExpanded = expandedIssue === issue.number;
  const hasComments = (issue.comments || 0) > 0;

  return '<div class="issue-row' + (isExpanded ? ' issue-row--expanded' : '') + '" data-issue="' + issue.number + '">' +
    '<div class="issue-row__header" onclick="toggleIssueExpand(' + issue.number + ')">' +
      '<span class="issue-row__icon issue-row__icon--' + status + '" style="color:' + statusInfo.color + '">' + ICONS[statusInfo.icon] + '</span>' +
      '<div style="min-width:0;flex:1">' +
        '<div class="issue-row__title-row">' +
          '<span class="issue-row__title">' + escapeHtml(issue.title) + '</span>' +
          renderStatusBadge(status) +
        '</div>' +
        '<div class="issue-row__meta">#' + issue.number + ' ' + (isOpen ? 'opened' : 'closed') + ' ' + timeAgo(issue.created_at) + ' by ' + escapeHtml(issue.user?.login || "unknown") +
          (hasComments ? ' &bull; ' + ICONS.message + ' ' + issue.comments : '') +
        '</div>' +
        (labels ? '<div class="issue-row__labels">' + labels + '</div>' : "") +
      '</div>' +
      '<span class="issue-row__cmt issue-row__cmt--' + (isExpanded ? 'expanded' : 'collapsed') + '">' + (isExpanded ? ICONS.chevron_down : ICONS.chevron_right) + '</span>' +
    '</div>' +
    (isExpanded ? '<div class="issue-detail">' +
      '<div class="issue-detail__body prose">' + renderMarkdown(issue.body || "_No description provided._") + '</div>' +
      '<div class="issue-detail__actions">' +
        '<a class="btn btn--sm" href="' + escapeHtml(issue.html_url) + '" target="_blank" rel="noopener">' + ICONS.external + ' View on GitHub</a>' +
        '<a class="btn btn--sm" href="' + escapeHtml(issue.html_url) + '#issuecomment-new" target="_blank" rel="noopener">' + ICONS.message + ' Reply</a>' +
      '</div>' +
      '<div class="issue-comments" id="issue-comments-' + issue.number + '">' +
        '<div class="issue-comments__loading">' + ICONS.loader + ' Loading comments...</div>' +
      '</div>' +
    '</div>' : "") +
  '</div>';
}

function bindIssueEvents() {
}

function submitQuickIssue(e) {
  e.preventDefault();
  const title = document.getElementById("qi-title").value.trim();
  const body = document.getElementById("qi-body").value.trim();
  const type = document.getElementById("qi-type").value;
  const qiLoader = document.getElementById("qi-loader").value;
  const qiVersion = document.getElementById("qi-version").value;
  if (!title) return;

  const labels = ["mod:" + currentMod.mod_id];
  if (type === "bug") labels.push("bug");
  if (type === "enhancement") labels.push("enhancement");
  if (type === "question") labels.push("question");

  const fullTitle = "[" + currentMod.name + "] " + title;

  const fullBody = "## Mod Info\n" +
    "- **Mod**: " + currentMod.name + " (`" + currentMod.mod_id + "`)\n" +
    (qiLoader ? "- **Loader**: " + loaderName(qiLoader) + "\n" : "") +
    (qiVersion ? "- **Minecraft Version**: " + qiVersion + "\n" : "") +
    "- **Mod Version**: " + (versions[0]?.version_number || "unknown") + "\n\n" +
    "## Description\n" + (body || "_No description provided._") + "\n\n---\n*Submitted via ModItamio*";

  const url = ISSUES_NEW_URL +
    "?labels=" + encodeURIComponent(labels.join(",")) +
    "&title=" + encodeURIComponent(fullTitle) +
    "&body=" + encodeURIComponent(fullBody);

  window.open(url, "_blank");
  document.getElementById("qi-result").innerHTML = '<span style="color:var(--leaf)">' + ICONS.check + ' Opened GitHub in a new tab to complete your submission</span>';
}

document.addEventListener("DOMContentLoaded", init);
