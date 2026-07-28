"use strict";

let versionInfo = null;
let currentMod = null;
let vNumber = "";
let vLoader = "";

async function init() {
  const params = new URLSearchParams(location.search);
  const modId = params.get("id");
  vNumber = params.get("v") || "";
  vLoader = normLoader(params.get("l") || "");

  renderNavbar("mods");
  const root = document.getElementById("app");

  if (!modId || !vNumber) {
    root.innerHTML = '<div class="empty"><p>Missing parameters.</p><a class="btn" href="' + PATHS.browse + '">' + ICONS.chevron_left + ' Back to mods</a></div>';
    return;
  }

  root.innerHTML = '<div class="loading">Loading…</div>';

  try {
    currentMod = await fetchJSON(CONFIG.dataUrl + "/" + modId + ".json");
    let versions = currentMod.versions || [];
    versionInfo = versions.find(v => v.version_number === vNumber && (!vLoader || (v.loaders||[]).includes(vLoader)));
    if (!versionInfo) versionInfo = versions.find(v => v.version_number === vNumber);
    if (!versionInfo) throw new Error("Version not found");

    document.title = escapeHtml(versionInfo.name || versionInfo.version_number) + " — " + escapeHtml(currentMod.name);
    root.innerHTML = renderVersionHtml();
  } catch (e) {
    root.innerHTML = '<div class="empty"><p>' + escapeHtml(e.message) + '</p><a class="btn" href="' + PATHS.mod + '?id=' + encodeURIComponent(modId) + '">' + ICONS.chevron_left + ' Back to mod</a></div>';
  }
}

function renderVersionHtml() {
  if (!versionInfo || !currentMod) return '<div class="empty"><p>Version not found.</p></div>';
  const v = versionInfo;
  const iconUrl = sanitizeUrl(currentMod.icon_url) || CONFIG.modsAssetBase + "/" + currentMod.mod_id + "/icon.png";
  const letter = (currentMod.name || "M")[0].toUpperCase();
  const files = v.files || [];
  const primaryFile = files.find(f => f.primary) || files[0];

  return '<div class="proj-header">' +
    '<div class="proj-header__main">' +
      '<div class="proj-icon-wrap">' +
        '<img class="proj-header__icon" src="' + escapeHtml(iconUrl) + '" alt="" loading="lazy" onerror="this.style.display=\'none\';this.nextElementSibling.style.display=\'flex\'">' +
        '<div class="proj-icon-fallback" style="display:none">' + escapeHtml(letter) + '</div>' +
      '</div>' +
      '<div class="proj-header__info">' +
        '<div style="display:flex;align-items:center;gap:var(--space-2);margin-bottom:var(--space-1)">' +
          '<a href="' + PATHS.mod + '?id=' + encodeURIComponent(currentMod.mod_id) + '" style="color:var(--color-text-dim);font-size:var(--fs-sm);text-decoration:none;display:flex;align-items:center;gap:var(--space-1)">' + ICONS.chevron_left + ' ' + escapeHtml(currentMod.name) + '</a>' +
        '</div>' +
        '<h1 class="proj-header__title">' + escapeHtml(v.name || v.version_number) + '</h1>' +
        '<div class="proj-header__tags">' + versionBadge(v.version_type) + renderLoaderTags(v.loaders) + renderVersionTags(v.game_versions) + '</div>' +
      '</div>' +
    '</div>' +
    '<div class="proj-header__actions">' +
      (primaryFile ? '<a class="btn btn--lg btn--primary" href="' + escapeHtml(primaryFile.url) + '" download><span class="btn__icon">' + ICONS.download + '</span><span class="btn__label">Download</span></a>' : "") +
      (files.length > 1 ? '<div class="btn-group" style="position:relative"><button class="btn btn--lg" onclick="this.nextElementSibling.style.display=this.nextElementSibling.style.display===\'none\'?\'block\':\'none\'">' + ICONS.chevron_down + '</button><div class="menu" style="display:none;right:0;top:110%">' + files.map(f => '<a class="menu__item" href="' + escapeHtml(f.url) + '" download>' + escapeHtml(f.filename || "Download") + ' (' + formatBytes(f.size) + ')</a>').join("") + '</div></div>' : "") +
    '</div>' +
  '</div>' +

  '<div class="vtabs"><div class="vtabs__item vtabs__item--active">Details</div></div>' +

  '<div class="version-detail-grid">' +
    '<div>' +
      '<div class="panel"><div class="panel__body prose">' +
        '<div class="changelog-text">' + renderMarkdown(v.changelog || "_No changelog provided._") + '</div>' +
      '</div></div>' +
    '</div>' +
    '<div class="version-side">' +
      '<div class="panel"><div class="panel__body" style="padding:var(--space-4)">' +
        '<h3 class="panel__title" style="margin-bottom:var(--space-3);font-size:var(--fs-base)">Files</h3>' +
        '<div style="display:flex;flex-direction:column;gap:var(--space-2)">' +
          (files.length ? files.map(f => '<div style="display:flex;justify-content:space-between;align-items:center;gap:var(--space-2)"><div style="min-width:0;flex:1"><div style="font-size:var(--fs-sm);font-weight:600;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">' + escapeHtml(f.filename || "file") + '</div><div style="font-size:var(--fs-xs);color:var(--color-text-dim)">' + formatBytes(f.size) + '</div></div><a class="btn btn--sm btn--icon" href="' + escapeHtml(f.url) + '" download title="Download">' + ICONS.download + '</a></div>').join("") : '<div style="font-size:var(--fs-sm);color:var(--color-text-dim)">No files available</div>') +
        '</div>' +
      '</div></div>' +
      '<div class="panel"><div class="panel__body" style="padding:var(--space-4)">' +
        '<h3 class="panel__title" style="margin-bottom:var(--space-3);font-size:var(--fs-base)">Metadata</h3>' +
        '<div style="display:flex;flex-direction:column;gap:var(--space-3);font-size:var(--fs-sm)">' +
          metaRow("Version number", '<div style="font-weight:600;font-family:var(--font-mono)">' + escapeHtml(v.version_number) + '</div>') +
          metaRow("Release type", versionBadge(v.version_type)) +
          metaRow("Loaders", '<div style="display:flex;flex-wrap:wrap;gap:var(--space-1)">' + renderLoaderTags(v.loaders) + '</div>') +
          metaRow("Versions", '<div style="display:flex;flex-wrap:wrap;gap:var(--space-1)">' + renderVersionTags(v.game_versions) + '</div>') +
          metaRow("Downloads", ICONS.download + " " + formatNumber(v.downloads || 0)) +
          metaRow("Published", formatDate(v.date_published)) +
        '</div>' +
      '</div></div>' +
    '</div>' +
  '</div>';
}

function metaRow(label, value) {
  return '<div><div style="color:var(--color-text-dim);font-size:var(--fs-xs);text-transform:uppercase;letter-spacing:0.05em;margin-bottom:2px">' + label + '</div><div>' + value + '</div></div>';
}

document.addEventListener("DOMContentLoaded", init);
