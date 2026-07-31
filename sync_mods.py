#!/usr/bin/env python3
"""Sync mod data from Modrinth API to docs/data/."""
import json
import os
import sys
import time
import urllib.request
import urllib.error
import urllib.parse
from datetime import datetime, timezone

DATA_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "docs", "data")
API_BASE = "https://api.modrinth.com/v2"
USER_AGENT = "ModItamio-Sync/1.0 (https://github.com/Sekai0NI0itamio/ModItamio)"


def api_get(path, params=None):
    url = API_BASE + path
    if params:
        url += "?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(req, timeout=30) as resp:
                return json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as e:
            if e.code == 429:
                time.sleep(2 ** attempt)
                continue
            raise
        except Exception:
            if attempt == 2:
                raise
            time.sleep(1)
    return None


def hex_color(color_int):
    if color_int is None:
        return "#5b8c5a"
    return "#{:06x}".format(color_int & 0xFFFFFF)


def transform_project(proj, versions_data):
    all_game_versions = set()
    all_loaders = set()
    for v in versions_data:
        for gv in v.get("game_versions", []):
            all_game_versions.add(gv)
        for l in v.get("loaders", []):
            all_loaders.add(l.lower())

    proj_game_versions = set(proj.get("game_versions", []))
    proj_loaders = set(l.lower() for l in proj.get("loaders", []))
    all_game_versions.update(proj_game_versions)
    all_loaders.update(proj_loaders)

    versions = []
    for v in versions_data:
        files = []
        for f in v.get("files", []):
            files.append({
                "filename": f.get("filename", ""),
                "primary": f.get("primary", False),
                "size": f.get("size", 0),
                "url": f.get("url", ""),
            })
        versions.append({
            "name": v.get("name", v.get("version_number", "")),
            "version_number": v.get("version_number", ""),
            "changelog": v.get("changelog", "") or "",
            "date_published": v.get("date_published", ""),
            "downloads": v.get("downloads", 0),
            "version_type": v.get("version_type", "release"),
            "game_versions": v.get("game_versions", []),
            "loaders": [l.lower() for l in v.get("loaders", [])],
            "files": files,
        })

    versions.sort(key=lambda x: x.get("date_published", ""), reverse=True)

    license_info = proj.get("license") or {}
    license_id = license_info.get("id", "MIT") if isinstance(license_info, dict) else str(license_info)

    gallery = []
    for g in proj.get("gallery", []) or []:
        gallery.append({
            "url": g.get("url", ""),
            "title": g.get("title", ""),
            "description": g.get("description", ""),
            "created": g.get("created", ""),
        })

    slug = proj.get("slug", "")
    desc = proj.get("body", "") or ""
    import re
    gif_match = re.search(r'https?://[^\s"\'()<>]+\.gif(?:\?[^\s"\'()<>]*)?', desc, re.IGNORECASE)
    gif_url = gif_match.group(0) if gif_match else ""
    return {
        "mod_id": slug,
        "name": proj.get("title", ""),
        "summary": proj.get("description", ""),
        "description": desc,
        "author": "Sekai0ni0Itamio",
        "icon_url": proj.get("icon_url", "") or "",
        "color": hex_color(proj.get("color")),
        "categories": proj.get("categories", []),
        "client_side": proj.get("client_side", "optional"),
        "server_side": proj.get("server_side", "optional"),
        "downloads": proj.get("downloads", 0),
        "followers": proj.get("followers", 0),
        "date_published": proj.get("published", ""),
        "updated": proj.get("updated", ""),
        "license": license_id,
        "discord_url": proj.get("discord_url") or "",
        "issues_url": proj.get("issues_url") or "",
        "source_url": proj.get("source_url") or "",
        "wiki_url": proj.get("wiki_url") or "",
        "modrinth_url": f"https://modrinth.com/mod/{slug}",
        "site_url": f"mod/?id={slug}",
        "gif_url": gif_url,
        "loaders": sorted(all_loaders),
        "game_versions": sorted(all_game_versions),
        "gallery": gallery,
        "versions": versions,
    }


def compare_versions(a, b):
    def parse(v):
        parts = []
        for p in v.split("."):
            try:
                parts.append((0, int(p)))
            except ValueError:
                parts.append((1, p))
        return parts
    pa, pb = parse(a), parse(b)
    length = max(len(pa), len(pb))
    for i in range(length):
        ai = pa[i] if i < len(pa) else (0, 0)
        bi = pb[i] if i < len(pb) else (0, 0)
        if ai[0] != bi[0]:
            return -1 if ai[0] < bi[0] else 1
        if ai[1] != bi[1]:
            if isinstance(ai[1], int) and isinstance(bi[1], int):
                return ai[1] - bi[1]
            return -1 if str(ai[1]) < str(bi[1]) else 1
    return 0


