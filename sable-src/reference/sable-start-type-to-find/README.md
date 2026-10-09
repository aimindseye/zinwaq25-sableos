# Sable Start type-to-find (portable reference source)

Pure-Kotlin ranking logic for All Apps type-to-find: label prefix, then word prefix, then substring; case-insensitive; stable by label;
`firstMatch` is what Enter launches. It was extracted from the retired standalone `home` app (removed in D1b).

```text
ROLE=PORTABLE_REFERENCE_SOURCE
CANONICAL_CONSUMER=Launcher3-hosted Sable Start   (interface request IR-002)
GRADLE_MODULE=NO
ANDROID_MANIFEST=NO
APK=NO
HOME_INTENT=NO
ANDROID_FRAMEWORK_DEPENDENCY=NO
HIDDEN_API=NO
NEW_EXTERNAL_DEPENDENCY=NO
```

This directory is not part of any Gradle build and is not a runtime library that Launcher3 is required to consume. The canonical
integration lane decides whether to port it into Launcher3/SableStart, convert it into a Soong library, or reimplement the small
interface natively. There is no HOME process here and none may be added around it.

The tests use JUnit 4 (`org.junit.Test`). Developer validation runs them under the local JUnit compatibility shim
(`JVM_SHIM_TESTS`), not under Gradle.

## Keyboard-first model (P1)

`KeyInput.kt`, `StartModel.kt` and `Routing.kt` add the framework-free keyboard-first interaction model (focus, navigation, type-to-find,
Back/Escape, MoveHome/MoveEnd, key repeat, focus restoration, routing precedence). See `docs/SABLE_START_KEYBOARD_FIRST_SOURCE.md` for the behaviour and
the canonical integration handoff. It is still not a Gradle module, not HOME and not runtime.
