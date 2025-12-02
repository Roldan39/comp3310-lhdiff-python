import unittest
from lhdiff_v2.utils import SimilarityCalculator
from lhdiff_v2.engine import LHEngine
from lhdiff_v2.models import LineNode

class TestUtils(unittest.TestCase):
    def test_levenshtein(self):
        self.assertAlmostEqual(SimilarityCalculator.levenshtein_similarity("abc", "abc"), 1.0)
        self.assertAlmostEqual(SimilarityCalculator.levenshtein_similarity("abc", "def"), 0.0)
        self.assertAlmostEqual(SimilarityCalculator.levenshtein_similarity("kitten", "sitting"), 0.57, places=2)

    def test_simhash(self):
        h1 = SimilarityCalculator.get_simhash("int x = 0")
        h2 = SimilarityCalculator.get_simhash("int x = 0")
        self.assertEqual(h1, h2)
        
        h3 = SimilarityCalculator.get_simhash("int y = 1")
        sim = SimilarityCalculator.get_hamming_similarity(h1, h3)
        self.assertTrue(0.0 <= sim <= 1.0)

class TestEngine(unittest.TestCase):
    def setUp(self):
        self.nodes_a = [
            LineNode(1, "line one", ["line", "one"], 123),
            LineNode(2, "line two", ["line", "two"], 456)
        ]
        self.nodes_b = [
            LineNode(1, "line one", ["line", "one"], 123),
            LineNode(2, "line three", ["line", "three"], 789)
        ]
        self.engine = LHEngine(self.nodes_a, self.nodes_b)

    def test_matrix_build(self):
        # Anchor check: line 1 should be an anchor
        self.assertIn((0, 0), self.engine.anchors)
        
        # Matrix size check
        self.assertEqual(len(self.engine.matrix), 2)
        self.assertEqual(len(self.engine.matrix[0]), 2)

    def test_run_basic(self):
        config = {
            "CONTENT_WEIGHT": 0.5,
            "CONTEXT_WEIGHT": 0.5,
            "PASS1_THRESHOLD": 0.5,
            "PASS2_THRESHOLD": 0.5
        }
        results = self.engine.run(config)
        # Should match 1->1 (anchor)
        self.assertEqual(results[0], (1, [1]))

if __name__ == '__main__':
    unittest.main()
