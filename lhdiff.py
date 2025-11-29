import difflib
import argparse
import sys
import re
import hashlib

# ==========================================
# DEFAULT CONFIGURATION
# ==========================================
DEFAULT_CONFIG = {
    "CONTENT_WEIGHT": 0.5,    # Importance of text similarity
    "CONTEXT_WEIGHT": 0.5,    # Importance of neighbor similarity
    "WINDOW_SIZE": 8,         # Context window (lines up/down)
    "PASS1_THRESHOLD": 0.75,  # Strictness for "The Hawk"
    "PASS2_THRESHOLD": 0.40   # Strictness for "The Mouse"
}

# ==========================================
# HELPER FUNCTIONS (SimHash & Similarity)
# ==========================================

def get_simhash(text):
    """
    Generates a 64-bit SimHash fingerprint for the given text.
    Step 3 of Project Slides: Efficiently hashing context.
    """
    if not text: return 0
    tokens = text.split()
    if not tokens: return 0
    
    v = [0] * 64
    for token in tokens:
        # Create a stable hash for the token
        token_hash = int(hashlib.md5(token.encode('utf-8')).hexdigest(), 16)
        for i in range(64):
            bit = (token_hash >> i) & 1
            if bit == 1: v[i] += 1
            else:        v[i] -= 1
            
    fingerprint = 0
    for i in range(64):
        if v[i] > 0: fingerprint |= (1 << i)
    return fingerprint

def get_hamming_similarity(hash1, hash2):
    """
    Calculates similarity based on Hamming distance between two SimHashes.
    Returns 1.0 (Identical) to 0.0 (Different).
    """
    x = (hash1 ^ hash2) & ((1 << 64) - 1)
    distance = bin(x).count('1')
    return 1 - (distance / 64.0)

def levenshtein_similarity(s1, s2):
    """
    Standard Levenshtein Ratio (0.0 to 1.0)
    """
    if not s1 and not s2: return 1.0
    if not s1 or not s2: return 0.0
    
    if len(s1) < len(s2): return levenshtein_similarity(s2, s1)
    
    previous_row = range(len(s2) + 1)
    for i, c1 in enumerate(s1):
        current_row = [i + 1]
        for j, c2 in enumerate(s2):
            insertions = previous_row[j + 1] + 1
            deletions = current_row[j] + 1
            substitutions = previous_row[j] + (c1 != c2)
            current_row.append(min(insertions, deletions, substitutions))
        previous_row = current_row
    
    dist = previous_row[-1]
    return 1.0 - (dist / max(len(s1), len(s2)))

def preprocess_file(filepath):
    """Reads file, lowercases, and pads symbols."""
    lines = []
    try:
        with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
            for line in f:
                line = line.strip().lower()
                # "array[i]" -> "array [ i ]" for better tokenization
                line = re.sub(r'([^\w\s])', r' \1 ', line)
                line = " ".join(line.split())
                lines.append(line)
    except FileNotFoundError:
        return []
    return lines

# ==========================================
# CORE LOGIC
# ==========================================

def run_lhdiff(old_file, new_file, config=None):
    # Load Config (Use defaults if none provided)
    cfg = config if config else DEFAULT_CONFIG
    
    lines_a = preprocess_file(old_file)
    lines_b = preprocess_file(new_file)
    
    left_list = list(enumerate(lines_a))
    right_list = list(enumerate(lines_b))
    
    results = []
    used_old = set()
    used_new = set()

    # --- STEP 1: ANCHORS (Exact Matches) ---
    matcher = difflib.SequenceMatcher(None, lines_a, lines_b)
    for block in matcher.get_matching_blocks():
        for i in range(block.size):
            old_idx = block.a + i
            new_idx = block.b + i
            results.append((old_idx + 1, [new_idx + 1]))
            used_old.add(old_idx)
            used_new.add(new_idx)

    # --- PRE-CALCULATE CONTEXT (SimHash) ---
    def get_context(lines, idx):
        start = max(0, idx - cfg["WINDOW_SIZE"])
        end = min(len(lines), idx + cfg["WINDOW_SIZE"] + 1)
        return " ".join(lines[start:end])

    left_hashes = {i: get_simhash(get_context(lines_a, i)) for i in range(len(lines_a))}
    right_hashes = {i: get_simhash(get_context(lines_b, i)) for i in range(len(lines_b))}

    # --- STEP 2: TWO-PASS MATCHING ---
    def run_pass(threshold):
        for l_idx, l_text in left_list:
            if l_idx in used_old: continue
            
            best_score = -1
            best_match_idx = -1
            
            for r_idx, r_text in right_list:
                if r_idx in used_new: continue
                
                # Combined Similarity Score
                content_sim = levenshtein_similarity(l_text, r_text)
                context_sim = get_hamming_similarity(left_hashes[l_idx], right_hashes[r_idx])
                
                score = (content_sim * cfg["CONTENT_WEIGHT"]) + \
                        (context_sim * cfg["CONTEXT_WEIGHT"])
                
                if score > best_score:
                    best_score = score
                    best_match_idx = r_idx

            if best_score > threshold:
                # Check for SPLIT (One Old -> Two New)
                is_split = False
                if best_match_idx + 1 < len(lines_b):
                    next_idx = best_match_idx + 1
                    if next_idx not in used_new:
                        merged = lines_b[best_match_idx] + " " + lines_b[next_idx]
                        # If merged is closer to old line than single match
                        if levenshtein_similarity(l_text, merged) > \
                           levenshtein_similarity(l_text, lines_b[best_match_idx]):
                            
                            results.append((l_idx + 1, [best_match_idx + 1, next_idx + 1]))
                            used_new.add(best_match_idx)
                            used_new.add(next_idx)
                            used_old.add(l_idx)
                            is_split = True
                
                if not is_split:
                    results.append((l_idx + 1, [best_match_idx + 1]))
                    used_new.add(best_match_idx)
                    used_old.add(l_idx)

    # Pass 1: Hawk (High Confidence)
    run_pass(cfg["PASS1_THRESHOLD"])
    
    # Pass 2: Mouse (Fill Gaps)
    run_pass(cfg["PASS2_THRESHOLD"])

    results.sort(key=lambda x: x[0])
    return results

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("old_file")
    parser.add_argument("new_file")
    args = parser.parse_args()

    mappings = run_lhdiff(args.old_file, args.new_file)
    for old, new_list in mappings:
        print(f"{old} -> {','.join(map(str, new_list))}")