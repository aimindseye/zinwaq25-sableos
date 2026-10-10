// Host test for SableKeyboardBacklight, added to LineageOS Settings by
// patches/framework/packages/apps/Settings/0901. tests/run.sh extracts the
// class from the patch, compiles it with this file and runs main().

import com.android.settings.sable.SableKeyboardBacklight;

public final class SableKeyboardBacklightTest {
    private static int failures = 0;
    private static int checks = 0;

    private static void eq(String name, Object expected, Object actual) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual)) {
            failures++;
            System.out.println("FAIL " + name + " expected <" + expected + "> got <" + actual + ">");
        }
    }

    public static void main(String[] args) {
        // Missing or odd values mean on, the driver's default.
        eq("enabled missing", true, SableKeyboardBacklight.isEnabled(null));
        eq("enabled empty", true, SableKeyboardBacklight.isEnabled(""));
        eq("enabled 1", true, SableKeyboardBacklight.isEnabled("1"));
        eq("enabled 0", false, SableKeyboardBacklight.isEnabled("0"));
        eq("enabled 0 padded", false, SableKeyboardBacklight.isEnabled(" 0\n"));
        eq("enabled value on", "1", SableKeyboardBacklight.enabledValue(true));
        eq("enabled value off", "0", SableKeyboardBacklight.enabledValue(false));

        // Every slider step gives a level the driver accepts (1-255), top step is full.
        int previous = 0;
        for (int step = 1; step <= SableKeyboardBacklight.STEPS; step++) {
            int level = SableKeyboardBacklight.levelForStep(step);
            eq("level in range " + step, true, level >= 1 && level <= 255);
            eq("level rises " + step, true, level > previous);
            eq("round trip " + step, step, SableKeyboardBacklight.stepForLevel(Integer.toString(level)));
            previous = level;
        }
        eq("top step full", 255, SableKeyboardBacklight.levelForStep(SableKeyboardBacklight.STEPS));
        eq("step clamp low", SableKeyboardBacklight.levelForStep(1), SableKeyboardBacklight.levelForStep(0));
        eq("step clamp high", 255, SableKeyboardBacklight.levelForStep(99));

        // Stored levels outside the driver range, or not numbers, read as full.
        eq("level missing", SableKeyboardBacklight.STEPS, SableKeyboardBacklight.stepForLevel(null));
        eq("level junk", SableKeyboardBacklight.STEPS, SableKeyboardBacklight.stepForLevel("bright"));
        eq("level 0", SableKeyboardBacklight.STEPS, SableKeyboardBacklight.stepForLevel("0"));
        eq("level 256", SableKeyboardBacklight.STEPS, SableKeyboardBacklight.stepForLevel("256"));
        eq("level 1", 1, SableKeyboardBacklight.stepForLevel("1"));
        eq("level 128", 5, SableKeyboardBacklight.stepForLevel("128"));

        System.out.println("CHECKS=" + checks + " FAILED=" + failures);
        if (failures != 0) {
            System.exit(1);
        }
    }
}
