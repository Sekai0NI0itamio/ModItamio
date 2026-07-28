/* ============================================================
   ModItamio Site — rendering engine
   Reads generated JSON data, renders Modrinth-style mod pages.
   Self-contained: no external dependencies (markdown parser inline).
   ============================================================ */

const CONFIG = {
  // GitHub repo for issues + raw content
  repoOwner: "Sekai0NI0itamio",
  repoName: "ModItamio",
  // When running locally, data is in ./data/. When on GitHub Pages,
  // it's at the same relative path.
  dataBase: "./data",
  assetsBase: "./assets",
  modsAssetBase: "./mods", // per-mod icons, jars, gallery live under mods/<modId>/
};

const REPO_URL = `https://github.com/${CONFIG.repoOwner}/${CONFIG.repoName}`;
const ISSUES_API = `https://api.github.com/repos/${CONFIG.repoOwner}/${CONFIG.repoName}/issues`;
const ISSUES_NEW_URL = `${REPO_URL}/issues/new`;

/* ==================== Utility ==================== */

function $(sel, root = document) { return root.querySelector(sel); }
function $$(sel, root = document) { return [...root.querySelectorAll(sel)]; }
function el(tag, props = {}, ...children) {
  const e = document.createElement(tag);
  for (const [k, v] of Object.entries(props)) {
    if (k === "class") e.className = v;
    else if (k === "html") e.innerHTML = v;
    else if (k.startsWith("on") && typeof v === "function") e.addEventListener(k.slice(2).toLowerCase(), v);
    else if (k === "style" && typeof v === "object") Object.assign(e.style, v);
    else if (v !== null && v !== undefined) e.setAttribute(k, v);
  }
  for (const c of children.flat()) {
    if (c == null || c === false) continue;
    e.appendChild(typeof c === "string" ? document.createTextNode(c) : c);
  }
  return e;
}

function formatNumber(n) {
  if (n == null) return "0";
  if (n >= 1_000_000) return (n / 1_000_000).toFixed(1).replace(/\.0$/, "") + "M";
  if (n >= 1_000) return (n / 1_000).toFixed(1).replace(/\.0$/, "") + "k";
  return String(n);
}

function formatDate(iso) {
  if (!iso) return "";
  const d = new Date(iso);
  return d.toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });
}

function timeAgo(iso) {
  if (!iso) return "";
  const diff = Date.now() - new Date(iso).getTime();
  const days = Math.floor(diff / 86400000);
  if (days < 1) return "today";
  if (days < 7) return `${days} day${days > 1 ? "s" : ""} ago`;
  if (days < 30) return `${Math.floor(days / 7)} week${days >= 14 ? "s" : ""} ago`;
  if (days < 365) return `${Math.floor(days / 30)} month${days >= 60 ? "s" : ""} ago`;
  return `${Math.floor(days / 365)} year${days >= 730 ? "s" : ""} ago`;
}

