# Performance capture

The app ships a small, manually scoped entry-point profile and a launcher startup profile. They
replace the old `HSP` wildcard over every app method. Compose and other AndroidX AARs still supply
their own profiles. These checked-in app rules are conservative defaults, not measured profiles.

The `baselineprofile` test-only module captures cold launcher startup separately from library
navigation and scrolling. It creates 32 local video fixtures in its own `Movies/mpvRxBenchmark/`
folder. The 34 KB clip was generated with FFmpeg's `testsrc2`; it contains no third-party media.
Use a disposable API 33+ device/emulator for capture. No account or media server is required.

Run **Capture Baseline Profiles** from GitHub Actions, or connect a device and run:

```sh
./gradlew :app:generateStandardReleaseBaselineProfile -PtargetAbi=x86_64
```

Choose the connected device's ABI (`arm64-v8a` for most physical phones). The plugin builds a
non-minified, profileable target for collection and writes measured rules to
`app/src/standardRelease/generated/baselineProfiles/`. Review and commit both generated text files
to package them in subsequent Standard release builds. Other distributions and preview have
independent variant tasks; generate their own profiles before claiming benefits for them. The
manual capture workflow uploads its results and does not push commits or publish an APK.
It first runs artwork regression tests for tier isolation, shared requests, bounded transfers,
cancellation, stale offline artwork and memory trimming. The test fixtures are generated locally.

For timings, use Android Studio's instrumented test configuration for `StartupBenchmarks` against
the plugin's **benchmark** variant on a physical device. Compare cold-start TTID/TTFD with
`CompilationMode.None` and `BaselineProfileMode.Require` on the same device and seeded library.
The folder screen reports fully drawn after initial data or a usable permission/empty/error screen.
The scrolling benchmark records frame timing. Emulator timings are not release performance evidence.
Ordinary app builds keep automatic profile generation disabled and require no connected device.

Standard CI also compiles the artwork regression tests and profile capture sources without requiring
a device. Every CI release build uploads `size-report-<distribution>` and adds an archive breakdown to
the job summary: DEX, fonts, assets, native libraries per ABI and the ART profile budget. Compare
the same distribution/ABI at two commits. Universal APK size and AAB archive size do not describe
a single device's Play download. Oversized binary Baseline Profiles fail the reporting step.

Runtime changes bound bitmap caches independently of `largeHeap`, respond to Android memory
trimming, limit remote/network decoding, stream remote originals to disk and use size-tiered
artwork. Cache trimming drops references, never recycles displayed bitmaps or clears generated
thumbnails. Remote originals have a 128 MiB maintenance budget; local/network generated thumbnails
remain until the user's explicit clear. Full player artwork keeps the 1024 px decode tier.

Further changes to broad crypto/JNI keep rules, native codecs, `largeHeap`, or native packaging
need release measurements and SMB/SFTP/playback compatibility checks. Existing R8/resource
shrinking, ABI splits, delayed native warmup, hidden-controls polling and playback wake-lock
ownership are retained. No latency percentage or delivered APK saving is claimed without a
release artifact and device measurements.
