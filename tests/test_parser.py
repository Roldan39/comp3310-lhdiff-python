import unittest
import os
from lhdiff_v2.input_controller import InputController, RawFileParser, CombinedFileParser

class TestInputController(unittest.TestCase):
    def setUp(self):
        self.controller = InputController()
        # Create dummy files
        with open("test_a.txt", "w") as f: f.write("line 1\nline 2")
        with open("test_b.txt", "w") as f: f.write("line 1\nline 3")
        with open("test_combined.txt", "w") as f:
            f.write("--- OLD FILE ---\nold 1\n--- NEW FILE ---\nnew 1")

    def tearDown(self):
        if os.path.exists("test_a.txt"): os.remove("test_a.txt")
        if os.path.exists("test_b.txt"): os.remove("test_b.txt")
        if os.path.exists("test_combined.txt"): os.remove("test_combined.txt")

    def test_raw_parsing(self):
        nodes_a, nodes_b = self.controller.parse("test_a.txt", "test_b.txt")
        self.assertEqual(len(nodes_a), 2)
        self.assertEqual(len(nodes_b), 2)
        self.assertEqual(nodes_a[0].content, "line 1")

    def test_combined_parsing(self):
        nodes_a, nodes_b = self.controller.parse("test_combined.txt")
        self.assertEqual(len(nodes_a), 1)
        self.assertEqual(len(nodes_b), 1)
        self.assertEqual(nodes_a[0].content, "old 1")

if __name__ == '__main__':
    unittest.main()
