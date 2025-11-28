import difflib
import math
import argparse
import sys
from collections import Counter

# ==========================================
# CONFIGURATION (The "Tuning Knobs")
# ==========================================
# Team: Adjust these values to improve accuracy!
CONTENT_WEIGHT = 0.6   # How much the line text matters (Slide 288 says 0.6)
CONTEXT_WEIGHT = 0.4   # How much the neighbors matter (Slide 288 says 0.4)
MATCH_THRESHOLD = 0.40 # Minimum score required to declare a match
WINDOW_SIZE = 4        # How many lines above/below to look at for context
# ==========================================
# CORE LOGIC (The "Engine")
# ==========================================

def preprocess_file(filepath):
    """
    Step 1: Preprocessing
    Reads a file, strips whitespace, and converts to lowercase.
    """
    clean_lines = []
    # robust encoding handling for various file types
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as file:
            for line in file:
                clean_lines.append(line.strip().lower())
    except FileNotFoundError:
        print(f"Error: File not found {filepath}")
        return []
    return clean_lines

def get_context_string(lines, index, window=4):
    """ Helper: Grabs 4 lines before and after for Context Similarity. """
    start = max(0, index - window)
    end = min(len(lines), index + window + 1)
    return " ".join(lines[start:end])

def levenshtein_distance(s1, s2):
    """ Step 3a: Content Similarity Math """
    if len(s1) < len(s2): return levenshtein_distance(s2, s1)
    if len(s2) == 0: return len(s1)
    previous_row = range(len(s2) + 1)
    for i, c1 in enumerate(s1):
        current_row = [i + 1]
        for j, c2 in enumerate(s2):
            insertions = previous_row[j + 1] + 1
            deletions = current_row[j] + 1
            substitutions = previous_row[j] + (c1 != c2)
            current_row.append(min(insertions, deletions, substitutions))
        previous_row = current_row
    return previous_row[-1]

def get_content_similarity(s1, s2):
    """ Returns 0.0 to 1.0 based on Levenshtein Distance """
    dist = levenshtein_distance(s1, s2)
    max_len = max(len(s1), len(s2))
    if max_len == 0: return 1.0
    return 1 - (dist / max_len)

def get_cosine_similarity(text1, text2):
    """ Step 3b: Context Similarity Math (Bag of Words) """
    vec1 = Counter(text1.split())
    vec2 = Counter(text2.split())
    intersection = set(vec1.keys()) & set(vec2.keys())
    numerator = sum([vec1[x] * vec2[x] for x in intersection])
    sum1 = sum([vec1[x]**2 for x in vec1.keys()])
    sum2 = sum([vec2[x]**2 for x in vec2.keys()])
    denominator = math.sqrt(sum1) * math.sqrt(sum2)
    return 0.0 if denominator == 0 else numerator / denominator

def check_line_splits(l_text, r_idx, right_list_data):
    """
    Step 5: Detect Line Splits (Bi-Directional)
    Checks if merging the best match with its neighbor (Next or Previous)
    improves the Levenshtein score.
    """
    current_match_text = ""
    current_list_pos = -1
    
    # Locate current match in the candidate list
    for i, (idx, text) in enumerate(right_list_data):
        if idx == r_idx:
            current_match_text = text
            current_list_pos = i
            break
            
    if current_list_pos == -1: return None, None

    dist_single = levenshtein_distance(l_text, current_match_text)
    
    # Check Forward Split (Merge with Next)
    if current_list_pos + 1 < len(right_list_data):
        next_idx, next_text = right_list_data[current_list_pos + 1]
        if next_idx == r_idx + 1:
            merged_fwd = current_match_text + " " + next_text
            dist_fwd = levenshtein_distance(l_text, merged_fwd)
            if dist_fwd < dist_single:
                return merged_fwd, next_idx

    # Check Backward Split (Merge with Previous)
    if current_list_pos > 0:
        prev_idx, prev_text = right_list_data[current_list_pos - 1]
        if prev_idx == r_idx - 1:
            merged_bwd = prev_text + " " + current_match_text
            dist_bwd = levenshtein_distance(l_text, merged_bwd)
            if dist_bwd < dist_single:
                return merged_bwd, prev_idx
                
    return None, None

def run_lhdiff(old_file_path, new_file_path):
    """
    The Main Controller.
    Takes two file paths, runs the algorithm, and returns a list of results.
    Return format: [(OldLineNum, [NewLineNum1, NewLineNum2...]), ...]
    """
    lines_a = preprocess_file(old_file_path)
    lines_b = preprocess_file(new_file_path)
    
    if not lines_a or not lines_b:
        return []

    # --- Step 2: Unix Diff (Anchors) ---
    matcher = difflib.SequenceMatcher(None, lines_a, lines_b)
    results = []
    
    # Store anchors first
    for block in matcher.get_matching_blocks():
        start_a, start_b, length = block
        for i in range(length):
            # +1 because humans count from 1
            results.append((start_a + i + 1, [start_b + i + 1]))

    # --- Prepare for Step 4 ---
    # We need to isolate the lines that *didn't* match (the gaps)
    left_list = []
    right_list = []
    last_a = 0
    last_b = 0
    
    for match in matcher.get_matching_blocks():
        start_a, start_b, length = match
        for i in range(last_a, start_a):
            left_list.append((i, lines_a[i]))
        for i in range(last_b, start_b):
            right_list.append((i, lines_b[i]))
        last_a = start_a + length
        last_b = start_b + length

    # --- Step 4: Resolve Conflicts & Step 5: Splits ---
    used_new_lines = set()
    
    for l_idx, l_text in left_list:
        best_score = -1
        best_match_index = -1
        
        context_a = get_context_string(lines_a, l_idx)
        
        # Compare against all available lines in the gap
        for r_idx, r_text in right_list:
            if r_idx in used_new_lines: continue
            
            context_b = get_context_string(lines_b, r_idx)
            content_sim = get_content_similarity(l_text, r_text)
            context_sim = get_cosine_similarity(context_a, context_b)
            
            # Weighted Average Formula
            combined_score = (CONTENT_WEIGHT * content_sim) + (CONTEXT_WEIGHT * context_sim)
            
            if combined_score > best_score:
                best_score = combined_score
                best_match_index = r_idx
        
        # Threshold Check (Using Global Var)
        if best_score > MATCH_THRESHOLD:
            improved_match, neighbor_idx = check_line_splits(l_text, best_match_index, right_list)
            
            if improved_match:
                # Map to both lines (Split detected)
                idx1 = min(best_match_index, neighbor_idx) + 1
                idx2 = max(best_match_index, neighbor_idx) + 1
                results.append((l_idx + 1, [idx1, idx2]))
                used_new_lines.add(best_match_index)
                used_new_lines.add(neighbor_idx)
            else:
                # Map to single line
                results.append((l_idx + 1, [best_match_index + 1]))
                used_new_lines.add(best_match_index)

    # Sort results by Old Line Number for readability
    results.sort(key=lambda x: x[0])
    return results

# ==========================================
# COMMAND LINE INTERFACE (The "Driver")
# ==========================================
if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="LHDiff Tool")
    parser.add_argument("old_file", help="Path to Old Version")
    parser.add_argument("new_file", help="Path to New Version")
    args = parser.parse_args()

    # Run the engine
    mappings = run_lhdiff(args.old_file, args.new_file)
    
    # Print results to screen
    for old_line, new_lines in mappings:
        new_lines_str = ",".join(map(str, new_lines))
        print(f"{old_line} -> {new_lines_str}")