function escapeHtml(s) {
  if (s == null) return "";
  return String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

/* ==================== Lightweight Markdown Parser ==================== */
/* Supports: headings, bold, italic, code, code blocks, links, images,
   lists, blockquotes, tables, hr, paragraphs. Sufficient for Modrinth-style
   description rendering. */

function renderMarkdown(md) {
  if (!md) return "";
  let lines = md.replace(/\r\n/g, "\n").split("\n");
  let html = [];
  let i = 0;
  let inList = false;
  let listType = "ul";

  function closeList() {
    if (inList) { html.push(`</${listType}>`); inList = false; }
  }

  function inline(text) {
    // Images: ![alt](url)
    text = text.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img alt="$1" src="$2" loading="lazy">');
    // Links: [text](url)
    text = text.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank" rel="noopener nofollow ugc">$1</a>');
    // Bold
    text = text.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
    text = text.replace(/__([^_]+)__/g, "<strong>$1</strong>");
    // Italic
    text = text.replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>");
    text = text.replace(/(^|[^_])_([^_]+)_/g, "$1<em>$2</em>");
    // Inline code
    text = text.replace(/`([^`]+)`/g, "<code>$1</code>");
    return text;
  }

  while (i < lines.length) {
    let line = lines[i];

    // Code block
    if (line.trimStart().startsWith("```")) {
      closeList();
      const lang = line.trim().slice(3);
      let code = [];
      i++;
      while (i < lines.length && !lines[i].trimStart().startsWith("```")) { code.push(lines[i]); i++; }
      i++; // skip closing ```
      html.push(`<pre><code class="language-${escapeHtml(lang)}">${escapeHtml(code.join("\n"))}</code></pre>`);
      continue;
    }

    // Heading
    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) {
      closeList();
      const level = h[1].length;
      html.push(`<h${level}>${inline(escapeHtml(h[2]))}</h${level}>`);
      i++;
      continue;
    }

    // Horizontal rule
    if (/^(-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
      closeList();
      html.push("<hr>");
      i++;
      continue;
    }

    // Blockquote
    if (line.startsWith("> ")) {
      closeList();
      let quote = [];
      while (i < lines.length && lines[i].startsWith("> ")) { quote.push(lines[i].slice(2)); i++; }
      html.push(`<blockquote>${inline(escapeHtml(quote.join(" ")))}</blockquote>`);
      continue;
    }

    // Table
    if (line.includes("|") && i + 1 < lines.length && /^\s*\|?[\s:|-]+\|?\s*$/.test(lines[i + 1])) {
      closeList();
      const headers = line.split("|").map(s => s.trim()).filter(Boolean);
      i += 2; // skip header + separator
      let rows = [];
      while (i < lines.length && lines[i].includes("|") && lines[i].trim()) {
        rows.push(lines[i].split("|").map(s => s.trim()));
        i++;
      }
      let t = "<table><thead><tr>";
      headers.forEach(h => t += `<th>${inline(escapeHtml(h))}</th>`);
      t += "</tr></thead><tbody>";
      rows.forEach(r => {
        t += "<tr>";
        for (let j = 0; j < headers.length; j++) t += `<td>${inline(escapeHtml(r[j] || ""))}</td>`;
        t += "</tr>";
      });
      t += "</tbody></table>";
      html.push(t);
      continue;
    }

    // Unordered list
    if (/^\s*[-*+]\s+/.test(line)) {
      if (!inList || listType !== "ul") { closeList(); inList = true; listType = "ul"; html.push("<ul>"); }
      html.push(`<li>${inline(escapeHtml(line.replace(/^\s*[-*+]\s+/, "")))}</li>`);
      i++;
      continue;
    }

    // Ordered list
    if (/^\s*\d+\.\s+/.test(line)) {
      if (!inList || listType !== "ol") { closeList(); inList = true; listType = "ol"; html.push("<ol>"); }
      html.push(`<li>${inline(escapeHtml(line.replace(/^\s*\d+\.\s+/, "")))}</li>`);
      i++;
      continue;
    }

    // Empty line
    if (line.trim() === "") {
      closeList();
      i++;
      continue;
    }

    // Paragraph
    closeList();
    let para = [line];
    i++;
    while (i < lines.length && lines[i].trim() !== "" && !/^(#{1,6}\s|>|```|\s*[-*+]\s|\s*\d+\.\s)/.test(lines[i]) && !(lines[i].includes("|") && i + 1 < lines.length && /^\s*\|?[\s:|-]+\|?\s*$/.test(lines[i + 1]))) {
      para.push(lines[i]);
      i++;
    }
    html.push(`<p>${inline(escapeHtml(para.join(" ")))}</p>`);
  }
  closeList();
  return html.join("\n");
}

/* ==================== Loader colors ==================== */
const LOADER_COLORS = {
  fabric: "#dbb69b", forge: "#959eef", neoforge: "#f99e6b", quilt: "#c796f9",
  paper: "#eeaaaa", purpur: "#c3abf7", spigot: "#f1cc84", sponge: "#f9e580",
};

function loaderColor(loader) {
  return LOADER_COLORS[loader?.toLowerCase()] || "#1bd96a";
}

