/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package app.gyrolet.mpvrx.ui.player

import android.content.Context
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Builds mpv OUTPUT hook GLSL shaders for the three fx-ambient modes:
 * Cinema, Echo, and Mirror.
 *
 * These shaders are adapted from the fx_ambient Vulkan GLSL package
 * (fx_ambient.frag / fx_common.glsl) and rewritten for mpv's hook system
 * which uses OpenGL ES 2.0 / 3.0 instead of Vulkan.
 *
 * Key differences from the Vulkan originals:
 * - No push constants / descriptor sets — geometry parameters are baked in
 *   as GLSL #defines at shader-compile time (Kotlin side).
 * - No uHist / uEcho textures — Cinema and Echo approximate temporal blur
 *   with manual multi-tap sampling within a single frame.
 * - No specialisation constants — each mode is a separate shader file.
 * - No adjustable user parameters — every mode uses fixed, hand-tuned values.
 *
 * All three modes follow the same structure:
 *   1. Remap HOOKED_pos -> video_uv  (scale is baked from Kotlin).
 *   2. Early-return original pixel for inside_video (no modification to video).
 *   3. Compute bar-depth ratio dn in [0,1].
 *   4. Early-return vec4(0,0,0,1) when dn >= 1.0 (beyond reach -> true black).
 *   5. Sample, colour-grade, fade, dither, output.
 */
object FxAmbientShaderBuilder {

  // -- Private helpers ---------------------------------------------------------

  private fun f(value: Double): String {
    val normalised = if (abs(value) < 0.0000005) 0.0 else value
    val s = String.format(Locale.US, "%.8f", normalised).trimEnd('0').trimEnd('.')
    return if ('.' in s) s else "$s.0"
  }

  /**
   * Builds the standard mpv hook header and GLSL helper functions.
   * scaleX / scaleY encode how much the output is wider/taller than the
   * video, so (SCALE_X - 1) / 2 gives the bar thickness in video-UV space.
   */
  private fun buildHeader(desc: String, scaleX: Double, scaleY: Double): String = """
//!HOOK OUTPUT
//!BIND HOOKED
//!DESC $desc

#ifdef GL_ES
precision mediump float;
precision highp int;
#else
#define mediump
#define highp
#define lowp
#endif

// Bar geometry baked from Kotlin so the GPU never recomputes it.
// SCALE_X > 1 -> pillarboxed (left/right bars).
// SCALE_Y > 1 -> letterboxed (top/bottom bars).
#define SCALE_X ${f(scaleX)}
#define SCALE_Y ${f(scaleY)}

// -- Common GLSL helpers (identical subset to AmbientShaderBuilder) -----------

// Jorge Jimenez Interleaved Gradient Noise -- cache-coherent, zero trig.
mediump float ign(highp vec2 p) {
    mediump vec3 m = vec3(0.06711056, 0.00583715, 52.9829189);
    return fract(m.z * fract(dot(p, m.xy)));
}

mediump float luma(mediump vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

// Selective chroma boost: lifts saturation without blowing highlights.
mediump vec3 vibrance(mediump vec3 c, mediump float k) {
    return max(vec3(luma(c)) + (c - vec3(luma(c))) * k, vec3(0.0));
}

// Highlight roll-off from fx_common.glsl: c / (1 + k*luma).
// Dark/mid tones pass almost unchanged; bright bursts are softened.
mediump vec3 rolloff(mediump vec3 c, mediump float k) {
    return c * (1.0 / (1.0 + k * luma(c)));
}

// IGN-amplitude-scaled dither to dissolve 8-bit quantisation banding.
mediump vec3 dither_out(mediump vec3 c, highp vec2 p) {
    mediump float amp = clamp(max(c.r, max(c.g, c.b)) * 255.0, 0.0, 1.0);
    return c + (ign(p) - 0.5) * (amp / 255.0);
}

  """.trimIndent()

  // -- Shared video prologue ---------------------------------------------------

