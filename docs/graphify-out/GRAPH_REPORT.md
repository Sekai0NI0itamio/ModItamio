# Graph Report - docs  (2026-07-31)

## Corpus Check
- 68 files · ~162,177 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 76 nodes · 139 edges · 10 communities (8 shown, 2 thin omitted)
- Extraction: 97% EXTRACTED · 3% INFERRED · 0% AMBIGUOUS · INFERRED: 4 edges (avg confidence: 0.65)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `1482921b`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- app.js
- render
- escapeHtml
- search.js
- modCard
- renderSidebarFacets
- buildSidebarHtml
- init
- applyTheme
- fetchJSON

## God Nodes (most connected - your core abstractions)
1. `render()` - 20 edges
2. `escapeHtml()` - 9 edges
3. `writeUrl()` - 9 edges
4. `modCard()` - 8 edges
5. `saveState()` - 7 edges
6. `buildSidebarHtml()` - 6 edges
7. `init()` - 5 edges
8. `commitMainSearch()` - 5 edges
9. `renderSidebarFacets()` - 5 edges
10. `renderLoaderTags()` - 4 edges

## Surprising Connections (you probably didn't know these)
- `render()` --indirect_call--> `modCard()`  [INFERRED]
  assets/js/search.js → assets/js/app.js
- `applyFilters()` --indirect_call--> `getCategoryAlias()`  [INFERRED]
  assets/js/search.js → assets/js/app.js

## Import Cycles
- None detected.

## Communities (10 total, 2 thin omitted)

### Community 0 - "app.js"
Cohesion: 0.11
Nodes (11): CATEGORY_ALIASES, CATEGORY_GROUP_NAMES, CATEGORY_GROUPS, CONFIG, ICONS, LOADER_COLORS, LOADER_NAMES, LOADERS (+3 more)

### Community 1 - "render"
Cohesion: 0.17
Nodes (19): getCategoryAlias(), applyFilters(), clearFilters(), clearMainSearch(), commitMainSearch(), goPage(), onMainSearchKeydown(), removePill() (+11 more)

### Community 2 - "escapeHtml"
Cohesion: 0.25
Nodes (9): deriveLinkLabel(), escapeHtml(), modCardGifError(), normLoader(), renderCategoryTags(), renderLoaderTags(), renderMarkdown(), renderVersionTags() (+1 more)

### Community 3 - "search.js"
Cohesion: 0.33
Nodes (5): _activeSelection, FACET_SEARCH, PARAMS, renderSortSelect(), SORT_OPTIONS

### Community 4 - "modCard"
Cohesion: 0.50
Nodes (5): firstGifUrl(), formatNumber(), modCard(), sanitizeUrl(), timeAgo()

### Community 5 - "renderSidebarFacets"
Cohesion: 0.40
Nodes (5): allFacetCounts(), clearFacetSearch(), facetCounts(), onFacetSearchInput(), renderSidebarFacets()

### Community 6 - "buildSidebarHtml"
Cohesion: 0.50
Nodes (4): ALL_MODS, buildSidebarHtml(), compareVersions(), renderSidebarAd()

### Community 7 - "init"
Cohesion: 0.50
Nodes (4): init(), readUrl(), renderSkeleton(), syncSearchBox()

## Knowledge Gaps
- **14 isolated node(s):** `PATHS`, `CONFIG`, `CATEGORY_GROUPS`, `CATEGORY_GROUP_NAMES`, `CATEGORY_ALIASES` (+9 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **2 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `render()` connect `render` to `search.js`, `modCard`, `renderSidebarFacets`, `buildSidebarHtml`, `init`?**
  _High betweenness centrality (0.441) - this node is a cross-community bridge._
- **Why does `modCard()` connect `modCard` to `app.js`, `render`, `escapeHtml`?**
  _High betweenness centrality (0.414) - this node is a cross-community bridge._
- **Why does `applyFilters()` connect `render` to `search.js`?**
  _High betweenness centrality (0.082) - this node is a cross-community bridge._
- **What connects `PATHS`, `CONFIG`, `CATEGORY_GROUPS` to the rest of the system?**
  _14 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `app.js` be split into smaller, more focused modules?**
  _Cohesion score 0.10526315789473684 - nodes in this community are weakly interconnected._