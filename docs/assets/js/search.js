"use strict";

const PAGE_SIZE = 20;
const SORT_OPTIONS = [
  { value: "downloads", label: "Most Downloads" },
  { value: "followers", label: "Most Followers" },
  { value: "updated", label: "Recently Updated" },
  { value: "newest", label: "Newest" },
  { value: "name", label: "Name A-Z" },
];
let ALL_MODS = [];
let PARAMS = { q: "", c: [], l: [], v: [], s: "downloads", p: 1 };
let DRAFT_Q = "";
let FACET_SEARCH = { cat: "", loader: "", version: "" };
let _activeElement = null;
let _activeSelection = { start: 0, end: 0 };
let _savedScroll = 0;

function compareVersions(a, b) {
  const pa = a.split(".").map(p => { const n = parseInt(p, 10); return isNaN(n) ? p : n; });
  const pb = b.split(".").map(p => { const n = parseInt(p, 10); return isNaN(n) ? p : n; });
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const ai = i < pa.length ? pa[i] : 0;
    const bi = i < pb.length ? pb[i] : 0;
    if (typeof ai === "number" && typeof bi === "number") {
      if (ai !== bi) return ai - bi;
    } else {
      const as = String(ai), bs = String(bi);
      if (as !== bs) return as < bs ? -1 : 1;
    }
  }
  return 0;
}

async function init() {
  renderNavbar("mods");
  const root = document.getElementById("app");
  root.innerHTML = renderSkeleton();

  try {
    ALL_MODS = await loadAllMods();
    readUrl();
    render();
    syncSearchBox();
  } catch (e) {
    root.innerHTML = '<section class="search-page"><div class="search-page__empty">' +
      '<div class="search-page__empty-icon">' + ICONS.alert + '</div>' +
      '<h2 class="search-page__empty-title">Could not load mods</h2>' +
      '<p class="search-page__empty-text">' + escapeHtml(e.message) + '</p>' +
    '</div></section>';
  }
}

function renderSkeleton() {
  let cards = "";
  for (let i = 0; i < 6; i++) {
    cards += '<div class="skel-card">' +
      '<div class="skel skel-card__show"></div>' +
      '<div class="skel-card__body">' +
        '<div class="skel skel-line skel-line--title"></div>' +
        '<div class="skel skel-line"></div>' +
        '<div class="skel skel-line skel-line--short"></div>' +
        '<div class="skel skel-line skel-line--meta"></div>' +
      '</div>' +
    '</div>';
  }
  return '<section class="search-page">' +
    '<div class="search-page__header">' +
      '<div class="skel skel-kicker"></div>' +
      '<div class="skel skel-title"></div>' +
      '<div class="skel skel-sub"></div>' +
      '<div class="skel skel-search"></div>' +
    '</div>' +
    '<div class="search-page__layout">' +
      '<aside class="search-page__sidebar">' +
        '<div class="sidebar-panel">' +
          '<div class="skel skel-panel-head"></div>' +
          '<div class="skel skel-group"></div>' +
          '<div class="skel skel-group"></div>' +
          '<div class="skel skel-group"></div>' +
        '</div>' +
      '</aside>' +
      '<div class="search-page__main"><div class="mod-grid">' + cards + '</div></div>' +
    '</div>' +
  '</section>';
}

function renderSidebarAd() {
  return '<div class="ad-slot ad-slot--sidebar">' +
    '<div class="ad-label">Sponsored</div>' +
    '<div class="ad-unit">' +
      '<div class="ad-unit__art"></div>' +
      '<div class="ad-unit__copy">' +
        '<strong>Your ad here</strong>' +
        'Reach eco-conscious Minecraft players and developers. Premium placement with natural brand alignment.' +
      '</div>' +
      '<a href="#" class="btn btn--sm" style="width:100%">Learn about advertising</a>' +
    '</div>' +
  '</div>';
}

function renderSearchInlineAd() {
  return '<div class="ad-slot ad-slot--card" aria-label="Advertisement">' +
    '<span class="ad-label">Sponsored</span>' +
    '<div class="inline-ad">' +
      '<div class="inline-ad__content">' +
        '<strong>Support independent modding</strong>' +
        '<span>Your support helps us keep Minecraft mods running smoothly for everyone.</span>' +
      '</div>' +
      '<a href="#" class="btn btn--sm btn--primary">Learn more</a>' +
    '</div>' +
  '</div>';
}