  /**
   * Shared in-shader prologue injected into each mode's hook():
   *   - Remaps screen UV to video UV.
   *   - Computes safe clamping bounds (half-texel inset).
   *   - Early-returns the original pixel when inside the video.
   *   - Computes per-bar geometry: bx, by, dx, dy, d, thick, dn.
   *     dn = normalised depth into the bar (0 = at edge, >= 1 = beyond reach).
   * @param reachScale multiplied against bar thickness for the reach cutoff.
   */
  private fun videoPrologue(reachScale: String): String = """
    highp vec2 uv       = HOOKED_pos;
    highp vec2 video_uv = (uv - 0.5) * vec2(SCALE_X, SCALE_Y) + 0.5;

    // Half-texel inset to avoid fetching outside decoded frame boundaries.
    highp vec2 half_texel = vec2(0.5) / HOOKED_size;
    highp vec2 safe_min   = half_texel;
    highp vec2 safe_max   = vec2(1.0) - half_texel;

    // -- Inside-video pass-through -------------------------------------------
    bool inside_video = video_uv.x >= 0.0 && video_uv.x <= 1.0 &&
                        video_uv.y >= 0.0 && video_uv.y <= 1.0;
    if (inside_video) {
        return HOOKED_tex(clamp(video_uv, safe_min, safe_max));
    }

    // -- Bar geometry --------------------------------------------------------
    // Bar thickness (in video-UV space) = (SCALE - 1) / 2.
    highp float bx = max((SCALE_X - 1.0) * 0.5, 0.001);
    highp float by = max((SCALE_Y - 1.0) * 0.5, 0.001);

    // Penetration depth into each bar axis (>0 only when outside that axis).
    highp float dx = max(max(-video_uv.x, video_uv.x - 1.0), 0.0);
    highp float dy = max(max(-video_uv.y, video_uv.y - 1.0), 0.0);

    // Euclidean distance into the bar region.
    highp float d = length(vec2(dx, dy));

    // Which axis dominates: pillarbox (dx > dy) uses bx, letterbox uses by.
    highp float thick = (SCALE_X > SCALE_Y && dx > dy) ? bx : by;

    // Normalised depth: 0 at the video edge, 1 at the nominal reach boundary.
    highp float dn = clamp(d / (thick * $reachScale), 0.0, 1.0);
    if (dn >= 1.0) { return vec4(0.0, 0.0, 0.0, 1.0); }
  """.trimIndent().prependIndent("    ")

  // -- Cinema -----------------------------------------------------------------

  /**
   * Spiral tap offsets for the Cinema mode manual Gaussian blur.
   * Adapted from AmbientShaderBuilder.buildSpiralTapTable() -- equal weights.
   */
  private fun cinemaTapTable(count: Int, maxR: Double): String {
    val goldenAngle = 2.399963229728653
    val taps = (0 until count).joinToString(",\n") { i ->
      val r = sqrt((i.toDouble() + 0.5) / count) * maxR
      val theta = (i.toDouble() + 0.5) * goldenAngle
      "    vec2(${f(cos(theta) * r)}, ${f(sin(theta) * r)})"
    }
    return "const vec2 CINEMA_TAPS[$count] = vec2[$count](\n$taps\n);"
  }

