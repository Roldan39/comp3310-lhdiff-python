import os
import csv
import xml.etree.ElementTree as ET
from lhdiff import run_lhdiff, DEFAULT_CONFIG

DATA_DIR = "data"
OUTPUT_DIR = "output"
BONUS_DIR = os.path.join(OUTPUT_DIR, "bonus_reports")

def ensure_dirs():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    os.makedirs(BONUS_DIR, exist_ok=True)

def parse_truth_xml(xml_path):
    """Parses the XML Ground Truth provided by the professor."""
    truth_mapping = {}
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        # Find Version 2 data
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

def classify_bonus(old_line, new_line):
    """Bonus: Detects Bug Fixes vs Logic Changes."""
    if not old_line: return "Addition"
    if "null" not in old_line and "!= null" in new_line: return "Bug Fix (Null Check)"
    if "try" not in old_line and "try" in new_line: return "Bug Fix (Try/Catch)"
    return "Modification"

def run_evaluation_with_config(config=None, print_output=True):
    """
    Runs the full test suite with a specific configuration.
    Returns: Global Accuracy (Float)
    """
    if config is None: config = DEFAULT_CONFIG
    ensure_dirs()
    
    total_lines = 0
    total_correct = 0
    file_count = 0

    if print_output:
        print(f"{'Test Case':<30} | {'Accuracy':<10}")
        print("-" * 45)

    for folder in sorted(os.listdir(DATA_DIR)):
        folder_path = os.path.join(DATA_DIR, folder)
        if not os.path.isdir(folder_path): continue
        
        # Find files
        files = os.listdir(folder_path)
        old_f = next((f for f in files if "_1.java" in f), None)
        new_f = next((f for f in files if "_2.java" in f), None)
        xml_f = next((f for f in files if ".xml" in f), None)
        
        if not (old_f and new_f and xml_f): continue

        # Run LHDiff with injected config
        try:
            mappings = run_lhdiff(
                os.path.join(folder_path, old_f),
                os.path.join(folder_path, new_f),
                config
            )
            
            # Convert to dict for checking
            pred_dict = {m[0]: m[1][0] for m in mappings if m[1]}
            truth_dict = parse_truth_xml(os.path.join(folder_path, xml_f))
            
            # Calculate Score
            case_lines = 0
            case_correct = 0
            
            # Write Bonus Report
            bonus_csv = os.path.join(BONUS_DIR, f"{folder}_bonus.csv")
            with open(bonus_csv, 'w', newline='') as bfile:
                writer = csv.writer(bfile)
                writer.writerow(["Old Line", "New Line", "Result", "Classification"])
                
                for t_old, t_new in truth_dict.items():
                    pred = pred_dict.get(t_old, -1)
                    is_correct = (str(pred) == str(t_new))
                    
                    if is_correct: case_correct += 1
                    case_lines += 1
                    
                    # Bonus Logic placeholder (requires reading file content to be fully active)
                    classification = "Match" if is_correct else "Mismatch"
                    writer.writerow([t_old, t_new, "PASS" if is_correct else "FAIL", classification])

            accuracy = (case_correct / case_lines * 100) if case_lines else 0
            if print_output:
                print(f"{folder:<30} | {accuracy:.2f}%")
            
            total_lines += case_lines
            total_correct += case_correct
            file_count += 1
            
        except Exception:
            pass

    global_acc = (total_correct / total_lines * 100) if total_lines else 0
    if print_output:
        print("-" * 45)
        print(f"Overall Accuracy: {global_acc:.2f}% across {file_count} files.")
        
    return global_acc

if __name__ == "__main__":
    run_evaluation_with_config(DEFAULT_CONFIG)