function readUrl() {
  const p = new URLSearchParams(location.search);
  PARAMS.q = p.get("q") || "";
  DRAFT_Q = PARAMS.q;
  PARAMS.c = p.getAll("c");
  PARAMS.l = p.getAll("l");
  PARAMS.v = p.getAll("v");
  PARAMS.s = p.get("s") || "downloads";
  PARAMS.p = Math.max(1, parseInt(p.get("p") || "1", 10));
}

function writeUrl() {
  const p = new URLSearchParams();
  if (PARAMS.q) p.set("q", PARAMS.q);
  PARAMS.c.forEach(c => p.append("c", c));
  PARAMS.l.forEach(l => p.append("l", l));
  PARAMS.v.forEach(v => p.append("v", v));
  if (PARAMS.s !== "downloads") p.set("s", PARAMS.s);
  if (PARAMS.p > 1) p.set("p", String(PARAMS.p));
  const qs = p.toString();
  history.replaceState(null, "", qs ? "?" + qs : location.pathname);
  syncSearchBox();
}

function syncSearchBox() {
  const ns = document.getElementById("nav-search");
  if (ns) ns.value = PARAMS.q;
  DRAFT_Q = PARAMS.q;
}

function saveState() {
  const ae = document.activeElement;
  if (ae && (ae.tagName === "INPUT" || ae.tagName === "TEXTAREA" || ae.tagName === "SELECT")) {
    _activeElement = ae.id || ae.name || null;
    if (ae.tagName === "INPUT" || ae.tagName === "TEXTAREA") {
      _activeSelection = { start: ae.selectionStart || 0, end: ae.selectionEnd || 0 };
    }
  } else {
    _activeElement = null;
  }
  _savedScroll = window.scrollY;
}

function restoreState() {
  window.scrollTo(0, _savedScroll);
  if (_activeElement) {
    const el = document.getElementById(_activeElement);
    if (el) {
      el.focus();
      try {
        if (el.tagName === "INPUT" || el.tagName === "TEXTAREA") {
          const len = el.value.length;
          const start = Math.min(_activeSelection.start, len);
          const end = Math.min(_activeSelection.end, len);
          el.setSelectionRange(start, end);
        }
      } catch (e) {}
    }
  }
}

function applyFilters(mods) {
  return mods.filter(m => {
    if (PARAMS.q && !modMatchesQuery(m, PARAMS.q)) return false;
    if (PARAMS.l.length && !(m.loaders || []).some(l => PARAMS.l.includes(normLoader(l)))) return false;
    if (PARAMS.v.length) {
      const mvs = m.game_versions || [];
      if (!PARAMS.v.some(v => mvs.includes(v))) return false;
    }
    if (PARAMS.c.length) {
      const mcs = (m.categories || []).map(getCategoryAlias);
      if (!PARAMS.c.every(c => mcs.includes(getCategoryAlias(c)))) return false;
    }
    return true;
  });
}

function sortMods(mods) {
  const sorted = [...mods];
  switch (PARAMS.s) {
    case "downloads": sorted.sort((a, b) => (b.downloads || 0) - (a.downloads || 0)); break;
    case "followers": sorted.sort((a, b) => (b.followers || 0) - (a.followers || 0)); break;
    case "updated": sorted.sort((a, b) => (b.updated || b.date_published || "").localeCompare(a.updated || a.date_published || "")); break;
    case "newest": sorted.sort((a, b) => (b.date_published || "").localeCompare(a.date_published || "")); break;
    case "name": sorted.sort((a, b) => (a.name || "").localeCompare(b.name || "")); break;
  }
  return sorted;
}

function facetCounts(mods) {
  const cats = {}, loaders = {}, versions = {};
  mods.forEach(m => {
    (m.categories || []).forEach(c => { const a = getCategoryAlias(c); cats[a] = (cats[a] || 0) + 1; });
    (m.loaders || []).forEach(l => { const k = normLoader(l); loaders[k] = (loaders[k] || 0) + 1; });
    (m.game_versions || []).forEach(v => { versions[v] = (versions[v] || 0) + 1; });
  });
  return { cats, loaders, versions };
}

