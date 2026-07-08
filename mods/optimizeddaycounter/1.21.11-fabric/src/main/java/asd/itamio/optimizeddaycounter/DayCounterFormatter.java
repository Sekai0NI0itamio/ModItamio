package asd.itamio.optimizeddaycounter;

/**
 * Formats the world day and time display text for the HUD.
 */
public final class DayCounterFormatter {

    private static final long TICKS_PER_DAY = 24000L;
    private static final long TICKS_PER_HOUR = 1000L;

    private DayCounterFormatter() {
    }

    /**
     * Formats the display text based on total world time and current time.
     *
     * @param totalWorldTime The total world time (used for day counting)
     * @param worldTime      The current world time of day
     * @param displayMode    The display mode to use
     * @return The formatted display string, or empty string if invalid
     */
    public static String format(long totalWorldTime, long worldTime, DayCounterConfig.DisplayMode displayMode) {
        long dayNumber = Math.max(1L, (totalWorldTime / TICKS_PER_DAY) + 1L);
        String dayText = "Day " + dayNumber;

        if (displayMode == DayCounterConfig.DisplayMode.DAYS) {
            return dayText;
        }

        long ticksOfDay = normalizeTicks(worldTime % TICKS_PER_DAY);
        // Shift by 6 hours so that 6:00 AM (when villagers wake) is the start of the 12-hour cycle
        long shiftedTicks = normalizeTicks(ticksOfDay + (TICKS_PER_HOUR * 6L));
        long totalMinutes = (shiftedTicks * 1440L) / TICKS_PER_DAY;

        int hour24 = (int) (totalMinutes / 60L);
        int minute = (int) (totalMinutes % 60L);
        int hour12 = hour24 % 12;
        if (hour12 == 0) {
            hour12 = 12;
        }
        String meridiem = hour24 >= 12 ? "PM" : "AM";

        if (displayMode == DayCounterConfig.DisplayMode.DAYS_HOUR) {
            return dayText + " | " + hour12 + " " + meridiem;
        }

        return dayText + " | " + hour12 + ":" + twoDigits(minute) + " " + meridiem;
    }

    private static long normalizeTicks(long ticks) {
        long normalized = ticks % TICKS_PER_DAY;
        return normalized < 0L ? normalized + TICKS_PER_DAY : normalized;
    }

    private static String twoDigits(int value) {
        return value < 10 ? "0" + value : Integer.toString(value);
    }
}
