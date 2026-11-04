# Sable Speech (eSpeak NG)

Sable Speech is the text-to-speech engine SableOS ships, so Text Reader, the
"Speak what's focused" service, Maps and any app that speaks have a voice
without Google's engine. It is eSpeak NG, built from source.

## Product identity

```text
name     eSpeak NG (upstream label kept)
package  org.sableos.speech
module   SableSpeech
```

## Upstream pin

```text
repository  https://github.com/espeak-ng/espeak-ng.git
tag         1.52.0
commit      4870adfa25b1a32b4361592f1be8a40337c58d6c
gradle dir  android/   (ESPEAK_NG_GRADLE_DIR in upstream.env)
```

The checkout is ephemeral build input. `apply_sable_flavor.py` changes three
things and `verify_sable_speech.py` checks them:

- application id `org.sableos.speech`, so an eSpeak NG installed from F-Droid
  never clashes with the system copy;
- `arm64-v8a` only, the Q25's one ABI;
- no launcher entry. The engine is chosen and set up in Settings >
  Accessibility > Text-to-speech output; with no other engine installed,
  Android uses it by default.

The engine and its voice data stay as upstream builds them (about 100
languages, all on the phone). It requests no permission, so it never goes
online.

## Build

`scripts/build-apps.sh --root flavor:espeak` (row SableSpeech in
`product/q25/apps.tsv`). It needs NDK 26.1.10909125, CMake 3.22.1, Android
platform 34 and build-tools 34.0.0, plus a host C compiler: the voice data is
compiled on the build machine and packed into the APK. The release APK is
unsigned and the platform build signs it.

## Licence

eSpeak NG is GPL-3.0-or-later (its `COPYING`). SableOS ships it unmodified
apart from the three build changes above, and the source is the pinned upstream
commit plus the scripts in this folder.
