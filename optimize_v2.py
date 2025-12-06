"""
LHDiff V2 Batch Optimizer
=========================

This script runs a Genetic Algorithm to find the optimal weights and thresholds
for LHDiff V2 across the entire dataset.

Usage:
    python optimize_v2.py [data_dir]
"""
import json
import sys
import os
import random
import argparse
import xml.etree.ElementTree as ET
from typing import List, Dict
from lhdiff_v2.input_controller import InputController
from lhdiff_v2.engine import LHEngine

# Optimization Constants
NUM_GENERATIONS = 5
SAMPLES_PER_GEN = 20
TOP_K_SURVIVORS = 5

DEFAULT_RANGES = {
    "CONTENT_WEIGHT": (0.4, 0.9),
    "PASS1_THRESHOLD": (0.5, 0.9),
    "PASS2_THRESHOLD": (0.3, 0.6)
}

def parse_truth_json(json_path):
    """Parses the JSON Ground Truth."""
    truth_mapping = {}
    try:
        with open(json_path, 'r') as f:
            data = json.load(f)
            for old_line, new_line in data.get("mappings", {}).items():
                truth_mapping[int(old_line)] = int(new_line)
    except Exception as e:
        print(f"Error parsing JSON {json_path}: {e}")
    return truth_mapping

def parse_truth_xml(xml_path):
    """Parses the XML Ground Truth."""
    truth_mapping = {}
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        version = root.find(".//VERSION[@NUMBER='2']")
        if version is None: version = root.find(".//VERSION")
        if version is not None:
            for loc in version.findall("LOCATION"):
                orig = loc.get("ORIG")
                new = loc.get("NEW")
                if orig and new and int(new) != -1:
                    truth_mapping[int(orig)] = int(new)
    except:
        pass
    return truth_mapping

