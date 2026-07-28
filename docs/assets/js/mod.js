"use strict";

let currentMod = null;
let versions = [];
let issues = [];
let activeTab = "description";
let vFilter = { loader: "", version: "" };
let selectedVersion = "";
let selectedLoader = "";

async function init() {
  const params = new URLSearchParams(location.search);
  const modId = params.get("id");
  if (!modId) { document.getElementById("app").innerHTML = '<div class="empty"><p>Missing mod ID.</p></div>'; return; }

  renderNavbar("mods");
  const root = document.getElementById("app");
  root.innerHTML = '<div class="loading">Loading…</div>';

  try {
    currentMod = await fetchJSON(CONFIG.dataUrl + "/" + modId + ".json");
    versions = (currentMod.versions || []).slice().sort((a, b) => (b.date_published || "").localeCompare(a.date_published || ""));

    const releaseVers = versions.filter(v => (v.version_type || "release") === "release");
    const defaultV = releaseVers[0] || versions[0];
    if (defaultV) {
      selectedVersion = ((defaultV.game_versions || []).sort().reverse())[0] || "";
      selectedLoader = (defaultV.loaders || [])[0] || "";
    }

    try {
      const r = await fetch(ISSUES_API + "?state=open&labels=mod:" + encodeURIComponent(modId) + "&per_page=30&sort=updated");
      if (r.ok) issues = await r.json(); else issues = [];
    } catch(e) { issues = []; }

    document.title = escapeHtml(currentMod.name) + " — ModItamio";
    render();
  } catch (e) {
    root.innerHTML = '<div class="empty"><p>Mod not found: ' + escapeHtml(e.message) + '</p><a class="btn" href="mods.html">' + ICONS.chevron_left + ' Back to mods</a></div>';
  }
}

function getAvailableGameVersions() {
  let vers = versions;
  if (selectedLoader) vers = vers.filter(v => (v.loaders || []).includes(selectedLoader));
  const set = new Set();
  vers.forEach(v => (v.game_versions || []).forEach(gv => set.add(gv)));
  return [...set].sort(versionCompare).reverse();
}

function getAvailableLoaders() {
  let vers = versions;
  if (selectedVersion) vers = vers.filter(v => (v.game_versions || []).includes(selectedVersion));
  const set = new Set();
  vers.forEach(v => (v.loaders || []).forEach(l => set.add(l)));
  return [...set];
}

function getSelectedVersion() {
  let matches = versions;
  if (selectedVersion) matches = matches.filter(v => (v.game_versions || []).includes(selectedVersion));
  if (selectedLoader) matches = matches.filter(v => (v.loaders || []).includes(selectedLoader));
  matches.sort((a, b) => (b.date_published || "").localeCompare(a.date_published || ""));
  const release = matches.find(v => (v.version_type || "release") === "release");
  return release || matches[0] || null;
}

function render() {
  const root = document.getElementById("app");
  const iconUrl = currentMod.icon_url || CONFIG.modsAssetBase + "/" + currentMod.mod_id + "/icon.png";
  const letter = (currentMod.name || "M")[0].toUpperCase();
  const gallery = currentMod.gallery || [];

  const links = [];
  if (currentMod.source_url) links.push({ icon: ICONS.github, label: "Source", url: currentMod.source_url });
  if (currentMod.wiki_url) links.push({ icon: ICONS.book, label: "Wiki", url: currentMod.wiki_url });
  if (currentMod.discord_url) links.push({ icon: ICONS.discord, label: "Discord", url: currentMod.discord_url });
  const issuesUrl = "https://github.com/" + CONFIG.repoOwner + "/" + CONFIG.repoName + "/issues?q=is%3Aissue+label%3Amod:" + currentMod.mod_id;

  const gameVers = getAvailableGameVersions();
  const loaders = getAvailableLoaders();
  const selVer = getSelectedVersion();
  const selFiles = selVer ? (selVer.files || []) : [];
  const primaryFile = selFiles.find(f => f.primary) || selFiles[0];

  if (selectedVersion && !gameVers.includes(selectedVersion)) selectedVersion = gameVers[0] || "";
  if (selectedLoader && !loaders.includes(selectedLoader)) selectedLoader = loaders[0] || "";

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
  '</div>' +

  '<div class="proj-meta-row">' +
    '<span class="proj-meta__item">' + ICONS.download + ' ' + formatNumber(currentMod.downloads) + ' downloads</span>' +
    '<span class="proj-meta__item">' + ICONS.heart + ' ' + formatNumber(currentMod.followers) + ' followers</span>' +
    '<span class="proj-meta__item">' + ICONS.clock + ' Updated ' + timeAgo(currentMod.updated || currentMod.date_published) + '</span>' +
    (currentMod.author ? '<span class="proj-meta__item">by <strong>' + escapeHtml(currentMod.author) + '</strong></span>' : "") +
    links.map(l => '<a class="proj-meta__link" href="' + escapeHtml(l.url) + '" target="_blank" rel="noopener">' + l.icon + ' ' + escapeHtml(l.label) + ' ' + ICONS.external + '</a>').join("") +
    '<a class="proj-meta__link" href="' + issuesUrl + '" target="_blank" rel="noopener">' + ICONS.alert + ' Issues ' + ICONS.external + '</a>' +
  '</div>' +

  '<div class="proj-layout">' +
    '<div class="proj-main">' +
      '<div class="proj-tabs">' +
        renderTab("description", "Description") +
        (gallery.length ? renderTab("gallery", "Gallery (" + gallery.length + ")") : "") +
        renderTab("versions", "Versions (" + versions.length + ")") +
        (versions.some(v => v.changelog) ? renderTab("changelog", "Changelog") : "") +
        renderTab("issues", "Issues" + (issues.length ? " (" + issues.length + ")" : "")) +
      '</div>' +
      '<div class="proj-body" id="proj-body"></div>' +
    '</div>' +
    '<div class="proj-side">' + renderSidebar(selVer, primaryFile, gameVers, loaders) + '</div>' +
  '</div>';

  renderTabContent();
}

