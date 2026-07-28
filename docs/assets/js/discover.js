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

    resetCardIndex();
    CARD_LINK_EXTRA = "";
    mainWrap.innerHTML = renderHero(mods.length, totalDownloads, totalFollowers) +
      renderFeatured(featured) +
      renderInlineAd("Support independent modding", "Your support helps us keep Minecraft mods running smoothly for everyone.") +
      renderRecent(recent);

    const hs = document.getElementById("hero-search");
    if (hs) hs.addEventListener("keydown", e => {
      if (e.key === "Enter" && hs.value.trim()) location.href = BASE_PATH + "/browse/index.html?q=" + encodeURIComponent(hs.value.trim());
    });
  } catch (err) {
    mainWrap.innerHTML = '<div class="empty"><p>Could not load mods: ' + escapeHtml(err.message) + '</p></div>';
  }
}

function renderHero(count, downloads, followers) {
  return '<section class="hero">' +
    '<div class="hero__content">' +
      '<div class="hero__eyebrow">Sustainably crafted Minecraft mods</div>' +
      '<h1 class="hero__title">Mods made with <em>care</em>, built to last.</h1>' +
      '<p class="hero__subtitle">Thoughtfully engineered Minecraft modifications that respect your time, your performance, and your world. Discover smooth, reliable, purpose-driven tools.</p>' +
      '<div class="hero__search">' +
        '<span class="hero__search-icon">' + ICONS.search + '</span>' +
        '<input type="text" id="hero-search" placeholder="Search for mods…">' +
      '</div>' +
      '<div class="hero__stats">' +
        '<div class="hero__stat"><strong>' + count + '</strong><span>Curated mods</span></div>' +
        '<div class="hero__stat"><strong>' + formatNumber(downloads) + '</strong><span>Total downloads</span></div>' +
        '<div class="hero__stat"><strong>' + formatNumber(followers) + '</strong><span>Happy players</span></div>' +
      '</div>' +
    '</div>' +
    '<div class="hero__botanical">' + BOTANICAL_SVG + '</div>' +
  '</section>';
}

function renderInlineAd(title, copy) {
  return '<div class="ad-slot ad-slot--inline">' +
    '<div class="ad-label">Sponsored</div>' +
    '<div class="inline-ad">' +
      '<div class="inline-ad__content">' +
        '<strong>' + escapeHtml(title) + '</strong>' +
        '<span>' + escapeHtml(copy) + '</span>' +
      '</div>' +
      '<a href="#" class="btn btn--sm btn--primary">Learn more</a>' +
    '</div>' +
  '</div>';
}

function renderFeatured(mods) {
  if (!mods.length) return "";
  return '<section style="margin-top:48px">' +
    '<div class="section-head">' +
      '<div><div class="section-head__kicker">Most popular</div><h2>' + ICONS.star + ' Featured mods</h2></div>' +
      '<a href="mods.html" class="btn">Browse all ' + ICONS.chevron_right + '</a>' +
    '</div>' +
    '<div class="mod-grid">' + mods.map(modCard).join("") + '</div>' +
  '</section>';
}

function renderRecent(mods) {
  if (!mods.length) return "";
  return '<section style="margin-top:var(--space-8,48px)">' +
    '<div class="section-head">' +
      '<div><div class="section-head__kicker">Fresh from the garden</div><h2>' + ICONS.clock + ' Recently updated</h2></div>' +
      '<a href="mods.html?s=updated" class="btn">View all ' + ICONS.chevron_right + '</a>' +
    '</div>' +
    '<div class="mod-grid">' + mods.map(modCard).join("") + '</div>' +
  '</section>';
}

document.addEventListener("DOMContentLoaded", initDiscover);
