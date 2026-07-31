const BASE = (typeof BASE_PATH !== "undefined") ? BASE_PATH.replace(/\/$/, "") : ".";

const PATHS = {
  root: BASE + "/",
  browse: BASE + "/browse/",
  mod: BASE + "/mod/",
  version: BASE + "/version/",
};

const CONFIG = {
  repoOwner: "Sekai0NI0itamio",
  repoName: "ModItamio",
  dataUrl: BASE + "/data",
  modsAssetBase: BASE + "/mods",
};

const REPO_URL = `https://github.com/${CONFIG.repoOwner}/${CONFIG.repoName}`;
const ISSUES_API = `https://api.github.com/repos/${CONFIG.repoOwner}/${CONFIG.repoName}/issues`;
const ISSUES_NEW_URL = `${REPO_URL}/issues/new`;

const CATEGORY_GROUPS = {
  gameplay: ["adventure", "cursed", "decoration", "economy", "equipment", "food", "game-mechanics", "magic", "management", "minigame", "mobs", "redstone", "storage", "technology", "transportation", "utility", "worldgen", "world-generation", "combat", "social"],
  performance: ["optimization"],
  library: ["library"],
};
const CATEGORY_GROUP_NAMES = { gameplay: "Gameplay", performance: "Performance", library: "Library" };
const CATEGORY_ALIASES = {
  "utility": "utility", "optimization": "optimization", "social": "social", "economy": "economy",
  "combat": "combat", "redstone": "technology", "world-generation": "world-generation",
  "worldgen": "world-generation", "adventure": "adventure", "decoration": "decoration",
  "equipment": "equipment", "food": "food", "magic": "magic", "mobs": "mobs",
  "storage": "storage", "technology": "technology", "transportation": "transportation",
  "cursed": "cursed", "library": "library", "management": "management", "minigame": "minigame",
  "game-mechanics": "game-mechanics",
};
const LOADERS = ["fabric", "forge", "neoforge", "quilt", "liteloader", "rift", "modloader"];
const LOADER_NAMES = { fabric: "Fabric", forge: "Forge", neoforge: "NeoForge", quilt: "Quilt", liteloader: "LiteLoader", rift: "Rift", modloader: "ModLoader" };
const LOADER_COLORS = {
  fabric: "#5b8c5a", forge: "#6b5ce7", neoforge: "#d4723a", quilt: "#8b6fb5",
  liteloader: "#5b8c5a", rift: "#7a9e7e", modloader: "#9e8b6f",
};
function normLoader(l) { return String(l || "").toLowerCase().replace(/[^a-z]/g, ""); }
const VERSION_COLORS = { release: "#4a7c59", beta: "#c49a3c", alpha: "#c45c5c" };
const VERSION_NAMES = { release: "Release", beta: "Beta", alpha: "Alpha" };

function $(sel, root) { return (root || document).querySelector(sel); }
function $$(sel, root) { return [...(root || document).querySelectorAll(sel)]; }

