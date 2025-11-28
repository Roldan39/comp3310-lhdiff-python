import difflib
import math
import argparse
import sys
import re
from collections import Counter

# ==========================================
# CONFIGURATION
# ==========================================
CONTENT_WEIGHT = 0.6   
CONTEXT_WEIGHT = 0.4   
WINDOW_SIZE = 4        

# PASS 1: High confidence to lock in "Anchors" (Unique lines)
PASS1_THRESHOLD = 0.70 

# PASS 2: Lower confidence to fill in the "Gaps" (Common lines)
PASS2_THRESHOLD = 0.40

# ==========================================
# CORE LOGIC
# ==========================================

def preprocess_file(filepath):
    """
    Step 1: Preprocessing with Smarter Tokenization
    """
    clean_lines = []
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as file:
            for line in file:
                line = line.strip().lower()
                # Split symbols but keep them as tokens
                # "array[i]" -> "array [ i ]"
                line = re.sub(r'([^\w\s])', r' \1 ', line)
                line = " ".join(line.split())
                clean_lines.append(line)
    except FileNotFoundError:
        print(f"Error: File not found {filepath}")
        return []
    return clean_lines

def get_context_string(lines, index, window=4):
    start = max(0, index - window)
    end = min(len(lines), index + window + 1)
    return " ".join(lines[start:end])

def levenshtein_distance(s1, s2):
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
    if not s1 and not s2: return 1.0
    if not s1 or not s2: return 0.0
    
    dist = levenshtein_distance(s1, s2)
    max_len = max(len(s1), len(s2))
    if max_len == 0: return 1.0
    return 1 - (dist / max_len)

def get_cosine_similarity(text1, text2):
    if not text1 or not text2: return 0.0
    vec1 = Counter(text1.split())
    vec2 = Counter(text2.split())
    intersection = set(vec1.keys()) & set(vec2.keys())
    numerator = sum([vec1[x] * vec2[x] for x in intersection])
    sum1 = sum([vec1[x]**2 for x in vec1.keys()])
    sum2 = sum([vec2[x]**2 for x in vec2.keys()])
    denominator = math.sqrt(sum1) * math.sqrt(sum2)
    return 0.0 if denominator == 0 else numerator / denominator

def check_line_splits(l_text, r_idx, right_list_data):
    """ Step 5a: Detect Line Splits (One Old -> Many New) """
    current_match_text = ""
    current_list_pos = -1
    
    for i, (idx, text) in enumerate(right_list_data):
        if idx == r_idx:
            current_match_text = text
            current_list_pos = i
            break
            
    if current_list_pos == -1: return None, None

    dist_single = levenshtein_distance(l_text, current_match_text)
    
    # Forward Split
    if current_list_pos + 1 < len(right_list_data):
        next_idx, next_text = right_list_data[current_list_pos + 1]
        if next_idx == r_idx + 1:
            merged_fwd = current_match_text + " " + next_text
            dist_fwd = levenshtein_distance(l_text, merged_fwd)
            if dist_fwd < dist_single:
                return merged_fwd, next_idx

    # Backward Split
    if current_list_pos > 0:
        prev_idx, prev_text = right_list_data[current_list_pos - 1]
        if prev_idx == r_idx - 1:
            merged_bwd = prev_text + " " + current_match_text
            dist_bwd = levenshtein_distance(l_text, merged_bwd)
            if dist_bwd < dist_single:
                return merged_bwd, prev_idx
                
    return None, None

def check_line_merges(l_idx, l_text, r_text, lines_a):
    """ 
    Step 5b: Detect Line Merges (Many Old -> One New) 
    Checks if combining the current Old line with the Next Old line 
    matches the New line better.
    """
    # Look ahead in the OLD file
    if l_idx + 1 < len(lines_a):
        next_l_text = lines_a[l_idx + 1]
        merged_text = l_text + " " + next_l_text
        
        dist_single = levenshtein_distance(l_text, r_text)
        dist_merged = levenshtein_distance(merged_text, r_text)
        
        # If the merged version is closer to the New Line than the single version
        if dist_merged < dist_single:
            return True, l_idx + 1
            
    return False, -1

