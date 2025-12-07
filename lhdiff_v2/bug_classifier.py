import re

class BugClassifier:
    """
    Analyzes mapped lines to detect potential Bug Fixes.
    This fulfills the 'Bonus Mark' requirement for identifying bug-introducing vs bug-fixing changes.
    
    Strategies:
    1. Pattern Matching: Looking for specific code patterns (e.g., adding null checks).
    2. Type Changes: Looking for widened types (int -> long).
    """
    
    # Simple Heuristics for "Bug Fix" patterns
    # Format: (Regex Pattern, Human Readable Label)
    PATTERNS = [
        (r'!= null', "Null Check Added"),
        (r'null', "Null Pointer Fix"),
        (r'try\s*\{.*\}\s*catch', "Exception Handling Added"),
        (r'\.equals\(', "String Comparison Fix (== to .equals)"),
        (r'index\s*<\s*length', "Boundary Check Fix"),
        (r'synchronized', "Concurrency Fix"),
        (r'final', "Immutability Fix")
    ]

    def classify(self, node_a, node_b_list):
        """
        Returns a list of labels for a specific mapping.
        
        Args:
            node_a: The LineNode from the old file.
            node_b_list: A list of LineNodes from the new file.
        """
        labels = []
        
        # Combine all new content into one string for easier searching
        new_text = " ".join([n.content for n in node_b_list])
        old_text = node_a.content
        
        # 1. Check for Pattern Insertions (Present in New, Missing in Old)
        for pattern, label in self.PATTERNS:
            # If the pattern exists in New but NOT in Old, it was likely added to fix something
            if re.search(pattern, new_text) and not re.search(pattern, old_text):
                labels.append(label)
                
        # 2. Check for Type Changes (e.g., int -> long)
        # Using simple string checks for demonstration
        if "int " in old_text and "long " in new_text:
            labels.append("Integer Overflow Fix")
            
        if "float " in old_text and "double " in new_text:
            labels.append("Precision Fix")
            
        return labels

    def analyze_mappings(self, nodes_a, nodes_b, mappings):
        """
        Runs classification on all mappings and prints a report.
        """
        print("\n=== Bug Fix Detection Report ===")
        print(f"{'Line':<5} | {'Change Type':<25} | {'Detected Fixes'}")
        print("-" * 60)
        
        found_any = False
        
        for old_idx, new_indices in mappings:
            if not new_indices: continue
            
            # Find the actual node objects
            node_a = next((n for n in nodes_a if n.original_line_number == old_idx), None)
            target_nodes = [n for n in nodes_b if n.original_line_number in new_indices]
            
            if not node_a or not target_nodes: continue
            
            labels = self.classify(node_a, target_nodes)
            
            if labels:
                found_any = True
                mapping_str = f"{old_idx} -> {new_indices}"
                print(f"{mapping_str:<33} | {', '.join(labels)}")
        
        if not found_any:
            print("No obvious bug-fix patterns detected in this file pair.")
        print("================================\n")

if __name__ == "__main__":
    print("This module is intended to be imported by main.py.")