function formatNumber(n) {
  if (n == null) return "0";
  if (n >= 1000000) return (n / 1000000).toFixed(1).replace(/\.0$/, "") + "M";
  if (n >= 1000) return (n / 1000).toFixed(1).replace(/\.0$/, "") + "k";
  return String(n);
}
function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return "";
  if (bytes < 1024) return bytes + " B";
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + " KiB";
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + " MiB";
  return (bytes / 1073741824).toFixed(1) + " GiB";
}
function formatDate(iso) {
  if (!iso) return "";
  return new Date(iso).toLocaleDateString("en-US", { year: "numeric", month: "short", day: "numeric" });
}
function timeAgo(iso) {
  if (!iso) return "";
  const d = Math.floor((Date.now() - new Date(iso).getTime()) / 86400000);
  if (d < 1) return "today";
  if (d < 7) return d + "d ago";
  if (d < 30) return Math.floor(d / 7) + "w ago";
  if (d < 365) return Math.floor(d / 30) + "mo ago";
  return Math.floor(d / 365) + "y ago";
}
function escapeHtml(s) {
  if (s == null) return "";
  return String(s).replace(/[&<>"']/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

function sanitizeUrl(u) {
  if (!u) return "";
  return String(u).trim().replace(/^[`'"]+|[`'"]+$/g, "");
}

function deriveLinkLabel(url) {
  try {
    const u = new URL(url);
    const host = u.hostname.toLowerCase();
    const path = u.pathname;
    const params = u.searchParams;
    if (host === "ko-fi.com") {
      return '<span class="md-inline-badge md-badge--kofi">☕ Ko-fi</span>';
    }
    if (host === "modrinth.com" || host.endsWith(".modrinth.com")) {
      const verMatch = path.match(/\/mod\/[^/]+\/versions/);
      const loader = params.get("l");
      if (verMatch && loader) {
        const k = normLoader(loader);
        const name = LOADER_NAMES[k] || loader;
        return '<span class="md-inline-badge md-badge--loader md-badge--' + escapeHtml(k) + '">' + escapeHtml(name) + '</span>';
      }
      if (path.startsWith("/mod/")) {
        const parts = path.split("/").filter(Boolean);
        if (parts.length >= 2) return escapeHtml(parts[1]);
      }
      return "Modrinth";
    }
    if (host === "github.com") {
      const parts = path.split("/").filter(Boolean);
      if (parts.length >= 2) return escapeHtml(parts[0] + "/" + parts[1]);
      return "GitHub";
    }
    if (host === "curseforge.com" || host.endsWith(".curseforge.com")) {
      return "CurseForge";
    }
    if (host === "youtube.com" || host === "youtu.be" || host.endsWith(".youtube.com")) {
      return "YouTube";
    }
    if (host === "discord.gg" || host === "discord.com" || host.endsWith(".discord.com")) {
      return "Discord";
    }
    if (host === "twitter.com" || host === "x.com") {
      return "Twitter/X";
    }
    if (host === "reddit.com" || host.endsWith(".reddit.com")) {
      return "Reddit";
    }
    const dispHost = host.replace(/^www\./, "");
    return escapeHtml(dispHost);
  } catch(e) {
    return escapeHtml(url.slice(0, 60));
  }
}

function renderMarkdown(md) {
  if (!md) return "";
  md = md.replace(/\r\n/g, "\n");
  md = md.replace(/<!--[\s\S]*?-->/g, "");
  const safeBlocks = [];
  md = md.replace(/<center\b[^>]*>[\s\S]*?<\/center>/gi, function(m) {
    const idx = safeBlocks.length;
    safeBlocks.push(m);
    return "\u0000HTML" + idx + "\u0000";
  });
  md = md.replace(/<a\s[^>]*href\s*=\s*"[^"]*"[^>]*>\s*<img\s[^>]*src\s*=\s*"[^"]*"[^>]*>\s*<\/a>/gi, function(m) {
    const idx = safeBlocks.length;
    safeBlocks.push(m);
    return "\u0000HTML" + idx + "\u0000";
  });
  md = md.replace(/<img\s[^>]*src\s*=\s*"[^"]*"[^>]*\/?>/gi, function(m) {
    const idx = safeBlocks.length;
    safeBlocks.push(m);
    return "\u0000HTML" + idx + "\u0000";
  });
  md = md.replace(/<br\s*\/?>/gi, function(m) {
    const idx = safeBlocks.length;
    safeBlocks.push(m);
    return "\u0000HTML" + idx + "\u0000";
  });
  md = md.replace(/<details\b[^>]*>([\s\S]*?)<\/details>/gi, function(m, inner) {
    const idx = safeBlocks.length;
    const summaryMatch = inner.match(/<summary\b[^>]*>([\s\S]*?)<\/summary>/i);
    let summaryHtml = "Details";
    let bodyHtml = "";
    function hardBreaks(text) {
      return text.replace(/\n{2,}/g, "\u0000PARA\u0000").replace(/\n/g, "  \n").replace(/\u0000PARA\u0000/g, "\n\n");
    }
    if (summaryMatch) {
      summaryHtml = inline(summaryMatch[1].trim());
      const bodyRaw = inner.replace(summaryMatch[0], "").trim();
      bodyHtml = bodyRaw ? renderMarkdown(hardBreaks(bodyRaw)) : "";
    } else {
      bodyHtml = renderMarkdown(hardBreaks(inner.trim()));
    }
    const openAttr = /<details\b[^>]*\bopen\b/i.test(m) ? " open" : "";
    const safe = '<details class="md-details"' + openAttr + '><summary>' + summaryHtml + '</summary><div class="md-details__body">' + bodyHtml + '</div></details>';
    safeBlocks.push(safe);
    return "\u0000HTML" + idx + "\u0000";
  });
  md = md.replace(/<iframe\b[^>]*src\s*=\s*"([^"]+)"[^>]*><\/iframe>/gi, function(m, src) {
    const idx = safeBlocks.length;
    let safe = "";
    try {
      const url = new URL(src);
      const host = url.hostname.toLowerCase();
      if (host === "www.youtube.com" || host === "youtube.com" || host === "www.youtube-nocookie.com" || host === "youtube-nocookie.com" || host === "player.twitch.tv" || host === "clips.twitch.tv") {
        const width = m.match(/width\s*=\s*"(\d+)"/i);
        const height = m.match(/height\s*=\s*"(\d+)"/i);
        const title = m.match(/title\s*=\s*"([^"]*)"/i);
        const allow = m.match(/allow\s*=\s*"([^"]*)"/i);
        safe = '<div class="md-video-wrap"><iframe src="' + src + '"' +
          (width ? ' width="' + width[1] + '"' : '') +
          (height ? ' height="' + height[1] + '"' : '') +
          (title ? ' title="' + title[1].replace(/"/g, '&quot;') + '"' : ' title="Video player"') +
          ' frameborder="0"' +
          (allow ? ' allow="' + allow[1].replace(/"/g, '&quot;') + '"' : ' allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"') +
          ' allowfullscreen loading="lazy"></iframe></div>';
      }
    } catch(e) {}
    safeBlocks.push(safe);
    return "\u0000HTML" + idx + "\u0000";
  });
  function restoreSafe(text) {
    let result = text;
    for (let pass = 0; pass < 5; pass++) {
      const before = result;
      result = result.replace(/\u0000HTML(\d+)\u0000/g, function(_, i) {
        return safeBlocks[parseInt(i, 10)] || "";
      });
      if (result === before) break;
    }
    return result;
  }
  const lines = md.split("\n");
  const out = [];
  let i = 0, inList = false, listType = "ul", inPara = false;
  function closeList() { if (inList) { out.push("</" + listType + ">"); inList = false; } }
  function closePara() { if (inPara) { out.push("</p>"); inPara = false; } }
  function inline(text) {
    let html = escapeHtml(text);
    const placeholders = [];
    function protect(s) {
      const i = placeholders.length;
      placeholders.push(s);
      return "\u0001PL" + i + "\u0001";
    }
    html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, function(m, alt, src) {
      return protect('<img alt="' + alt + '" src="' + src + '" loading="lazy">');
    });
    html = html.replace(/\[([^\]]*)\]\(([^)]+)\)/g, function(match, label, url) {
      const trimmedLabel = (label || "").trim();
      if (trimmedLabel) {
        return protect('<a href="' + url + '" target="_blank" rel="noopener">' + trimmedLabel + '</a>');
      }
      return protect('<a href="' + url + '" target="_blank" rel="noopener" class="md-badge-link">' + deriveLinkLabel(url) + '</a>');
    });
    html = html.replace(/`([^`]+)`/g, function(m, code) {
      return protect("<code>" + code + "</code>");
    });
    html = html
      .replace(/\*\*\*([^*]+)\*\*\*/g, "<strong><em>$1</em></strong>")
      .replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>")
      .replace(/__([^_]+)__/g, "<strong>$1</strong>")
      .replace(/(?<!\*)\*([^*]+)\*(?!\*)/g, "<em>$1</em>")
      .replace(/(^|[^_])_([^_]+)_/g, "$1<em>$2</em>")
      .replace(/~~([^~]+)~~/g, "<del>$1</del>")
      .replace(/:white_check_mark:|✅/g, '<span class="md-check">✓</span>')
      .replace(/:x:|:negative_squared_cross_mark:|❌/g, '<span class="md-cross">✗</span>')
      .replace(/:warning:|⚠️/g, '<span class="md-warn">⚠</span>');
    for (let pass = 0; pass < 5; pass++) {
      const before = html;
      html = html.replace(/\u0001PL(\d+)\u0001/g, function(_, i) {
        return placeholders[parseInt(i, 10)] || "";
      });
      if (html === before) break;
    }
    return restoreSafe(html);
  }
  function parseTableRow(row, isHeader) {
    const cells = row.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map(c => c.trim());
    const tag = isHeader ? "th" : "td";
    return "<tr>" + cells.map(c => "<" + tag + ">" + inline(c) + "</" + tag + ">").join("") + "</tr>";
  }
  function isSeparatorRow(line) {
    return /^\s*\|?\s*:?-{3,}:?\s*(\|\s*:?-{3,}:?\s*)+\|?\s*$/.test(line);
  }
  while (i < lines.length) {
    const line = lines[i];
    if (line.trimStart().startsWith("```")) {
      closePara(); closeList();
      const code = []; i++;
      while (i < lines.length && !lines[i].trimStart().startsWith("```")) code.push(lines[i++]);
      i++; out.push("<pre><code>" + escapeHtml(code.join("\n")) + "</code></pre>"); continue;
    }
    if (line.trim().startsWith("|") && i + 1 < lines.length && isSeparatorRow(lines[i + 1].trim())) {
      closePara(); closeList();
      const headerRow = line;
      const sepRow = lines[i + 1];
      i += 2;
      const aligns = sepRow.trim().replace(/^\|/, "").replace(/\|$/, "").split("|").map(c => {
        const t = c.trim();
        if (t.startsWith(":") && t.endsWith(":")) return "center";
        if (t.endsWith(":")) return "right";
        if (t.startsWith(":")) return "left";
        return null;
      });
      const rows = [parseTableRow(headerRow, true)];
      while (i < lines.length && lines[i].trim().startsWith("|")) {
        rows.push(parseTableRow(lines[i], false));
        i++;
      }
      out.push('<div class="md-table-wrap"><table><thead>' + rows[0] + '</thead><tbody>' + rows.slice(1).join("") + '</tbody></table></div>');
      continue;
    }
    const h = line.match(/^(#{1,4})\s+(.*)$/);
    if (h) { closePara(); closeList(); const level = Math.min(h[1].length + 1, 4); out.push("<h" + level + ">" + inline(h[2]) + "</h" + level + ">"); i++; continue; }
    if (/^(-{3,}|\*{3,})\s*$/.test(line)) { closePara(); closeList(); out.push("<hr>"); i++; continue; }
    if (line.startsWith("> ")) { closePara(); closeList(); const q = []; while (i < lines.length && lines[i].startsWith("> ")) q.push(lines[i++].slice(2)); out.push("<blockquote>" + inline(q.join(" ")) + "</blockquote>"); continue; }
    if (/^\s*[-*+]\s+/.test(line)) {
      closePara();
      if (!inList || listType !== "ul") { closeList(); inList = true; listType = "ul"; out.push("<ul>"); }
      out.push("<li>" + inline(line.replace(/^\s*[-*+]\s+/, "").replace(/  $/, "")) + "</li>"); i++; continue;
    }
    if (/^\s*\d+\.\s+/.test(line)) {
      closePara();
      if (!inList || listType !== "ol") { closeList(); inList = true; listType = "ol"; out.push("<ol>"); }
      out.push("<li>" + inline(line.replace(/^\s*\d+\.\s+/, "").replace(/  $/, "")) + "</li>"); i++; continue;
    }
    if (/^\s*\u0000HTML\d+\u0000\s*$/.test(line)) {
      closePara(); closeList(); out.push(restoreSafe(line.trim())); i++; continue;
    }
    if (line.trim() === "") { closePara(); closeList(); i++; continue; }
    closeList();
    const strippedLine = line.replace(/  $/, "");
    if (!inPara) { out.push("<p>"); inPara = true; }
    else {
      const prevRaw = lines[i - 1] || "";
      if (/  $/.test(prevRaw)) out.push("<br>");
      else out.push(" ");
    }
    out.push(inline(strippedLine));
    i++;
  }
  closePara(); closeList();
  return out.join("\n");
}

