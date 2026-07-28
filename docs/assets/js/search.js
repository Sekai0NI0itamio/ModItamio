"use strict";

const PAGE_SIZE = 20;
let ALL_MODS = [];
let PARAMS = { q: "", c: [], l: [], v: [], s: "downloads", p: 1 };
let FACET_SEARCH = { cat: "", loader: "", version: "" };
let _activeElement = null;
let _activeSelection = { start: 0, end: 0 };
let _savedScroll = 0;

async function init() {
  renderNavbar("mods");
  const root = document.getElementById("app");
  root.innerHTML = '<div class="loading">Loading…</div>';

  try {
    ALL_MODS = await loadAllMods();
    readUrl();
    render();
    syncSearchBox();
  } catch (e) {
    root.innerHTML = '<div class="empty"><p>Failed to load mods: ' + escapeHtml(e.message) + '</p></div>';
  }
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
  return '<div class="ad-slot ad-slot--list">' +
    '<div class="ad-label">Sponsored</div>' +
    '<div class="inline-ad">' +
      '<div class="inline-ad__content">' +
        '<strong>Support open-source modding</strong>' +
        '<span>Consider sponsoring the developers behind your favorite mods to keep updates flowing.</span>' +
      '</div>' +
      '<a href="#" class="btn btn--sm btn--primary">Learn more</a>' +
    '</div>' +
  '</div>';
}

function readUrl() {
  const p = new URLSearchParams(location.search);
  PARAMS.q = p.get("q") || "";
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
  FACET_SEARCH = { cat: "", loader: "", version: "" };
  writeUrl(); render();
}

function goPage(p) { PARAMS.p = p; writeUrl(); render(); window.scrollTo({ top: 0, behavior: "smooth" }); }

function onMainSearchInput(e) {
  saveState();
  PARAMS.q = e.target.value;
  PARAMS.p = 1;
  writeUrl(); render();
}

function onFacetSearchInput(type, e) {
  saveState();
  FACET_SEARCH[type] = e.target.value;
  render();
}

function render() {
  const root = document.getElementById("app");
  const filtered = sortMods(applyFilters(ALL_MODS));
  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  PARAMS.p = Math.min(PARAMS.p, totalPages);
  const page = filtered.slice((PARAMS.p - 1) * PAGE_SIZE, PARAMS.p * PAGE_SIZE);

  const hasFilters = !!(PARAMS.q || PARAMS.c.length || PARAMS.l.length || PARAMS.v.length);
  const allCounts = allFacetCounts();

  function facetMatches(facetVal, searchTerm) {
    if (!searchTerm) return true;
    return facetVal.toLowerCase().includes(searchTerm.toLowerCase());
  }

  function renderFacetSearch(type, placeholder) {
    const val = FACET_SEARCH[type] || "";
    return '<div class="facet-search">' +
      '<span class="facet-search__icon">' + ICONS.search + '</span>' +
      '<input type="text" class="facet-search__input" id="facet-search-' + type + '" placeholder="' + escapeHtml(placeholder) + '" value="' + escapeHtml(val) + '" oninput="onFacetSearchInput(\'' + type + '\', event)">' +
      (val ? '<button class="facet-search__clear" onclick="clearFacetSearch(\'' + type + '\')">&times;</button>' : '') +
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
      catHtml += '<label class="facet-item"><input type="checkbox" ' + (checked ? "checked" : "") + ' onchange="toggleFacet(\'c\',\'' + escapeHtml(c) + '\')"><span class="facet-item__label">' + escapeHtml(c) + '</span><span class="facet-item__count">' + (allCounts.cats[c] || 0) + '</span></label>';
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
    loaderHtml += '<label class="facet-item"><input type="checkbox" ' + (checked ? "checked" : "") + ' onchange="toggleFacet(\'l\',\'' + l + '\')"><span class="facet-item__label">' + escapeHtml(LOADER_NAMES[l] || l) + '</span><span class="facet-item__count">' + (allCounts.loaders[l] || 0) + '</span></label>';
  }
  if (loaderSearch && !filteredLoaders.length) {
    loaderHtml += '<div class="facet-empty">No matching loaders</div>';
  }
  loaderHtml += '</div>';

  const allVersions = [...new Set(ALL_MODS.flatMap(m => m.game_versions || []))].sort().reverse();
  let versionHtml = '<div class="facet-group"><div class="facet-group__title">Game versions</div>';
  versionHtml += renderFacetSearch("version", "Filter versions…");
  const versionSearch = FACET_SEARCH.version;
  const filteredVersions = allVersions.filter(v => (allCounts.versions[v] > 0) && facetMatches(v, versionSearch));
  for (const v of filteredVersions) {
    const checked = PARAMS.v.includes(v);
    versionHtml += '<label class="facet-item"><input type="checkbox" ' + (checked ? "checked" : "") + ' onchange="toggleFacet(\'v\',\'' + escapeHtml(v) + '\')"><span class="facet-item__label">' + escapeHtml(v) + '</span><span class="facet-item__count">' + (allCounts.versions[v] || 0) + '</span></label>';
  }
  if (versionSearch && !filteredVersions.length) {
    versionHtml += '<div class="facet-empty">No matching versions</div>';
  }
  versionHtml += '</div>';

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
    gridHtml = '<div class="mod-list">' + cardsWithAd.join("") + '</div>';
  } else {
    gridHtml = '<div class="empty"><p>No mods match your filters.</p></div>';
  }

  const sortButtons = ['downloads', 'followers', 'updated', 'newest', 'name'].map(s =>
    '<button class="sort-btn' + (PARAMS.s === s ? " sort-btn--active" : "") + '" onclick="setSort(\'' + s + '\')">' + escapeHtml(s.charAt(0).toUpperCase() + s.slice(1)) + '</button>'
  ).join("");

  root.innerHTML = '<div class="search-layout">' +
    '<aside class="search-sidebar">' +
      '<button class="clear-btn" ' + (hasFilters ? "" : "disabled") + ' onclick="clearFilters()">' + ICONS.filter + ' Clear filters</button>' +
      catHtml + loaderHtml + versionHtml +
      renderSidebarAd() +
    '</aside>' +
    '<div class="search-main">' +
      '<div class="sort-bar">' +
        '<div class="sort-bar__count">' + filtered.length + ' result' + (filtered.length === 1 ? "" : "s") + '</div>' +
        '<div class="sort-bar__options">' + sortButtons + '</div>' +
      '</div>' +
      '<div class="main-search-bar">' +
        '<span class="main-search-bar__icon">' + ICONS.search + '</span>' +
        '<input type="text" id="main-search" class="main-search-bar__input" placeholder="Search mods…" value="' + escapeHtml(PARAMS.q) + '" oninput="onMainSearchInput(event)">' +
        (PARAMS.q ? '<button class="main-search-bar__clear" onclick="clearMainSearch()">&times;</button>' : '') +
      '</div>' +
      gridHtml +
      (totalPages > 1 ? renderPagination(totalPages) : "") +
    '</div>' +
  '</div>';

  restoreState();
}