class BatchOptimizer:
    """
    Manages the optimization process across multiple test cases.
    """
    def __init__(self, data_dir, use_full_dataset=False):
        """
        Args:
            data_dir (str): Directory containing test cases.
            use_full_dataset (bool): If True, disables sampling.
        """
        self.data_dir = data_dir
        self.use_full_dataset = use_full_dataset
        self.engines = [] # List of (engine, truth_mapping)
        self.ranges = DEFAULT_RANGES.copy()
        
        self._load_data()

    def _load_data(self):
        """Loads all valid test cases and pre-builds their engines."""
        print("Loading data and building matrices...", file=sys.stderr)
        controller = InputController()
        
        # 1. Get all potential folders
        all_folders = [f for f in sorted(os.listdir(self.data_dir)) 
                       if os.path.isdir(os.path.join(self.data_dir, f))]
        
        # 2. STATISTICAL SAMPLING (Pruning the Dataset)
        # If we have too many files, just pick 10 random ones to be our "Sample Population"
        # This reduces complexity from O(All_Files) to O(10)
        SAMPLE_SIZE = 10 
        if not self.use_full_dataset and len(all_folders) > SAMPLE_SIZE:
             # Randomly select, or sort by size and pick the smallest to avoid crashes
            import random
            selected_folders = random.sample(all_folders, SAMPLE_SIZE)
        else:
            selected_folders = all_folders

        for folder in selected_folders:
            folder_path = os.path.join(self.data_dir, folder)
            
            files = os.listdir(folder_path)
            
            # Strategy 1: Original Format
            old_f = next((f for f in files if "_1.java" in f), None)
            new_f = next((f for f in files if "_2.java" in f), None)
            xml_f = next((f for f in files if ".xml" in f), None)
            
            # Strategy 2: New Format
            if not (old_f and new_f):
                old_candidates = [f for f in files if f.startswith("old.") and f != "old.java"]
                new_candidates = [f for f in files if f.startswith("new.") and f != "new.java"]
                
                if "old.java" in files: old_f = "old.java"
                elif old_candidates: old_f = old_candidates[0]
                
                if "new.java" in files: new_f = "new.java"
                elif new_candidates: new_f = new_candidates[0]
            
            json_f = next((f for f in files if "ground_truth.json" in f), None)
            
            if not (old_f and new_f): continue
            if not (xml_f or json_f): continue

            # SAFETY CHECK: Skip massive files before parsing
            # If a file is > 2000 lines, skip it to save your MacBook
            if self._is_file_too_large(os.path.join(folder_path, old_f)):
                print(f"Skipping {folder} (File too large for optimization)", file=sys.stderr)
                continue

            try:
                nodes_a, nodes_b = controller.parse(
                    os.path.join(folder_path, old_f),
                    os.path.join(folder_path, new_f)
                )
                
                if json_f:
                    truth = parse_truth_json(os.path.join(folder_path, json_f))
                else:
                    truth = parse_truth_xml(os.path.join(folder_path, xml_f))
                    
                if truth:
                    engine = LHEngine(nodes_a, nodes_b)
                    self.engines.append((engine, truth))
            except Exception:
                pass
        print(f"Loaded {len(self.engines)} test cases.", file=sys.stderr)

    def _is_file_too_large(self, filepath, limit=2000):
        try:
            with open(filepath, 'r') as f:
                for i, _ in enumerate(f):
                    if i > limit: return True
            return False
        except:
            return True

    def optimize(self):
        """
        Runs the genetic algorithm to find the best global configuration.
        """
        print(f"Starting Optimization ({NUM_GENERATIONS} gens, {SAMPLES_PER_GEN} samples/gen)...")
        best_overall_score = 0
        best_overall_config = {}
        
        for gen in range(NUM_GENERATIONS):
            print(f"\n--- GENERATION {gen + 1} ---")
            gen_results = []
            
            for i in range(SAMPLES_PER_GEN):
                config = self._get_random_config(self.ranges)
                score = self._evaluate_batch(config)
                gen_results.append((score, config))
                
                if score > best_overall_score:
                    best_overall_score = score
                    best_overall_config = config
                    print(f"  New Best: {score:.2f}% | {config}")
            
            # Sort and narrow
            gen_results.sort(key=lambda x: x[0], reverse=True)
            survivors = [x[1] for x in gen_results[:TOP_K_SURVIVORS]]
            
            if gen < NUM_GENERATIONS - 1:
                self.ranges = self._narrow_ranges(survivors, self.ranges)
                
        print("\n" + "=" * 50)
        print("OPTIMIZATION COMPLETE")
        print(f"Highest Accuracy: {best_overall_score:.2f}%")
        print("Best Configuration:")
        print(best_overall_config)
        print("=" * 50)

    def _evaluate_batch(self, config):
        total_correct = 0
        total_lines = 0
        
        for engine, truth in self.engines:
            mappings = engine.run(config)
            pred_dict = {m[0]: m[1][0] for m in mappings if m[1]}
            
            for t_old, t_new in truth.items():
                if pred_dict.get(t_old) == t_new:
                    total_correct += 1
                total_lines += 1
                
        return (total_correct / total_lines * 100) if total_lines else 0

    def _get_random_config(self, ranges):
        cfg = {}
        cfg["CONTENT_WEIGHT"] = round(random.uniform(*ranges["CONTENT_WEIGHT"]), 2)
        cfg["PASS1_THRESHOLD"] = round(random.uniform(*ranges["PASS1_THRESHOLD"]), 2)
        cfg["PASS2_THRESHOLD"] = round(random.uniform(*ranges["PASS2_THRESHOLD"]), 2)
        cfg["CONTEXT_WEIGHT"] = round(1.0 - cfg["CONTENT_WEIGHT"], 2)
        return cfg

    def _narrow_ranges(self, top_configs, current_ranges):
        new_ranges = current_ranges.copy()
        def narrow(key):
            values = [c[key] for c in top_configs]
            v_min = min(values)
            v_max = max(values)
            padding = (v_max - v_min) * 0.2 if v_max != v_min else 0.05
            return (max(0.0, v_min - padding), min(1.0, v_max + padding))

        new_ranges["CONTENT_WEIGHT"] = narrow("CONTENT_WEIGHT")
        new_ranges["PASS1_THRESHOLD"] = narrow("PASS1_THRESHOLD")
        new_ranges["PASS2_THRESHOLD"] = narrow("PASS2_THRESHOLD")
        return new_ranges

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("data_dir", nargs="?", default="data")
    parser.add_argument("--full", action="store_true", help="Disable sampling and use invalid files check")
    args = parser.parse_args()
    
    optimizer = BatchOptimizer(args.data_dir, use_full_dataset=args.full)
    optimizer.optimize()
