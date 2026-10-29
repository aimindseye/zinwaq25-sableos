package org.sableos.titan2.vault.core

import org.sableos.titan2.theme.SableTexts

/**
 * Every line Secure Vault shows, in English, by string resource name. res/values/strings.xml
 * repeats them (VaultLinesTest keeps the two equal) and res/values-<lang>/strings.xml translates
 * them.
 */
object VaultLines {
    val EN: Map<String, String> =
        mapOf(
            "read_error" to "read error",
            "no_access" to " (no access",
            "partial_copy_left" to "; partial copy left",
            "cancelled_nothing_changed" to "Cancelled. Nothing changed.",
            "deleted" to "Deleted %1\$s.",
            "could_not_delete" to "Could not delete %1\$s.",
            "deleted_of_original_s_any" to
                "Deleted %1\$s of %2\$s original(s). Any not deleted are still where they were.",
            "open_with" to "Open %1\$s with",
            "opened_read_only_this_file" to "Opened %1\$s (read-only, this file only).",
            "no_app_in_private_space" to "No app in Private Space can open %1\$s.",
            "the_system_file_picker_is" to "The system file picker is not available.",
            "exported_a_copy_of_it" to
                "Exported a copy of %1\$s. It is no longer protected by the vault.",
            "export_failed_nothing_was_written" to "Export failed. Nothing was written.",
            "could" to "Could",
            "cancelled" to "Cancelled",
            "no_app" to "No app",
            "not_available" to "not available",
            "yes" to "Yes",
            "no" to "No",
            "import_label" to "Import",
            "export" to "Export",
            "lock_now" to "Lock now",
            "photos" to "Photos",
            "documents" to "Documents",
            "recordings" to "Recordings",
            "file_or_p_d_r" to
                "↑↓ file · ←→ or P D R section · Enter open · I import · E export · X delete · L " +
                "lock now",
            "open" to "Open %1\$s?",
            "the_app_you_pick_inside" to
                "The app you pick (inside Private Space) can read this one file until it closes " +
                "it. Enter or Y opens · Back or N cancels.",
            "export_2" to "Export %1\$s?",
            "a_copy_is_written_where" to
                "A copy is written where you choose next and is no longer protected by the " +
                "vault. The vault copy stays. Enter or Y continues · Back or N cancels.",
            "delete_from_the_vault" to "Delete %1\$s from the vault?",
            "it_cannot_be_recovered_enter" to
                "It cannot be recovered. Enter or Y deletes · Back or N cancels.",
            "imported_file_s_as_copies" to
                "Imported %1\$s file(s) as copies. Delete the originals?",
            "the_originals_are_still_where" to
                "The originals are still where they were. Enter or Y deletes them · Back or N " +
                "keeps them.",
            "imported_file_s_as_copies_2" to "Imported %1\$s file(s) as copies.",
            "could_not_import" to " Could not import: ",
            "secure_vault" to "Secure Vault",
            "private_space_photos_documents_recordings" to
                "Private Space · Photos · Documents · Recordings · import/export explicit",
            "secure_vault_works_only_inside" to
                "Secure Vault works only inside Private Space. Unlock Private Space in Sable " +
                "Start (All apps) and open Secure Vault there. Nothing is stored outside it.",
            "nothing_here_yet_press_i" to "Nothing here yet. Press I to import a copy of a file.",
            "could_not_lock_private_space" to
                "Could not lock Private Space from here; lock it from Sable Start. The vault " +
                "closed."
        )

    val t = SableTexts(EN)
}