/* ==================== Icons (inline SVG) ==================== */
const ICONS = {
  download: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>`,
  users: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/></svg>`,
  package: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="16.5" y1="9.4" x2="7.5" y2="4.21"/><path d="M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.27 6.96 12 12.01 20.73 6.96"/><line x1="12" y1="22.08" x2="12" y2="12"/></svg>`,
  calendar: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/></svg>`,
  tag: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>`,
  heart: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>`,
  code: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>`,
  issues: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>`,
  wiki: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>`,
  discord: `<svg viewBox="0 0 24 24" fill="currentColor"><path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028c.462-.63.874-1.295 1.226-1.994a.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128c.126-.094.252-.192.372-.291a.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.009c.12.099.246.198.373.292a.077.077 0 0 1-.006.127 12.3 12.3 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z"/></svg>`,
  globe: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="2" y1="12" x2="22" y2="12"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>`,
  book: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>`,
  sun: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>`,
  moon: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>`,
  search: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>`,
  external: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>`,
  star: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>`,
  arrowLeft: `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="19" y1="12" x2="5" y2="12"/><polyline points="12 19 5 12 12 5"/></svg>`,
};

/* ==================== Theme ==================== */
function initTheme() {
  const saved = localStorage.getItem("moditamio-theme");
  if (saved) document.documentElement.setAttribute("data-theme", saved);
  else if (window.matchMedia("(prefers-color-scheme: light)").matches) {
    document.documentElement.setAttribute("data-theme", "light");
  }
}

function toggleTheme() {
  const current = document.documentElement.getAttribute("data-theme");
  const next = current === "light" ? "dark" : "light";
  document.documentElement.setAttribute("data-theme", next);
  localStorage.setItem("moditamio-theme", next);
  updateThemeIcon();
}

function updateThemeIcon() {
  const isLight = document.documentElement.getAttribute("data-theme") === "light";
  $$(".theme-toggle").forEach(btn => btn.innerHTML = isLight ? ICONS.moon : ICONS.sun);
}

/* ==================== Navbar ==================== */
function renderNavbar(searchTerm = "") {
  return `
    <nav class="navbar">
      <div class="navbar__inner">
        <a href="index.html" class="navbar__brand">
          <span class="navbar__logo">M</span>
          ModItamio
        </a>
        <div class="navbar__search">
          <span class="navbar__search-icon">${ICONS.search}</span>
          <input type="text" id="search-input" placeholder="Search mods..." value="${escapeHtml(searchTerm)}">
        </div>
        <div class="navbar__links">
          <a href="${REPO_URL}" target="_blank" rel="noopener" class="navbar__link">${ICONS.code} GitHub</a>
          <a href="${REPO_URL}/issues" target="_blank" rel="noopener" class="navbar__link">Issues</a>
          <button class="theme-toggle" onclick="toggleTheme()" title="Toggle theme"></button>
        </div>
      </div>
    </nav>`;
}

/* ==================== Homepage ==================== */
async function renderHome() {
  const root = $("#app");
  document.title = "ModItamio — Minecraft Mods";

  root.innerHTML = renderNavbar() + `
    <div class="page-wrap" style="padding-top: 2rem; padding-bottom: 2rem;">
      <div class="loading"><div class="spinner"></div> Loading mods...</div>
    </div>`;

  try {
    const resp = await fetch(`${CONFIG.dataBase}/mods.json`, { cache: "no-store" });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const mods = await resp.json();
    modsState = mods;
    renderModGrid(mods, "");
  } catch (err) {
    root.innerHTML = renderNavbar() + `
      <div class="page-wrap" style="padding-top: 2rem;">
        <div class="empty">
          <h2>No mods published yet</h2>
          <p class="mt-md">Mods published via the ModItamio app will appear here.</p>
          <p class="mt-md text-secondary">(${escapeHtml(err.message)})</p>
        </div>
      </div>`;
  }
  updateThemeIcon();
  setupSearch();
}

let modsState = [];

function renderModGrid(mods, filter) {
  const container = $(".page-wrap") || $("#app");
  const filtered = filter
    ? mods.filter(m => (m.name + " " + m.summary + " " + m.mod_id).toLowerCase().includes(filter.toLowerCase()))
    : mods;

  const cards = filtered.map(m => {
    const iconUrl = m.icon_url || `${CONFIG.modsAssetBase}/${m.mod_id}/icon.png`;
    return `
      <a class="mod-card" href="mod.html?id=${encodeURIComponent(m.mod_id)}">
        <img class="mod-card__icon" src="${escapeHtml(iconUrl)}" alt="${escapeHtml(m.name)}" onerror="this.style.background='var(--surface-2)';this.src='data:image/svg+xml,<svg xmlns=\\'http://www.w3.org/2000/svg\\' viewBox=\\'0 0 100 100\\'><rect width=\\'100\\' height=\\'100\\' fill=\\'%2327292e\\'/><text x=\\'50\\' y=\\'60\\' font-size=\\'40\\' text-anchor=\\'middle\\' fill=\\'%231bd96a\\'>${escapeHtml((m.name||"M")[0].toUpperCase())}</text></svg>'">
        <div class="mod-card__body">
          <div class="mod-card__title">${escapeHtml(m.name)}</div>
          <div class="mod-card__summary">${escapeHtml(m.summary || "")}</div>
          <div class="mod-card__meta">
            <span class="mod-card__meta-item mod-card__downloads">${ICONS.download} ${formatNumber(m.downloads)}</span>
            <span class="mod-card__meta-item">${ICONS.users} ${formatNumber(m.followers)}</span>
            <span class="mod-card__meta-item">${ICONS.package} ${m.version_count || 0}</span>
          </div>
          <div class="flex gap-xs flex-wrap mt-md">
            ${(m.loaders || []).slice(0, 4).map(l => `<span class="tag tag--platform" style="--_color:${loaderColor(l)}">${escapeHtml(l)}</span>`).join("")}
          </div>
        </div>
      </a>`;
  }).join("");

  container.innerHTML = renderNavbar($("#search-input")?.value || "") + `
    <div class="page-wrap" style="padding-top: 2rem; padding-bottom: 2rem;">
      <div style="margin-bottom: 1.5rem;">
        <h1 style="font-size: 2rem; margin-bottom: 0.5rem;">Mods</h1>
        <p class="text-secondary">Browse all mods by Itamio. ${filtered.length} mod${filtered.length !== 1 ? "s" : ""}.</p>
      </div>
      ${filtered.length === 0 ? `<div class="empty"><p>No mods found${filter ? " matching \"" + escapeHtml(filter) + "\"" : ""}.</p></div>` : `<div class="mod-grid">${cards}</div>`}
    </div>
    <footer class="footer">
      <p>ModItamio — by Itamio. Contributor: Asd1281yss.</p>
      <p class="mt-md"><a href="${REPO_URL}" target="_blank" rel="noopener">View source on GitHub</a> · <a href="${REPO_URL}/issues" target="_blank" rel="noopener">Report an issue</a></p>
    </footer>`;

  updateThemeIcon();
  setupSearch();
}

function setupSearch() {
  const input = $("#search-input");
  if (input) {
    input.addEventListener("input", (e) => {
      renderModGrid(modsState, e.target.value);
      // refocus
      const newInput = $("#search-input");
      if (newInput) { newInput.focus(); newInput.setSelectionRange(e.target.selectionStart, e.target.selectionEnd); }
    });
  }
}

/* ==================== Mod Detail Page ==================== */
async function renderModPage(modId) {
  const root = $("#app");
  root.innerHTML = renderNavbar() + `
    <div class="mod-page"><div class="loading"><div class="spinner"></div> Loading mod...</div></div>`;
  updateThemeIcon();

  try {
    const resp = await fetch(`${CONFIG.dataBase}/${modId}.json`, { cache: "no-store" });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const mod = await resp.json();
    document.title = `${mod.name} — ModItamio`;
    renderModDetail(mod);
  } catch (err) {
    root.innerHTML = renderNavbar() + `
      <div class="mod-page"><div class="empty">
        <h2>Mod not found</h2>
        <p class="mt-md text-secondary">${escapeHtml(err.message)}</p>
        <a href="index.html" class="btn mt-lg">${ICONS.arrowLeft} Back to mods</a>
      </div></div>`;
    updateThemeIcon();
  }
}

function renderModDetail(mod) {
  const root = $("#app");
  const iconUrl = mod.icon_url || `${CONFIG.modsAssetBase}/${mod.mod_id}/icon.png`;
  const projectColor = mod.color || "#1bd96a";

  root.innerHTML = renderNavbar() + `
    <div class="bg-tint" style="--_project-color: ${projectColor}"></div>
    <div class="mod-page">
      <a href="index.html" class="btn" style="margin-bottom: 1rem;">${ICONS.arrowLeft} All Mods</a>

      <!-- Project Header -->
      <div class="mod-page__header">
        <div class="project-header">
          <img class="project-header__icon" src="${escapeHtml(iconUrl)}" alt="${escapeHtml(mod.name)}" onerror="this.style.display='none'">
          <div class="project-header__info">
            <h1 class="project-header__title">${escapeHtml(mod.name)}</h1>
            <p class="project-header__summary">${escapeHtml(mod.summary || "")}</p>
            <div class="project-header__meta">
              <span class="project-header__meta-item">${ICONS.download} <strong>${formatNumber(mod.downloads)}</strong> downloads</span>
              <span class="project-header__meta-item">${ICONS.heart} <strong>${formatNumber(mod.followers)}</strong> followers</span>
              <span class="project-header__meta-item">${ICONS.package} <strong>${mod.versions?.length || 0}</strong> versions</span>
              ${mod.date_published ? `<span class="project-header__meta-item">${ICONS.calendar} Published ${formatDate(mod.date_published)}</span>` : ""}
            </div>
            <div class="project-header__tags">
              ${(mod.loaders || []).map(l => `<span class="tag tag--platform" style="--_color:${loaderColor(l)}">${escapeHtml(l)}</span>`).join("")}
              ${(mod.categories || []).map(c => `<span class="tag">${escapeHtml(c)}</span>`).join("")}
              ${mod.license ? `<span class="tag">${ICONS.book} ${escapeHtml(mod.license)}</span>` : ""}
            </div>
            <div class="project-header__actions">
              ${mod.versions?.length ? `<a class="btn btn--primary" href="#versions">${ICONS.download} Download</a>` : ""}
              ${mod.source_url ? `<a class="btn" href="${escapeHtml(mod.source_url)}" target="_blank" rel="noopener nofollow ugc">${ICONS.code} Source</a>` : ""}
              <a class="btn" href="#issues">${ICONS.issues} Report Issue</a>
              ${mod.modrinth_url ? `<a class="btn" href="${escapeHtml(mod.modrinth_url)}" target="_blank" rel="noopener nofollow ugc">${ICONS.external} Modrinth</a>` : ""}
            </div>
          </div>
        </div>
      </div>

      <!-- Tabs -->
      <div class="tabs" id="tabs">
        <button class="tab tab--active" data-tab="description">Description</button>
        <button class="tab" data-tab="gallery" ${!mod.gallery?.length ? "disabled" : ""}>Gallery${mod.gallery?.length ? ` (${mod.gallery.length})` : ""}</button>
        <button class="tab" data-tab="versions">Versions${mod.versions?.length ? ` (${mod.versions.length})` : ""}</button>
        <button class="tab" data-tab="issues">Issues</button>
      </div>

      <!-- Layout -->
      <div class="mod-page__layout">
        <div class="mod-page__content">
          <!-- Description tab -->
          <div class="tab-panel" id="panel-description">
            <div class="card">
              <div class="description-body">${renderMarkdown(mod.description || mod.body || "")}</div>
            </div>
          </div>
          <!-- Gallery tab -->
          <div class="tab-panel hidden" id="panel-gallery">
            ${mod.gallery?.length ? `<div class="gallery-grid">${mod.gallery.map((g, i) => `
              <div class="gallery-item" onclick="openLightbox(${i})">
                <img class="gallery-item__img" src="${escapeHtml(g.url)}" alt="${escapeHtml(g.title || "")}" loading="lazy">
                <div class="gallery-item__body">
                  <div class="gallery-item__title">${escapeHtml(g.title || "")}</div>
                  ${g.description ? `<div class="gallery-item__desc">${escapeHtml(g.description)}</div>` : ""}
                </div>
              </div>`).join("")}</div>` : `<div class="empty"><p>No gallery images.</p></div>`}
          </div>
          <!-- Versions tab -->
          <div class="tab-panel hidden" id="panel-versions">
            <div class="card" style="padding: 0; overflow: hidden;">
              ${mod.versions?.length ? `<table class="versions-table">
                <thead><tr>
                  <th>Name</th><th>Game Versions</th><th>Loaders</th><th>Published</th><th>Downloads</th><th></th>
                </tr></thead>
                <tbody>
                  ${mod.versions.map(v => `
                    <tr>
                      <td class="row-name">
                        ${v.version_type ? `<span class="tag tag--${v.version_type}" style="margin-right:0.5rem">${v.version_type}</span>` : ""}
                        ${escapeHtml(v.name || v.version_number || "")}
                      </td>
                      <td><div class="tags-cell">${(v.game_versions || []).map(gv => `<span class="tag tag--version">${escapeHtml(gv)}</span>`).join("")}</div></td>
                      <td><div class="tags-cell">${(v.loaders || []).map(l => `<span class="tag tag--platform" style="--_color:${loaderColor(l)}">${escapeHtml(l)}</span>`).join("")}</div></td>
                      <td class="row-date">${formatDate(v.date_published)}</td>
                      <td class="row-downloads">${formatNumber(v.downloads)}</td>
                      <td>${v.files?.length ? `<a class="btn btn--primary" href="${escapeHtml(v.files[0].url)}" download onclick="event.stopPropagation()">${ICONS.download}</a>` : ""}</td>
                    </tr>`).join("")}
                </tbody>
              </table>` : `<div class="empty"><p>No versions published.</p></div>`}
            </div>
          </div>
          <!-- Issues tab -->
          <div class="tab-panel hidden" id="panel-issues">
            <div class="card">
              <h2 id="issues">Report an Issue</h2>
              <p class="text-secondary" style="margin-bottom: 1rem;">Found a bug or have a feature request? Fill out the form below — it will open a pre-filled issue on GitHub.</p>
              <form class="issue-form" id="issue-form">
                <div class="issue-form__field">
                  <label for="issue-title">Issue Title</label>
                  <input type="text" id="issue-title" placeholder="Brief summary of the issue" required>
                </div>
                <div class="issue-form__row">
                  <div class="issue-form__field">
                    <label for="issue-type">Issue Type</label>
                    <select id="issue-type">
                      <option value="bug">🐛 Bug Report</option>
                      <option value="crash">💥 Crash Report</option>
                      <option value="feature">✨ Feature Request</option>
                    </select>
                  </div>
                  <div class="issue-form__field">
                    <label for="issue-version">Minecraft Version</label>
                    <input type="text" id="issue-version" placeholder="e.g. 1.20.1">
                  </div>
                </div>
                <div class="issue-form__row">
                  <div class="issue-form__field">
                    <label for="issue-loader">Mod Loader</label>
                    <select id="issue-loader">
                      <option value="">— Select —</option>
                      ${(mod.loaders || []).map(l => `<option value="${escapeHtml(l)}">${escapeHtml(l)}</option>`).join("")}
                      <option value="other">Other</option>
                    </select>
                  </div>
                  <div class="issue-form__field">
                    <label for="issue-mod-version">Mod Version</label>
                    <input type="text" id="issue-mod-version" placeholder="e.g. 1.0.0" value="${escapeHtml(mod.versions?.[0]?.version_number || "")}">
                  </div>
                </div>
                <div class="issue-form__field">
                  <label for="issue-body">Description</label>
                  <textarea id="issue-body" placeholder="Describe the issue in detail. What happened? What did you expect? Steps to reproduce?" required></textarea>
                </div>
                <button type="submit" class="btn btn--primary btn--lg">${ICONS.issues} Create Issue on GitHub</button>
              </form>
            </div>
            <div class="card">
              <h2>Existing Issues</h2>
              <div id="issues-list"><div class="loading"><div class="spinner"></div> Loading issues...</div></div>
            </div>
          </div>
        </div>

        <!-- Sidebar -->
        <div class="mod-page__sidebar">
          <!-- Links -->
          <div class="sidebar-card">
            <h2>Links</h2>
            <div class="sidebar-links mt-md">
              <a href="${REPO_URL}/issues/new?labels=${encodeURIComponent(mod.mod_id)}" target="_blank" rel="noopener nofollow ugc">${ICONS.issues} Report issues</a>
              ${mod.source_url ? `<a href="${escapeHtml(mod.source_url)}" target="_blank" rel="noopener nofollow ugc">${ICONS.code} View source</a>` : ""}
              ${mod.wiki_url ? `<a href="${escapeHtml(mod.wiki_url)}" target="_blank" rel="noopener nofollow ugc">${ICONS.wiki} Visit wiki</a>` : ""}
              ${mod.discord_url ? `<a href="${escapeHtml(mod.discord_url)}" target="_blank" rel="noopener nofollow ugc">${ICONS.discord} Join Discord</a>` : ""}
              ${mod.modrinth_url ? `<a href="${escapeHtml(mod.modrinth_url)}" target="_blank" rel="noopener nofollow ugc">${ICONS.external} View on Modrinth</a>` : ""}
              ${mod.site_url ? `<a href="${escapeHtml(mod.site_url)}" target="_blank" rel="noopener nofollow ugc">${ICONS.globe} Visit website</a>` : ""}
            </div>
          </div>
          <!-- Details -->
          <div class="sidebar-card">
            <h2>Details</h2>
            <div class="mt-md">
              ${mod.license ? `<div class="sidebar-detail">${ICONS.book} Licensed ${escapeHtml(mod.license)}</div>` : ""}
              ${mod.followers != null ? `<div class="sidebar-detail">${ICONS.heart} ${formatNumber(mod.followers)} followers</div>` : ""}
              ${mod.date_published ? `<div class="sidebar-detail">${ICONS.calendar} Published ${timeAgo(mod.date_published)}</div>` : ""}
              ${mod.updated ? `<div class="sidebar-detail">${ICONS.package} Updated ${timeAgo(mod.updated)}</div>` : ""}
              <div class="sidebar-detail">${ICONS.tag} Mod ID: <code>${escapeHtml(mod.mod_id)}</code></div>
            </div>
          </div>
          <!-- Authors -->
          <div class="sidebar-card">
            <h2>Authors</h2>
            <div class="mt-md">
              <div class="sidebar-detail"><strong style="color:var(--color-text-primary)">Itamio</strong> — Author</div>
              <div class="sidebar-detail"><strong style="color:var(--color-text-primary)">Asd1281yss</strong> — Contributor</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Lightbox -->
    <div class="lightbox" id="lightbox" onclick="closeLightbox(event)">
      <button class="lightbox__close" onclick="closeLightbox()">✕</button>
      <button class="lightbox__nav lightbox__prev" onclick="event.stopPropagation();navLightbox(-1)">‹</button>
      <img class="lightbox__img" id="lightbox-img">
      <button class="lightbox__nav lightbox__next" onclick="event.stopPropagation();navLightbox(1)">›</button>
      <div class="lightbox__caption" id="lightbox-caption"></div>
    </div>

    <footer class="footer">
      <p>ModItamio — by Itamio. Contributor: Asd1281yss.</p>
      <p class="mt-md"><a href="${REPO_URL}" target="_blank" rel="noopener">View source on GitHub</a> · <a href="index.html">All Mods</a></p>
    </footer>`;

  updateThemeIcon();
  setupTabs();
  setupIssueForm(mod);
  loadIssues(mod.mod_id);
}

/* ==================== Tabs ==================== */
function setupTabs() {
  $$(".tab").forEach(tab => {
    tab.addEventListener("click", () => {
      if (tab.disabled) return;
      $$(".tab").forEach(t => t.classList.remove("tab--active"));
      tab.classList.add("tab--active");
      $$(".tab-panel").forEach(p => p.classList.add("hidden"));
      const panel = $(`#panel-${tab.dataset.tab}`);
      if (panel) panel.classList.remove("hidden");
    });
  });
}

