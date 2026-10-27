# AMOLED Focus — foundation (v0.1)
Open this folder in Android Studio (Ladybug+), let it sync (it generates the Gradle wrapper), run on a device.
Enable: Settings → Accessibility → AMOLED Focus. Unit tests: `./gradlew :app:testDebugUnitTest`.

Implemented: session state machine (+restore after reboot/process death), BlockingEngine, AccessibilityService (package name only,
canRetrieveWindowContent=false), block screen, Safe List (max 3), Reset Tokens (earn 1 per 5 completed sessions), stats, blocked attempts,
Focus Garden stage, ongoing countdown notification, AMOLED/Dark/Light themes, onboarding, setup + app picker.
Not yet built: routines/scheduling, sounds, DND, widgets, history screen, settings screen, backup/export, biometric gate, OEM guides, Compose UI tests.
Not compiled/verified in the authoring environment (no Android SDK) — expect a few fix-ups on first build.
