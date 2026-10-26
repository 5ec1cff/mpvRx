# Suspend video before changing the Android output

- mpvlib 1.1.4 / bundled mpv 3aecc359 queues a whole-file seek for UPDATE_VO only
  when a video track is selected. Both vo and wid have that update flag. Video
  track deselection itself uninitializes the video chain, leaving audio running.
- PlaybackSession.detachRendererSurfaceLocked calls suspendVideoTrackLocked first:
  save the selected id with the current loaded media generation, synchronously set
  vid=no and read it back, then set vo=null / force-window=no and detach JNI wid.
  Do not reorder these steps or restore video before the final vo/wid update.
- bindSurface uses the actual Android Surface directly. Its token/owner checks
  cancel stale activation jobs. Wait for a nonzero 64-bit wid and actual current-vo
  before restoring the generation-scoped track or deferred auto-selection.
- A file can finish loading after losing its Surface. FILE_LOADED must suspend
  video too when no renderer is ready. load() clears the previous saved id; a new
  background file defers auto-selection until its renderer returns. Track setters
  override a suspended selection, including explicit no-video requests.
- No ImageReader/ImageWriter relay or playback_surface JNI helper remains. With
  no presentation Surface, video decoding and GPU output are released. A visible
  mini player/PiP still needs video; Surface owner identity prevents an outgoing
  view from detaching a newer owner's window.
- Keep the previous Activity/Service focus handoff fixes. Native audio continuity
  does not prevent an unrelated focus callback from pausing playback.
- A paused EGL window can retain an old-sized buffer after the immediate resize
  redraw. resizeSurface schedules one more redraw after 150ms, guarded by owner,
  attachment token, dimensions and paused state. It never seeks or starts a
  repeating frame consumer, and also covers mini-player Surface resizing.

Validation uses scripts/check-background-playback.py against real APKs: Home/Recents,
screen off/on, rapid cycles, paused return/resize. --inspect-video enables temporary
mpv IPC only through test preferences and restores them afterward. It checks that
vid=false, current-vo/video-codec are unavailable in the background, pause=false and
audio-pts advances, then verifies the selected video track and decoder return.
JSON IPC encodes vid=no as false (the string getter used by the app returns "no").
Use --pause-resize --check-paused-image (Pillow required) to compare the image
before and after restoring the window size. A decoder and nonblack screenshot
alone missed a cropped frame in r2050. The follow-up redraw reduced the measured
normalized pixel error from 0.440 to 0.000 in the Android 15 MediaCodec fixture.

Passed on Android 15 with SurfaceView/gpu and TextureView/gpu-next, software decode
and H.264 MediaCodec; Android 8 No Vulkan also passed. A two-video-track fixture kept
vid=2 across handoffs. Direct HTTP with Range support passed too. All these surface
handoffs had zero extra player SEEK / PLAYBACK_RESTART events. Inspect screenshots
as well: counters and decoder presence do not prove visible image recovery.
An emulator queue probe changed files through MediaSession Next while backgrounded:
the outgoing vid=2 was discarded, the new file stayed audio-only, and returning
selected its available vid=1. Its normal saved-position seek belongs to the new-file
load, not the Surface handoff.

Limits: selecting video can initiate a demux refresh seek and reread data without
resetting AO. Slow/unseekable streams, physical Vulkan/HDR, audible output continuity
and battery measurements still need device QA. Shared muxed data may still be read
while video is disabled. Trail CLI/cache was unavailable, so source spans were read
directly and the graph could not be rebuilt.
