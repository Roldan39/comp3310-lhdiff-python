import os
import csv
import xml.etree.ElementTree as ET
from lhdiff import run_lhdiff

def parse_truth_xml(xml_path):
    """
    Reads the XML file provided by the professor.
    Extracts the 'Correct' mapping: { OldLine : NewLine }
    """
    truth_mapping = {}
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        
        # Look for Version 2 (New File) mappings
        # Note: XML format might vary, checking for "VERSION" tag
        version = root.find(".//VERSION[@NUMBER='2']")
        if version is None:
            version = root.find(".//VERSION")
            
        if version is not None:
            for loc in version.findall("LOCATION"):
                orig = loc.get("ORIG")
                new = loc.get("NEW")
                if orig and new:
                    # XML uses -1 for "deleted"
                    if int(new) != -1:
                        truth_mapping[int(orig)] = int(new)
    except Exception as e:
        print(f"Warning: Could not parse XML {xml_path}: {e}")
        
    return truth_mapping

def evaluate_all():
    data_dir = "data"
    output_dir = "output"
    os.makedirs(output_dir, exist_ok=True)
    
    # Summary stats
    total_files = 0
    total_lines = 0
    total_correct = 0
    
    # Loop through every folder in 'data'
    for folder in sorted(os.listdir(data_dir)):
        folder_path = os.path.join(data_dir, folder)
        if not os.path.isdir(folder_path): continue
        
        print(f"Processing Test Case: {folder}...")
        
        # Identify files
        files = os.listdir(folder_path)
        old_file = next((f for f in files if "_1.java" in f), None)
        new_file = next((f for f in files if "_2.java" in f), None)
        xml_file = next((f for f in files if ".xml" in f), None)
        
        if not (old_file and new_file and xml_file):
            print(f"  -> Skipping {folder} (Missing files)")
            continue
            
        # Run LHDiff
        try:
            old_path = os.path.join(folder_path, old_file)
            new_path = os.path.join(folder_path, new_file)
            predicted_mappings = run_lhdiff(old_path, new_path) # Returns list of tuples
            
            # Convert our list [(1, [2]), (2, [3,4])] to a dictionary for easy checking
            # For this evaluation, we take the first mapped line if split
            pred_dict = {}
            for p_old, p_new_list in predicted_mappings:
                if p_new_list:
                    pred_dict[p_old] = p_new_list[0] 
            
            # Load Truth
            truth_dict = parse_truth_xml(os.path.join(folder_path, xml_file))
            
            # Compare and Write Report
            csv_path = os.path.join(output_dir, f"{folder}_report.csv")
            with open(csv_path, 'w', newline='') as csvfile:
                writer = csv.writer(csvfile)
                writer.writerow(["Old Line", "Actual New", "Predicted New", "Result"])
                
                case_lines = 0
                case_correct = 0
                
                # Check every line in the Truth XML
                for t_old, t_new in sorted(truth_dict.items()):
                    prediction = pred_dict.get(t_old, "Deleted")
                    
                    is_correct = (str(prediction) == str(t_new))
                    result = "PASS" if is_correct else "FAIL"
                    
                    writer.writerow([t_old, t_new, prediction, result])
                    
                    case_lines += 1
                    if is_correct: case_correct += 1
            
            accuracy = (case_correct / case_lines * 100) if case_lines > 0 else 0
            print(f"  -> Accuracy: {accuracy:.2f}% ({case_correct}/{case_lines})")
            
            total_files += 1
            total_lines += case_lines
            total_correct += case_correct
            
        except Exception as e:
            print(f"  -> Error running test: {e}")

    # Final Summary
    print("-" * 30)
    print(f"Overall Accuracy: {(total_correct/total_lines*100):.2f}% across {total_files} files.")

if __name__ == "__main__":
    evaluate_all()