  /**
   * Builds the Cinema ambient shader.
   *
   * Algorithm (adapted from fx_ambient cinema()):
   * 1. Map the current bar pixel to a slightly zoomed-out video UV, so that
   *    the whole frame is projected into the bars as if from further away.
   * 2. Sample the video at that UV 8 times via a Spiral Poisson disk whose
   *    radius grows linearly with bar depth dn (near-edge = sharp, far = blurry).
   * 3. Apply vibrance -> rolloff -> power-law fade -> dither.
   */
  fun buildCinema(@Suppress("UNUSED_PARAMETER") context: Context, scaleX: Double, scaleY: Double): String {
    val tapCount  = 8
    val baseRadius = 0.025
    val farRadius  = 0.10
    val tapTable   = cinemaTapTable(tapCount, farRadius)

    return """
${buildHeader("Fx Ambient -- Cinema", scaleX, scaleY)}

// Pre-computed Spiral Poisson tap offsets (max reach = $farRadius UV units).
$tapTable

vec4 hook() {
${videoPrologue("1.35")}

    // -- Cinema projection UV ------------------------------------------------
    // Divide by a zoom factor slightly > 1 so the full frame is visible
    // inside the bars; the factor grows with dn so far pixels see a wider view.
    highp float zoom = 1.10 + 0.28 * dn;
    highp vec2 c_uv  = (video_uv - 0.5) / zoom + 0.5;

    // -- 8-tap spiral Gaussian blur ------------------------------------------
    // Blend radius interpolates from baseRadius (sharp, near edge) to
    // farRadius (blurry, at bar end) as a linear function of dn.
    mediump float br       = mix(${f(baseRadius)}, ${f(farRadius)}, dn);
    mediump float br_scale = br / ${f(farRadius)};

    mediump vec3  acc = vec3(0.0);
    mediump float twt = 0.0;
    for (int i = 0; i < $tapCount; i++) {
        vec2 tap_off = CINEMA_TAPS[i] * br_scale;
        mediump vec3  s = HOOKED_tex(clamp(c_uv + tap_off, safe_min, safe_max)).rgb;
        mediump float w = 1.0 + luma(s) * 1.5;
        acc += s * w;
        twt += w;
    }
    mediump vec3 col = acc / max(twt, 0.001);

    // -- Colour grade --------------------------------------------------------
    col = vibrance(col, 1.08);
    col = rolloff(col, 0.55);

    // -- Fade: bright at edge, gone at bar end --------------------------------
    mediump float fade = pow(max(1.0 - dn, 0.0), 1.15) * 0.72;
    mediump vec3 ambient_rgb = col * fade;

    // -- Dither --------------------------------------------------------------
    ambient_rgb = dither_out(ambient_rgb, uv * HOOKED_size);
    return vec4(ambient_rgb, 1.0);
}
    """.trimIndent()
  }

  // -- Echo -------------------------------------------------------------------

  /**
   * Builds the Echo ambient shader.
   *
   * Algorithm (inspired by fx_ambient echo()):
   * The original uses a temporal ring buffer (compute shader) to store edge
   * colours from previous frames and slowly push them outward. Without cross-
   * frame state in mpv hooks, we approximate with a spatial smear:
   * 5 taps taken along the bar-parallel direction (perpendicular to the
   * outward vector) with Gaussian spread growing with depth dn.
   */
  fun buildEcho(@Suppress("UNUSED_PARAMETER") context: Context, scaleX: Double, scaleY: Double): String =
    """
${buildHeader("Fx Ambient -- Echo", scaleX, scaleY)}

vec4 hook() {
${videoPrologue("1.20")}

    // -- Edge origin (nearest video pixel) -----------------------------------
    highp vec2 edge_origin = clamp(video_uv, safe_min, safe_max);

    // -- Determine smear axis (parallel to bar, perpendicular to outward) ----
    // For pillarbox bars (left/right) smear vertically; for letterbox smear horizontally.
    bool in_side_bar = (SCALE_X > SCALE_Y) && (dx > dy);
    highp vec2 smear_dir = in_side_bar ? vec2(0.0, 1.0) : vec2(1.0, 0.0);

    // Smear width in video-UV space grows from tight at edge to wide at depth.
    mediump float sw = 0.02 + 0.12 * dn;

    // -- 5-tap Gaussian along smear axis -------------------------------------
    mediump vec3  acc = vec3(0.0);
    mediump float twt = 0.0;
    const float W0 = 0.0625;
    const float W1 = 0.25;
    const float W2 = 0.375;

    highp vec2 t0 = clamp(edge_origin - smear_dir * (sw * 2.0), safe_min, safe_max);
    highp vec2 t1 = clamp(edge_origin - smear_dir * sw,          safe_min, safe_max);
    highp vec2 t2 = edge_origin;
    highp vec2 t3 = clamp(edge_origin + smear_dir * sw,          safe_min, safe_max);
    highp vec2 t4 = clamp(edge_origin + smear_dir * (sw * 2.0), safe_min, safe_max);

    mediump vec3 s0 = HOOKED_tex(t0).rgb; acc += s0 * W0; twt += W0;
    mediump vec3 s1 = HOOKED_tex(t1).rgb; acc += s1 * W1; twt += W1;
    mediump vec3 s2 = HOOKED_tex(t2).rgb; acc += s2 * W2; twt += W2;
    mediump vec3 s3 = HOOKED_tex(t3).rgb; acc += s3 * W1; twt += W1;
    mediump vec3 s4 = HOOKED_tex(t4).rgb; acc += s4 * W0; twt += W0;
    mediump vec3 col = acc / max(twt, 0.001);

    // -- Colour grade --------------------------------------------------------
    col = vibrance(col, 1.15);
    col = rolloff(col, 0.55);

    // -- Fade: power law gives a crisp edge followed by a long soft tail -----
    mediump float fade = pow(max(1.0 - dn, 0.0), 1.8) * 0.85;
    mediump vec3 ambient_rgb = col * fade;

    // -- Dither --------------------------------------------------------------
    ambient_rgb = dither_out(ambient_rgb, uv * HOOKED_size);
    return vec4(ambient_rgb, 1.0);
}
    """.trimIndent()

