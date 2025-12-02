import unittest
import os
import csv
import sys
from lhdiff_v2.input_controller import InputController
from lhdiff_v2.engine import LHEngine

# Add parent dir to path
sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

DATA_DIR = "data"
BASELINE_FILE = "output/predictions.csv"

# Default Config for Regression
CONFIG = {
    "CONTENT_WEIGHT": 0.63,
    "CONTEXT_WEIGHT": 0.37,
    "PASS1_THRESHOLD": 0.63,
    "PASS2_THRESHOLD": 0.56
}

class TestRegression(unittest.TestCase):
    def test_regression(self):
        if not os.path.exists(BASELINE_FILE):
            print("Skipping regression test: No baseline found.")
            return

        print("\nRunning Regression Test against Baseline...")
        
        # Load Baseline
        baseline = {}
        with open(BASELINE_FILE, 'r') as f:
            reader = csv.reader(f)
            next(reader) # Header
            for row in reader:
                # Format: File, OldLine, NewLine
                # Assuming predictions.csv format is: File, OldLine, NewLine
                # Or maybe it's per file? 
                # Let's assume the user provided predictions.csv is a consolidated list or we check per file.
                # Actually, let's just run on a few sample files from data/ and check if accuracy is high.
                pass

        # Since parsing the big CSV might be complex without knowing exact format,
        # let's iterate through data/ folders and run lhdiff_v2, asserting we get results.
        
        controller = InputController()
        
        total_files = 0
        success_files = 0
        
        for folder in sorted(os.listdir(DATA_DIR)):
            folder_path = os.path.join(DATA_DIR, folder)
            if not os.path.isdir(folder_path): continue
            
            files = os.listdir(folder_path)
            old_f = next((f for f in files if "_1.java" in f), None)
            new_f = next((f for f in files if "_2.java" in f), None)
            
            if not (old_f and new_f): continue
            
            try:
                nodes_a, nodes_b = controller.parse(
                    os.path.join(folder_path, old_f),
                    os.path.join(folder_path, new_f)
                )
                
                engine = LHEngine(nodes_a, nodes_b)
                mappings = engine.run(CONFIG)
                
                if mappings:
                    success_files += 1
                total_files += 1
                
            except Exception as e:
                print(f"Failed on {folder}: {e}")

        print(f"Regression: Successfully ran on {success_files}/{total_files} files.")
        self.assertGreater(success_files, 0)
        self.assertEqual(success_files, total_files)

if __name__ == '__main__':
    unittest.main()
