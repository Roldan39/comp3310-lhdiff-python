"""
LHDiff V2 Evaluator
===================

This script evaluates the performance of LHDiff V2 against a dataset of test cases.
It supports both the original dataset format (files + XML) and the new format (files + JSON).

Usage:
    python evaluate_v2.py [data_dir]
"""
import os
import sys
import json
import argparse
import xml.etree.ElementTree as ET
from lhdiff_v2.input_controller import InputController
from lhdiff_v2.engine import LHEngine

# Default Config
DEFAULT_CONFIG = {
    "CONTENT_WEIGHT": 0.41,
    "CONTEXT_WEIGHT": 0.64,
    "PASS1_THRESHOLD": 0.49,
    "PASS2_THRESHOLD": 0.59
}

def parse_truth_json(json_path):
    """Parses the JSON Ground Truth."""
    truth_mapping = {}
    try:
        with open(json_path, 'r') as f:
            data = json.load(f)
            # "mappings": { "1": 1, ... }
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

def evaluate(data_dir, config=None):
    """
    Evaluates LHDiff accuracy on all test cases in the data directory.

    Args:
        data_dir (str): Path to the directory containing test cases.
        config (dict, optional): Configuration dictionary. Defaults to DEFAULT_CONFIG.
    """
    if config is None: config = DEFAULT_CONFIG
    
    print(f"{'Test Case':<40} | {'Accuracy':<10}")
    print("-" * 55)
    
    total_lines = 0
    total_correct = 0
    file_count = 0
    
    controller = InputController()

    for folder in sorted(os.listdir(data_dir)):
        folder_path = os.path.join(data_dir, folder)
        if not os.path.isdir(folder_path): continue
        
        files = os.listdir(folder_path)
        
        # Strategy 1: Original Format (_1.java, _2.java, .xml)
        old_f = next((f for f in files if "_1.java" in f), None)
        new_f = next((f for f in files if "_2.java" in f), None)
        xml_f = next((f for f in files if ".xml" in f), None)
        
        # Strategy 2: New Format (old.*, new.*, ground_truth.json)
        if not (old_f and new_f):
            # Find any file starting with "old." and "new."
            old_candidates = [f for f in files if f.startswith("old.") and f != "old.java"] # prioritize java if explicit check needed, but here we just want any
            new_candidates = [f for f in files if f.startswith("new.") and f != "new.java"]
            
            if "old.java" in files: old_f = "old.java"
            elif old_candidates: old_f = old_candidates[0]
            
            if "new.java" in files: new_f = "new.java"
            elif new_candidates: new_f = new_candidates[0]
        
        json_f = next((f for f in files if "ground_truth.json" in f), None)
        
        if not (old_f and new_f): continue
        if not (xml_f or json_f): continue

        try:
            # Parse
            nodes_a, nodes_b = controller.parse(
                os.path.join(folder_path, old_f),
                os.path.join(folder_path, new_f)
            )
            
            # Run Engine
            engine = LHEngine(nodes_a, nodes_b)
            mappings = engine.run(config)
            
            # Verify
            pred_dict = {m[0]: m[1][0] for m in mappings if m[1]}
            
            if json_f:
                truth_dict = parse_truth_json(os.path.join(folder_path, json_f))
            else:
                truth_dict = parse_truth_xml(os.path.join(folder_path, xml_f))
            
            case_lines = 0
            case_correct = 0
            
            for t_old, t_new in truth_dict.items():
                pred = pred_dict.get(t_old, -1)
                if str(pred) == str(t_new):
                    case_correct += 1
                case_lines += 1
            
            accuracy = (case_correct / case_lines * 100) if case_lines else 0
            print(f"{folder:<40} | {accuracy:.2f}%")
            
            total_lines += case_lines
            total_correct += case_correct
            file_count += 1
            
        except Exception as e:
            print(f"{folder:<40} | ERROR: {e}")

    global_acc = (total_correct / total_lines * 100) if total_lines else 0
    print("-" * 55)
    print(f"Overall Accuracy: {global_acc:.2f}% across {file_count} files.")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="LHDiff V2 Batch Evaluator")
    parser.add_argument("data_dir", nargs="?", default="data", help="Path to data directory (default: data)")
    args = parser.parse_args()
    
    if not os.path.exists(args.data_dir):
        print(f"Error: Directory '{args.data_dir}' not found.")
        sys.exit(1)
        
    evaluate(args.data_dir)
