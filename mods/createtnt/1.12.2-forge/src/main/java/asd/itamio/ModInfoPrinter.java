package asd.itamio;

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

    /**
     * Prints the mod info card to the given logger at INFO level.
     * @param logLine A functional interface to log a line (e.g., LOGGER::info or System.out::println)
     * @param modName The display name of the mod
     * @param version The mod version string
     */
    public static void print(LogLine logLine, String modName, String version) {
        print(logLine, modName, version, null);
    }

    /**
     * Prints the mod info card with optional credits section.
     * @param credits Credits text (reference mods, authors, etc.), or null to omit.
     */
    public static void print(LogLine logLine, String modName, String version, String credits) {
        java.util.List<String> linesList = new java.util.ArrayList<>();
        linesList.add("");
        linesList.add(TL + repeat(HORIZ, WIDTH - 2) + TR);
        linesList.add(VERT + padCenter("MOD INFO CARD", WIDTH - 2) + VERT);
        linesList.add(VERT + repeat("─", WIDTH - 2) + VERT);
        linesList.add(VERT + padLeft("  Mod: " + modName, WIDTH - 2) + VERT);
        linesList.add(VERT + padLeft("  Version: " + version, WIDTH - 2) + VERT);
        linesList.add(VERT + repeat("─", WIDTH - 2) + VERT);
        linesList.add(VERT + padLeft("  Author: " + AUTHOR, WIDTH - 2) + VERT);
        linesList.add(VERT + padLeft("  Contributor: " + CONTRIBUTOR, WIDTH - 2) + VERT);

        if (credits != null && !credits.isEmpty()) {
            linesList.add(VERT + repeat("─", WIDTH - 2) + VERT);
            // Wrap credits to fit within the box width.
            String[] words = credits.split(" ");
            StringBuilder current = new StringBuilder("  ");
            for (String word : words) {
                if (current.length() + word.length() + 1 > WIDTH - 2) {
                    linesList.add(VERT + padLeft(current.toString(), WIDTH - 2) + VERT);
                    current = new StringBuilder("  " + word);
                } else {
                    if (current.length() > 2) current.append(" ");
                    current.append(word);
                }
            }
            if (current.length() > 2) {
                linesList.add(VERT + padLeft(current.toString(), WIDTH - 2) + VERT);
            }
        }

        linesList.add(VERT + repeat("─", WIDTH - 2) + VERT);
        linesList.add(VERT + padCenter(STATEMENT, WIDTH - 2) + VERT);
        linesList.add(VERT + padCenter(STATEMENT2, WIDTH - 2) + VERT);
        linesList.add(VERT + padCenter(STATEMENT3, WIDTH - 2) + VERT);
        linesList.add(BL + repeat(HORIZ, WIDTH - 2) + BR);
        linesList.add("");

        for (String line : linesList) {
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

    /**
     * Functional interface for logging a line.
     * Use LOGGER::info or System.out::println.
     */
    @FunctionalInterface
    public interface LogLine {
        void log(String line);
    }
}