package org.sableos.titan2.spaces.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line Sable Spaces shows, in English, by string resource name. res/values/strings.xml repeats
 * them (SpacesTextTest keeps the two equal) and res/values-<lang>/strings.xml translates them.
 */
object SpacesText {
    val EN: Map<String, String> =
        mapOf(
            "work_space" to "Work space",
            "work" to "Work",
            "intro" to
                "Keep work apps apart from yours: their own data and accounts, a Work " +
                "badge, and a Personal · Work switch on Start that can pause them.",
            "make" to "Make a Work space",
            "making" to "Android is making the Work space. This takes a minute.",
            "not_allowed" to
                "This phone cannot make a Work space now. A Work space or a phone manager may " +
                "already be in charge.",
            "cancelled" to "No Work space was made.",
            "ready" to
                "Your Work space is ready. Add the apps you use for work: each gets its own copy " +
                "with its own data.",
            "paused" to
                "Work apps are paused. Turn them on from Start (All apps, Work) to add apps.",
            "in_work" to "%1\$s · in Work",
            "no_apps" to "Every app you have is already in Work.",
            "could_not_open" to "The Work space did not answer. Try again in a moment.",
            "remove" to "Remove Work space",
            "add_confirm" to "Add %1\$s (%2\$s) to Work? Enter adds it, Back cancels.",
            "added" to "%1\$s is now in Work.",
            "add_failed" to "%1\$s could not be added to Work.",
            "remove_confirm" to
                "Remove the Work space with every work app and its data? Y removes it, N keeps it.",
            "yes" to "Yes",
            "options" to "Work space options",
            "options_intro" to "These apply inside the Work space only. Each starts as Allowed.",
            "option_copy" to "Copy from work apps into personal apps",
            "option_share" to "Share from personal apps into work apps",
            "option_screenshots" to "Screenshots of work apps",
            "allowed" to "Allowed",
            "blocked" to "Blocked",
            "option_line" to "%1\$s: %2\$s",
            "work_lock" to "Work lock",
            "work_lock_about" to
                "A separate PIN, pattern or password for work apps, asked when you open them after the phone was locked.",
            "options_hint" to
                "Up and Down choose · Enter changes it · L sets the Work lock · Back leaves",
            "options_failed" to "Android did not accept that change.",
            "no" to "No",
            "hint" to
                "Up and Down choose an app · Enter adds it · O options · R removes the Work space · Back leaves"
        )

    val t = SableTexts(EN)
}
