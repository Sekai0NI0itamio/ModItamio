# Graph Report - D:\ModItamio\docs  (2026-07-31)

## Corpus Check
- cluster-only mode — file stats not available

## Summary
- 147 nodes · 276 edges · 21 communities (13 shown, 8 thin omitted)
- Extraction: 95% EXTRACTED · 5% INFERRED · 0% AMBIGUOUS · INFERRED: 14 edges (avg confidence: 0.67)
- Token cost: 651 input · 219 output

## Graph Freshness
- Built from commit: `fc48578c`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- UI Utilities & Theming
- Issue Management
- Search & Filtering
- Mod Discovery UI
- Sidebar & Download Logic
- Mod List Rendering
- URL & State Management
- Tab & Issue View Logic
- Mod Detail Components
- Issue List Rendering
- Mod Page Initialization
- Search Initialization
- Version Display Logic
- Browse Mods Subdirectory
- Homepage
- Mod Page
- Mod Page Subdirectory
- Browse Mods Page
- Download Redirect
- Version Page
- Version Page Subdirectory

## God Nodes (most connected - your core abstractions)
1. `render()` - 20 edges
2. `renderTabContent()` - 14 edges
3. `modCard()` - 10 edges
4. `escapeHtml()` - 9 edges
5. `writeUrl()` - 9 edges
6. `syncUrl()` - 8 edges
7. `renderIssues()` - 8 edges
8. `normLoader()` - 7 edges
9. `loaderName()` - 7 edges
10. `init()` - 7 edges

## Surprising Connections (you probably didn't know these)
- `SeedProtect Mod Icon` --semantically_similar_to--> `SmoothContainer Mod`  [AMBIGUOUS] [semantically similar]
  mods/seedprotect/icon.png → CHANGELOG.md
- `getAvailableLoaders()` --indirect_call--> `normLoader()`  [INFERRED]
  assets/js/mod.js → assets/js/app.js
- `pickDefaultLoader()` --indirect_call--> `normLoader()`  [INFERRED]
  assets/js/mod.js → assets/js/app.js
- `renderIssues()` --indirect_call--> `normLoader()`  [INFERRED]
  assets/js/mod.js → assets/js/app.js
- `renderVersions()` --indirect_call--> `normLoader()`  [INFERRED]
  assets/js/mod.js → assets/js/app.js

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **ModItamio Website Pages** — index, mod, mods, version, browse_index, mod_index, version_index [INFERRED 0.75]

## Communities (21 total, 8 thin omitted)

### Community 0 - "UI Utilities & Theming"
Cohesion: 0.08
Nodes (25): applyTheme(), CATEGORY_ALIASES, CATEGORY_GROUP_NAMES, CATEGORY_GROUPS, CONFIG, deriveLinkLabel(), escapeHtml(), fetchJSON() (+17 more)

### Community 1 - "Issue Management"
Cohesion: 0.17
Nodes (14): getFilteredVersions(), getFilteredVersionsForSidebar(), ISSUE_STATUS, issueComments, issues, loadersInclude(), loadIssueComments(), pickDefaultGameVer() (+6 more)

### Community 2 - "Search & Filtering"
Cohesion: 0.23
Nodes (12): _activeSelection, ALL_MODS, allFacetCounts(), buildSidebarHtml(), clearFacetSearch(), compareVersions(), FACET_SEARCH, facetCounts() (+4 more)

### Community 3 - "Mod Discovery UI"
Cohesion: 0.29
Nodes (10): firstGifUrl(), formatNumber(), modCard(), sanitizeUrl(), timeAgo(), initDiscover(), renderFeatured(), renderHero() (+2 more)

### Community 4 - "Sidebar & Download Logic"
Cohesion: 0.35
Nodes (11): bindSidebarSelectors(), getAvailableGameVers(), getAvailableLoaders(), getSelectedFile(), loaderName(), onSidebarLoaderChange(), onSidebarVerChange(), renderDescription() (+3 more)

### Community 5 - "Mod List Rendering"
Cohesion: 0.20
Nodes (10): applyFilters(), render(), renderEmptyState(), renderFilterPills(), renderPagination(), renderSearchInlineAd(), renderSortSelect(), restoreState() (+2 more)

### Community 6 - "URL & State Management"
Cohesion: 0.31
Nodes (10): clearFilters(), clearMainSearch(), commitMainSearch(), goPage(), onMainSearchKeydown(), removePill(), saveState(), setSort() (+2 more)

### Community 7 - "Tab & Issue View Logic"
Cohesion: 0.31
Nodes (9): bindIssueEvents(), onVerFilterLoader(), onVerFilterVersion(), renderChangelog(), renderGallery(), renderTabContent(), setIssueFilter(), switchTab() (+1 more)

### Community 8 - "Mod Detail Components"
Cohesion: 0.33
Nodes (6): Changelog, SmoothContainer Changelog, Compatibility Matrix, SmoothContainer Compatibility, SeedProtect Mod Icon, SmoothContainer Mod

### Community 9 - "Issue List Rendering"
Cohesion: 0.70
Nodes (5): getFilteredIssues(), getIssueStatus(), renderIssueRow(), renderIssues(), renderStatusBadge()

### Community 10 - "Mod Page Initialization"
Cohesion: 0.40
Nodes (5): getPrimaryFile(), init(), pickDefaultLoader(), render(), renderTab()

### Community 11 - "Search Initialization"
Cohesion: 0.50
Nodes (4): init(), readUrl(), renderSkeleton(), syncSearchBox()

### Community 12 - "Version Display Logic"
Cohesion: 0.83
Nodes (3): init(), metaRow(), renderVersionHtml()

## Ambiguous Edges - Review These
- `SeedProtect Mod Icon` → `SmoothContainer Mod`  [AMBIGUOUS]
  mods/seedprotect/icon.png · relation: semantically_similar_to

## Knowledge Gaps
- **30 isolated node(s):** `PATHS`, `CONFIG`, `CATEGORY_GROUPS`, `CATEGORY_GROUP_NAMES`, `CATEGORY_ALIASES` (+25 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **8 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **What is the exact relationship between `SeedProtect Mod Icon` and `SmoothContainer Mod`?**
  _Edge tagged AMBIGUOUS (relation: semantically_similar_to) - confidence is low._
- **Why does `normLoader()` connect `UI Utilities & Theming` to `Issue List Rendering`, `Mod Page Initialization`, `Sidebar & Download Logic`, `Issue Management`?**
  _High betweenness centrality (0.360) - this node is a cross-community bridge._
- **Why does `modCard()` connect `Mod Discovery UI` to `UI Utilities & Theming`, `Mod List Rendering`?**
  _High betweenness centrality (0.323) - this node is a cross-community bridge._
- **Why does `render()` connect `Mod List Rendering` to `Mod Discovery UI`, `Search & Filtering`, `Search Initialization`, `URL & State Management`?**
  _High betweenness centrality (0.277) - this node is a cross-community bridge._
- **Are the 3 inferred relationships involving `modCard()` (e.g. with `renderFeatured()` and `renderRecent()`) actually correct?**
  _`modCard()` has 3 INFERRED edges - model-reasoned connections that need verification._
- **What connects `PATHS`, `CONFIG`, `CATEGORY_GROUPS` to the rest of the system?**
  _30 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `UI Utilities & Theming` be split into smaller, more focused modules?**
  _Cohesion score 0.08143939393939394 - nodes in this community are weakly interconnected._