const ICONS = {
  sprout: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M7 20h10"/><path d="M10 20c5.5-2.5.8-6.4 3-10"/><path d="M9.5 9.4c1.1.8 1.8 2.2 2.3 3.7-2 .4-3.5.4-4.8-.3-1.2-.6-2.3-1.9-3-4.2 2.8-.5 4.4 0 5.5.8z"/><path d="M14.1 6a7 7 0 0 0-1.1 4c1.9-.1 3.3-.6 4.3-1.4 1-1 1.6-2.3 1.7-4.6-2.7.1-4 1-4.9 2z"/></svg>',
  download: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>',
  heart: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/></svg>',
  star: '<svg width="16" height="16" viewBox="0 0 24 24" fill="currentColor"><path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z"/></svg>',
  search: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/></svg>',
  sun: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/></svg>',
  moon: '<svg width="17" height="17" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/></svg>',
  menu: '<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="3" y1="6" x2="21" y2="6"/><line x1="3" y1="12" x2="21" y2="12"/><line x1="3" y1="18" x2="21" y2="18"/></svg>',
  external: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/><polyline points="15 3 21 3 21 9"/><line x1="10" y1="14" x2="21" y2="3"/></svg>',
  chevron_down: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="6 9 12 15 18 9"/></svg>',
  chevron_left: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="15 18 9 12 15 6"/></svg>',
  chevron_right: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="9 18 15 12 9 6"/></svg>',
  clock: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>',
  book: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20"/><path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z"/></svg>',
  images: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><rect x="3" y="3" width="18" height="18" rx="2" ry="2"/><circle cx="8.5" cy="8.5" r="1.5"/><polyline points="21 15 16 10 5 21"/></svg>',
  file: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M13 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9z"/><polyline points="13 2 13 9 20 9"/></svg>',
  alert: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>',
  code: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="16 18 22 12 16 6"/><polyline points="8 6 2 12 8 18"/></svg>',
  github: '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/></svg>',
  filter: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polygon points="22 3 2 3 10 12.46 10 19 14 21 14 12.46 22 3"/></svg>',
  x: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/></svg>',
  check: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"/></svg>',
  arrow_up_right: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="7" y1="17" x2="17" y2="7"/><polyline points="7 7 17 7 17 17"/></svg>',
  arrow_down_right: '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="7" y1="7" x2="17" y2="17"/><polyline points="17 7 17 17 7 17"/></svg>',
  discord: '<svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor"><path d="M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028c.462-.63.874-1.295 1.226-1.994a.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z"/></svg>',
  message: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>',
  loader: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="spin-icon"><path d="M21 12a9 9 0 1 1-6.219-8.56"/></svg>',
  tag: '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg>',
  send: '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg>',
};

