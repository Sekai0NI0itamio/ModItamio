"use strict";

let currentMod = null;
let versions = [];
let issues = [];
let activeTab = "description";
let vFilter = { loader: "", version: "" };

async function init() {
  const params = new URLSearchParams(location.search);
  const modId = params.get("id");
  if (!modId) { document.getElementById("app").innerHTML = '<div class="empty"><p>Missing mod ID.</p></div>'; return; }

  renderNavbar("mods");
  const root = document.getElementById("app");
  root.innerHTML = '<div class="loading">Loading…</div>';

  try {
    currentMod = await fetchJSON(CONFIG.dataUrl + "/mods/" + modId + ".json");
    try { versions = await fetchJSON(CONFIG.dataUrl + "/mods/" + modId + "/versions.json"); } catch(e) { versions = []; }
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

function render() {
  const root = document.getElementById("app");
  const iconUrl = currentMod.icon_url || CONFIG.modsAssetBase + "/" + currentMod.mod_id + "/icon.png";
  const letter = (currentMod.name || "M")[0].toUpperCase();
  const gallery = currentMod.gallery || [];
  const primaryFile = getPrimaryFile();

  const links = [];
  if (currentMod.source_url) links.push({ icon: ICONS.github, label: "Source", url: currentMod.source_url });
  if (currentMod.issues_url) links.push({ icon: ICONS.alert, label: "Issues", url: currentMod.issues_url });
  if (currentMod.wiki_url) links.push({ icon: ICONS.book, label: "Wiki", url: currentMod.wiki_url });
  if (currentMod.discord_url) links.push({ icon: ICONS.discord, label: "Discord", url: currentMod.discord_url });

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
      (primaryFile ? '<a class="btn btn--lg btn--primary" href="' + escapeHtml(primaryFile.url) + '" download><span class="btn__icon">' + ICONS.download + '</span><span class="btn__label">Download</span></a>' : "") +
      '<a class="btn btn--lg" href="' + ISSUES_NEW_URL + '?labels=mod:' + escapeHtml(currentMod.mod_id) + '&title=' + encodeURIComponent("[" + currentMod.name + "] ") + '" target="_blank" rel="noopener"><span class="btn__icon">' + ICONS.alert + '</span><span class="btn__label">Report issue</span></a>' +
    '</div>' +
  '</div>' +

  '<div class="proj-meta-row">' +
    '<span class="proj-meta__item">' + ICONS.download + ' ' + formatNumber(currentMod.downloads) + ' downloads</span>' +
    '<span class="proj-meta__item">' + ICONS.heart + ' ' + formatNumber(currentMod.followers) + ' followers</span>' +
    '<span class="proj-meta__item">' + ICONS.clock + ' Updated ' + timeAgo(currentMod.updated || currentMod.date_published) + '</span>' +
    (currentMod.author ? '<span class="proj-meta__item">by <strong>' + escapeHtml(currentMod.author) + '</strong></span>' : "") +
    links.map(l => '<a class="proj-meta__link" href="' + escapeHtml(l.url) + '" target="_blank" rel="noopener">' + l.icon + ' ' + escapeHtml(l.label) + ' ' + ICONS.external + '</a>').join("") +
  '</div>' +

  '<div class="proj-tabs">' +
    renderTab("description", "Description") +
    (gallery.length ? renderTab("gallery", "Gallery (" + gallery.length + ")") : "") +
    renderTab("versions", "Versions (" + versions.length + ")") +
    (currentMod.changelog ? renderTab("changelog", "Changelog") : "") +
    renderTab("issues", "Issues" + (issues.length ? " (" + issues.length + ")" : "")) +
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
  renderTabContent();
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
  if (vFilter.loader) list = list.filter(v => (v.loaders || []).includes(vFilter.loader));
  if (vFilter.version) list = list.filter(v => (v.game_versions || []).includes(vFilter.version));
  list.sort((a, b) => (b.date_published || "").localeCompare(a.date_published || ""));
  return list;
}

function renderVersions() {
  const allLoaders = [...new Set(versions.flatMap(v => v.loaders || []))];
  const allVersions = [...new Set(versions.flatMap(v => v.game_versions || []))].sort().reverse();
  const filtered = getFilteredVersions();

  return '<div class="version-filters">' +
    '<select class="form-select" onchange="vFilter.loader=this.value;renderTabContent()"><option value="">All loaders</option>' +
      allLoaders.map(l => '<option value="' + l + '"' + (vFilter.loader === l ? " selected" : "") + '>' + escapeHtml(LOADER_NAMES[l] || l) + '</option>').join("") +
    '</select>' +
    '<select class="form-select" onchange="vFilter.version=this.value;renderTabContent()"><option value="">All versions</option>' +
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
  return '<div class="panel"><div class="panel__body prose changelog-text">' + renderMarkdown(currentMod.changelog || "_No changelog provided._") + '</div></div>';
}

function renderIssues() {
  const modIssues = issues.filter(issue => {
    const labels = (issue.labels || []).map(l => typeof l === "string" ? l : l.name);
    return labels.some(l => l === "mod:" + currentMod.mod_id);
  });
  const newIssueUrl = ISSUES_NEW_URL + "?labels=mod:" + encodeURIComponent(currentMod.mod_id) + "&title=" + encodeURIComponent("[" + currentMod.name + "] ");

  return '<div class="issues-layout">' +
    '<div>' +
      '<div class="panel"><div class="panel__body">' +
        '<div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:var(--space-4)">' +
          '<h3 style="font-size:var(--fs-lg);font-weight:700;margin:0;color:var(--color-text-bright)">Open issues</h3>' +
          '<a class="btn btn--primary" href="' + newIssueUrl + '" target="_blank" rel="noopener"><span class="btn__icon">' + ICONS.alert + '</span><span class="btn__label">New issue</span></a>' +
        '</div>' +
        (modIssues.length === 0 ? '<div class="empty" style="padding:var(--space-8)"><p style="margin-bottom:var(--space-2)">No open issues for this mod.</p><a class="btn" href="' + newIssueUrl + '" target="_blank" rel="noopener">Report one</a></div>' :
        '<div class="issue-list">' + modIssues.map(renderIssueRow).join("") + '</div>') +
      '</div></div>' +
    '</div>' +
    '<div class="issues-sidebar">' +
      '<div class="panel"><div class="panel__body" style="padding:var(--space-4)">' +
        '<h3 style="font-size:var(--fs-base);font-weight:700;margin:0 0 var(--space-3);color:var(--color-text-bright)">Quick report</h3>' +
        '<form id="quick-issue-form" onsubmit="submitQuickIssue(event)">' +
          '<label style="display:block;font-size:var(--fs-sm);font-weight:600;color:var(--color-text-bright);margin-bottom:var(--space-1)">Title</label>' +
          '<input class="form-input" type="text" id="qi-title" placeholder="Brief summary of the issue..." required style="width:100%;margin-bottom:var(--space-3)">' +
          '<label style="display:block;font-size:var(--fs-sm);font-weight:600;color:var(--color-text-bright);margin-bottom:var(--space-1)">Description</label>' +
          '<textarea class="form-input" id="qi-body" rows="4" placeholder="Steps to reproduce, expected behavior, etc..." style="width:100%;margin-bottom:var(--space-3);resize:vertical"></textarea>' +
          '<button type="submit" class="btn btn--primary" style="width:100%">Submit on GitHub</button>' +
        '</form>' +
        '<div id="qi-result" style="margin-top:var(--space-2);font-size:var(--fs-sm)"></div>' +
      '</div></div>' +
    '</div>' +
  '</div>';
}

function renderIssueRow(issue) {
  const labels = (issue.labels || []).map(l => {
    const name = typeof l === "string" ? l : l.name;
    const color = typeof l === "string" ? "#555" : "#" + (l.color || "555");
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

function submitQuickIssue(e) {
  e.preventDefault();
  const title = document.getElementById("qi-title").value.trim();
  const body = document.getElementById("qi-body").value.trim();
  if (!title) return;
  const url = ISSUES_NEW_URL + "?labels=" + encodeURIComponent("mod:" + currentMod.mod_id) + "&title=" + encodeURIComponent(title) + "&body=" + encodeURIComponent(body || "");
  window.open(url, "_blank");
  document.getElementById("qi-result").innerHTML = '<span style="color:var(--color-brand)">' + ICONS.check + ' Opened GitHub in new tab</span>';
}

document.addEventListener("DOMContentLoaded", init);
