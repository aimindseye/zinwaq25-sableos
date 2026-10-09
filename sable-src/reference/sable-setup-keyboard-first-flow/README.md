# Sable Setup keyboard-first flow (portable reference source)

Pure-Kotlin model of a keyboard-first setup flow (welcome, Wi-Fi list and password, account sign-in, SIM, keyboard choice) for the
GrapheneOS SetupWizard2 integration. It is a specification-as-code for the canonical lane; it is not a wizard.

```text
ROLE=PORTABLE_REFERENCE_SOURCE
CANONICAL_CONSUMER=GrapheneOS SetupWizard2 Titan integration   (interface request IR-014)
GRADLE_MODULE=NO
ANDROID_MANIFEST=NO
APK=NO
SETUP_WIZARD_FORK=NO
ANDROID_FRAMEWORK_DEPENDENCY=NO
HIDDEN_API=NO
NEW_EXTERNAL_DEPENDENCY=NO
```

It consumes the normalized key events of `reference/sable-start-type-to-find` (P1). Sable Setup stays a helper that only opens the
real platform setup; nothing here creates a second SetupWizard.