const BOTANICAL_SVG = '<svg viewBox="0 0 160 210" fill="none"><path d="M82 206C81 150 84 104 105 55" stroke="currentColor" stroke-width="2"/><path d="M91 111C61 95 46 71 51 42C79 48 95 69 91 111Z" fill="currentColor" fill-opacity=".14" stroke="currentColor"/><path d="M92 96C115 77 130 54 127 27C102 36 90 59 92 96Z" fill="currentColor" fill-opacity=".14" stroke="currentColor"/><path d="M83 154C53 145 35 125 35 100C62 105 80 123 83 154Z" fill="currentColor" fill-opacity=".14" stroke="currentColor"/></svg>';

let _theme = localStorage.getItem("mi-theme") || "light";
function applyTheme() {
  document.documentElement.setAttribute("data-theme", _theme);
}
function toggleTheme() {
  _theme = _theme === "dark" ? "light" : "dark";
  localStorage.setItem("mi-theme", _theme);
  applyTheme();
  const btn = document.getElementById("theme-btn");
  if (btn) btn.innerHTML = ICONS[_theme === "dark" ? "sun" : "moon"];
  document.querySelectorAll(".nav__dropdown-theme").forEach(function(b) {
    b.innerHTML = ICONS[_theme === "dark" ? "sun" : "moon"] + " Theme";
  });
}
applyTheme();

