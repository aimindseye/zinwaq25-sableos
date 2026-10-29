# Languages

What a user sees in another language, and how to add one.

## What already works

| Layer | Status |
|---|---|
| Android and LineageOS (Settings, Phone, Contacts, system dialogs) | Every Android language. Pick it in first-boot setup or Settings > System > Languages |
| Typing (Sable Keyboard) | 35 typing languages: Latin languages with accents (Español, Français, Deutsch, Português, Italiano, Polski, Türkçe, Svenska, Nederlands), Cyrillic, Greek, Arabic, Hebrew, Urdu, Hindi and nine other Indic scripts (InScript and an "ABC" phonetic layout), Korean, Japanese kana and Chinese pinyin. Add them in Sable Keyboard's settings; Ctrl+Space or the globe key switches. Non-Latin layouts are on-screen; the physical keys type Latin |
| Sable apps | Every Sable app keeps its English in `res/values/strings.xml` (sable-src 0085) and ships draft translations in 18 languages (Arabic, Chinese (Simplified), Dutch, French, German, Greek, Hebrew, Hindi, Italian, Japanese, Korean, Polish, Portuguese (Brazil), Russian, Spanish, Swedish, Turkish, Urdu) as `values-<lang>/strings.xml` (sable-src 0086, 0087, 0088). They are drafts checked against the English, not yet reviewed by a fluent speaker; a language without a file shows English |
| Upstream apps (Mail, Reader, Text reader, Terminal, Browser, Files, Photos) | Their upstream translations, unchanged |

## The files to translate

Each Sable app has one English file. Copy it to `values-<lang>/strings.xml` next to it (for
example `values-fr`, `values-de`, `values-zh-rCN`), translate the text between the tags, and keep:

- every `name="..."` as it is, and every key in the file (the app's test fails on a missing one);
- every placeholder (`%1$s`, `%2$s`, `%1$d`) and the `%%` that stands for a percent sign;
- key names and single key letters in hints ("C new", "Alt+Shift+R", "Space", "Esc") as the key
  printed on the Q25, translating only the word next to it;
- product names (Sable Start, Sable Hub, Private Space, Gboard, Zinwa Q25).

Lines marked `translatable="false"` are left out.

| App | English file |
|---|---|
| Sable Start | `sable-src/src/android/packages/apps/SableStart/res/values/strings.xml` |
| Sable Hub | `sable-src/apps/r8/android/hub/src/main/res/values/strings.xml` |
| Sable Messages | `sable-src/apps/r8/android/messages/src/main/res/values/strings.xml` |
| Sable Media | `sable-src/apps/r8/android/media/src/main/res/values/strings.xml` |
| Sable Notes | `sable-src/apps/r8/android/notes/src/main/res/values/strings.xml` |
| Sable Calendar | `sable-src/apps/r8/android/calendar/src/main/res/values/strings.xml` |
| Sable Weather | `sable-src/apps/r8/android/weather/src/main/res/values/strings.xml` |
| Sable Calculator | `sable-src/apps/r8/android/calculator/src/main/res/values/strings.xml` |
| Calculator's unit converter | `sable-src/apps/r8/android/convert/src/main/res/values/strings.xml` |
| Sable Games | `sable-src/apps/r8/android/games/src/main/res/values/strings.xml` |
| Lines shared by the Compose apps | `sable-src/src/android/shared/sabledesign/src/main/res/values/strings.xml` (Sable Start keeps a copy at the end of its own file) |
| Sable Keyboard | `sable-src/apps/titan2/platform/keyboard/src/main/res/values/strings.xml` |
| Sable Camera | `sable-src/apps/titan2/platform/camera/src/main/res/values/strings.xml` |
| Sable Tools | `sable-src/apps/titan2/platform/tools/src/main/res/values/strings.xml` |
| App display compatibility | `sable-src/apps/titan2/platform/displaycompat/src/main/res/values/strings.xml` |
| Secure Vault | `sable-src/apps/titan2/platform/vault/src/main/res/values/strings.xml` |
| Sound Profiles | `sable-src/apps/titan2/platform/profiles/src/main/res/values/strings.xml` |
| Sable Setup | `sable-src/apps/titan2/platform/setup/src/main/res/values/strings.xml` |

What stays English on purpose: the Sable Tools reports and Radio diagnostics (they are written for
support and compared across phones), log and exception messages, file names, search operators
(`from:`, `app:`) and the vendor factory-test item names. App display compatibility and Secure
Vault colour a status line as a warning when it contains words such as "could not" or "Not saved":
translate those keys with the same words the messages use.

## How a Sable app reads its text

1. Every user-visible line is in one table of resource name to English, in Android format syntax
   (`%1$s`, `%1$d`), named `<App>Text.kt` (Secure Vault's is `VaultLines.kt`). Code calls
   `t("key", args)`, a `SableTexts` (`sable-theme` for the platform apps, `sabledesign` for the
   Compose apps), so pure unit tests keep reading English.
2. `res/values/strings.xml` repeats the table. Each app's `<App>TextTest` fails when the two differ,
   and checks that every `values-<lang>/strings.xml` has all keys with the same placeholders.
   SonarCloud leaves the tables out of duplication (`sonar-project.properties`).
3. The app's `Application` (Sable Start: its activities) calls `<App>Text.t.bindTo(this)`. Lines
   then come from the string resources, so the phone's language picks the translation and English
   is the fallback. Enum labels keep the key and look the text up when shown, so a language change
   takes effect without a restart.
4. The Gradle apps keep every string through the resource shrinker (`res/raw/sable_strings_keep.xml`),
   because the lookup is by name. `tests/run.sh` gate 19 checks each app has its table, its
   `strings.xml`, the binding and the test.

## Order of work

1. Sable Setup (done: Spanish, French and German drafts).
   Sable Weather (done: ar, de, el, es, fr, hi, it, iw, ko, nl, pl, pt, ru, sv, tr, ur drafts, sable-src 0086;
   Japanese and Chinese left out, see below).
   Sable Calculator, its converter and the shared design lines (done: ar, de, el, es, fr, hi, it, iw, ja, ko, nl, pl,
   pt, ru, sv, tr, ur, zh-rCN drafts, sable-src 0087; Sable Start keeps its own copy of the shared lines).
2. Every Sable app's English moved into `strings.xml` (done, sable-src 0085).
3. Translations, starting with the apps a new user meets first: Sable Start, Sable Hub, Messages,
   Sable Keyboard, then the rest.

Each translation needs a fluent reviewer before it ships, and none of the Setup drafts has had one yet.
The owner's 2026-10-29 drafts also covered 15 more languages (it, pt, nl, pl, sv, tr, ru, el, ar,
he, ur, hi, ko, ja and Pinyin Chinese). They were left out because about a third of each file
was English words mixed into the translation, which reads worse than the English fallback.
Chinese should be Simplified Chinese (`values-zh-rCN`), not Pinyin.
The Weather drafts for Japanese (kana only, no kanji) and Chinese (Pinyin) were left out for the same
reason, and the owner's Sable Tools file for every language was a placeholder (`[De] Back`), so
Sable Tools still has no translation.
