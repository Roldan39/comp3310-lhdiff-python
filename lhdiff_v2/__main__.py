"""
LHDiff V2 Entry Point
=====================

This module serves as the command-line interface for the LHDiff V2 tool.
It orchestrates the input parsing, engine initialization, optional calibration,
and execution of the diff algorithm.

Usage:
    python -m lhdiff_v2.main <source_a> [source_b] [--calibrate]
"""
import argparse
import sys
import os
import xml.etree.ElementTree as ET
from .input_controller import InputController
from .engine import LHEngine
from .optimizer import GeneticOptimizer

# Default Configuration
DEFAULT_CONFIG = {
    "CONTENT_WEIGHT": 0.76,
    "CONTEXT_WEIGHT": 0.24,
    "PASS1_THRESHOLD": 0.66,
    "PASS2_THRESHOLD": 0.42
}

def parse_truth_xml(xml_path):
    """
    Parses the XML Ground Truth if available.

    Args:
        xml_path (str): Path to the XML file containing ground truth.

    Returns:
        dict: A mapping of {old_line_num: new_line_num}.
    """
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

def main():
    """
    Main execution function.
    
    1. Parses command line arguments.
    2. Reads and parses input files.
    3. Initializes the LHEngine.
    4. Optionally runs auto-calibration if ground truth is found.
    5. Runs the diff algorithm.
    6. Prints the results to stdout.
    """
    parser = argparse.ArgumentParser(description="LHDiff V2: Modular Monolith Edition")
    parser.add_argument("source_a", help="First file or single combined/xml file")
    parser.add_argument("source_b", nargs="?", help="Second file (optional)")
    parser.add_argument("--calibrate", action="store_true", help="Run auto-calibration if ground truth is available")
    args = parser.parse_args()

    # 1. Parse Input
    controller = InputController()
    nodes_a, nodes_b = controller.parse(args.source_a, args.source_b)
    
    if not nodes_a and not nodes_b:
        print("Error: No input data found.")
        sys.exit(1)

    # 2. Initialize Engine (Builds Matrix)
    engine = LHEngine(nodes_a, nodes_b)
    
    # 3. Auto-Calibration (Optional)
    config = DEFAULT_CONFIG
    
    if args.calibrate:
        print("Auto-Calibrating...", file=sys.stderr)
        truth = {}
        
        # Case A: Input is XML (might contain truth)
        if args.source_a.endswith('.xml'):
            truth = parse_truth_xml(args.source_a)
            
        # Case B: Input is Java files (look for XML in same dir)
        elif args.source_a and args.source_b:
            dir_path = os.path.dirname(os.path.abspath(args.source_a))
            # Find first XML in dir
            for f in os.listdir(dir_path):
                if f.endswith('.xml'):
                    truth = parse_truth_xml(os.path.join(dir_path, f))
                    break
        
        if truth:
            optimizer = GeneticOptimizer(engine, truth)
            config = optimizer.optimize()
            print(f"Calibrated Config: {config}", file=sys.stderr)
        else:
            print("Warning: No ground truth found for calibration.", file=sys.stderr)

    # 4. Run Diff
    mappings = engine.run(config)
    
    # 5. Output Results
    for old, new_list in mappings:
        print(f"{old} -> {','.join(map(str, new_list))}")

if __name__ == "__main__":
    main()