function allFacetCounts() { return facetCounts(ALL_MODS); }

function toggleFacet(type, val) {
  saveState();
  const arr = PARAMS[type];
  const i = arr.indexOf(val);
  if (i >= 0) arr.splice(i, 1); else arr.push(val);
  PARAMS.p = 1;
  writeUrl(); render();
}

function setSort(s) {
  saveState();
  PARAMS.s = s; PARAMS.p = 1; writeUrl(); render();
}

function clearFilters() {
  saveState();
  PARAMS.q = ""; PARAMS.c = []; PARAMS.l = []; PARAMS.v = []; PARAMS.p = 1;
  DRAFT_Q = "";
  FACET_SEARCH = { cat: "", loader: "", version: "" };
  writeUrl(); render();
}

function removePill(type, val) {
  saveState();
  if (type === "q") {
    PARAMS.q = "";
    DRAFT_Q = "";
  } else {
    const arr = PARAMS[type];
    const i = arr.indexOf(val);
    if (i >= 0) arr.splice(i, 1);
  }
  PARAMS.p = 1;
  writeUrl(); render();
}

function goPage(p) { PARAMS.p = p; writeUrl(); render(); window.scrollTo({ top: 0, behavior: "smooth" }); }

function onMainSearchInput(e) {
  DRAFT_Q = e.target.value;
  const clearBtn = document.querySelector(".main-search-bar__clear");
  if (clearBtn) {
    clearBtn.style.display = DRAFT_Q ? "" : "none";
  } else if (DRAFT_Q) {
    const bar = e.target.closest(".main-search-bar");
    if (bar) {
      const btn = document.createElement("button");
      btn.className = "main-search-bar__clear";
      btn.innerHTML = ICONS.x;
      btn.onclick = clearMainSearch;
      bar.appendChild(btn);
    }
  }
}

function onMainSearchKeydown(e) {
  if (e.key === "Enter") {
    e.preventDefault();
    commitMainSearch();
  }
}

function commitMainSearch() {
  saveState();
  PARAMS.q = DRAFT_Q.trim();
  PARAMS.p = 1;
  writeUrl();
  render();
}

function onFacetSearchInput(type, e) {
  FACET_SEARCH[type] = e.target.value;
  renderSidebarFacets();
}