function renderTab(id, label) {
  return '<button class="proj-tab' + (activeTab === id ? " proj-tab--active" : "") + '" data-tab="' + id + '" onclick="switchTab(\'' + id + '\')">' + label + '</button>';
}

function switchTab(tab) {
  activeTab = tab;
  document.querySelectorAll(".proj-tab").forEach(t => t.classList.toggle("proj-tab--active", t.dataset.tab === tab));
  renderTabContent();
}

function renderSidebar(selVer, primaryFile, gameVers, loaders) {
  const verLabel = selVer ? escapeHtml(selVer.name || selVer.version_number) : "No version";
  const verDate = selVer ? formatDate(selVer.date_published) : "";
  const verType = selVer ? (selVer.version_type || "release") : "release";
  const files = selVer ? (selVer.files || []) : [];

  let html = '<div class="panel"><div class="panel__body" style="padding:var(--space-4)">';

  if (primaryFile) {
    html += '<a class="btn btn--lg btn--primary btn--block" href="' + escapeHtml(primaryFile.url) + '" download style="margin-bottom:var(--space-3)">' +
      '<span class="btn__icon">' + ICONS.download + '</span>' +
      '<span class="btn__label">Download ' + escapeHtml(primaryFile.filename || "") + '</span>' +
    '</a>';
  } else {
    html += '<button class="btn btn--lg btn--primary btn--block" disabled style="margin-bottom:var(--space-3);opacity:0.5">' +
      '<span class="btn__icon">' + ICONS.download + '</span>' +
      '<span class="btn__label">No file available</span>' +
    '</button>';
  }

  html += '<div class="sf-section">' +
    '<label class="sf-label">Game version</label>' +
    '<select class="form-select form-select--block" id="sf-version" onchange="selectSidebarVersion(this.value)">';
  for (const v of gameVers) {
    html += '<option value="' + escapeHtml(v) + '"' + (v === selectedVersion ? " selected" : "") + '>' + escapeHtml(v) + '</option>';
  }
  html += '</select></div>';

  html += '<div class="sf-section">' +
    '<label class="sf-label">Loader</label>' +
    '<div class="sf-loaders">';
  for (const l of loaders) {
    const active = l === selectedLoader;
    html += '<button class="sf-loader' + (active ? " sf-loader--active" : "") + '" data-loader="' + escapeHtml(l) + '" onclick="selectSidebarLoader(\'' + escapeHtml(l) + '\')">' +
      '<span class="sf-loader__dot" style="background:' + (active ? "var(--color-brand)" : "var(--color-text-dim)") + '"></span>' +
      escapeHtml(LOADER_NAMES[l] || l) +
    '</button>';
  }
  html += '</div></div>';

  if (selVer) {
    html += '<div class="sf-divider"></div>';
    html += '<div class="sf-verinfo">';
    html += '<div class="sf-verinfo__row"><span class="sf-verinfo__key">Version</span><span class="sf-verinfo__val" style="font-weight:600">' + verLabel + '</span></div>';
    html += '<div class="sf-verinfo__row"><span class="sf-verinfo__key">Type</span>' + versionBadge(verType) + '</div>';
    html += '<div class="sf-verinfo__row"><span class="sf-verinfo__key">Released</span><span class="sf-verinfo__val">' + verDate + '</span></div>';
    html += '<div class="sf-verinfo__row"><span class="sf-verinfo__key">Downloads</span><span class="sf-verinfo__val">' + ICONS.download + ' ' + formatNumber(selVer.downloads || 0) + '</span></div>';
    if (files.length) {
      const totalSize = files.reduce((s, f) => s + (f.size || 0), 0);
      html += '<div class="sf-verinfo__row"><span class="sf-verinfo__key">Files</span><span class="sf-verinfo__val">' + files.length + ' file' + (files.length !== 1 ? "s" : "") + (totalSize ? " (" + formatBytes(totalSize) + ")" : "") + '</span></div>';
    }
    html += '</div>';

    if (files.length > 0) {
      html += '<div class="sf-divider"></div>';
      html += '<div class="sf-files-title">Files</div>';
      html += '<div class="sf-files">';
      for (const f of files) {
        html += '<a class="sf-file" href="' + escapeHtml(f.url) + '" download>' +
          '<div style="min-width:0;flex:1">' +
            '<div class="sf-file__name">' + escapeHtml(f.filename || "file") + '</div>' +
            '<div class="sf-file__size">' + formatBytes(f.size) + (f.primary ? ' • <span style="color:var(--color-brand)">primary</span>' : "") + '</div>' +
          '</div>' +
          '<span class="sf-file__dl">' + ICONS.download + '</span>' +
        '</a>';
      }
      html += '</div>';
    }

    html += '<div style="margin-top:var(--space-3);display:flex;gap:var(--space-2);flex-wrap:wrap">';
    html += '<a class="btn btn--sm" href="version.html?id=' + encodeURIComponent(currentMod.mod_id) + '&v=' + encodeURIComponent(selVer.version_number) + '&l=' + encodeURIComponent(selectedLoader || "") + '" style="flex:1;justify-content:center">' + ICONS.info + ' View details</a>';
    html += '</div>';
  }

  html += '</div></div>';

  html += '<div class="panel" style="margin-top:var(--space-3)"><div class="panel__body" style="padding:var(--space-4)">';
  html += '<a class="btn btn--block" href="' + ISSUES_NEW_URL + '?labels=mod:' + escapeHtml(currentMod.mod_id) + '&title=' + encodeURIComponent("[" + currentMod.name + "] ") + '" target="_blank" rel="noopener"><span class="btn__icon">' + ICONS.alert + '</span><span class="btn__label">Report issue</span></a>';
  html += '</div></div>';

  return html;
}