/* ==================== Lightbox ==================== */
let lightboxIndex = 0;
let currentGallery = [];

window.openLightbox = function(index) {
  const mod = window._currentMod;
  if (!mod?.gallery?.length) return;
  currentGallery = mod.gallery;
  lightboxIndex = index;
  showLightboxImage();
  $("#lightbox").classList.add("lightbox--open");
  document.body.style.overflow = "hidden";
};

window.closeLightbox = function(e) {
  if (e && e.target.closest(".lightbox__img")) return;
  $("#lightbox").classList.remove("lightbox--open");
  document.body.style.overflow = "";
};

window.navLightbox = function(dir) {
  lightboxIndex = (lightboxIndex + dir + currentGallery.length) % currentGallery.length;
  showLightboxImage();
};

function showLightboxImage() {
  const item = currentGallery[lightboxIndex];
  if (!item) return;
  $("#lightbox-img").src = item.url;
  $("#lightbox-caption").innerHTML = `<strong>${escapeHtml(item.title || "")}</strong>${item.description ? " — " + escapeHtml(item.description) : ""}`;
}

document.addEventListener("keydown", (e) => {
  if (!$("#lightbox")?.classList.contains("lightbox--open")) return;
  if (e.key === "Escape") closeLightbox();
  if (e.key === "ArrowLeft") navLightbox(-1);
  if (e.key === "ArrowRight") navLightbox(1);
});