function clearFacetSearch(type) {
  saveState();
  FACET_SEARCH[type] = "";
  render();
}

function clearMainSearch() {
  saveState();
  PARAMS.q = "";
  PARAMS.p = 1;
  writeUrl();
  render();
}

function renderPagination(total) {
  let html = '<div class="pagination">';
  html += '<button class="pg-btn" ' + (PARAMS.p <= 1 ? "disabled" : 'onclick="goPage(' + (PARAMS.p - 1) + ')"') + '>' + ICONS.chevron_left + '</button>';
  const pages = [];
  for (let i = 1; i <= total; i++) {
    if (i === 1 || i === total || (i >= PARAMS.p - 2 && i <= PARAMS.p + 2)) pages.push(i);
    else if (pages[pages.length - 1] !== "…") pages.push("…");
  }
  for (const pg of pages) {
    if (pg === "…") html += '<span class="pg-ellipsis">…</span>';
    else html += '<button class="pg-btn' + (pg === PARAMS.p ? " pg-btn--active" : "") + '" onclick="goPage(' + pg + ')">' + pg + '</button>';
  }
  html += '<button class="pg-btn" ' + (PARAMS.p >= total ? "disabled" : 'onclick="goPage(' + (PARAMS.p + 1) + ')"') + '>' + ICONS.chevron_right + '</button>';
  html += '</div>';
  return html;
}

document.addEventListener("DOMContentLoaded", init);

(function() {
  let idleTimer = null;
  const IDLE_DELAY = 1800;

  function activateSidebar() {
    document.body.classList.remove("sidebar-dormant");
    document.body.classList.add("sidebar-active");
    clearTimeout(idleTimer);
  }

  function scheduleDormant() {
    if (window.innerWidth < 901) return;
    const sb = document.querySelector(".search-sidebar");
    if (sb && (sb.matches(":hover") || sb.matches(":focus-within"))) return;
    idleTimer = setTimeout(() => {
      document.body.classList.remove("sidebar-active");
      document.body.classList.add("sidebar-dormant");
    }, IDLE_DELAY);
  }

  window.addEventListener("scroll", () => {
    activateSidebar();
    clearTimeout(idleTimer);
    idleTimer = setTimeout(scheduleDormant, IDLE_DELAY);
  }, { passive: true });

  document.addEventListener("mousemove", (e) => {
    const sb = document.querySelector(".search-sidebar");
    if (sb) {
      const rect = sb.getBoundingClientRect();
      const nearSidebar = e.clientX < rect.right + 60;
      if (nearSidebar) {
        activateSidebar();
        clearTimeout(idleTimer);
      }
    }
  });

  window.addEventListener("load", () => {
    idleTimer = setTimeout(scheduleDormant, 2500);
  });
})();