function buildSidebarHtml(hasFilters, allCounts) {
  function facetMatches(facetVal, searchTerm) {
    if (!searchTerm) return true;
    return facetVal.toLowerCase().includes(searchTerm.toLowerCase());
  }

  function renderFacetSearch(type, placeholder) {
    const val = FACET_SEARCH[type] || "";
    const label = type === "cat" ? "Filter categories" : type === "loader" ? "Filter loaders" : "Filter versions";
    return '<div class="facet-search">' +
      '<span class="facet-search__icon">' + ICONS.search + '</span>' +
      '<input type="text" class="facet-search__input" id="facet-search-' + type + '" placeholder="' + escapeHtml(placeholder) + '" aria-label="' + label + '" value="' + escapeHtml(val) + '" oninput="onFacetSearchInput(\'' + type + '\', event)">' +
      (val ? '<button class="facet-search__clear" aria-label="Clear search" onclick="clearFacetSearch(\'' + type + '\')">' + ICONS.x + '</button>' : '') +
    '</div>';
  }

  let catHtml = "";
  for (const [group, catNames] of Object.entries(CATEGORY_GROUPS)) {
    const groupCats = catNames.filter(c => (allCounts.cats[c] || 0) > 0);
    if (!groupCats.length) continue;
    const searchTerm = FACET_SEARCH.cat;
    const filteredCats = groupCats.filter(c => facetMatches(c, searchTerm));
    catHtml += '<div class="facet-group"><div class="facet-group__title">' + escapeHtml(CATEGORY_GROUP_NAMES[group] || group) + '</div>';
    if (groupCats.length > 6) {
      catHtml += renderFacetSearch("cat", "Filter categories…");
    }
    for (const c of filteredCats) {
      const checked = PARAMS.c.includes(c);
      catHtml += '<label class="facet-item"><input type="checkbox" name="c" value="' + escapeHtml(c) + '" ' + (checked ? "checked" : "") + ' onchange="toggleFacet(\'c\',\'' + escapeHtml(c) + '\')"><span class="facet-item__label">' + escapeHtml(c) + '</span><span class="facet-item__count">' + (allCounts.cats[c] || 0) + '</span></label>';
    }
    if (searchTerm && !filteredCats.length) {
      catHtml += '<div class="facet-empty">No matching categories</div>';
    }
    catHtml += '</div>';
  }

  const allLoaderKeys = LOADERS.filter(l => (allCounts.loaders[l] > 0));
  let loaderHtml = '<div class="facet-group"><div class="facet-group__title">Loaders</div>';
  if (allLoaderKeys.length > 4) {
    loaderHtml += renderFacetSearch("loader", "Filter loaders…");
  }
  const loaderSearch = FACET_SEARCH.loader;
  const filteredLoaders = allLoaderKeys.filter(l => {
    const name = (LOADER_NAMES[l] || l);
    return facetMatches(name, loaderSearch) || facetMatches(l, loaderSearch);
  });
  for (const l of filteredLoaders) {
    const checked = PARAMS.l.includes(l);
    loaderHtml += '<label class="facet-item"><input type="checkbox" name="l" value="' + l + '" ' + (checked ? "checked" : "") + ' onchange="toggleFacet(\'l\',\'' + l + '\')"><span class="facet-item__label">' + escapeHtml(LOADER_NAMES[l] || l) + '</span><span class="facet-item__count">' + (allCounts.loaders[l] || 0) + '</span></label>';
  }
  if (loaderSearch && !filteredLoaders.length) {
    loaderHtml += '<div class="facet-empty">No matching loaders</div>';
  }
  loaderHtml += '</div>';

  const allVersions = [...new Set(ALL_MODS.flatMap(m => m.game_versions || []))].sort(compareVersions).reverse();
  let versionHtml = '<div class="facet-group"><div class="facet-group__title">Game versions</div>';
  versionHtml += renderFacetSearch("version", "Filter versions…");
  const versionSearch = FACET_SEARCH.version;
  const filteredVersions = allVersions.filter(v => (allCounts.versions[v] > 0) && facetMatches(v, versionSearch));
  for (const v of filteredVersions) {
    const checked = PARAMS.v.includes(v);
    versionHtml += '<label class="facet-item"><input type="checkbox" name="v" value="' + escapeHtml(v) + '" ' + (checked ? "checked" : "") + ' onchange="toggleFacet(\'v\',\'' + escapeHtml(v) + '\')"><span class="facet-item__label">' + escapeHtml(v) + '</span><span class="facet-item__count">' + (allCounts.versions[v] || 0) + '</span></label>';
  }
  if (versionSearch && !filteredVersions.length) {
    versionHtml += '<div class="facet-empty">No matching versions</div>';
  }
  versionHtml += '</div>';

  const activeCount = (PARAMS.q ? 1 : 0) + PARAMS.c.length + PARAMS.l.length + PARAMS.v.length;
  return '<div class="sidebar-panel">' +
    '<div class="sidebar-panel__head">' +
      '<span class="sidebar-panel__title">Filters</span>' +
      (activeCount ? '<span class="sidebar-panel__badge">' + activeCount + '</span>' : '') +
      '<button class="clear-btn" ' + (hasFilters ? "" : "disabled") + ' onclick="clearFilters()" aria-label="Clear all filters">' + ICONS.filter + ' Clear</button>' +
    '</div>' +
    catHtml + loaderHtml + versionHtml +
    renderSidebarAd() +
  '</div>';
}

function renderSidebarFacets() {
  const sidebar = document.querySelector(".search-page__sidebar");
  if (!sidebar) return;
  const facetInput = document.activeElement;
  let facetId = null;
  let facetSelStart = 0;
  let facetSelEnd = 0;
  if (facetInput && facetInput.classList.contains("facet-search__input")) {
    facetId = facetInput.id;
    facetSelStart = facetInput.selectionStart || 0;
    facetSelEnd = facetInput.selectionEnd || 0;
  }
  const allCounts = allFacetCounts();
  const hasFilters = !!(PARAMS.q || PARAMS.c.length || PARAMS.l.length || PARAMS.v.length);
  sidebar.innerHTML = buildSidebarHtml(hasFilters, allCounts);
  if (facetId) {
    const el = document.getElementById(facetId);
    if (el) {
      el.focus();
      try { el.setSelectionRange(facetSelStart, facetSelEnd); } catch (e) {}
    }
  }
}

