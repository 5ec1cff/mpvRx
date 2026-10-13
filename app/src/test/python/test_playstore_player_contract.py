"""Source-only Play Store player checks; run with python -B (no Android build)."""

from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[4]
PLAYER = ROOT / "app" / "src" / "main" / "java" / "app" / "gyrolet" / "mpvrx" / "ui" / "player"


def source(name):
    return (PLAYER / name).read_text(encoding="utf-8")


class PlayStorePlayerContract(unittest.TestCase):
    def test_scoped_subtitle_add_uses_document_picker(self):
        sheets = source(Path("controls") / "PlayerSheets.kt")
        self.assertRegex(
            sheets,
            r"onAddSubtitle = \{\s*"
            r"if \(app\.gyrolet\.mpvrx\.BuildConfig\.SCOPED_STORAGE_ONLY\) \{\s*"
            r"openSystemSubtitlePicker\(\)\s*"
            r"\} else \{\s*showFilePicker = true\s*\}",
        )
        self.assertIn("ActivityResultContracts.OpenDocument()", sheets)
        self.assertIn("onAddSubtitle(it)", sheets)
        self.assertIn("openSystemSubtitlePicker()", sheets)

    def test_scoped_audio_add_uses_document_picker(self):
        sheets = source(Path("controls") / "PlayerSheets.kt")
        self.assertRegex(
            sheets,
            r"onAddAudioTrack = \{\s*"
            r"if \(app\.gyrolet\.mpvrx\.BuildConfig\.SCOPED_STORAGE_ONLY\) \{\s*"
            r"openSystemAudioPicker\(\)\s*"
            r"\} else \{\s*showAudioFilePicker = true\s*\}",
        )
        self.assertIn("onAddAudio(it)", sheets)

    def test_scoped_history_keeps_content_uri_and_legacy_query(self):
        activity = source("PlayerActivity.kt")
        method = activity.split("private fun filePathForUri(uri: Uri): String =", 1)[1]
        method = method.split("else -> uri.toString()", 1)[0]
        self.assertRegex(
            method,
            r'"content" ->\s*'
            r"if \(app\.gyrolet\.mpvrx\.BuildConfig\.SCOPED_STORAGE_ONLY\) \{\s*"
            r"//[^\n]*\n\s*uri\.toString\(\)\s*"
            r"\} else \{\s*contentResolver",
        )
        self.assertIn(".query(uri, arrayOf(MediaStore.MediaColumns.DATA)", method)
        self.assertIn('"file" -> uri.path ?: uri.toString()', method)

    def test_disabled_ytdlp_cannot_offer_youtube_download(self):
        view_model = source("PlayerViewModel.kt")
        self.assertRegex(
            view_model,
            r"private fun currentYouTubeSource\(\): String\? \{\s*"
            r"if \(!app\.gyrolet\.mpvrx\.BuildConfig\.ENABLE_YTDLP\) return null",
        )
        self.assertIn("currentYouTubeSource() != null || currentJellyfinVideoSource() != null", view_model)
        self.assertIn("val sourceUrl = currentYouTubeSource() ?: return null", view_model)


if __name__ == "__main__":
    unittest.main()