/* ==================== Issues ==================== */
function setupIssueForm(mod) {
  window._currentMod = mod;
  const form = $("#issue-form");
  if (!form) return;
  form.addEventListener("submit", (e) => {
    e.preventDefault();
    const title = $("#issue-title").value.trim();
    const type = $("#issue-type").value;
    const mcVersion = $("#issue-version").value.trim();
    const loader = $("#issue-loader").value;
    const modVersion = $("#issue-mod-version").value.trim();
    const body = $("#issue-body").value.trim();

    const typeLabels = { bug: "bug", crash: "crash", feature: "enhancement" };

    const bodyFormatted = [
      `**Mod:** ${mod.name} (\`${mod.mod_id}\`)`,
      `**Mod Version:** ${modVersion || "—"}`,
      `**Minecraft Version:** ${mcVersion || "—"}`,
      `**Mod Loader:** ${loader || "—"}`,
      "",
      `**Issue Type:** ${type}`,
      "",
      "**Description:**",
      body,
      "",
      "---",
      `*This issue was reported via the ModItamio website.*`,
    ].join("\n");

    const labels = [mod.mod_id, typeLabels[type]].filter(Boolean).join(",");
    const url = `${ISSUES_NEW_URL}?title=${encodeURIComponent(title)}&body=${encodeURIComponent(bodyFormatted)}&labels=${encodeURIComponent(labels)}`;
    window.open(url, "_blank");
  });
}

