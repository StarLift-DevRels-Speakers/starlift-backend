import unittest

import analyze


class AnalyzeTest(unittest.TestCase):
    def test_russian_word_fragments_keep_the_original_word(self):
        entries = [
            {"text": " По", "offsets": {"from": 0, "to": 100}},
            {"text": "-моему,", "offsets": {"from": 100, "to": 300}},
            {"text": " как", "offsets": {"from": 350, "to": 450}},
            {"text": " бы.", "offsets": {"from": 450, "to": 900}},
        ]
        segments = analyze.transcript_segments(entries, 800)
        self.assertEqual(segments[0]["text"], "По-моему, как бы.")
        self.assertEqual([word["text"] for word in segments[0]["words"]], ["По-моему", "как", "бы"])
        result = analyze.analyze(segments, [[0, 800]], 700, 800)
        candidate = result["candidates"][0]
        self.assertEqual((candidate["startMs"], candidate["endMs"]), (350, 800))
        summary = result["candidateSummary"][0]
        self.assertEqual(summary["count"], 1)
        self.assertEqual(summary["occurrencesPer100Words"], 33.33)

    def test_partial_final_window_uses_its_real_duration(self):
        segment = {"id": 1, "text": "раз два", "startMs": 0, "endMs": 15000, "words": [
            {"text": "раз", "startMs": 1000, "endMs": 2000, "timingSource": "whisper"},
            {"text": "два", "startMs": 12000, "endMs": 14000, "timingSource": "whisper"},
        ]}
        result = analyze.tempo_series([segment], 15000)
        self.assertEqual([point["wordsPerMinute"] for point in result], [6.0, 12.0])
        self.assertEqual(sum(point["wordCount"] for point in result), 2)

    def test_legacy_segments_explicitly_use_estimated_tempo(self):
        segment = {"id": 1, "text": "раз два", "startMs": 0, "endMs": 15000}
        result = analyze.analyze([segment], [[0, 15000]], 700, 15000)
        self.assertEqual(result["wordCount"], 2)
        self.assertTrue(all(point["timingSource"] == "estimated" for point in result["tempo"]))

    def test_multiword_asr_entry_has_estimated_word_timing(self):
        segments = analyze.transcript_segments([
            {"text": "ну вот", "offsets": {"from": 100, "to": 800}},
        ], 1000)
        self.assertTrue(all(word["timingSource"] == "estimated" for word in segments[0]["words"]))

    def test_silence_has_no_tempo_or_candidate_rates(self):
        result = analyze.analyze([], [], 700, 30000)
        self.assertEqual(result["tempo"], [])
        self.assertIsNone(result["wordsPerMinute"])
        self.assertTrue(all(item["occurrencesPer100Words"] is None for item in result["candidateSummary"]))


if __name__ == "__main__":
    unittest.main()
