package asd.itamio.optimizeddaycounter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Configuration for the Optimized Day Counter mod.
 * Supports hot-reloading by checking the file's last-modified timestamp.
 */
public class DayCounterConfig {

    private static final long RELOAD_CHECK_INTERVAL_MS = 500L;

    private final File file;

    private Anchor anchor = Anchor.TOP_RIGHT;
    private DisplayMode displayMode = DisplayMode.DAYS;
    private int offsetX = 6;
    private int offsetY = 6;
    private long lastModified = -1L;
    private long lastLength = -1L;
    private long lastCheckTime = 0L;

    public DayCounterConfig(File file) {
        this.file = file;
    }

    public synchronized void load() {
        ensureParentDirectoryExists();
        if (!file.exists()) {
            writeDefaultFile();
        }
        readFile();
    }

    public synchronized void reloadIfChanged() {
        long now = System.currentTimeMillis();
        if (now - lastCheckTime < RELOAD_CHECK_INTERVAL_MS) {
            return;
        }
        lastCheckTime = now;

        if (!file.exists()) {
            writeDefaultFile();
            readFile();
            return;
        }

        long modified = file.lastModified();
        long length = file.length();
        if (modified != lastModified || length != lastLength) {
            readFile();
        }
    }

    public synchronized Anchor getAnchor() {
        return anchor;
    }

    public synchronized DisplayMode getDisplayMode() {
        return displayMode;
    }

    public synchronized int getOffsetX() {
        return offsetX;
    }

    public synchronized int getOffsetY() {
        return offsetY;
    }

    private void ensureParentDirectoryExists() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            System.err.println("[MODAPP-ERROR] Could not create config directory: " + parent.getAbsolutePath());
        }
    }

    private void writeDefaultFile() {
        List<String> lines = new ArrayList<String>();
        lines.add("# Optimized Day Counter configuration");
        lines.add("# This file hot-reloads while the game is running.");
        lines.add("#");
        lines.add("# display_mode options:");
        lines.add("# days");
        lines.add("# days_hour");
        lines.add("# days_hour_minute");
        lines.add("display_mode=days");
        lines.add("");
        lines.add("# anchor options:");
        lines.add("# top_left");
        lines.add("# top_right");
        lines.add("# bottom_left");
        lines.add("# bottom_right");
        lines.add("# center_top");
        lines.add("# center_bottom");
        lines.add("anchor=top_right");
        lines.add("");
        lines.add("# Pixel offsets from the selected anchor.");
        lines.add("offset_x=6");
        lines.add("offset_y=6");

        try {
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            System.err.println("[MODAPP-ERROR] Could not write default config file: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    private void readFile() {
        try {
            List<String> lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            applyLines(lines);
            lastModified = file.lastModified();
            lastLength = file.length();
        } catch (IOException exception) {
            System.err.println("[MODAPP-ERROR] Could not read config file: " + exception.getMessage());
            exception.printStackTrace();
        }
    }

    private void applyLines(List<String> lines) {
        Anchor parsedAnchor = Anchor.TOP_RIGHT;
        DisplayMode parsedDisplayMode = DisplayMode.DAYS;
        int parsedOffsetX = 6;
        int parsedOffsetY = 6;

        for (String rawLine : lines) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0 || separatorIndex >= line.length() - 1) {
                continue;
            }

            String key = line.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(separatorIndex + 1).trim();

            if ("anchor".equals(key)) {
                parsedAnchor = Anchor.fromConfig(value, parsedAnchor);
            } else if ("display_mode".equals(key)) {
                parsedDisplayMode = DisplayMode.fromConfig(value, parsedDisplayMode);
            } else if ("offset_x".equals(key)) {
                parsedOffsetX = parseInteger(value, parsedOffsetX);
            } else if ("offset_y".equals(key)) {
                parsedOffsetY = parseInteger(value, parsedOffsetY);
            }
        }

        anchor = parsedAnchor;
        displayMode = parsedDisplayMode;
        offsetX = parsedOffsetX;
        offsetY = parsedOffsetY;
    }

    private int parseInteger(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    public enum DisplayMode {
        DAYS,
        DAYS_HOUR,
        DAYS_HOUR_MINUTE;

        public static DisplayMode fromConfig(String rawValue, DisplayMode fallback) {
            if (rawValue == null) {
                return fallback;
            }

            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("days".equals(normalized)) {
                return DAYS;
            }
            if ("days_hour".equals(normalized)) {
                return DAYS_HOUR;
            }
            if ("days_hour_minute".equals(normalized)) {
                return DAYS_HOUR_MINUTE;
            }
            return fallback;
        }
    }

    public enum Anchor {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT,
        CENTER_TOP,
        CENTER_BOTTOM;

        public static Anchor fromConfig(String rawValue, Anchor fallback) {
            if (rawValue == null) {
                return fallback;
            }

            String normalized = rawValue.trim().toLowerCase(Locale.ROOT);
            if ("top_left".equals(normalized)) {
                return TOP_LEFT;
            }
            if ("top_right".equals(normalized)) {
                return TOP_RIGHT;
            }
            if ("bottom_left".equals(normalized)) {
                return BOTTOM_LEFT;
            }
            if ("bottom_right".equals(normalized)) {
                return BOTTOM_RIGHT;
            }
            if ("center_top".equals(normalized)) {
                return CENTER_TOP;
            }
            if ("center_bottom".equals(normalized)) {
                return CENTER_BOTTOM;
            }
            return fallback;
        }

        public int resolveX(int screenWidth, int textWidth, int offsetX) {
            switch (this) {
                case TOP_RIGHT:
                case BOTTOM_RIGHT:
                    return screenWidth - textWidth - offsetX;
                case CENTER_TOP:
                case CENTER_BOTTOM:
                    return (screenWidth - textWidth) / 2 + offsetX;
                case TOP_LEFT:
                case BOTTOM_LEFT:
                default:
                    return offsetX;
            }
        }

        public int resolveY(int screenHeight, int textHeight, int offsetY) {
            switch (this) {
                case BOTTOM_LEFT:
                case BOTTOM_RIGHT:
                case CENTER_BOTTOM:
                    return screenHeight - textHeight - offsetY;
                case TOP_LEFT:
                case TOP_RIGHT:
                case CENTER_TOP:
                default:
                    return offsetY;
            }
        }
    }
}
