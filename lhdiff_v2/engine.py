import difflib
from typing import List, Tuple, Dict, Set
from .models import LineNode
from .utils import SimilarityCalculator

class LHEngine:
    """
    The core engine for LHDiff V2.
    Implements Matrix Caching for performance and the Two-Pass matching strategy.
    """

    def __init__(self, nodes_a: List[LineNode], nodes_b: List[LineNode]):
        """
        Initializes the LHEngine with two lists of LineNodes.

        Args:
            nodes_a (List[LineNode]): Nodes from the source file (old version).
            nodes_b (List[LineNode]): Nodes from the target file (new version).
        """
        self.nodes_a = nodes_a
        self.nodes_b = nodes_b
        
        # Matrix Cache: [i][j] = (content_sim, context_sim)
        # We use a dictionary for sparse storage or just list of lists?
        # Given N*M can be large, let's use a list of lists but only populate for non-anchors if we wanted to be super optimized.
        # But for simplicity and "Speed Solution", we'll pre-calc everything once.
        self.matrix = [] 
        self.anchors = set() # Set of indices (i, j) that are exact matches
        
        self._build_matrix()

    def _build_matrix(self):
        """
        Pre-calculates Levenshtein and Hamming distances for all pairs.
        Also identifies exact matches (Anchors) to skip them during optimization.
        
        This method populates:
        - self.anchors: Set of (i, j) tuples for exact matches.
        - self.matrix: 2D list where matrix[i][j] = (content_score, context_score).
        """
        # 1. Identify Anchors (Exact Matches) using SequenceMatcher
        # This mimics the "Unix Diff" check
        a_contents = [n.content for n in self.nodes_a]
        b_contents = [n.content for n in self.nodes_b]
        
        matcher = difflib.SequenceMatcher(None, a_contents, b_contents)
        for block in matcher.get_matching_blocks():
            for k in range(block.size):
                self.anchors.add((block.a + k, block.b + k))

        # 2. Build Matrix
        # Optimization: Skip rows/cols that are already anchored
        anchor_rows = {i for i, j in self.anchors}
        anchor_cols = {j for i, j in self.anchors}
        
        # Initialize matrix with placeholders
        # We use a dense matrix for simplicity, but only populate needed cells
        self.matrix = [[(0.0, 0.0) for _ in range(len(self.nodes_b))] for _ in range(len(self.nodes_a))]
        
        for i, node_a in enumerate(self.nodes_a):
            if i in anchor_rows: continue
            
            for j, node_b in enumerate(self.nodes_b):
                if j in anchor_cols: continue
                
                content_sim = SimilarityCalculator.levenshtein_similarity(node_a.content, node_b.content)
                context_sim = SimilarityCalculator.get_hamming_similarity(node_a.simhash, node_b.simhash)
                
                self.matrix[i][j] = (content_sim, context_sim)

    def run(self, config: Dict) -> List[Tuple[int, List[int]]]:
        """
        Executes the Two-Pass strategy using the cached matrix and provided config.

        Args:
            config (Dict): Configuration dictionary containing weights and thresholds.
                           Keys: 'CONTENT_WEIGHT', 'CONTEXT_WEIGHT', 'PASS1_THRESHOLD', 'PASS2_THRESHOLD'.

        Returns:
            List[Tuple[int, List[int]]]: A list of mappings. Each mapping is a tuple of (old_line_num, [new_line_nums]).
        """
        results = []
        used_old = set()
        used_new = set()

        # --- STEP 1: ANCHORS ---
        # We always accept anchors first
        for i, j in self.anchors:
            results.append((self.nodes_a[i].original_line_number, [self.nodes_b[j].original_line_number]))
            used_old.add(i)
            used_new.add(j)

        # --- STEP 2: TWO-PASS MATCHING ---
        def run_pass(threshold):
            for i, node_a in enumerate(self.nodes_a):
                if i in used_old: continue
                
                best_score = -1
                best_match_idx = -1
                
                for j, node_b in enumerate(self.nodes_b):
                    if j in used_new: continue
                    
                    # RETRIEVE FROM CACHE
                    content_sim, context_sim = self.matrix[i][j]
                    
                    score = (content_sim * config["CONTENT_WEIGHT"]) + \
                            (context_sim * config["CONTEXT_WEIGHT"])
                    
                    if score > best_score:
                        best_score = score
                        best_match_idx = j

                if best_score > threshold:
                    # Check for SPLIT (One Old -> Two New)
                    is_split = False
                    if best_match_idx + 1 < len(self.nodes_b):
                        next_idx = best_match_idx + 1
                        if next_idx not in used_new:
                            # On-the-fly calculation for split
                            merged_content = self.nodes_b[best_match_idx].content + " " + self.nodes_b[next_idx].content
                            merged_sim = SimilarityCalculator.levenshtein_similarity(node_a.content, merged_content)
                            
                            single_sim = self.matrix[i][best_match_idx][0] # Content sim from cache
                            
                            if merged_sim > single_sim:
                                results.append((node_a.original_line_number, [self.nodes_b[best_match_idx].original_line_number, self.nodes_b[next_idx].original_line_number]))
                                used_new.add(best_match_idx)
                                used_new.add(next_idx)
                                used_old.add(i)
                                is_split = True

                    # Check for MERGE (Two Old -> One New)
                    # We only check this if we haven't found a split
                    is_merge = False
                    if not is_split and i + 1 < len(self.nodes_a):
                        next_old_idx = i + 1
                        if next_old_idx not in used_old:
                            # On-the-fly calculation for merge
                            merged_content_old = node_a.content + " " + self.nodes_a[next_old_idx].content
                            merged_sim = SimilarityCalculator.levenshtein_similarity(merged_content_old, self.nodes_b[best_match_idx].content)
                            
                            single_sim = self.matrix[i][best_match_idx][0]
                            
                            if merged_sim > single_sim:
                                results.append((node_a.original_line_number, [self.nodes_b[best_match_idx].original_line_number]))
                                results.append((self.nodes_a[next_old_idx].original_line_number, [self.nodes_b[best_match_idx].original_line_number]))
                                used_new.add(best_match_idx)
                                used_old.add(i)
                                used_old.add(next_old_idx)
                                is_merge = True
                    
                    if not is_split and not is_merge:
                        results.append((node_a.original_line_number, [self.nodes_b[best_match_idx].original_line_number]))
                        used_new.add(best_match_idx)
                        used_old.add(i)

        # Pass 1: Hawk
        run_pass(config["PASS1_THRESHOLD"])
        
        # Pass 2: Mouse
        run_pass(config["PASS2_THRESHOLD"])

        results.sort(key=lambda x: x[0])
        return results
