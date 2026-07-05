package asd.itamio.seedprotect;

/**
 * Mod Info Card Printer
 * Prints a nicely formatted info card box on mod launch.
 * Shared across all loader variants of Itamio mods.
 */
public class ModInfoPrinter {

    private static final String AUTHOR = "Itamio";
    private static final String CONTRIBUTOR = "Asd1281yss";
    private static final String STATEMENT = "I just want to make minecraft more playable,";
    private static final String STATEMENT2 = "and if my mod fails to do so, don't use it.";
    private static final String STATEMENT3 = "Hope you enjoy :)";

    private static final int WIDTH = 52;
    private static final String HORIZ = "═";
    private static final String VERT = "║";
    private static final String TL = "╔";
    private static final String TR = "╗";
    private static final String BL = "╚";
    private static final String BR = "╝";

    public static void print(LogLine logLine, String modName, String version) {
        String[] lines = {
            "",
            TL + repeat(HORIZ, WIDTH - 2) + TR,
            VERT + padCenter("MOD INFO CARD", WIDTH - 2) + VERT,
            VERT + repeat("─", WIDTH - 2) + VERT,
            VERT + padLeft("  Mod: " + modName, WIDTH - 2) + VERT,
            VERT + padLeft("  Version: " + version, WIDTH - 2) + VERT,
            VERT + repeat("─", WIDTH - 2) + VERT,
            VERT + padLeft("  Author: " + AUTHOR, WIDTH - 2) + VERT,
            VERT + padLeft("  Contributor: " + CONTRIBUTOR, WIDTH - 2) + VERT,
            VERT + repeat("─", WIDTH - 2) + VERT,
            VERT + padCenter(STATEMENT, WIDTH - 2) + VERT,
            VERT + padCenter(STATEMENT2, WIDTH - 2) + VERT,
            VERT + padCenter(STATEMENT3, WIDTH - 2) + VERT,
            BL + repeat(HORIZ, WIDTH - 2) + BR,
            ""
        };

        for (String line : lines) {
            logLine.log(line);
        }
    }

    private static String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private static String padCenter(String text, int width) {
        int padding = width - text.length();
        int left = padding / 2;
        int right = padding - left;
        return repeat(" ", left) + text + repeat(" ", right);
    }

    private static String padLeft(String text, int width) {
        int padding = width - text.length();
        if (padding > 0) {
            return text + repeat(" ", padding);
        }
        return text;
    }

    @FunctionalInterface
    public interface LogLine {
        void log(String line);
    }
}
