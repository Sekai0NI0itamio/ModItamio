# ModItamio

Source code and assets for **Itamio's** Minecraft mods.

- **Author:** Itamio
- **Contributor:** Asd1281yss
- **Donate:** [Ko-fi](https://ko-fi.com/U2P522M1IW)

## Repository layout

```
mods/              # One folder per mod, named by modId. Each contains
                  # one subfolder per version+loader (e.g. 1.21.1-fabric)
                  # holding the full source tree for that build.
assets/            # Modrinth page assets — banners, icons, screenshots.
                  # Referenced from Modrinth markdown via raw.githubusercontent URLs.
docs/              # Compatibility matrix + global changelog.
.github/           # Issue templates (bug / feature / crash).
```

## Mods

| Mod | Description | Loaders | Status |
|-----|-------------|---------|--------|
| _(none published yet)_ | | | |

> When a new mod is published, add a row here and a folder under `mods/<modId>/`.

## Reporting issues

Use the issue templates — pick the one that matches your report:

- **[Bug Report](../../issues/new?template=bug_report.yml)** — something doesn't work as expected
- **[Crash Report](../../issues/new?template=crash_report.yml)** — game crashed with this mod installed
- **[Feature Request](../../issues/new?template=feature_request.yml)** — suggest a new feature or improvement

Please include the Minecraft version, loader, and mod version. If you're reporting from the AssetDesign app, paste any `[MODAPP-ERROR]` lines from the log.

## License

Each mod has its own license — see the `license` field on its Modrinth project page. The repository structure and issue templates are MIT.
