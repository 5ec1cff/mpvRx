/* SPDX-License-Identifier: AGPL-3.0-or-later */
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

/* API 26–28's ImageWriter.newInstance infers its format from the native window.
 * HAL_PIXEL_FORMAT_IMPLEMENTATION_DEFINED (0x22) is ImageFormat.PRIVATE: buffers
 * are imported from ImageReader, so no pixel format conversion is performed. */
JNIEXPORT void JNICALL
Java_app_gyrolet_mpvrx_ui_player_PlaybackSurfaceNative_useOpaqueBuffers(
    JNIEnv *env, jobject instance, jobject surface)
{
    (void)instance;
    ANativeWindow *window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"),
                        "Playback Surface has no native window");
        return;
    }
    int result = ANativeWindow_setBuffersGeometry(window, 0, 0, 0x22);
    ANativeWindow_release(window);
    if (result < 0) {
        (*env)->ThrowNew(env, (*env)->FindClass(env, "java/lang/IllegalStateException"),
                        "Playback Surface cannot import opaque buffers");
    }
}
