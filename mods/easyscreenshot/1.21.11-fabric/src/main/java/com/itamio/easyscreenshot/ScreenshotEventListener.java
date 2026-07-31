package com.itamio.easyscreenshot;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public class ScreenshotEventListener {
    private static final Path SCREENSHOT_DIR = Path.of(
            System.getProperty("user.home"), "Downloads");
    private static final Set<String> knownScreenshots = new LinkedHashSet<>();
    private static final List<String> recentScreenshots = new ArrayList<>();
    private static boolean watching = false;

    public static void register() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            startWatching();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!watching) {
                startWatching();
            }
        });
    }

    private static void startWatching() {
        if (watching) return;
        try {
            Files.createDirectories(SCREENSHOT_DIR);
            loadExistingScreenshots();

            Thread watcherThread = new Thread(() -> {
                try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
                    SCREENSHOT_DIR.register(watcher,
                            StandardWatchEventKinds.ENTRY_CREATE,
                            StandardWatchEventKinds.ENTRY_DELETE);
                    watching = true;

                    while (watching) {
                        WatchKey key;
                        try {
                            key = watcher.poll(1, java.util.concurrent.TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            break;
                        }
                        if (key == null) continue;

                        for (WatchEvent<?> event : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = event.kind();
                            Path filename = (Path) event.context();
                            String name = filename.toString();

                            if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                                if (name.startsWith("20") && name.endsWith(".png")) {
                                    File file = SCREENSHOT_DIR.resolve(name).toFile();
                                    if (file.exists()) {
                                        onScreenshotCreated(file);
                                    }
                                }
                            } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                knownScreenshots.remove(name);
                                recentScreenshots.remove(SCREENSHOT_DIR.resolve(name).toString());
                            }
                        }
                        key.reset();
                    }
                } catch (IOException e) {
                    EasyScreenshot.LOGGER.warn("Screenshot watcher stopped", e);
                }
            }, "ScreenshotWatcher");
            watcherThread.setDaemon(true);
            watcherThread.start();
        } catch (IOException e) {
            EasyScreenshot.LOGGER.warn("Failed to start screenshot watcher", e);
        }
    }

    private static void loadExistingScreenshots() {
        try {
            File[] files = SCREENSHOT_DIR.toFile().listFiles((dir, name) ->
                    name.startsWith("20") && name.endsWith(".png"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File f : files) {
                    knownScreenshots.add(f.getName());
                    if (recentScreenshots.size() < 50) {
                        recentScreenshots.add(f.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            EasyScreenshot.LOGGER.warn("Failed to load existing screenshots", e);
        }
    }

    private static void onScreenshotCreated(File file) {
        String absPath = file.getAbsolutePath();
        knownScreenshots.add(file.getName());
        recentScreenshots.add(0, absPath);
        if (recentScreenshots.size() > 200) {
            recentScreenshots.remove(recentScreenshots.size() - 1);
        }

        if (ScreenshotConfig.getInstance().autoOpenFolder) {
            openFolder(file.getParentFile());
        }

        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return;

        client.execute(() -> {
            if (client.player == null) return;

            String escaped = absPath.replace("\\", "\\\\").replace("\"", "\\\"");
            Component message = buildScreenshotMessage(file.getName(), escaped);
            if (client.gui != null) {
                client.gui.getChat().addMessage(message);
            }
        });
    }

    private static Component buildScreenshotMessage(String filename, String escapedPath) {
        MutableComponent root = Component.literal("");
        root.append(Component.literal("[EasySS] ").withStyle(style ->
                style.withColor(net.minecraft.ChatFormatting.GOLD)));

        List<String> buttonOrder = ScreenshotConfig.getButtonOrder();

        for (int i = 0; i < buttonOrder.size(); i++) {
            if (i > 0) root.append(Component.literal(" "));
            String btn = buttonOrder.get(i).trim().toUpperCase();

            net.minecraft.ChatFormatting color = getButtonColor(btn.toLowerCase());
            String label = "[" + btn + "]";

            MutableComponent button = Component.literal(label).withStyle(style ->
                    style.withColor(color)
                            .withClickEvent(new ClickEvent.RunCommand(
                                    "/easyscreenshot " + btn.toLowerCase() + " \"" + escapedPath + "\""))
                            .withHoverEvent(new HoverEvent.ShowText(
                                    Component.literal(btn + ": " + filename))));
            root.append(button);
        }

        return root;
    }

    private static net.minecraft.ChatFormatting getButtonColor(String action) {
        ScreenshotConfig cfg = ScreenshotConfig.getInstance();
        String colorName;
        switch (action) {
            case "copy": colorName = cfg.copyColor; break;
            case "open": colorName = cfg.openColor; break;
            case "openfolder": colorName = cfg.openFolderColor; break;
            case "rename": colorName = cfg.renameColor; break;
            case "delete": colorName = cfg.deleteColor; break;
            default: return net.minecraft.ChatFormatting.WHITE;
        }
        try {
            return net.minecraft.ChatFormatting.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException e) {
            return net.minecraft.ChatFormatting.WHITE;
        }
    }

    public static void openFolder(File folder) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(folder);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"explorer", folder.getAbsolutePath()});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", folder.getAbsolutePath()});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", folder.getAbsolutePath()});
                }
            }
        } catch (IOException e) {
            EasyScreenshot.LOGGER.warn("Failed to open folder: {}", folder, e);
        }
    }

    public static void openFile(File file) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop.getDesktop().open(file);
            } else {
                String os = System.getProperty("os.name").toLowerCase();
                if (os.contains("win")) {
                    Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", file.getAbsolutePath()});
                } else if (os.contains("mac")) {
                    Runtime.getRuntime().exec(new String[]{"open", file.getAbsolutePath()});
                } else {
                    Runtime.getRuntime().exec(new String[]{"xdg-open", file.getAbsolutePath()});
                }
            }
        } catch (IOException e) {
            EasyScreenshot.LOGGER.warn("Failed to open file: {}", file, e);
        }
    }

    public static List<String> getRecentScreenshots() {
        return new ArrayList<>(recentScreenshots);
    }

    public static List<String> getRecentScreenshotPaths(int max) {
        List<String> result = new ArrayList<>();
        int count = 0;
        for (String path : recentScreenshots) {
            if (count >= max) break;
            File f = new File(path);
            if (f.exists() && f.isFile()) {
                result.add(path);
                count++;
            }
        }
        return result;
    }

    public static void refreshRecentList() {
        recentScreenshots.clear();
        try {
            File[] files = SCREENSHOT_DIR.toFile().listFiles((dir, name) ->
                    name.startsWith("20") && name.endsWith(".png"));
            if (files != null) {
                Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                for (File f : files) {
                    recentScreenshots.add(f.getAbsolutePath());
                    if (recentScreenshots.size() >= 200) break;
                }
            }
        } catch (Exception e) {
            EasyScreenshot.LOGGER.warn("Failed to refresh screenshot list", e);
        }
    }
}
