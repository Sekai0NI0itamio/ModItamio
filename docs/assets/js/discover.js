async function initDiscover() {
  document.body.classList.add("has-hero");
  const root = document.getElementById("app");
  renderNavbar("discover");

  const heroWrap = document.createElement("div");
  heroWrap.className = "hero-wrap";
  root.appendChild(heroWrap);
  heroWrap.innerHTML = renderDiscoverLoading();

  const mainWrap = document.createElement("main");
  mainWrap.className = "page";
  root.appendChild(mainWrap);
  mainWrap.innerHTML = "";

  try {
    const mods = await loadAllMods();
    const totalDownloads = mods.reduce((s, m) => s + (m.downloads || 0), 0);
    const totalFollowers = mods.reduce((s, m) => s + (m.followers || 0), 0);
    const featured = [...mods].sort((a, b) => (b.downloads || 0) - (a.downloads || 0)).slice(0, 6);
    const recent = [...mods].sort((a, b) => (b.updated || b.date_published || "").localeCompare(a.updated || b.date_published || "")).slice(0, 8);

    resetCardIndex();
    CARD_LINK_EXTRA = "";
    heroWrap.innerHTML = renderHero(mods.length, totalDownloads, totalFollowers);
    mainWrap.innerHTML = mods.length ? renderFeatured(featured) +
      renderInlineAd("Support independent modding", "Your support helps us keep Minecraft mods running smoothly for everyone.") +
      renderRecent(recent) : renderDiscoverEmpty();

  } catch (err) {
    heroWrap.innerHTML = "";
    mainWrap.innerHTML = '<div class="empty discover-error" role="alert">' +
      '<span class="discover-error__mark" aria-hidden="true">' + ICONS.sprout + '</span>' +
      '<h1>We couldn’t open the mod garden.</h1>' +
      '<p>' + escapeHtml(err.message || "The mod catalogue did not load.") + '</p>' +
      '<button class="btn btn--primary" type="button" onclick="location.reload()">Try again</button>' +
    '</div>';
  }
}

function renderDiscoverLoading() {
  return '<section class="hero hero--loading" aria-busy="true" aria-label="Loading ModItamio">' +
    '<div class="hero__content">' +
      '<div class="discover-skeleton discover-skeleton--eyebrow"></div>' +
      '<div class="discover-skeleton discover-skeleton--title"></div>' +
      '<div class="discover-skeleton discover-skeleton--copy"></div>' +
      '<div class="discover-skeleton discover-skeleton--search"></div>' +
      '<span class="loading">Tending the mod garden…</span>' +
    '</div>' +
  '</section>';
}

function renderDiscoverEmpty() {
  return '<div class="empty discover-error" role="status">' +
    '<span class="discover-error__mark" aria-hidden="true">' + ICONS.sprout + '</span>' +
    '<h1>The garden is quiet for now.</h1>' +
    '<p>There are no mods in the catalogue yet. Please check back soon.</p>' +
  '</div>';
}

function renderHero(count, downloads, followers) {
  return '<section class="hero">' +
    '<div class="hero__content">' +
      '<div class="hero__eyebrow">Sustainably crafted Minecraft mods</div>' +
      '<h1 class="hero__title">Mods made with <em>care</em>, built to last.</h1>' +
      '<p class="hero__subtitle">Thoughtfully engineered Minecraft mods that respect your time, performance, and world.</p>' +
      '<form class="hero__search" action="' + PATHS.browse + '" method="get" role="search">' +
        '<label class="sr-only" for="hero-search">Search the mod catalogue</label>' +
        '<span class="hero__search-icon">' + ICONS.search + '</span>' +
        '<input type="search" name="q" id="hero-search" placeholder="Search the mod catalogue" autocomplete="off">' +
        '<button type="submit" aria-label="Search mods">Search</button>' +
      '</form>' +
      '<div class="hero__stats">' +
        '<div class="hero__stat"><strong>' + count + '</strong><span>Curated mods</span></div>' +
        '<div class="hero__stat"><strong>' + formatNumber(downloads) + '</strong><span>Total downloads</span></div>' +
        '<div class="hero__stat"><strong>' + formatNumber(followers) + '</strong><span>Total followers</span></div>' +
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
  return '<section class="home-section home-section--featured" aria-labelledby="featured-title">' +
    '<div class="section-head">' +
      '<div><div class="section-head__kicker">Most popular</div><h2 id="featured-title">' + ICONS.star + ' Featured mods</h2><p>Trusted picks from across the catalogue.</p></div>' +
      '<a href="' + PATHS.browse + '" class="btn">Browse all ' + ICONS.chevron_right + '</a>' +
    '</div>' +
    '<div class="mod-grid">' + mods.map(modCard).join("") + '</div>' +
  '</section>';
}

function renderRecent(mods) {
  if (!mods.length) return "";
  return '<section class="home-section home-section--recent" aria-labelledby="recent-title">' +
    '<div class="section-head">' +
      '<div><div class="section-head__kicker">Fresh from the garden</div><h2 id="recent-title">' + ICONS.clock + ' Recently updated</h2><p>The latest maintained releases and improvements.</p></div>' +
      '<a href="' + PATHS.browse + '?s=updated" class="btn">View all ' + ICONS.chevron_right + '</a>' +
    '</div>' +
    '<div class="mod-grid">' + mods.map(modCard).join("") + '</div>' +
  '</section>';
}

document.addEventListener("DOMContentLoaded", initDiscover);
