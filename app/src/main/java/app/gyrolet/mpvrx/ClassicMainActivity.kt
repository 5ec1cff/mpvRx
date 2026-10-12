/*
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package app.gyrolet.mpvrx

/**
 * Same mpvRx UI as [MainActivity], but with the original icon on the Android system splash.
 * This is a real target Activity rather than a launcher shim, avoiding an extra startup hop.
 */
class ClassicMainActivity : MainActivity()
