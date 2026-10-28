# Build preferences

- Build and deliver the `release` variant by default.
- Use the existing Android debug signing configuration for release APKs; a separate release keystore is unnecessary.
- Unless the user explicitly requests emulator validation, skip emulator checks and deliver the release APK for the user to test.