function renderFilterPills() {
  const pills = [];
  if (PARAMS.q) {
    pills.push('<button class="filter-pill" onclick="removePill(\'q\',\'\')"><span class="filter-pill__label">' + escapeHtml(PARAMS.q) + '</span><span class="filter-pill__remove">' + ICONS.x + '</span></button>');
  }
  PARAMS.c.forEach(c => {
    pills.push('<button class="filter-pill" onclick="removePill(\'c\',\'' + escapeHtml(c) + '\')"><span class="filter-pill__label">' + escapeHtml(c) + '</span><span class="filter-pill__remove">' + ICONS.x + '</span></button>');
  });
  PARAMS.l.forEach(l => {
    pills.push('<button class="filter-pill" onclick="removePill(\'l\',\'' + l + '\')"><span class="filter-pill__label">' + escapeHtml(LOADER_NAMES[l] || l) + '</span><span class="filter-pill__remove">' + ICONS.x + '</span></button>');
  });
  PARAMS.v.forEach(v => {
    pills.push('<button class="filter-pill" onclick="removePill(\'v\',\'' + escapeHtml(v) + '\')"><span class="filter-pill__label">' + escapeHtml(v) + '</span><span class="filter-pill__remove">' + ICONS.x + '</span></button>');
  });
  return '<div class="filter-pills">' + pills.join("") +
    '<button class="filter-pills__clear" aria-label="Clear all filters" onclick="clearFilters()">Clear all</button>' +
  '</div>';
}

function renderSortSelect() {
  const current = SORT_OPTIONS.find(o => o.value === PARAMS.s) || SORT_OPTIONS[0];
  return '<div class="sort-select">' +
    '<div class="sort-select__trigger" aria-hidden="true"><span>' + escapeHtml(current.label) + '</span>' + ICONS.chevron_down + '</div>' +
    '<select aria-label="Sort results" onchange="setSort(this.value)">' +
      SORT_OPTIONS.map(o => '<option value="' + o.value + '"' + (o.value === PARAMS.s ? " selected" : "") + '>' + escapeHtml(o.label) + '</option>').join("") +
    '</select>' +
  '</div>';
}

function renderEmptyState() {
  return '<div class="search-page__empty">' +
    '<div class="search-page__empty-icon">' + ICONS.search + '</div>' +
    '<h2 class="search-page__empty-title">No mods match your filters.</h2>' +
    '<p class="search-page__empty-text">Try adjusting your search terms or clearing some filters.</p>' +
  '</div>';
}

