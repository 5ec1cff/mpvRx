# Video background playback must retain the native window

- mpvlib 1.1.4 marks both `vo` and `wid` as UPDATE_VO. Changing either while a video
  track is selected queues a whole-file exact seek. It flushes AudioTrack and resets
  audio synchronization even when `pause` stays false. Keeping the same mpv instance
  and preserving `vid` alone does not provide continuous audio.
- `PlaybackSurfaceRelay` owns one PRIVATE ImageReader Surface for the core's lifetime.
  `PlaybackSession.bindSurface` changes its ImageWriter presentation target, not native
  wid/vo. Unbind only closes the presentation writer. Continue consuming frames without
  a target or a hidden window will eventually backpressure video/audio playback.
- ImageWriter must use PRIVATE too. Importing an opaque Image into a writer inferred
  from a normal SurfaceView's format is rejected. API 29+ has an explicit-format
  factory; 26–28 use the small native-window format helper. Match output dimensions:
  BLAST rejects old-sized buffers after window resizing. Never block the consumer
  waiting for a window: leave a spare writer slot and discard late video frames.
- A retained VO redraws a paused frame through an empty client-owned ASS overlay;
  do not use a zero-distance seek for fullscreen/mini-player presentation changes.
- Home normally stops but does not destroy PlayerActivity. Its old focus listener
  must recognize a completed background handoff, and the retained service must regain
  focus on subsequent departures. Mark BACKGROUND even when mini-player is enabled;
  the renderer no longer needs to be disabled to hand audio ownership to the service.

Validation: actual APKs on Android 15/API 35 with SurfaceView, TextureView and gpu-next
over OpenGL; Android 8/API 26 with No Vulkan. Home/Recents, screen off/on, paused return,
paused resize and resume passed with no seek/restart after startup. The unpatched
master produced 7 seeks and 6 extra playback restarts for Home and screen-off round trips.
`scripts/check-background-playback.py` exercises this with a >=60-second local clip,
saves native logs/screenshots, checks that playback really started, checks unexpected
pause writes, and restores the debug app's preferences. `--expect-interruptions` runs
the negative control. Inspect the screenshots too: event counts cannot detect black video.

Limits: emulator Vulkan capability detection fell back to OpenGL; physical Vulkan,
HDR/encoded-color metadata, Bluetooth, long-running power and high-resolution playback
still need device QA. Rendering/decoding continues in the background in this design,
which trades some background power usage for preserving the audio timeline. Standard
mpvlib's existing Vulkan symbol dependency fails to load on the API 26 emulator; use
the existing No Vulkan flavor there. Trail CLI/cache was unavailable.
