import random
import statistics
from evaluate import run_evaluation_with_config

# ==========================================
# OPTIMIZATION SETTINGS
# ==========================================
NUM_GENERATIONS = 3
SAMPLES_PER_GEN = 10
TOP_K_SURVIVORS = 3

# Initial Ranges
RANGES = {
    "CONTENT_WEIGHT": (0.4, 0.9),      # 0.4 to 0.9
    "PASS1_THRESHOLD": (0.5, 0.9),     # 0.5 to 0.9
    "PASS2_THRESHOLD": (0.3, 0.6),     # 0.3 to 0.6
    "WINDOW_SIZE": [4, 5, 6, 8, 10, 12] # Discrete choices
}

def get_random_config(ranges):
    """Generates a random configuration within the given ranges."""
    cfg = {}
    # Continuous variables
    cfg["CONTENT_WEIGHT"] = round(random.uniform(*ranges["CONTENT_WEIGHT"]), 2)
    cfg["PASS1_THRESHOLD"] = round(random.uniform(*ranges["PASS1_THRESHOLD"]), 2)
    cfg["PASS2_THRESHOLD"] = round(random.uniform(*ranges["PASS2_THRESHOLD"]), 2)
    
    # Discrete variables
    if isinstance(ranges["WINDOW_SIZE"], list):
        cfg["WINDOW_SIZE"] = random.choice(ranges["WINDOW_SIZE"])
    else:
        # If it became a tuple range during narrowing, cast to int
        w_min, w_max = ranges["WINDOW_SIZE"]
        cfg["WINDOW_SIZE"] = random.randint(int(w_min), int(w_max))
        
    # Derived variable
    cfg["CONTEXT_WEIGHT"] = round(1.0 - cfg["CONTENT_WEIGHT"], 2)
    return cfg

def narrow_ranges(top_configs, current_ranges):
    """Narrow the search space based on the best performers."""
    new_ranges = current_ranges.copy()
    
    # Helper to narrow continuous range
    def narrow_continuous(key):
        values = [c[key] for c in top_configs]
        mean = statistics.mean(values)
        stdev = statistics.stdev(values) if len(values) > 1 else 0.05
        
        # New range is Mean +/- Stdev (clamped to original bounds if needed, but simple min/max is safer)
        # Let's use Min/Max of top performers +/- 10% padding
        v_min = min(values)
        v_max = max(values)
        padding = (v_max - v_min) * 0.2 if v_max != v_min else 0.05
        
        return (max(0.0, v_min - padding), min(1.0, v_max + padding))

    new_ranges["CONTENT_WEIGHT"] = narrow_continuous("CONTENT_WEIGHT")
    new_ranges["PASS1_THRESHOLD"] = narrow_continuous("PASS1_THRESHOLD")
    new_ranges["PASS2_THRESHOLD"] = narrow_continuous("PASS2_THRESHOLD")
    
    # Narrow discrete range for Window Size
    w_values = [c["WINDOW_SIZE"] for c in top_configs]
    w_min = min(w_values)
    w_max = max(w_values)
    # If we have a list, filter it. If we have a tuple, narrow it.
    if isinstance(current_ranges["WINDOW_SIZE"], list):
        # Keep values within min-max range
        new_ranges["WINDOW_SIZE"] = [w for w in current_ranges["WINDOW_SIZE"] if w_min <= w <= w_max]
        if not new_ranges["WINDOW_SIZE"]: # Safety fallback
             new_ranges["WINDOW_SIZE"] = [w_min]
    else:
        new_ranges["WINDOW_SIZE"] = (w_min, w_max)
        
    return new_ranges

def optimize():
    print(f"Starting Randomized Narrowing Search...")
    print(f"Generations: {NUM_GENERATIONS}, Samples/Gen: {SAMPLES_PER_GEN}")
    print("-" * 50)
    
    current_ranges = RANGES.copy()
    best_overall_score = 0
    best_overall_config = {}
    
    for gen in range(NUM_GENERATIONS):
        print(f"\n--- GENERATION {gen + 1} ---")
        print(f"Search Space: {current_ranges}")
        
        gen_results = []
        
        for i in range(SAMPLES_PER_GEN):
            config = get_random_config(current_ranges)
            
            # Run Evaluation (Silent)
            score = run_evaluation_with_config(config, print_output=False)
            gen_results.append((score, config))
            
            print(f"  [{i+1}/{SAMPLES_PER_GEN}] Score: {score:.2f}% | {config}")
            
            if score > best_overall_score:
                best_overall_score = score
                best_overall_config = config
                print(f"    >>> NEW BEST OVERALL! ({score:.2f}%)")
        
        # Sort by score descending
        gen_results.sort(key=lambda x: x[0], reverse=True)
        
        # Pick survivors
        survivors = [x[1] for x in gen_results[:TOP_K_SURVIVORS]]
        
        # Narrow ranges for next gen
        if gen < NUM_GENERATIONS - 1:
            current_ranges = narrow_ranges(survivors, current_ranges)
            
    print("\n" + "=" * 50)
    print("OPTIMIZATION COMPLETE")
    print(f"Highest Accuracy Achieved: {best_overall_score:.2f}%")
    print("Best Configuration:")
    print(best_overall_config)
    print("=" * 50)
    print("Copy these values into the top of lhdiff.py!")

if __name__ == "__main__":
    optimize()