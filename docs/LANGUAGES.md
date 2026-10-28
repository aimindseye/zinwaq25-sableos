# Languages

What a user sees in another language, and how to add one.

## What already works

| Layer | Status |
|---|---|
| Android and LineageOS (Settings, Phone, Contacts, system dialogs) | Every Android language. Pick it in first-boot setup or Settings > System > Languages |
| Typing (Sable Keyboard) | 35 typing languages: Latin languages with accents (Español, Français, Deutsch, Português, Italiano, Polski, Türkçe, Svenska, Nederlands), Cyrillic, Greek, Arabic, Hebrew, Urdu, Hindi and nine other Indic scripts (InScript and an "ABC" phonetic layout), Korean, Japanese kana and Chinese pinyin. Add them in Sable Keyboard's settings; Ctrl+Space or the globe key switches. Non-Latin layouts are on-screen; the physical keys type Latin |
| Sable apps | English only, except Sable Setup, which has a pilot Spanish translation (sable-src 0075) |

## Translating a Sable app

Sable Setup is the pattern (`sable-src/apps/titan2/platform/setup`):

1. Put every user-visible line in one map of resource name to English, in Android format syntax
   (`%1$s`, `%1$d`): `core/SetupText.kt`. Code calls `t("key", args)` (a `SableTexts` from
   `sable-theme`), so pure unit tests keep reading English.
2. Repeat the map in `res/values/strings.xml`. `SetupTextTest` fails when the two differ.
3. In `onCreate`, call `SetupText.t.bindTo(this)`. Lines then come from the app's string
   resources, so the phone's language picks the translation and English is the fallback.
4. Add `res/values-<lang>/strings.xml` with every key. `SetupTextTest` checks each translation has
   all keys and the same placeholders.

Product names (Sable Start, Gboard, Zinwa Q25) stay as they are. Compose apps (`sable-src/apps/r8`)
can use `stringResource` directly; the same test (English file equals the code's list, translations
complete) keeps them in step.

## Order of work

1. Sable Setup (done, Spanish pilot).
2. The apps a new user meets first: Sable Start, Sable Hub, Phone and Messages strings, Sable Tools.
3. The rest of the Sable apps.

Each translation needs a fluent reviewer before it ships; the Spanish pilot was written without one.
