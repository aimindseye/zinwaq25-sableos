// Host test for the pure classes added to LineageOS Settings by
// patches/framework/packages/apps/Settings/0101, 0102 and 0103. tests/run.sh
// extracts SableAppearancePolicy.java and SableBuildInfo.java from the
// patches, compiles them with this file and runs main().

import com.android.settings.sable.SableAppearancePolicy;
import com.android.settings.sable.SableBuildInfo;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Predicate;

public final class SableSettingsPolicyTest {
    private static int failures = 0;
    private static int checks = 0;

    private static void check(String name, boolean ok) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FAIL " + name);
        }
    }

    private static void eq(String name, Object expected, Object actual) {
        check(name + " expected <" + expected + "> got <" + actual + ">",
                expected == null ? actual == null : expected.equals(actual));
    }

    public static void main(String[] args) {
        // Accent table matches SableAccentTokens order and seeds.
        eq("accents", Arrays.asList("blue", "green", "purple", "orange", "slate"),
                Arrays.asList(SableAppearancePolicy.ACCENTS));
        eq("seeds", Arrays.asList("4D9CFF", "35C66B", "7A42E8", "F28C45", "64748B"),
                Arrays.asList(SableAppearancePolicy.ACCENT_SEEDS));
        eq("default accent", "blue", SableAppearancePolicy.DEFAULT_ACCENT);

        check("valid accent", SableAppearancePolicy.isValidAccent("slate"));
        check("invalid accent", !SableAppearancePolicy.isValidAccent("magenta"));
        check("null accent", !SableAppearancePolicy.isValidAccent(null));
        eq("normalize unknown", "blue", SableAppearancePolicy.normalizeAccent("magenta"));
        eq("normalize null", "blue", SableAppearancePolicy.normalizeAccent(null));
        eq("normalize known", "orange", SableAppearancePolicy.normalizeAccent("orange"));
        eq("seed purple", "7A42E8", SableAppearancePolicy.seedHexForAccent("purple"));
        eq("seed fallback", "4D9CFF", SableAppearancePolicy.seedHexForAccent("bogus"));

        // One system theme source: the mode column always says follow-system.
        eq("reported mode", "follow-system", SableAppearancePolicy.reportedMode());
        eq("light -> MODE_NIGHT_NO", 1, SableAppearancePolicy.nightModeFor("light"));
        eq("dark -> MODE_NIGHT_YES", 2, SableAppearancePolicy.nightModeFor("dark"));
        eq("follow-system unchanged", -1, SableAppearancePolicy.nightModeFor("follow-system"));
        eq("unknown unchanged", -1, SableAppearancePolicy.nightModeFor("sepia"));
        check("valid modes", SableAppearancePolicy.isValidMode("light")
                && SableAppearancePolicy.isValidMode("dark")
                && SableAppearancePolicy.isValidMode("follow-system"));
        check("invalid mode", !SableAppearancePolicy.isValidMode("auto"));

        // Writers: Settings itself or Sable Start only.
        check("self may write", SableAppearancePolicy.isAllowedWriter(true, null));
        check("launcher may write", SableAppearancePolicy.isAllowedWriter(false,
                new String[] {"org.sableos.launcher"}));
        check("shared uid with launcher may write", SableAppearancePolicy.isAllowedWriter(false,
                new String[] {"com.example.other", "org.sableos.launcher"}));
        check("other app may not write", !SableAppearancePolicy.isAllowedWriter(false,
                new String[] {"org.sableos.hub"}));
        check("unknown uid may not write", !SableAppearancePolicy.isAllowedWriter(false, null));

        // Corner style (0103): compact default, rounded opt-in, values equal SableCornerStyle.
        eq("corner styles", Arrays.asList("compact", "rounded"),
                Arrays.asList(SableAppearancePolicy.CORNER_STYLES));
        eq("default corner style", "compact", SableAppearancePolicy.DEFAULT_CORNER_STYLE);
        eq("corner column", "corner_style", SableAppearancePolicy.COLUMN_CORNER_STYLE);
        check("valid corner", SableAppearancePolicy.isValidCornerStyle("rounded"));
        check("invalid corner", !SableAppearancePolicy.isValidCornerStyle("pill"));
        check("null corner", !SableAppearancePolicy.isValidCornerStyle(null));
        check("case-sensitive corner", !SableAppearancePolicy.isValidCornerStyle("Rounded"));
        eq("normalize corner unknown", "compact", SableAppearancePolicy.normalizeCornerStyle("pill"));
        eq("normalize corner null", "compact", SableAppearancePolicy.normalizeCornerStyle(null));
        eq("normalize corner known", "rounded", SableAppearancePolicy.normalizeCornerStyle("rounded"));

        // Corner style writers: Settings, or DisplayCompat as shipped on the system image.
        String dc = "org.sableos.titan2.displaycompat";
        eq("displaycompat package", dc, SableAppearancePolicy.DISPLAY_COMPAT_PACKAGE);
        Predicate<String> factory = pkg -> true;
        Predicate<String> updatedOrUser = pkg -> false;
        check("self may write corner",
                SableAppearancePolicy.isAllowedCornerStyleWriter(true, null, null));
        check("factory displaycompat may write corner",
                SableAppearancePolicy.isAllowedCornerStyleWriter(false, new String[] {dc}, factory));
        check("updated or sideloaded displaycompat may not write corner",
                !SableAppearancePolicy.isAllowedCornerStyleWriter(false, new String[] {dc},
                        updatedOrUser));
        check("launcher may not write corner",
                !SableAppearancePolicy.isAllowedCornerStyleWriter(false,
                        new String[] {"org.sableos.launcher"}, factory));
        check("other system app may not write corner",
                !SableAppearancePolicy.isAllowedCornerStyleWriter(false,
                        new String[] {"org.sableos.hub"}, factory));
        check("unknown uid may not write corner",
                !SableAppearancePolicy.isAllowedCornerStyleWriter(false, null, factory));
        check("predicate is asked about displaycompat only",
                !SableAppearancePolicy.isAllowedCornerStyleWriter(false,
                        new String[] {"org.sableos.hub", dc}, pkg -> !pkg.equals(dc)));
        check("displaycompat is not an accent writer",
                !SableAppearancePolicy.isAllowedWriter(false, new String[] {dc}));

        // Per-column update rule: (writesModeOrAccent, writesCorner, appearanceW, cornerW).
        check("launcher accent", SableAppearancePolicy.isAllowedUpdate(true, false, true, false));
        check("launcher corner refused",
                !SableAppearancePolicy.isAllowedUpdate(false, true, true, false));
        check("launcher accent+corner refused",
                !SableAppearancePolicy.isAllowedUpdate(true, true, true, false));
        check("displaycompat corner", SableAppearancePolicy.isAllowedUpdate(false, true, false, true));
        check("displaycompat accent refused",
                !SableAppearancePolicy.isAllowedUpdate(true, false, false, true));
        check("settings both", SableAppearancePolicy.isAllowedUpdate(true, true, true, true));
        check("bare refresh by a writer", SableAppearancePolicy.isAllowedUpdate(false, false, false, true));
        check("bare refresh by a stranger refused",
                !SableAppearancePolicy.isAllowedUpdate(false, false, false, false));

        // System palette fields in the form ThemeOverlayController reads.
        Map<String, String> fields = SableAppearancePolicy.themeCustomizationFor("green");
        eq("color source", "preset", fields.get("android.theme.customization.color_source"));
        eq("system palette", "35C66B", fields.get("android.theme.customization.system_palette"));
        eq("accent color", "35C66B", fields.get("android.theme.customization.accent_color"));
        eq("theme style", "TONAL_SPOT", fields.get("android.theme.customization.theme_style"));
        eq("field count", 4, fields.size());

        // About phone summary.
        eq("full summary", "Q4 · lineage-23.2 · 0afdeef1c2d3",
                SableBuildInfo.summary("Q4", "lineage-23.2",
                        "0afdeef1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7", "Unknown"));
        eq("uncommitted source dropped", "Q4 · lineage-23.2",
                SableBuildInfo.summary("Q4", "lineage-23.2", "uncommitted", "Unknown"));
        eq("blank parts dropped", "Q5",
                SableBuildInfo.summary(" Q5 ", "", null, "Unknown"));
        eq("nothing set", "Unknown", SableBuildInfo.summary("", null, " ", "Unknown"));

        System.out.println("SETTINGS_POLICY_CHECKS=" + checks + " FAILED=" + failures);
        if (failures > 0) {
            System.exit(1);
        }
    }
}
