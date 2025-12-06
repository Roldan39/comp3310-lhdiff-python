import random
import statistics
from typing import Dict, List, Tuple
from .engine import LHEngine

# Optimization Constants
NUM_GENERATIONS = 3
SAMPLES_PER_GEN = 20
TOP_K_SURVIVORS = 5

DEFAULT_RANGES = {
    "CONTENT_WEIGHT": (0.4, 0.9),
    "PASS1_THRESHOLD": (0.5, 0.9),
    "PASS2_THRESHOLD": (0.3, 0.6)
}

class GeneticOptimizer:
    """
    Optimizes LHDiff weights using a genetic algorithm.
    Leverages the LHEngine's matrix cache for speed.
    """
    
    def __init__(self, engine: LHEngine, truth_mapping: Dict[int, int]):
        """
        Initializes the optimizer.

        Args:
            engine (LHEngine): The engine instance (with pre-calculated matrix).
            truth_mapping (Dict[int, int]): Ground truth mapping {old_line: new_line}.
        """
        self.engine = engine
        self.truth_mapping = truth_mapping
        self.ranges = DEFAULT_RANGES.copy()

    def optimize(self) -> Dict:
        """
        Runs the optimization loop and returns the best config.
        
        Returns:
            Dict: The best configuration found.
        """
        best_overall_score = 0
        best_overall_config = {}
        
        # Initial random config to start
        best_overall_config = self._get_random_config(self.ranges)

        for gen in range(NUM_GENERATIONS):
            gen_results = []
            
            for _ in range(SAMPLES_PER_GEN):
                config = self._get_random_config(self.ranges)
                score = self._evaluate(config)
                gen_results.append((score, config))
                
                if score > best_overall_score:
                    best_overall_score = score
                    best_overall_config = config
            
            # Sort and narrow
            gen_results.sort(key=lambda x: x[0], reverse=True)
            survivors = [x[1] for x in gen_results[:TOP_K_SURVIVORS]]
            
            if gen < NUM_GENERATIONS - 1:
                self.ranges = self._narrow_ranges(survivors, self.ranges)
                
        return best_overall_config

    def _evaluate(self, config: Dict) -> float:
        """Runs the engine with config and compares against truth."""
        
        total = len(self.truth_mapping)
        if total == 0: return 0.0

        mappings = self.engine.run(config)
        
        # Convert to dict for checking
        pred_dict = {m[0]: m[1][0] for m in mappings if m[1]}
        
        correct = 0
        
        for t_old, t_new in self.truth_mapping.items():
            if pred_dict.get(t_old) == t_new:
                correct += 1
                
        return (correct / total) * 100.0

    def _get_random_config(self, ranges) -> Dict:
        """Generates a random configuration within current ranges."""
        cfg = {}
        cfg["CONTENT_WEIGHT"] = round(random.uniform(*ranges["CONTENT_WEIGHT"]), 2)
        cfg["PASS1_THRESHOLD"] = round(random.uniform(*ranges["PASS1_THRESHOLD"]), 2)
        cfg["PASS2_THRESHOLD"] = round(random.uniform(*ranges["PASS2_THRESHOLD"]), 2)
        cfg["CONTEXT_WEIGHT"] = round(1.0 - cfg["CONTENT_WEIGHT"], 2)
        return cfg

    def _narrow_ranges(self, top_configs, current_ranges):
        """
        Narrows search space using Statistical Mean and Standard Deviation.
        This is more stable than Min/Max (Outlier Resistant).
        """
        new_ranges = current_ranges.copy()
        
        def get_stat_range(key):
            values = [c[key] for c in top_configs]
            
            # If all values are the same (variance = 0), force a small search window
            if len(set(values)) <= 1:
                mu = values[0]
                sigma = 0.05 # Artificial standard deviation
            else:
                mu = statistics.mean(values)
                sigma = statistics.stdev(values)
            
            # 95% Confidence Interval (2 Standard Deviations)
            # We prune everything outside this range
            lower = max(0.0, mu - (2 * sigma))
            upper = min(1.0, mu + (2 * sigma))
            
            return (lower, upper)

        new_ranges["CONTENT_WEIGHT"] = get_stat_range("CONTENT_WEIGHT")
        new_ranges["PASS1_THRESHOLD"] = get_stat_range("PASS1_THRESHOLD")
        new_ranges["PASS2_THRESHOLD"] = get_stat_range("PASS2_THRESHOLD")
        
        return new_ranges
