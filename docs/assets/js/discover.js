async function initDiscover() {
  document.body.classList.add("has-hero");
  const root = document.getElementById("app");
  renderNavbar("discover");

  const mainWrap = document.createElement("main");
  mainWrap.className = "page";
  root.appendChild(mainWrap);
  mainWrap.innerHTML = '<div class="loading">Loading…</div>';

  try {
    const mods = await loadAllMods();
    const totalDownloads = mods.reduce((s, m) => s + (m.downloads || 0), 0);
    const totalFollowers = mods.reduce((s, m) => s + (m.followers || 0), 0);
    const featured = [...mods].sort((a, b) => (b.downloads || 0) - (a.downloads || 0)).slice(0, 6);
    const recent = [...mods].sort((a, b) => (b.updated || b.date_published || "").localeCompare(a.updated || a.date_published || "")).slice(0, 8);

    mainWrap.innerHTML = renderHero() + renderStats(mods.length, totalDownloads, totalFollowers) +
      renderFeatured(featured) + renderRecent(recent);

    const hs = document.getElementById("hero-search");
    if (hs) hs.addEventListener("keydown", e => {
      if (e.key === "Enter" && hs.value.trim()) location.href = "mods.html?q=" + encodeURIComponent(hs.value.trim());
    });
  } catch (err) {
    mainWrap.innerHTML = '<div class="empty"><p>Could not load mods: ' + escapeHtml(err.message) + '</p></div>';
  }
}

function renderHero() {
  return '<section class="hero">' +
    '<div class="hero__logo">M</div>' +
    '<h1 class="hero__title">ModItamio</h1>' +
    '<p class="hero__subtitle">Minecraft mods by Itamio. Discover, download, and report issues.</p>' +
    '<div class="hero__search">' +
      '<span class="hero__search-icon">' + ICONS.search + '</span>' +
      '<input type="text" id="hero-search" placeholder="Search mods…">' +
    '</div>' +
  '</section>';
}

function renderStats(count, downloads, followers) {
  return '<div class="stats">' +
    '<div class="stats__item"><div class="stats__val">' + count + '</div><div class="stats__label">Mods</div></div>' +
    '<div class="stats__item"><div class="stats__val">' + formatNumber(downloads) + '</div><div class="stats__label">Downloads</div></div>' +
    '<div class="stats__item"><div class="stats__val">' + formatNumber(followers) + '</div><div class="stats__label">Followers</div></div>' +
  '</div>';
}

function renderFeatured(mods) {
  if (!mods.length) return "";
  return '<section>' +
    '<div class="section-head"><h2>' + ICONS.star + ' Featured</h2><a href="mods.html" class="btn">Browse all</a></div>' +
    '<div class="mod-grid">' + mods.map(modCard).join("") + '</div>' +
  '</section>';
}

function renderRecent(mods) {
  if (!mods.length) return "";
  return '<section style="margin-top:var(--space-8)">' +
    '<div class="section-head"><h2>' + ICONS.clock + ' Recently updated</h2><a href="mods.html?s=updated" class="btn">View all</a></div>' +
    '<div class="mod-grid">' + mods.map(modCard).join("") + '</div>' +
  '</section>';
}

document.addEventListener("DOMContentLoaded", initDiscover);
