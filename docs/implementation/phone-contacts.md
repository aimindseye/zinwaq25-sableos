# Phone and Contacts presentation

Port of the approved SableOS R9 Phone + Contacts presentation
(`aimindseye/sableos` `patches/android-17-grapheneos-2026081300/apply_r9_phone_contacts_presentation.py`,
written for GrapheneOS Android 17) to LineageOS 23.2, checked against
`platform_sable/docs/design/SABLE_PHONE_DIALER_UX.md`, `CONTACTS_PEOPLE_UX.md`,
their `*_VISUAL_CONFIRMATION.md` checklists, `HARDWARE_DIAGNOSTICS_AND_DIALER_CODES.md`
and the Titan 2 screen references (`titan2-temp` 7570470,
`apps/titan2/screens/docs/SCREEN_TRACEABILITY.md`, rows 5 and 7).

```text
PACKAGE=phone-contacts
SURFACES=Phone (packages/apps/Dialer) contacts tab, Contacts (packages/apps/Contacts) list
PATCHES=Dialer 0701-0702, Contacts 0751-0752 (range 0700-0799)
CONTACT_DATA_OWNER=ContactsProvider (unchanged)
SECOND_CONTACT_INDEX_OR_DATABASE=NO
SUBTITLE_NUMBER_CACHE=in-memory, read-only, bounded (5000 contacts / 20000 rows), rebuilt per load
ALPHABET_RAILS_PER_LIST=1 (reads the upstream section index)
ACCOUNTS_SYNC_PERMISSIONS_CONTACT_SEMANTICS=UNCHANGED
TELEPHONY_DIALPAD_INCALL_CALL_END_KEYS=UNTOUCHED
PURE_HOST_TESTS=PASS (20 per app copy)
TYPE_CHECK=javac against android-all API 36 + project sources (see below)
BUILD_TESTED=NO   DEVICE_TESTED=NO
```

Nothing here has been built with the LineageOS build system or run on a phone.
The pure classes compile with `javac` and their host tests pass. Every Java file
the patches add or change was type-checked with `javac` against the Android 16
framework jar together with the real Dialer and Contacts sources; only
libraries that are not reachable here (AndroidX, Material, vcard, PhoneCommon,
generated `R`) were stubbed (see "Type check").

## Which apps the Q25 ships

* `LineageOS/android` `default.xml` (lineage-23.2): `packages/apps/Dialer` =
  `LineageOS/android_packages_apps_Dialer`, `packages/apps/Contacts` =
  `LineageOS/android_packages_apps_Contacts`, `packages/providers/ContactsProvider`
  = `LineageOS/android_packages_providers_ContactsProvider` (not patched).
* `device/xelex/Q25` `lineage_Q25.mk` inherits `full_base_telephony.mk`
  (`full_base.mk` → `generic_no_telephony.mk` → `handheld_product.mk`, which
  lists `Contacts`; `telephony.mk` → `telephony_product.mk`, which lists
  `Dialer`) and `vendor/lineage/config/common_full_phone.mk`. Nothing in the
  Sable product layer overrides either app.

| Project | Base commit (lineage-23.2) | Patches |
|---|---|---|
| `packages/apps/Dialer` | `6da8042323a97d5b3cba1fd975709cc42f29916f` (2026-09-18) | `0701` pure model, `0702` contacts tab |
| `packages/apps/Contacts` | `02bbe49b4ed8e8094d9573ff52747ff8050d9813` (2026-06-01) | `0751` pure model, `0752` list |

Each project's patches apply in order to a clean checkout of its base with
`git apply` and reproduce the committed tree exactly.

## What a user gets

**Phone > Contacts tab and Contacts list**

* Each contact row shows the name and, below it, the contact's default number
  with its type: `Mobile · +1 555 0101`. The number is the one marked default
  (`IS_SUPER_PRIMARY`), else the account's primary, else the first stored
  number; it is shown as stored, never reformatted. Contacts without a number
  show the name only. In Contacts only local-directory rows get it (remote
  directory search rows have no local contact id).