  // -- Mirror -----------------------------------------------------------------

  /**
   * Builds the Mirror ambient shader.
   *
   * Algorithm (adapted from fx_ambient mirror()):
   * 1. Fold video_uv across [0,1] boundaries (mirror wrap).
   * 2. Sample the folded UV 3 times along the fold direction with a spread
   *    that grows with depth dn, simulating blur in the reflection.
   * 3. Top bar (video_uv.y < 0) is dimmed by 0.7 -- ceiling reflects less.
   */
  fun buildMirror(@Suppress("UNUSED_PARAMETER") context: Context, scaleX: Double, scaleY: Double): String =
    """
${buildHeader("Fx Ambient -- Mirror", scaleX, scaleY)}

vec4 hook() {
${videoPrologue("1.15")}

    // -- Fold video_uv across [0,1] boundaries (mirror wrap) -----------------
    highp vec2 m_uv = video_uv;
    m_uv = mix(m_uv, -m_uv,             step(m_uv, vec2(0.0)));
    m_uv = mix(m_uv, vec2(2.0) - m_uv, step(vec2(1.0), m_uv));
    m_uv = clamp(m_uv, safe_min, safe_max);

    // -- Blur axis: fold along the same axis as the bar ----------------------
    bool in_side_bar = (SCALE_X > SCALE_Y) && (dx > dy);
    highp vec2 blur_dir = in_side_bar ? vec2(1.0, 0.0) : vec2(0.0, 1.0);
    mediump float bw = mix(0.0, 0.06, pow(dn, 0.7));

    // -- 3-tap blur along blur_dir -------------------------------------------
    mediump vec3 col = vec3(0.0);
    col += HOOKED_tex(clamp(m_uv - blur_dir * bw, safe_min, safe_max)).rgb * 0.25;
    col += HOOKED_tex(clamp(m_uv,                  safe_min, safe_max)).rgb * 0.50;
    col += HOOKED_tex(clamp(m_uv + blur_dir * bw, safe_min, safe_max)).rgb * 0.25;

    // -- Colour grade --------------------------------------------------------
    col = vibrance(col, 0.92);
    col = rolloff(col, 0.30);

    // -- Mirror fade: tight power + Gaussian decay so seam is crisp ----------
    mediump float fade = pow(max(1.0 - dn, 0.0), 2.0) *
                         (0.55 + 0.45 * exp(-6.0 * dn));

    // Ceiling (top bar) reflects less light than floor -- dim by 30%.
    if (video_uv.y < 0.0) { fade *= 0.7; }

    mediump vec3 ambient_rgb = col * (fade * 0.55);

    // -- Dither --------------------------------------------------------------
    ambient_rgb = dither_out(ambient_rgb, uv * HOOKED_size);
    return vec4(ambient_rgb, 1.0);
}
    """.trimIndent()
}