function render() {
  const root = document.getElementById("app");
  const filtered = sortMods(applyFilters(ALL_MODS));
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  PARAMS.p = Math.min(PARAMS.p, totalPages);
  const page = filtered.slice((PARAMS.p - 1) * PAGE_SIZE, PARAMS.p * PAGE_SIZE);

  const hasFilters = !!(PARAMS.q || PARAMS.c.length || PARAMS.l.length || PARAMS.v.length);
  const allCounts = allFacetCounts();

  resetCardIndex();
  const selLoader = PARAMS.l[0] || "";
  const selVer = PARAMS.v[0] || "";
  const extraParts = [];
  if (selLoader) extraParts.push("l=" + encodeURIComponent(selLoader));
  if (selVer) extraParts.push("g=" + encodeURIComponent(selVer));
  CARD_LINK_EXTRA = extraParts.join("&");
  let gridHtml = "";
  if (page.length) {
    const cards = page.map(modCard);
    const adInsertIndex = Math.min(6, cards.length);
    const cardsWithAd = [...cards.slice(0, adInsertIndex), renderSearchInlineAd(), ...cards.slice(adInsertIndex)];
    gridHtml = '<div class="mod-grid">' + cardsWithAd.join("") + '</div>';
  } else {
    gridHtml = renderEmptyState();
  }

  const searchVal = DRAFT_Q;
  const showClear = !!searchVal;

  // Save focused facet checkbox before innerHTML replace destroys focus
  const prevFocus = document.activeElement;
  let prevFacetName = null;
  let prevFacetValue = null;
  if (prevFocus && prevFocus.tagName === "INPUT" && prevFocus.type === "checkbox" && prevFocus.name) {
    prevFacetName = prevFocus.name;
    prevFacetValue = prevFocus.value;
  }

  root.innerHTML = '<section class="search-page">' +
    '<header class="search-page__header">' +
      '<p class="search-page__overline">' + ICONS.sprout + ' Browse the garden</p>' +
      '<h1 class="search-page__title">Explore Mods</h1>' +
      '<p class="search-page__subtitle">Find the right mod for your version and loader.</p>' +
      '<div class="main-search-bar">' +
        '<span class="main-search-bar__icon">' + ICONS.search + '</span>' +
        '<input type="text" id="main-search" class="main-search-bar__input" placeholder="Search mods…" value="' + escapeHtml(searchVal) + '" oninput="onMainSearchInput(event)" onkeydown="onMainSearchKeydown(event)" autocomplete="off" aria-label="Search mods">' +
        (showClear ? '<button class="main-search-bar__clear" onclick="clearMainSearch()" aria-label="Clear search">' + ICONS.x + '</button>' : '') +
      '</div>' +
    '</header>' +
    '<div class="search-page__layout">' +
      '<aside class="search-page__sidebar">' +
        buildSidebarHtml(hasFilters, allCounts) +
      '</aside>' +
      '<div class="search-page__main">' +
        (hasFilters ? renderFilterPills() : "") +
        '<h2 class="sr-only">Mod results</h2>' +
        '<div class="search-page__controls">' +
          '<span class="search-page__result-count" role="status" aria-live="polite">Showing <strong>' + filtered.length + '</strong> mod' + (filtered.length === 1 ? "" : "s") + '</span>' +
          renderSortSelect() +
        '</div>' +
        gridHtml +
        (totalPages > 1 ? renderPagination(totalPages) : "") +
      '</div>' +
    '</div>' +
  '</section>';

  // Restore focus to previously focused facet checkbox
  if (prevFacetName && prevFacetValue) {
    const checkboxes = root.querySelectorAll('input[type="checkbox"][name="' + prevFacetName + '"]');
    for (const cb of checkboxes) {
      if (cb.value === prevFacetValue) { cb.focus(); break; }
    }
  }

  restoreState();
}

function clearFacetSearch(type) {
  FACET_SEARCH[type] = "";
  renderSidebarFacets();
}

function clearMainSearch() {
  saveState();
  DRAFT_Q = "";
  PARAMS.q = "";
  PARAMS.p = 1;
  writeUrl();
  render();
}

function renderPagination(total) {
  let html = '<nav class="pagination" aria-label="Results pages">';
  html += '<button class="pg-btn" ' + (PARAMS.p <= 1 ? "disabled" : 'onclick="goPage(' + (PARAMS.p - 1) + ')"') + ' aria-label="Previous page">' + ICONS.chevron_left + '</button>';
  const pages = [];
  for (let i = 1; i <= total; i++) {
    if (i === 1 || i === total || (i >= PARAMS.p - 2 && i <= PARAMS.p + 2)) pages.push(i);
    else if (pages[pages.length - 1] !== "…") pages.push("…");
  }
  for (const pg of pages) {
    if (pg === "…") html += '<span class="pg-ellipsis">…</span>';
    else html += '<button class="pg-btn' + (pg === PARAMS.p ? " pg-btn--active" : "") + '" onclick="goPage(' + pg + ')"' + (pg === PARAMS.p ? ' aria-current="page"' : '') + ' aria-label="Page ' + pg + '">' + pg + '</button>';
  }
  html += '<button class="pg-btn" ' + (PARAMS.p >= total ? "disabled" : 'onclick="goPage(' + (PARAMS.p + 1) + ')"') + ' aria-label="Next page">' + ICONS.chevron_right + '</button>';
  html += '</nav>';
  return html;
}

document.addEventListener("DOMContentLoaded", init);
