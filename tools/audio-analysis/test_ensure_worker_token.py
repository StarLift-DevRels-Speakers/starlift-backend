from pathlib import Path
import re
import tempfile
import unittest

from ensure_worker_token import ensure_token


class DeploymentTokenTest(unittest.TestCase):
    def test_missing_file_creates_private_persistent_token(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "environment/backend.env"
            ensure_token(path)
            content = path.read_text()
            self.assertRegex(content, r"^AUDIO_ANALYSIS_WORKER_TOKEN=[a-f0-9]{64}\n$")
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            ensure_token(path)
            self.assertEqual(path.read_text(), content)

    def test_empty_values_are_replaced_without_touching_other_credentials(self):
        for value in ("", '""', "''", " # generate", '"" # generate'):
            with self.subTest(value=value), tempfile.TemporaryDirectory() as folder:
                path = Path(folder) / "backend.env"
                path.write_text("GIGACHAT_TOKEN=keep-me\nAUDIO_ANALYSIS_WORKER_TOKEN=" + value + "\nJWT_SECRET=also-keep\n")
                ensure_token(path)
                self.assertIn("GIGACHAT_TOKEN=keep-me\n", path.read_text())
                self.assertIn("\nJWT_SECRET=also-keep\n", path.read_text())
                self.assertRegex(path.read_text(), r"AUDIO_ANALYSIS_WORKER_TOKEN=[a-f0-9]{64}\n")

    def test_existing_quoted_token_is_not_rotated(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "backend.env"
            content = 'AUDIO_ANALYSIS_WORKER_TOKEN="existing-secret" # shared with app\n'
            path.write_text(content)
            ensure_token(path)
            self.assertEqual(path.read_text(), content)

    def test_last_assignment_is_used_like_compose(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "backend.env"
            path.write_text("AUDIO_ANALYSIS_WORKER_TOKEN=old\nAUDIO_ANALYSIS_WORKER_TOKEN=\n")
            ensure_token(path)
            self.assertTrue(re.fullmatch(r"AUDIO_ANALYSIS_WORKER_TOKEN=[a-f0-9]{64}", path.read_text().splitlines()[-1]))