function selectSidebarVersion(v) {
  selectedVersion = v;
  const newLoaders = getAvailableLoaders();
  if (selectedLoader && !newLoaders.includes(selectedLoader)) selectedLoader = newLoaders[0] || "";
  render();
}

function selectSidebarLoader(l) {
  selectedLoader = l;
  const newVers = getAvailableGameVersions();
  if (selectedVersion && !newVers.includes(selectedVersion)) selectedVersion = newVers[0] || "";
  render();
}

function renderTabContent() {
  const body = document.getElementById("proj-body");
  if (!body) return;
  switch (activeTab) {
    case "description": body.innerHTML = renderDescription(); break;
    case "gallery": body.innerHTML = renderGallery(); break;
    case "versions": body.innerHTML = renderVersions(); break;
    case "changelog": body.innerHTML = renderChangelog(); break;
    case "issues": body.innerHTML = renderIssues(); break;
  }
}

function renderDescription() {
  const desc = currentMod.body || currentMod.description || currentMod.summary || "_No description provided._";
  return '<div class="panel"><div class="panel__body prose">' + renderMarkdown(desc) + '</div></div>';
}

function renderGallery() {
  const g = currentMod.gallery || [];
  if (!g.length) return '<div class="empty"><p>No gallery images.</p></div>';
  return '<div class="gallery-grid">' + g.map((img, i) =>
    '<a href="' + escapeHtml(img.url) + '" target="_blank" rel="noopener" class="gallery-item"><img src="' + escapeHtml(img.url) + '" alt="' + escapeHtml(img.title || "Gallery image " + (i+1)) + '" loading="lazy"></a>'
  ).join("") + '</div>';
}

function getFilteredVersions() {
  let list = [...versions];
  if (vFilter.loader) list = list.filter(v => (v.loaders || []).includes(vFilter.loader));
  if (vFilter.version) list = list.filter(v => (v.game_versions || []).includes(vFilter.version));
  return list;
}