def run_lhdiff(old_file_path, new_file_path):
    lines_a = preprocess_file(old_file_path)
    lines_b = preprocess_file(new_file_path)
    
    if not lines_a or not lines_b: return []

    matcher = difflib.SequenceMatcher(None, lines_a, lines_b)
    results = []
    
    used_new_lines = set()
    used_old_lines = set()

    # 1. Exact Matches (Anchors)
    for block in matcher.get_matching_blocks():
        start_a, start_b, length = block
        for i in range(length):
            old_idx = start_a + i + 1
            new_idx = start_b + i + 1
            results.append((old_idx, [new_idx]))
            used_old_lines.add(old_idx - 1)
            used_new_lines.add(new_idx - 1)

    left_list = [(i, lines_a[i]) for i in range(len(lines_a))]
    right_list = [(i, lines_b[i]) for i in range(len(lines_b))]

    # --- TWO-PASS MATCHING ---
    def run_pass(threshold):
        for l_idx, l_text in left_list:
            if l_idx in used_old_lines: continue
            
            best_score = -1
            best_match_index = -1
            best_match_text = ""
            
            context_a = get_context_string(lines_a, l_idx)
            
            for r_idx, r_text in right_list:
                if r_idx in used_new_lines: continue
                
                context_b = get_context_string(lines_b, r_idx)
                content_sim = get_content_similarity(l_text, r_text)
                context_sim = get_cosine_similarity(context_a, context_b)
                
                combined_score = (CONTENT_WEIGHT * content_sim) + (CONTEXT_WEIGHT * context_sim)
                
                if combined_score > best_score:
                    best_score = combined_score
                    best_match_index = r_idx
                    best_match_text = r_text
            
            if best_score > threshold:
                # 1. Check for SPLITS (One Old -> Two New)
                improved_match_split, neighbor_idx = check_line_splits(l_text, best_match_index, right_list)
                
                # 2. Check for MERGES (Two Old -> One New)
                is_merge, merge_neighbor_idx = check_line_merges(l_idx, l_text, best_match_text, lines_a)

                if improved_match_split and neighbor_idx not in used_new_lines:
                     # It's a SPLIT
                     idx1 = min(best_match_index, neighbor_idx) + 1
                     idx2 = max(best_match_index, neighbor_idx) + 1
                     results.append((l_idx + 1, [idx1, idx2]))
                     used_new_lines.add(best_match_index)
                     used_new_lines.add(neighbor_idx)
                     used_old_lines.add(l_idx)
                
                elif is_merge and merge_neighbor_idx not in used_old_lines:
                     # It's a MERGE
                     # Map CURRENT Old Line
                     results.append((l_idx + 1, [best_match_index + 1]))
                     # Map NEXT Old Line to same New Line
                     results.append((merge_neighbor_idx + 1, [best_match_index + 1]))
                     
                     used_new_lines.add(best_match_index)
                     used_old_lines.add(l_idx)
                     used_old_lines.add(merge_neighbor_idx)

                else:
                     # Standard 1-to-1 Match
                     results.append((l_idx + 1, [best_match_index + 1]))
                     used_new_lines.add(best_match_index)
                     used_old_lines.add(l_idx)

    # Pass 1: Hawk (High confidence)
    run_pass(PASS1_THRESHOLD)

    # Pass 2: Mouse (Fill gaps)
    run_pass(PASS2_THRESHOLD)

    results.sort(key=lambda x: x[0])
    return results

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="LHDiff Tool")
    parser.add_argument("old_file", help="Path to Old Version")
    parser.add_argument("new_file", help="Path to New Version")
    args = parser.parse_args()

    mappings = run_lhdiff(args.old_file, args.new_file)
    for old_line, new_lines in mappings:
        new_lines_str = ",".join(map(str, new_lines))
        print(f"{old_line} -> {new_lines_str}")