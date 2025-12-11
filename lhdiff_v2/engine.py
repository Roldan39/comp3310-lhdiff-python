import difflib
from typing import List, Tuple, Dict, Set
from .models import LineNode
from .utils import SimilarityCalculator

class LHEngine:
    """
    Enhanced LHDiff Engine with explicit deletion and addition tracking.
    """

    def __init__(self, nodes_a: List[LineNode], nodes_b: List[LineNode]):
        self.nodes_a = nodes_a
        self.nodes_b = nodes_b
        self.matrix = []
        self.anchors = set()
        
        # Only build matrix if both files have content
        if nodes_a and nodes_b:
            self._build_matrix()

    def _build_matrix(self):
        """Pre-calculates similarity scores and identifies anchors."""
        a_contents = [n.content for n in self.nodes_a]
        b_contents = [n.content for n in self.nodes_b]
        
        matcher = difflib.SequenceMatcher(None, a_contents, b_contents)
        for block in matcher.get_matching_blocks():
            for k in range(block.size):
                self.anchors.add((block.a + k, block.b + k))

        anchor_rows = {i for i, j in self.anchors}
        anchor_cols = {j for i, j in self.anchors}
        
        self.matrix = [[(0.0, 0.0) for _ in range(len(self.nodes_b))] 
                       for _ in range(len(self.nodes_a))]
        
        for i, node_a in enumerate(self.nodes_a):
            if i in anchor_rows: continue
            
            for j, node_b in enumerate(self.nodes_b):
                if j in anchor_cols: continue
                
                content_sim = SimilarityCalculator.levenshtein_similarity(
                    node_a.content, node_b.content)
                context_sim = SimilarityCalculator.get_hamming_similarity(
                    node_a.simhash, node_b.simhash)
                
                self.matrix[i][j] = (content_sim, context_sim)

    def run(self, config: Dict, include_unmapped: bool = True) -> List[Tuple[int, List[int]]]:
        """
        Executes the Two-Pass matching strategy.
        
        Args:
            config: Configuration with weights and thresholds
            include_unmapped: If True, includes deletions (old -> [-1]) 
                            and additions ([-1] -> new)
        
        Returns:
            List of mappings (old_line, [new_lines])
            - Deletions appear as (old_line, [-1])
            - Additions appear as (-1, [new_line])
        """
        results = []
        used_old = set()
        used_new = set()

        # Handle edge case: Empty old file (all additions)
        if not self.nodes_a and self.nodes_b:
            if include_unmapped:
                for node_b in self.nodes_b:
                    results.append((-1, [node_b.original_line_number]))
            return results

        # Handle edge case: Empty new file (all deletions)
        if self.nodes_a and not self.nodes_b:
            if include_unmapped:
                for node_a in self.nodes_a:
                    results.append((node_a.original_line_number, [-1]))
            return results

        # Handle edge case: Both empty
        if not self.nodes_a and not self.nodes_b:
            return results

        # --- STEP 1: ANCHORS ---
        for i, j in self.anchors:
            results.append((
                self.nodes_a[i].original_line_number, 
                [self.nodes_b[j].original_line_number]
            ))
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
                    
                    content_sim, context_sim = self.matrix[i][j]
                    score = (content_sim * config["CONTENT_WEIGHT"]) + \
                            (context_sim * config["CONTEXT_WEIGHT"])
                    
                    if score > best_score:
                        best_score = score
                        best_match_idx = j

                if best_score > threshold:
                    # Check for SPLIT
                    is_split = False
                    if best_match_idx + 1 < len(self.nodes_b):
                        next_idx = best_match_idx + 1
                        if next_idx not in used_new:
                            merged_content = (self.nodes_b[best_match_idx].content + 
                                            " " + self.nodes_b[next_idx].content)
                            merged_sim = SimilarityCalculator.levenshtein_similarity(
                                node_a.content, merged_content)
                            single_sim = self.matrix[i][best_match_idx][0]
                            
                            if merged_sim > single_sim:
                                results.append((
                                    node_a.original_line_number,
                                    [self.nodes_b[best_match_idx].original_line_number,
                                     self.nodes_b[next_idx].original_line_number]
                                ))
                                used_new.add(best_match_idx)
                                used_new.add(next_idx)
                                used_old.add(i)
                                is_split = True

                    # Check for MERGE
                    is_merge = False
                    if not is_split and i + 1 < len(self.nodes_a):
                        next_old_idx = i + 1
                        if next_old_idx not in used_old:
                            merged_content_old = (node_a.content + " " + 
                                                self.nodes_a[next_old_idx].content)
                            merged_sim = SimilarityCalculator.levenshtein_similarity(
                                merged_content_old, 
                                self.nodes_b[best_match_idx].content)
                            single_sim = self.matrix[i][best_match_idx][0]
                            
                            if merged_sim > single_sim:
                                results.append((
                                    node_a.original_line_number,
                                    [self.nodes_b[best_match_idx].original_line_number]
                                ))
                                results.append((
                                    self.nodes_a[next_old_idx].original_line_number,
                                    [self.nodes_b[best_match_idx].original_line_number]
                                ))
                                used_new.add(best_match_idx)
                                used_old.add(i)
                                used_old.add(next_old_idx)
                                is_merge = True
                    
                    if not is_split and not is_merge:
                        results.append((
                            node_a.original_line_number,
                            [self.nodes_b[best_match_idx].original_line_number]
                        ))
                        used_new.add(best_match_idx)
                        used_old.add(i)

        run_pass(config["PASS1_THRESHOLD"])
        run_pass(config["PASS2_THRESHOLD"])

        # --- STEP 3: TRACK UNMAPPED (Deletions and Additions) ---
        if include_unmapped:
            # Deletions: Old lines that never got matched
            for i, node_a in enumerate(self.nodes_a):
                if i not in used_old:
                    results.append((node_a.original_line_number, [-1]))
            
            # Additions: New lines that never got matched
            for j, node_b in enumerate(self.nodes_b):
                if j not in used_new:
                    results.append((-1, [node_b.original_line_number]))

        results.sort(key=lambda x: (x[0] == -1, x[0]))  # Deletions first, then by line number
        return results