function renderVersions() {
  const allLoaders = [...new Set(versions.flatMap(v => v.loaders || []))];
  const allVersions = [...new Set(versions.flatMap(v => v.game_versions || []))].sort(versionCompare).reverse();
  const filtered = getFilteredVersions();

  return '<div class="version-filters">' +
    '<select class="form-select" onchange="vFilter.loader=this.value;renderTabContent()"><option value="">All loaders</option>' +
      allLoaders.map(l => '<option value="' + l + '"' + (vFilter.loader === l ? " selected" : "") + '>' + escapeHtml(LOADER_NAMES[l] || l) + '</option>').join("") +
    '</select>' +
    '<select class="form-select" onchange="vFilter.version=this.value;renderTabContent()"><option value="">All versions</option>' +
      allVersions.map(v => '<option value="' + escapeHtml(v) + '"' + (vFilter.version === v ? " selected" : "") + '>' + escapeHtml(v) + '</option>').join("") +
    '</select>' +
    '<button class="btn btn--sm" onclick="vFilter={loader:\'\',version:\'\'};renderTabContent()" ' + (!vFilter.loader && !vFilter.version ? "disabled" : "") + '>Clear</button>' +
  '</div>' +
  (!filtered.length ? '<div class="empty"><p>No versions match the filters.</p></div>' :
  '<div class="vlist">' + filtered.map(v => renderVersionRow(v)).join("") + '</div>');
}

function renderVersionRow(v) {
  const files = v.files || [];
  const primary = files.find(f => f.primary) || files[0];
  const isSelected = (v.game_versions || []).includes(selectedVersion) && (v.loaders || []).includes(selectedLoader);
  return '<div class="vrow' + (isSelected ? " vrow--selected" : "") + '">' +
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
  const versionsWithChangelog = versions.filter(v => v.changelog);
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

function renderIssues() {
  const modIssues = issues.filter(issue => {
    const labels = (issue.labels || []).map(l => typeof l === "string" ? l : l.name);
    return labels.some(l => l === "mod:" + currentMod.mod_id);
  });
  const newIssueUrl = ISSUES_NEW_URL + "?labels=mod:" + encodeURIComponent(currentMod.mod_id) + "&title=" + encodeURIComponent("[" + currentMod.name + "] ");

  return '<div>' +
      '<div class="panel"><div class="panel__body">' +
        '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-4)">' +
          '<h3 style="font-size:var(--fs-lg);font-weight:700;margin:0;color:var(--color-text-bright)">Open issues</h3>' +
          '<a class="btn btn--primary" href="' + newIssueUrl + '" target="_blank" rel="noopener"><span class="btn__icon">' + ICONS.alert + '</span><span class="btn__label">New issue</span></a>' +
        '</div>' +
        (modIssues.length === 0 ? '<div class="empty" style="padding:var(--space-8)"><p style="margin-bottom:var(--space-2)">No open issues for this mod.</p><a class="btn" href="' + newIssueUrl + '" target="_blank" rel="noopener">Report one</a></div>' :
        '<div class="issue-list">' + modIssues.map(renderIssueRow).join("") + '</div>') +
      '</div></div>' +
  '</div>';
}

function renderIssueRow(issue) {
  const labels = (issue.labels || []).map(l => {
    const name = typeof l === "string" ? l : l.name;
    if (name.startsWith("mod:")) return "";
    return '<span class="issue-label" style="--label:#' + (typeof l === "string" ? "555" : l.color || "555") + '">' + escapeHtml(name) + '</span>';
  }).filter(Boolean).join("");
  return '<a class="issue-row" href="' + escapeHtml(issue.html_url) + '" target="_blank" rel="noopener">' +
    '<span class="issue-row__icon" style="color:var(--color-brand)">' + ICONS.alert + '</span>' +
    '<div style="min-width:0;flex:1">' +
      '<div class="issue-row__title">' + escapeHtml(issue.title) + '</div>' +
      '<div class="issue-row__meta">#' + issue.number + ' opened ' + timeAgo(issue.created_at) + ' by ' + escapeHtml(issue.user?.login || "unknown") + '</div>' +
      (labels ? '<div class="issue-row__labels">' + labels + '</div>' : "") +
    '</div>' +
    '<span class="issue-row__cmt">' + ICONS.chevron_right + '</span>' +
  '</a>';
}

function versionCompare(a, b) {
  const pa = String(a).split(".").map(p => { const n = parseInt(p); return isNaN(n) ? 0 : n; });
  const pb = String(b).split(".").map(p => { const n = parseInt(p); return isNaN(n) ? 0 : n; });
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const x = pa[i] || 0, y = pb[i] || 0;
    if (x !== y) return x - y;
  }
  return 0;
}

document.addEventListener("DOMContentLoaded", init);