* One A-Z rail on the right edge of the list. Letters with no contacts are
  dimmed and skipped by focus; tapping or clicking a letter scrolls to its
  first contact. While the rail is shown the upstream scroll thumb (Dialer's
  `FastScroller`, Contacts' ListView fast scroll) is hidden, so there is one
  rail, not two. The rail hides when the list is empty and in Contacts search.
* Physical keyboard, list focused and search closed:
  * `A`-`Z` (and letters of other scripts) move focus to the first contact
    filed under that letter; pressing the same letter again moves to the next
    one and wraps. Accented section titles file under their base letter.
  * A letter with no contacts shows `No contacts under X`; focus stays.
  * Letters never call, message, open, edit or delete anyone. Enter still opens
    the focused row as upstream.
  * A held letter does nothing (no racing through the list, no accidental
    search). Ctrl/Alt/Meta combinations keep their old meaning.
  * Contacts: `/` opens an empty search. Digits and other characters keep the
    upstream type-to-search (so typing a number still searches numbers).
  * Once a search field has focus, every letter is query text (the field
    consumes keys before the list sees them). Selection mode is unchanged.
* Presentation: Contacts section letters are 22sp, light, not all caps; the
  Contacts toolbar and the Phone bottom bar are flat (no elevation).

## Source port decisions

The R9 patcher's transforms, and what happened to each on LineageOS 23.2:

| R9 transform (GrapheneOS) | LineageOS 23.2 port |
|---|---|
| Dialer/Contacts hard-coded colours (`#4D9CFF`, `#45515F`, neutral backgrounds) | Not ported. LineageOS already maps these colours to `system_accent1_*` / `system_neutral1_*`, and KF-B (`Settings/0101`) applies the Sable accent to the system palette, so both apps follow the Sable accent without a hard-coded value (which would ignore the user's accent choice). |
| Dialer toolbar background transparent, elevation 0 | Elevation is already 0 upstream. The rounded search-field background is kept: it is the explicit search field (`SEARCH_FIELD_EXPLICIT`). |
| Dialer bottom nav elevation 8dp → 0 | Ported. |
| Dialpad key background → transparent | Not ported: it removes the pressed/focused feedback, and the design requires visible focus and touch targets on the onscreen dialpad. |
| Contacts toolbar elevation → 0, `SectionHeaderStyle` 22sp light | Ported. |
| Dialer `ContactsAdapter.getPositionForHeader`, Contacts `IndexerListAdapter.getListPositionForSection` | Replaced by the pure `SableAlphabetIndex` built from the same section index. The R9 Contacts position ignored the ListView header view (search header) and was off by one; the port adds `getHeaderViewsCount()`. No change to `IndexerListAdapter`. |
| A-Z `LinearLayout` rail built inline in each fragment | One `SableAlphabetRail` view class per app; letters without contacts dimmed/unfocusable; upstream thumb hidden while shown (R9 left both visible). |
| Phone number subtitle: unbounded `LongSparseArray` filled on the main thread from a `CursorLoader` | `SablePreferredNumbers`: bounded map (5000 contacts, query `limit` 20000 rows, numbers over 64 chars skipped), ranked default > primary > first row, built in an `AsyncTaskLoader` off the main thread, reloaded by a content observer, plus the type label. |
| (not in R9) keyboard A-Z jump, repeat-to-cycle, `No contacts under X`, `/` search | Added from `CONTACTS_PEOPLE_UX` "Physical keyboard alphabet shortcuts" and the Titan 2 `ContactsPeopleScreen` reference, policy in pure `SableListKeys`. |
| Blocked-number substrate verification (BlockedNumberContract, settings activity) | Nothing to change: LineageOS Dialer uses the platform blocked-number provider and `BlockedNumbersSettingsActivity`; untouched. |

Dialer codes (`HARDWARE_DIAGNOSTICS_AND_DIALER_CODES.md`): the policy is
`RAW_DIALER_CODE_COMPATIBILITY=PRESERVE_WHERE_AVAILABLE`. LineageOS Dialer's
secret-code handling (`*#*#...#*#*` broadcast) is unchanged, so the Sable Tools
code keeps working as KF-C describes; no Dialer change is needed for it.

## File map

Dialer (`packages/apps/Dialer`):

| File | Patch | Kind |
|---|---|---|
| `java/com/android/dialer/sable/SableAlphabetIndex.java` | 0701 | new, pure |
| `java/com/android/dialer/sable/SablePreferredNumbers.java` | 0701 | new, pure |
| `java/com/android/dialer/sable/SableSubtitle.java` | 0701 | new, pure |
| `java/com/android/dialer/sable/SableListKeys.java` | 0701 | new, pure |
| `java/com/android/dialer/sable/SableAlphabetRail.java` | 0702 | new view (no `R`) |
| `java/com/android/dialer/contactsfragment/SableNumbersLoader.java` | 0702 | new loader |
| `java/com/android/dialer/contactsfragment/ContactsFragment.java` | 0702 | rail, key listener, second loader |
| `java/com/android/dialer/contactsfragment/ContactsAdapter.java` | 0702 | numbers, `getSableIndex()` |
| `java/com/android/dialer/contactsfragment/ContactViewHolder.java` | 0702 | subtitle line |
| `.../contactsfragment/res/layout/contact_row.xml`, `res/values/dimens.xml`, `res/values/sable_strings.xml` | 0702 | row layout, 13sp subtitle, 2 strings |
| `.../main/impl/bottomnav/res/layout/bottom_nav_bar_layout.xml` | 0702 | elevation 0 |

Contacts (`packages/apps/Contacts`):

| File | Patch | Kind |
|---|---|---|
| `src/com/android/contacts/sable/SableAlphabetIndex.java`, `SablePreferredNumbers.java`, `SableSubtitle.java`, `SableListKeys.java` | 0751 | new, pure (same code as Dialer's, other package, 4-space indent) |
| `src/com/android/contacts/sable/SableAlphabetRail.java` | 0752 | new view |
| `src/com/android/contacts/list/SableNumbersLoader.java` | 0752 | new loader (framework `AsyncTaskLoader`, as Contacts uses the framework `LoaderManager`) |
| `src/com/android/contacts/list/DefaultContactBrowseListFragment.java` | 0752 | rail, `onSableKey`, second loader |
| `src/com/android/contacts/list/DefaultContactListAdapter.java` | 0752 | label + number on local rows |
| `src/com/android/contacts/activities/PeopleActivity.java` | 0752 | 4 lines: offer the key to `onSableKey` before type-to-search |
| `res/values/styles.xml`, `res/layout/people_activity_toolbar.xml`, `res/values/sable_strings.xml` | 0752 | section style, flat toolbar, 2 strings |

This repository: `patches/framework/packages/apps/{Dialer,Contacts}/*.patch`,
`tests/framework/packages/apps/{Dialer,Contacts}/*Test.kt` (picked up by
`tests/framework/run-pure-tests.sh`, which `tests/run.sh` runs), rows in
`patches/README.md`, gates in `docs/QUALIFICATION.md`.

## Boundaries kept

* **ContactsProvider owns the data.** The only new provider access is one
  read-only query per list surface of `Phone.CONTENT_URI` (contact id, number,
  type, label, primary flags; `limit` 20000), with the app's existing
  READ_CONTACTS permission. Nothing is inserted, updated, deleted, linked or
  persisted; the map lives only in the fragment while it is shown. Section
  headers, the rail, search and sort order keep using the provider's own
  results; there is no second index or database.
* **Accounts, sync, permissions, contact semantics:** untouched. Filters,
  account headers, favorites, linking and editing paths are not changed. A
  failed query (for example permission revoked mid-load) shows names only.
* **Telephony:** the dialpad, call log, in-call UI, emergency path, SIM choice
  and Call/End keys are untouched. Call/End behaviour belongs to the device key
  map (`device-profile/KEYMAP.md`), not the apps.
* **Device neutrality:** no model checks. The rail width is 28dp and the row
  text uses existing dimensions, so it follows the density chosen for the
  device (`SABLE_LCD_DENSITY`).

## Design acceptance keys

| Key | Status |
|---|---|
| `CONTACT_PROVIDER_OWNERSHIP_RETAINED`, `PROVIDER_OWNERSHIP_RETAINED`, `CUSTOM_CONTACTS_PROVIDER=NO`, `UNIFIED_PRIVATE_CONTACT_DB=NO`, `SILENT_CONTACT_MERGE=NO` | Code: read-only query, no writes, no storage. Device: Q4-PHONE-CONTACTS-OWNER |
| `ALPHABET_SHORTCUTS`, `PHYSICAL_KEYBOARD_A_TO_Z_JUMP`, `REPEATED_LETTER_CYCLES_MATCHES` | Code + host tests (`SableAlphabetIndexTest`, `SableListKeysTest`). Device: Q4-PHONE-CONTACTS |
| `LETTER_KEYS_DO_NOT_OPEN_RANDOM_CONTACTS` | Code: jump only moves selection/focus; tests. Device: Q4-PHONE-CONTACTS |
| `ALPHABET_INDEX_VISIBLE`, one rail | Code (rail replaces the thumb). Device screenshot: Q4-PHONE-CONTACTS |
| `SEARCH_FIELD_EXPLICIT` (`/` opens search; letters are text once search is focused) | Contacts: code + tests. Phone: letters in the search field stay text; `/` from the Phone contacts tab is not wired (the search bar belongs to the activity), see "Left" |
| "No contacts under X" status | Code (toast). Device |
| `ROW_PRIMARY=DISPLAY_NAME`, `ROW_SECONDARY` context | Code: name first, `Type · number` second |
| `RAW_PROVIDER_IDS_DEFAULT_VISIBLE=NO`, `PACKAGE_NAMES_DEFAULT_VISIBLE=NO` | Code: only name, type label, number shown |
| `SCREEN_READER_LABELS`, `NO_FOCUS_TRAPS` (rail) | Code: each rail letter has "Jump to X"; unavailable letters unfocusable; the rail is a sibling of the list so Left/Right leave it. Device: TalkBack pass |
| `TOUCH_TARGETS`, `POINTER_INTERACTION` | Rail letters are clickable with ripple; row touch unchanged. Device |
| `PHYSICAL_KEYBOARD_DIALING`, `ONSCREEN_DIALPAD`, `STAR_POUND_PLUS_ENTRY`, `DTMF_IN_CALL_KEYPAD`, `EMERGENCY_CALL_PATH_PRESERVED` | Upstream LineageOS Dialer behaviour, untouched. Device: Q4-PHONE-CORE |
| `LOCKED_MODE_REDACTION`, `PRIVATE_MODE_REDACTION` | No new surface outside the unlocked apps; lockscreen and notifications untouched |
| `HUB_CALL_HANDOFF`, `PERSON_DETAIL`, actions sheet, source/provider view | Out of this package (Hub and upstream QuickContact) |

## Type check

The owner asked for code that compiles with only device testing left. No
LineageOS build runs here (Google Maven and AOSP are blocked), so:

* **Framework:** `android-all-instrumented` API 36 (Robolectric's Android 16
  jar).
* **Project sources:** `javac -sourcepath` on the real Dialer `java/` tree and
  the real Contacts `src/` + `src-bind/` trees, so every Dialer/Contacts class
  the changed files reference is the real one. Dialer was compiled in full
  closure; Contacts with `-implicit:none` (referenced classes resolved from
  source, not code-generated).
* **Libraries from Maven Central (real jars):** Guava, Dagger, javax.inject,
  JSR-305, Error Prone annotations, libphonenumber (+ geocoder, prefixmapper).
* **Stubbed (not reachable: Google Maven / AOSP blocked):** AndroidX
  (annotation, recyclerview, loader, fragment, activity-result, collection,
  core, appcompat, drawerlayout, swiperefreshlayout, coordinatorlayout,
  cardview, palette, localbroadcastmanager), Material (Snackbar,
  TextInputLayout), `com.android.vcard`, PhoneCommon (`AnimUtils`,
  `PhoneConstants`), `android.support.v4.os.ResultReceiver`,
  `android.net.sip.SipManager`, Robolectric marker interfaces. Each stub
  carries only members the code uses, with the library's real signatures.
  `com.android.common.widget.CompositeCursorAdapter` (frameworks/ex) is the
  real source from LineageOS's `android_frameworks_ex` (`staging/cm-14.0`;
  the file is unchanged in AOSP since). `R` classes were generated from the
  projects' real resource directories (names only), so a misspelt resource
  name in the patches fails the check.
* **Files checked:** Dialer `ContactsFragment`, `ContactsAdapter`,
  `ContactViewHolder`, `SableNumbersLoader`, `sable/*` (5); Contacts
  `DefaultContactBrowseListFragment`, `DefaultContactListAdapter`,
  `PeopleActivity`, `SableNumbersLoader`, `sable/*` (5).
* **Result:** no errors in any added or changed file. The only errors left are
  in unchanged upstream files (`FastScroller`, `TelecomUtil`,
  `ContactEntryListFragment`, `SimCard`, `DynamicShortcuts`,
  `ContactsNotificationChannelsUtil`) and come from the Robolectric jar, whose
  constants such as `MotionEvent.ACTION_DOWN` and `Build.VERSION_CODES.O` are
  not compile-time constants (`javap -constants` shows them non-final), so
  `case`/annotation uses fail there and nowhere else.

Not covered by this check: resource linking (aapt2), layout attribute
validity, AndroidX behaviour, R8. The layouts were checked as well-formed XML.

## Tests

```sh
K=<scratchpad with kotlinc and the JUnit shim>
SABLE_KOTLINC=$K/kotlinc/bin/kotlinc SABLE_JUNIT=$K/shim/Shim.kt \
  bash tests/framework/run-pure-tests.sh
# PASS  framework pure tests packages/apps/Dialer: RAN=20 FAILED=0
# PASS  framework pure tests packages/apps/Contacts: RAN=20 FAILED=0
bash tests/run.sh
```

The tests cover bucket keys (accents, non-Latin letters, star/`#`/`…`
sections), positions with header offsets, repeat-to-cycle and wrap, a letter
split over two sections, the Contacts favorites section, malformed indexes,
number ranking, the contact and row bounds, blank and over-long numbers,
snapshot immutability, subtitle text with bidi isolates and label truncation,
and the key policy (search/selection/modifier/held/`/`/digits).

## Left (needs the phone or a later decision)

* All of Q4-PHONE-CONTACTS, Q4-PHONE-CONTACTS-OWNER and Q4-PHONE-CORE: runtime
  look at 720x720, rail letter height on the chosen density, TalkBack, key
  routing through the Q25 key map.
* Phone: `/` from the contacts tab to the toolbar search (needs a hook into
  `MainSearchController` in the activity); not done to keep the patch inside
  the contacts fragment.
* Person detail, action sheet, source/provider view and Hub handoff
  (`CONTACTS_PEOPLE_UX`) stay upstream QuickContact/Hub; not part of R9.
* The "No contacts under X" status uses a Toast; a list-level status line
  would need a layout change in both apps.
* A real AOSP build (aapt2 + javac with the real AndroidX) to close the stub
  gap above.

## Integration

1. Merge branch `q25/phone-contacts`. It adds the four patches, the two test
   directories, this document, rows in `patches/README.md` (range 0700-0799)
   and three Q4 gates in `docs/QUALIFICATION.md`.
2. Nothing to stage: `scripts/apply-framework-patches.sh` picks up any
   `patches/framework/<project>/*.patch`, and both apps are already in the
   image. No change to `product/q25/apps.tsv` or `scripts/stage-product.sh`.
3. After `repo sync`, `scripts/apply-framework-patches.sh check` must report
   `APPLIES` for `packages/apps/Dialer` and `packages/apps/Contacts`; if the
   bases moved, re-apply in a synced tree and regenerate with
   `git format-patch -2 --start-number 701` (Dialer) / `751` (Contacts).
