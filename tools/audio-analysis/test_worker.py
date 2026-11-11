import io
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import worker


class WorkerTest(unittest.TestCase):
    def response(self, body, headers=None):
        response = io.BytesIO(body)
        response.headers = headers or {}
        return response

    def test_worker_token_requires_https_or_loopback(self):
        for url in ("http://localhost:8081", "http://[::1]:8081", "https://speaker.example"):
            with self.subTest(url=url):
                self.assertTrue(worker.valid_base_url(url))
        for url in ("http://speaker.example", "http://localhost@speaker.example",
                    "https://speaker.example?token=secret", "file:///tmp"):
            with self.subTest(url=url):
                self.assertFalse(worker.valid_base_url(url))

    def test_download_checks_deadline_after_a_partial_read(self):
        response = self.response(b"partial")
        with tempfile.TemporaryDirectory() as folder, \
                patch.object(worker, "urlopen", return_value=response), \
                patch.object(response, "read", side_effect=AssertionError("Use a partial read")), \
                patch.object(worker.time, "monotonic", side_effect=[10, 71]):
            path = Path(folder) / "input"
            with self.assertRaises(TimeoutError):
                worker.download("http://localhost/audio", path)
            self.assertEqual(path.read_bytes(), b"")

    def test_private_docker_http_requires_explicit_opt_in(self):
        self.assertFalse(worker.valid_base_url("http://app:8080"))
        self.assertTrue(worker.valid_base_url("http://app:8080", allow_insecure_http=True))
        for url in ("http://user:password@app:8080", "http://app:8080?token=secret", "file:///tmp", "ftp://app"):
            with self.subTest(url=url):
                self.assertFalse(worker.valid_base_url(url, allow_insecure_http=True))

    def test_download_checks_bytes_even_without_content_length(self):
        with tempfile.TemporaryDirectory() as folder, \
                patch.object(worker, "urlopen", return_value=self.response(b"12345")), \
                patch.object(worker, "MAX_BYTES", 4):
            with self.assertRaises(worker.InvalidAudio):
                worker.download("http://localhost/audio", Path(folder) / "input")

    def test_download_rejects_an_incomplete_response(self):
        response = self.response(b"partial", {"Content-Length": "10"})
        with tempfile.TemporaryDirectory() as folder, \
                patch.object(worker, "urlopen", return_value=response):
            with self.assertRaisesRegex(OSError, "Incomplete audio download"):
                worker.download("http://localhost/audio", Path(folder) / "input")

    def test_one_shot_reports_an_unreachable_backend(self):
        with tempfile.TemporaryDirectory() as folder:
            model = Path(folder) / "model"
            model.touch()
            argv = ["worker.py", "--model", str(model), "--vad-model", str(model), "--once"]
            with patch.object(worker.sys, "argv", argv), \
                    patch.dict(worker.os.environ, {"AUDIO_ANALYSIS_WORKER_TOKEN": "test"}), \
                    patch.object(worker.shutil, "which", return_value="/tmp/tool"), \
                    patch.object(worker, "request", side_effect=OSError):
                with self.assertRaisesRegex(SystemExit, "Spring недоступен"):
                    worker.main()

    def test_readiness_requires_a_successful_authenticated_request(self):
        with tempfile.TemporaryDirectory() as folder:
            model = Path(folder) / "model"
            model.touch()
            health = Path(folder) / "health"
            argv = ["worker.py", "--model", str(model), "--vad-model", str(model), "--once",
                    "--base-url", "http://app:8080", "--allow-insecure-http", "--health-file", str(health)]
            with patch.object(worker.sys, "argv", argv), \
                    patch.dict(worker.os.environ, {"AUDIO_ANALYSIS_WORKER_TOKEN": "test"}), \
                    patch.object(worker.shutil, "which", return_value="/tmp/tool"):
                with patch.object(worker, "request", return_value=None):
                    worker.main()
                self.assertTrue(health.exists())
                with patch.object(worker, "request", side_effect=OSError):
                    with self.assertRaises(SystemExit):
                        worker.main()
                self.assertFalse(health.exists())


if __name__ == "__main__":
    unittest.main()