async function loadIssues(modId) {
  const container = $("#issues-list");
  if (!container) return;
  try {
    const resp = await fetch(`${ISSUES_API}?labels=${encodeURIComponent(modId)}&state=all&per_page=30`, {
      headers: { "Accept": "application/vnd.github.v3+json" },
    });
    if (!resp.ok) throw new Error(`HTTP ${resp.status}`);
    const issues = (await resp.json()).filter(i => !i.pull_request);
    if (!issues.length) {
      container.innerHTML = `<div class="empty"><p>No issues reported yet.</p></div>`;
      return;
    }
    container.innerHTML = `<div class="issues-list">${issues.map(issue => `
      <div class="issue-item">
        <div class="issue-item__header">
          <span class="issue-item__badge ${issue.state === "open" ? "issue-item__badge--open" : "issue-item__badge--closed"}">${issue.state}</span>
          <span class="issue-item__title"><a href="${issue.html_url}" target="_blank" rel="noopener">${escapeHtml(issue.title)}</a></span>
          <span class="text-secondary">#${issue.number}</span>
        </div>
        ${issue.labels?.length ? `<div class="flex gap-xs flex-wrap mt-md">${issue.labels.map(l => `<span class="issue-label" style="background-color:#${l.color || "666"}">${escapeHtml(l.name)}</span>`).join("")}</div>` : ""}
        <div class="issue-item__meta">by ${escapeHtml(issue.user?.login || "?")} · ${timeAgo(issue.created_at)} · ${issue.comments} comment${issue.comments !== 1 ? "s" : ""}</div>
      </div>`).join("")}</div>`;
  } catch (err) {
    container.innerHTML = `<div class="empty"><p>Could not load issues (${escapeHtml(err.message)}).</p><p class="mt-md"><a href="${REPO_URL}/issues?labels=${encodeURIComponent(modId)}" target="_blank" rel="noopener">View on GitHub →</a></p></div>`;
  }
}

/* ==================== Router ==================== */
function route() {
  const params = new URLSearchParams(location.search);
  const path = location.pathname.split("/").pop();
  const modId = params.get("id");
  initTheme();

  if (path === "mod.html" && modId) {
    renderModPage(modId);
  } else {
    renderHome();
  }
}

document.addEventListener("DOMContentLoaded", route);