function renderLoaderTags(loaders) {
  return (loaders || []).map(l => {
    const k = normLoader(l);
    const name = LOADER_NAMES[k] || l;
    const color = LOADER_COLORS[k] || "var(--color-text-dim)";
    return `<span class="tag tag--loader" style="--loader-color:${color}">${escapeHtml(name)}</span>`;
  }).join("");
}
function renderCategoryTags(cats) {
  return (cats || []).slice(0, 3).map(c => `<span class="tag tag--cat">${escapeHtml(c)}</span>`).join("");
}
function renderVersionTags(versions) {
  return (versions || []).slice(0, 5).map(v => `<span class="tag tag--version">${escapeHtml(v)}</span>`).join("") +
    ((versions || []).length > 5 ? `<span class="tag tag--version">+${versions.length - 5}</span>` : "");
}
function versionBadge(type) {
  const t = type || "release";
  return `<span class="tag tag--vtype" style="--vtype-color:${VERSION_COLORS[t] || VERSION_COLORS.release}">${escapeHtml(VERSION_NAMES[t] || t)}</span>`;
}

let cardIndex = 0;
let CARD_LINK_EXTRA = "";

function firstGifUrl(m) {
  const direct = sanitizeUrl(m.gif_url);
  if (/^https?:\/\//i.test(direct)) return direct;
  const desc = m.description || "";
  const hit = desc.match(/https?:\/\/[^\s"'()<>]+\.gif(?:\?[^\s"'()<>]*)?/i);
  return hit ? hit[0] : "";
}

function modCardGifError(img) {
  const show = img.closest(".mod-card__media");
  if (!show) return;
  const color = img.getAttribute("data-color") || "";
  const icon = img.getAttribute("data-icon") || "";
  const placeholder = document.createElement("div");
  placeholder.className = "mod-card__placeholder";
  if (color) placeholder.style.background = color;
  if (icon) placeholder.innerHTML = '<img class="mod-card__placeholder-icon" src="' + escapeHtml(icon) + '" alt="" loading="lazy">';
  show.insertBefore(placeholder, show.firstChild);
  img.remove();
}

function modCard(m) {
  const iconUrl = sanitizeUrl(m.icon_url) || CONFIG.modsAssetBase + "/" + m.mod_id + "/icon.png";
  const gifUrl = firstGifUrl(m);
  const idx = cardIndex++;
  const href = PATHS.mod + "?id=" + encodeURIComponent(m.mod_id) + (CARD_LINK_EXTRA ? "&" + CARD_LINK_EXTRA : "");
  const color = /^#[0-9a-f]{3,8}$/i.test(m.color || "") ? m.color : "";
  const iconHtml = escapeHtml(iconUrl);
  const firstLetter = (m.name || "M")[0].toUpperCase();
  const stats =
    '<span class="mod-card__stat">' + ICONS.download + ' ' + formatNumber(m.downloads) + '</span>' +
    '<span class="mod-card__stat">' + ICONS.heart + ' ' + formatNumber(m.followers) + '</span>' +
    '<span class="mod-card__stat">' + ICONS.clock + ' ' + timeAgo(m.updated || m.date_published) + '</span>';
  const loaders = renderLoaderTags((m.loaders || []).slice(0, 3));

  /* media area: GIF/screenshot or colored placeholder */
  var media;
  if (gifUrl) {
    media = '<img class="mod-card__gif" src="' + escapeHtml(gifUrl) + '" alt="" loading="lazy"' +
      ' data-icon="' + iconHtml + '"' +
      (color ? ' data-color="' + color + '"' : "") +
      ' onerror="modCardGifError(this)">';
  } else {
    media = '<div class="mod-card__placeholder"' + (color ? ' style="background:' + color + '"' : "") + '>' +
      '<span class="mod-card__letter">' + firstLetter + '</span>' +
    '</div>';
  }

  return '<a class="mod-card" href="' + href + '" style="--i:' + idx + '">' +
    '<div class="mod-card__media">' + media +
      '<img class="mod-card__icon" src="' + iconHtml + '" alt="" loading="lazy"' +
        ' onerror="this.onerror=null;this.style.display=\'none\'">' +
    '</div>' +
    '<div class="mod-card__body">' +
      '<div class="mod-card__header">' +
        '<h3 class="mod-card__title">' + escapeHtml(m.name) + '</h3>' +
        '<span class="mod-card__author">by ' + escapeHtml(m.author || "Itamio") + '</span>' +
      '</div>' +
      '<p class="mod-card__summary">' + escapeHtml(m.summary || "") + '</p>' +
      '<div class="mod-card__tags">' + loaders + '</div>' +
      '<div class="mod-card__meta">' + stats + '</div>' +
    '</div>' +
  '</a>';
}
function resetCardIndex() { cardIndex = 0; }

function renderNavbar(active) {
  const el = document.getElementById("navbar");
  if (!el) return;
  el.className = "nav";
  el.innerHTML = '<div class="nav__inner">' +
    '<a href="' + PATHS.root + '" class="nav__brand"><span class="nav__logo">' + ICONS.sprout + '</span><span class="nav__brand-name">ModItamio</span></a>' +
    '<nav class="nav__links" id="nav-links">' +
      '<a href="' + PATHS.root + '" class="nav__link' + (active === "discover" ? " nav__link--active" : "") + '">Discover</a>' +
      '<a href="' + PATHS.browse + '" class="nav__link' + (active === "mods" ? " nav__link--active" : "") + '">Browse</a>' +
      '<a href="' + REPO_URL + '/issues" target="_blank" rel="noopener" class="nav__link">Issues' + ICONS.external + '</a>' +
      '<a href="' + REPO_URL + '" target="_blank" rel="noopener" class="nav__link nav__link--icon" title="GitHub">' + ICONS.github + '</a>' +
      '<button id="theme-btn" class="nav__theme-btn" onclick="toggleTheme()" title="Toggle theme">' + ICONS[_theme === "dark" ? "sun" : "moon"] + '</button>' +
    '</nav>' +
    '<button class="nav__hamburger" id="nav-hamburger" aria-expanded="false" aria-controls="nav-dropdown" aria-label="Toggle navigation">' +
      '<span class="nav__hamburger-line"></span>' +
      '<span class="nav__hamburger-line"></span>' +
      '<span class="nav__hamburger-line"></span>' +
    '</button>' +
    '<div class="nav__dropdown" id="nav-dropdown" role="menu">' +
      '<a href="' + PATHS.root + '" class="nav__dropdown-link' + (active === "discover" ? " nav__dropdown-link--active" : "") + '" role="menuitem">Discover</a>' +
      '<a href="' + PATHS.browse + '" class="nav__dropdown-link' + (active === "mods" ? " nav__dropdown-link--active" : "") + '" role="menuitem">Browse</a>' +
      '<div class="nav__dropdown-divider"></div>' +
      '<a href="' + REPO_URL + '/issues" target="_blank" rel="noopener" class="nav__dropdown-link" role="menuitem">Issues' + ICONS.external + '</a>' +
      '<a href="' + REPO_URL + '" target="_blank" rel="noopener" class="nav__dropdown-link" role="menuitem">' + ICONS.github + ' GitHub</a>' +
      '<button class="nav__dropdown-theme" onclick="toggleTheme()" title="Toggle theme">' + ICONS[_theme === "dark" ? "sun" : "moon"] + ' Theme</button>' +
    '</div>' +
  '</div>';

  // Hamburger toggle
  const hamburger = document.getElementById("nav-hamburger");
  const dropdown = document.getElementById("nav-dropdown");
  const nav = document.getElementById("navbar");

  if (hamburger && dropdown && nav) {
    const toggleMenu = function() {
      const isOpen = nav.classList.toggle("nav--open");
      hamburger.setAttribute("aria-expanded", String(isOpen));
    };
    const closeMenu = function() {
      nav.classList.remove("nav--open");
      hamburger.setAttribute("aria-expanded", "false");
    };

    hamburger.addEventListener("click", toggleMenu);

    // Keyboard: Escape closes menu
    document.addEventListener("keydown", function(e) {
      if (e.key === "Escape" && nav.classList.contains("nav--open")) {
        closeMenu();
        hamburger.focus();
      }
    });

    // Click outside to close
    document.addEventListener("click", function(e) {
      if (!nav.contains(e.target) && nav.classList.contains("nav--open")) {
        closeMenu();
      }
    });

    // Close menu on resize to desktop
    window.addEventListener("resize", function() {
      if (window.innerWidth > 620 && nav.classList.contains("nav--open")) {
        closeMenu();
      }
    });
    dropdown.addEventListener("click", function(e) {
      if (e.target.closest("a") || e.target.closest("button")) {
        if (window.innerWidth <= 620) {
          closeMenu();
        }
      }
    });
  }
}

async function fetchJSON(url) {
  const r = await fetch(url);
  if (!r.ok) throw new Error("Failed to fetch " + url);
  return r.json();
}

async function loadAllMods() {
  const data = await fetchJSON(CONFIG.dataUrl + "/mods.json");
  return Array.isArray(data) ? data : (data.mods || []);
}

function getCategoryAlias(cat) {
  const lc = (cat || "").toLowerCase();
  return CATEGORY_ALIASES[lc] || lc;
}

function modMatchesQuery(m, q) {
  if (!q) return true;
  const terms = q.toLowerCase().split(/\s+/).filter(Boolean);
  const hay = (m.name + " " + (m.summary || "") + " " + (m.description || "") + " " + (m.author || "") + " " + (m.categories || []).join(" ")).toLowerCase();
  return terms.every(t => hay.includes(t));
}

async function loadIssues() {
  try {
    const r = await fetch(ISSUES_API + "?state=open&per_page=30&sort=updated");
    if (!r.ok) return [];
    return await r.json();
  } catch (e) { return []; }
}

function closeMenuOnClick() {
  document.addEventListener("click", (e) => {
    document.querySelectorAll(".menu").forEach(menu => {
      if (!menu.contains(e.target) && !e.target.closest(".btn-group")) menu.style.display = "none";
    });
  });
}
closeMenuOnClick();
