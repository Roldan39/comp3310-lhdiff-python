import unittest
import subprocess
import os
import sys

class TestAcceptance(unittest.TestCase):
    """
    Acceptance Tests: Verify the application from the user's perspective (CLI).
    """
    
    def test_cli_help(self):
        """Test that --help runs without error."""
        result = subprocess.run(
            [sys.executable, "-m", "lhdiff_v2", "--help"],
            capture_output=True,
            text=True
        )
        self.assertEqual(result.returncode, 0)
        self.assertIn("LHDiff V2", result.stdout)

    def test_cli_basic_flow(self):
        """Test running on a real file pair (if available)."""
        # Use a small dummy file pair for speed
        with open("acc_test_a.txt", "w") as f: f.write("line 1\nline 2")
        with open("acc_test_b.txt", "w") as f: f.write("line 1\nline 3")
        
        try:
            result = subprocess.run(
                [sys.executable, "-m", "lhdiff_v2", "acc_test_a.txt", "acc_test_b.txt"],
                capture_output=True,
                text=True
            )
            self.assertEqual(result.returncode, 0)
            # Expect 1->1 match
            self.assertIn("1 -> 1", result.stdout)
        finally:
            if os.path.exists("acc_test_a.txt"): os.remove("acc_test_a.txt")
            if os.path.exists("acc_test_b.txt"): os.remove("acc_test_b.txt")

    def test_cli_missing_file(self):
        """Test error handling for missing files."""
        result = subprocess.run(
            [sys.executable, "-m", "lhdiff_v2", "non_existent_file.txt"],
            capture_output=True,
            text=True
        )
        # Should probably exit with 0 but print error, or exit 1. 
        # Current implementation prints warning and returns empty list, so exit 0.
        # But let's check stdout for warning.
        self.assertIn("Warning: File not found", result.stdout)

if __name__ == '__main__':
    unittest.main()
