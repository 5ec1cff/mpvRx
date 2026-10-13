"""Source-only regression checks. These do not compile or exercise Android APIs."""

from pathlib import Path
import re
import unittest
import xml.etree.ElementTree as ET


APP = Path(__file__).resolve().parents[3]
JAVA = APP / "src" / "main" / "java" / "app" / "gyrolet" / "mpvrx"
ANDROID = "{http://schemas.android.com/apk/res/android}"
TOOLS = "{http://schemas.android.com/tools}"


def source(relative_path):
    return (JAVA / relative_path).read_text(encoding="utf-8")


class PlayStoreStorageContractTest(unittest.TestCase):
    def test_flavor_flags_remain_isolated(self):
        build = (APP / "build.gradle.kts").read_text(encoding="utf-8")
        for flavor in ("standard", "playstore", "noVulkan", "fongmi"):
            block = build.split(f'create("{flavor}") {{', 1)[1].split("\n    }", 1)[0]
            scoped = "true" if flavor == "playstore" else "false"
            updates = "false" if flavor == "playstore" else "true"
            self.assertIn(f'"SCOPED_STORAGE_ONLY", "{scoped}"', block)
            self.assertIn(f'"ENABLE_UPDATE_FEATURE", "{updates}"', block)

    def test_manifest_removes_restricted_permissions_only_in_playstore(self):
        main = ET.parse(APP / "src" / "main" / "AndroidManifest.xml").getroot()
        store = ET.parse(APP / "src" / "playstore" / "AndroidManifest.xml").getroot()
        main_permissions = {p.get(ANDROID + "name") for p in main.findall("uses-permission")}
        removed = {
            p.get(ANDROID + "name")
            for p in store.findall("uses-permission")
            if p.get(TOOLS + "node") == "remove"
        }
        for permission in ("MANAGE_EXTERNAL_STORAGE", "REQUEST_INSTALL_PACKAGES"):
            name = "android.permission." + permission
            self.assertIn(name, main_permissions)
            self.assertIn(name, removed)
        self.assertIn("android:requestLegacyExternalStorage",
                      store.find("application").get(TOOLS + "remove"))

    def test_android_10_uses_read_permission_and_scoped_mutations(self):
        text = source("utils/permission/PermissionUtils.kt")
        self.assertRegex(
            text,
            r"SCOPED_STORAGE_ONLY && Build.VERSION.SDK_INT == Build.VERSION_CODES.Q ->\s*"
            r"android.Manifest.permission.READ_EXTERNAL_STORAGE",
        )
        self.assertEqual(text.count("if (!BuildConfig.SCOPED_STORAGE_ONLY) {"), 2)
        self.assertEqual(text.count("error is RecoverableSecurityException"), 2)

    def test_document_mutations_do_not_use_media_store_approval(self):
        text = source("utils/permission/PermissionUtils.kt")
        self.assertIn("videos.filter { isMediaStoreUri(it.uri) }", text)
        self.assertIn('it.uri.scheme == "content" && !isMediaStoreUri(it.uri)', text)
        self.assertIn("DocumentsContract.deleteDocument", text)
        self.assertIn("DocumentsContract.renameDocument", text)
        self.assertIn("require(uris.all(::isMediaStoreUri))", text)
        self.assertIn("contentVideos.chunked(2_000)", text)
        self.assertNotIn("if (!file.exists() || file.delete())", text)

    def test_cancelled_permission_continuations_are_not_resumed(self):
        text = source("utils/permission/PermissionUtils.kt")
        self.assertIn("if (mediaRequestPending)", text)
        self.assertIn("if (continuation.isActive) continuation.resumeWith(Result.success(true))", text)
        self.assertIn("if (continuation.isActive) continuation.resumeWith(Result.success(false))", text)

    def test_scoped_scanners_do_not_fall_back_to_filesystem_walks(self):
        for name in ("FolderViewScanner", "TreeViewScanner"):
            text = source(f"utils/storage/{name}.kt")
            body = text.split("fun scanFileSystemRoots(", 1)[1]
            self.assertIn("if (app.gyrolet.mpvrx.BuildConfig.SCOPED_STORAGE_ONLY) return",
                          body.split("try {", 1)[0])
            self.assertIn("!app.gyrolet.mpvrx.BuildConfig.SCOPED_STORAGE_ONLY && !file.exists()", text)
        video_scan = source("utils/storage/VideoScanUtils.kt")
        self.assertRegex(video_scan, r"SCOPED_STORAGE_ONLY &&\s*folder.exists\(\)")

    def test_audio_search_keeps_a_playable_content_uri(self):
        text = source("repository/MediaFileRepository.kt").split("suspend fun searchAudio(", 1)[1]
        text = text.split("private data class AudioFolderAggregate", 1)[0]
        self.assertIn("MediaStore.Audio.Media._ID", text)
        self.assertIn("uri = if (BuildConfig.SCOPED_STORAGE_ONLY)", text)
        self.assertIn("Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI", text)

    def test_blacklists_reach_folder_tree_and_library_views(self):
        for relative in (
            "ui/browser/folderlist/FolderListViewModel.kt",
            "ui/browser/filesystem/FileSystemBrowserViewModel.kt",
            "ui/browser/medialibrary/MediaLibraryViewModel.kt",
        ):
            self.assertIn("isMediaPathInFolder", source(relative))
        policies = source("utils/storage/MediaScanPolicies.kt")
        self.assertIn("DocumentsContract.getDocumentId(folder)", policies)
        self.assertIn('itemId.startsWith("$folderId/")', policies)
        self.assertIn("ScopedHiddenFolderScanner.isIndexedDescendant(path, folderPath)", policies)
        self.assertIn('if (path?.startsWith("content://") == true) return path', policies)

    def test_unsupported_folder_actions_are_hidden_in_playstore(self):
        folder = source("ui/browser/folderlist/FolderListScreen.kt")
        self.assertIn("showRename = selectionManager.isSingleSelection && !BuildConfig.SCOPED_STORAGE_ONLY", folder)
        self.assertIn("showDelete = !BuildConfig.SCOPED_STORAGE_ONLY", folder)
        tree = source("ui/browser/filesystem/FileSystemBrowserScreen.kt")
        self.assertIn("showDelete = !app.gyrolet.mpvrx.BuildConfig.SCOPED_STORAGE_ONLY || onlyVideosSelected", tree)

    def test_android_10_copy_move_uses_scoped_destination(self):
        text = source("utils/media/CopyPasteOps.kt")
        self.assertRegex(text, r"SCOPED_STORAGE_ONLY && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q\) \{\s*false")

    def test_selected_audio_folders_obey_library_duration_filter(self):
        scanner = source("utils/media/ScopedHiddenFolderScanner.kt")
        self.assertIn("if (audioOnly && !isAudio) return@run null", scanner)
        self.assertIn("minimumAudioDurationSeconds.coerceAtLeast(0).toLong() * 1_000L", scanner)
        repository = source("repository/MediaFileRepository.kt")
        self.assertIn("minimumAudioDurationSeconds = minimumAudioDurationSeconds,\n          audioOnly = true", repository)

    def test_playstore_info_is_short(self):
        strings = ET.parse(APP / "src" / "main" / "res" / "values" / "strings.xml").getroot()
        values = {item.get("name"): "".join(item.itertext()) for item in strings.findall("string")}
        self.assertLessEqual(len(values["pref_playstore_hidden_summary"]), 80)
        self.assertLessEqual(len(values["pref_playstore_hidden_limitations"]), 100)


if __name__ == "__main__":
    unittest.main()
