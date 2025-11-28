import difflib
import math
from collections import Counter

def preprocess_file(filepath):
    clean_lines = []
    with open(filepath, 'r') as file:
        for line in file:
            clean_lines.append(line.strip().lower())
    return clean_lines
def detect_unchanged_lines(file_a_lines, file_b_lines):
    """
    Uses Python's difflib (Unix diff algorithm) to find lines that are identical.
    """
    # Initialize the matcher with our two lists of lines
    matcher = difflib.SequenceMatcher(None, file_a_lines, file_b_lines)
    
    # get_matching_blocks returns chunks of lines that match exactly
    # It returns a list of tuples: (start_index_in_a, start_index_in_b, length_of_match)
    matching_blocks = matcher.get_matching_blocks()
    
    print("\n--- Step 2: Unchanged Lines Detected ---")
    for block in matching_blocks:
        start_a, start_b, length = block
        
        # We ignore matches of length 0 (end of file marker)
        if length > 0:
            # Grab the actual text from the first file to show what matched
            # We assume if they match, the text is the same in both
            matched_lines = file_a_lines[start_a : start_a + length]
            
            for i, line in enumerate(matched_lines):
                # Print: Line Number in A <-> Line Number in B : Content
                # We add 1 to indices because humans count from 1, computers from 0
                print(f"Line {start_a + i + 1} (Old) == Line {start_b + i + 1} (New) : {line}")

def get_unmatched_lines(file_a_lines, file_b_lines):
    matcher = difflib.SequenceMatcher(None, file_a_lines, file_b_lines)
    left_list = []
    right_list = []
    last_a = 0
    last_b = 0
    
    for match in matcher.get_matching_blocks():
        start_a, start_b, length = match
        for i in range(last_a, start_a):
            left_list.append((i, file_a_lines[i]))
        for i in range(last_b, start_b):
            right_list.append((i, file_b_lines[i]))
        last_a = start_a + length
        last_b = start_b + length
    return left_list, right_list

def levenshtein_distance(s1, s2):
    if len(s1) < len(s2):
        return levenshtein_distance(s2, s1)
    if len(s2) == 0:
        return len(s1)
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
    dist = levenshtein_distance(s1, s2)
    max_len = max(len(s1), len(s2))
    if max_len == 0: return 1.0
    return 1 - (dist / max_len)

# --- NEW: Helper to get neighbor lines (Context) ---
def get_context_string(lines, index, window=4):
    """
    Grabs 4 lines before and 4 lines after the given index.
    Combines them into one big string.
    """
    start = max(0, index - window)
    end = min(len(lines), index + window + 1)
    # Join all neighboring lines with spaces
    return " ".join(lines[start:end])

# --- NEW: Cosine Similarity Function ---
def get_cosine_similarity(text1, text2):
    """
    Calculates Cosine Similarity between two strings.
    1. Turn strings into "Vectors" (word counts).
    2. Calculate Dot Product.
    3. Divide by magnitude.
    """
    # Tokenize: simple split by space
    vec1 = Counter(text1.split())
    vec2 = Counter(text2.split())
    
    # Get all unique words
    intersection = set(vec1.keys()) & set(vec2.keys())
    
    # Calculate Dot Product (A . B)
    numerator = sum([vec1[x] * vec2[x] for x in intersection])
    
    # Calculate Magnitude (|A| * |B|)
    sum1 = sum([vec1[x]**2 for x in vec1.keys()])
    sum2 = sum([vec2[x]**2 for x in vec2.keys()])
    denominator = math.sqrt(sum1) * math.sqrt(sum2)
    
    if denominator == 0:
        return 0.0
    else:
        return numerator / denominator

# ... (Keep all functions: preprocess, unmatched, levenshtein, cosine, etc. UP TO HERE) ...

# --- NEW: Improved Bi-Directional Split Check ---
def check_line_splits(l_text, r_idx, right_list_data):
    """
    Checks if merging the match with its neighbors (Next OR Previous) improves the score.
    Returns: (merged_text, neighbor_index_to_mark_as_used)
    """
    current_match_text = ""
    neighbor_text = ""
    neighbor_idx = -1
    
    # 1. Locate our current match in the list
    current_list_pos = -1
    for i, (idx, text) in enumerate(right_list_data):
        if idx == r_idx:
            current_match_text = text
            current_list_pos = i
            break
            
    if current_list_pos == -1: return None, None

    dist_single = levenshtein_distance(l_text, current_match_text)
    
    # 2. Try Merging with NEXT Line (Forward Split)
    # Check if next element exists AND is the immediate next line number
    if current_list_pos + 1 < len(right_list_data):
        next_idx, next_text = right_list_data[current_list_pos + 1]
        if next_idx == r_idx + 1:
            merged_fwd = current_match_text + " " + next_text
            dist_fwd = levenshtein_distance(l_text, merged_fwd)
            
            # If Forward Merge is better, return it
            if dist_fwd < dist_single:
                return merged_fwd, next_idx

    # 3. Try Merging with PREVIOUS Line (Backward Split)
    # Check if prev element exists AND is the immediate prev line number
    if current_list_pos > 0:
        prev_idx, prev_text = right_list_data[current_list_pos - 1]
        if prev_idx == r_idx - 1:
            merged_bwd = prev_text + " " + current_match_text
            dist_bwd = levenshtein_distance(l_text, merged_bwd)
            
            # If Backward Merge is better, return it
            if dist_bwd < dist_single:
                return merged_bwd, prev_idx
                
    return None, None

# --- Main Execution ---
if __name__ == "__main__":
    lines_a = preprocess_file("source_a.txt")
    lines_b = preprocess_file("source_b.txt")
    
    # 1. Anchors (Optional print)
    # detect_unchanged_lines(lines_a, lines_b) 

    # 2. Get Unmatched
    left, right = get_unmatched_lines(lines_a, lines_b)
    
    print("--- Final Output: Mappings with Bi-Directional Split Detection ---")
    
    used_new_lines = set()
    
    for l_idx, l_text in left:
        best_score = -1
        best_match_index = -1
        best_match_text = ""
        
        context_a = get_context_string(lines_a, l_idx)
        
        # FIND BEST SINGLE MATCH
        for r_idx, r_text in right:
            if r_idx in used_new_lines: continue
            
            context_b = get_context_string(lines_b, r_idx)
            content_sim = get_content_similarity(l_text, r_text)
            context_sim = get_cosine_similarity(context_a, context_b)
            combined_score = (0.6 * content_sim) + (0.4 * context_sim)
            
            if combined_score > best_score:
                best_score = combined_score
                best_match_index = r_idx
                best_match_text = r_text
        
        # CHECK FOR SPLITS
        if best_score > 0.40: # Lowered slightly to catch the split parts
            # Try to see if merging with a neighbor helps
            improved_match, neighbor_idx = check_line_splits(l_text, best_match_index, right)
            
            if improved_match:
                print(f"[SPLIT DETECTED] Old Line {l_idx+1} maps to combined lines {min(best_match_index, neighbor_idx)+1} & {max(best_match_index, neighbor_idx)+1}")
                print(f"                 Original: '{l_text}'")
                print(f"                 Mapped:   '{improved_match}'")
                
                # Mark both lines as used
                used_new_lines.add(best_match_index)
                used_new_lines.add(neighbor_idx) 
            else:
                print(f"[MATCH] Old Line {l_idx+1} -> New Line {best_match_index+1}")
                print(f"        '{l_text}' -> '{best_match_text}'")
                used_new_lines.add(best_match_index)