def main():
    mods_list_path = os.path.join(DATA_DIR, "mods.json")
    with open(mods_list_path, "r", encoding="utf-8") as f:
        existing_mods = json.load(f)

    existing_slugs = {m["mod_id"] for m in existing_mods}

    print("Fetching project list from Modrinth API...")
    api_slugs = set()
    try:
        offset = 0
        while True:
            search = api_get("/search", {
                "facets": '[["author:Itamio"]]',
                "limit": "100",
                "offset": str(offset),
            })
            hits = search.get("hits", []) if search else []
            if not hits:
                break
            for h in hits:
                api_slugs.add(h["slug"])
            offset += len(hits)
            if offset >= (search.get("total_hits", 0) if search else 0):
                break
    except Exception as e:
        print(f"WARNING: Search API failed: {e}")

    if not api_slugs:
        print("WARNING: Could not fetch project list from API, using existing slugs")
        slugs_to_fetch = sorted(existing_slugs)
    else:
        print(f"API returned {len(api_slugs)} projects")
        removed = existing_slugs - api_slugs
        added = api_slugs - existing_slugs
        if removed:
            print(f"Removed (no longer on Modrinth): {removed}")
        if added:
            print(f"New projects detected: {added}")
        slugs_to_fetch = sorted(api_slugs)

    print(f"Fetching {len(slugs_to_fetch)} mods...")

    all_mod_data = []
    total_downloads = 0
    total_followers = 0
    all_categories = set()
    all_loaders = set()
    all_game_versions = set()
    errors = []

    for i, slug in enumerate(slugs_to_fetch):
        print(f"[{i+1}/{len(slugs_to_fetch)}] Fetching {slug}...", end=" ", flush=True)
        try:
            proj = api_get(f"/project/{slug}")
            if not proj:
                print("SKIP (no data)")
                errors.append((slug, "no data"))
                continue
            versions = api_get(f"/project/{slug}/version") or []
            print(f"ok ({len(versions)} versions)")

            mod_data = transform_project(proj, versions)
            out_path = os.path.join(DATA_DIR, f"{slug}.json")
            with open(out_path, "w", encoding="utf-8") as f:
                json.dump(mod_data, f, ensure_ascii=False, indent=2)

            all_mod_data.append({
                "mod_id": mod_data["mod_id"],
                "name": mod_data["name"],
                "summary": mod_data["summary"],
                "description": mod_data["description"][:500],
                "author": mod_data["author"],
                "icon_url": mod_data["icon_url"],
                "color": mod_data["color"],
                "categories": mod_data["categories"],
                "client_side": mod_data["client_side"],
                "server_side": mod_data["server_side"],
                "downloads": mod_data["downloads"],
                "followers": mod_data["followers"],
                "date_published": mod_data["date_published"],
                "updated": mod_data["updated"],
                "loaders": mod_data["loaders"],
                "game_versions": mod_data["game_versions"],
                "license": mod_data["license"],
                "issues_url": mod_data["issues_url"],
                "source_url": mod_data["source_url"],
                "wiki_url": mod_data["wiki_url"],
                "discord_url": mod_data["discord_url"],
                "modrinth_url": mod_data["modrinth_url"],
                "site_url": mod_data["site_url"],
                "gif_url": mod_data.get("gif_url", ""),
            })

            total_downloads += mod_data["downloads"]
            total_followers += mod_data["followers"]
            for c in mod_data["categories"]:
                all_categories.add(c)
            for l in mod_data["loaders"]:
                all_loaders.add(l)
            for gv in mod_data["game_versions"]:
                all_game_versions.add(gv)

            time.sleep(0.3)
        except Exception as e:
            print(f"ERROR: {e}")
            errors.append((slug, str(e)))

    all_mod_data.sort(key=lambda m: m["name"].lower())
    with open(mods_list_path, "w", encoding="utf-8") as f:
        json.dump(all_mod_data, f, ensure_ascii=False, indent=2)
    print(f"\nWrote {len(all_mod_data)} mods to {mods_list_path}")

    sorted_versions = sorted(all_game_versions, key=lambda v: (lambda x: [int(p) if p.isdigit() else p for p in v.split(".")])(v))
    try:
        sorted_versions.sort(key=lambda v: tuple(
            (0, int(p)) if p.isdigit() else (1, p) for p in v.split(".")
        ), reverse=True)
    except Exception:
        pass

    stats = {
        "mod_count": len(all_mod_data),
        "total_downloads": total_downloads,
        "total_followers": total_followers,
        "categories": sorted(all_categories),
        "loaders": sorted(all_loaders),
        "game_versions": sorted_versions,
        "generated_at": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    stats_path = os.path.join(DATA_DIR, "stats.json")
    with open(stats_path, "w", encoding="utf-8") as f:
        json.dump(stats, f, ensure_ascii=False, indent=2)
    print(f"Wrote stats to {stats_path}")

    if errors:
        print(f"\n{len(errors)} errors:")
        for slug, err in errors:
            print(f"  {slug}: {err}")
    else:
        print("\nAll mods fetched successfully!")


if __name__ == "__main__":
    main()
