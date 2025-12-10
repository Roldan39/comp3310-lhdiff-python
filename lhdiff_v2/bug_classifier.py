import re
import sys

class BugClassifier:
    """
    Analyzes mapped lines to detect potential Bug Fixes using heuristic patterns.
    """
    
    # ANSI Color Codes for Professional Console Output
    HEADER = '\033[95m'
    BLUE = '\033[94m'
    CYAN = '\033[96m'
    GREEN = '\033[92m'
    WARNING = '\033[93m' # Yellow
    FAIL = '\033[91m'    # Red
    ENDC = '\033[0m'
    BOLD = '\033[1m'
    
    PATTERNS = [
        (r'!= null', "Null Check Injection", "High"),
        (r'null', "Null Pointer Reference", "Medium"),
        (r'try\s*\{', "Exception Handling", "Medium"),
        (r'\.equals\(', "String Equality Fix", "High"),
        (r'(>=|<=|<|>)', "Boundary Condition Change", "Medium"),
        (r'synchronized', "Concurrency/Thread Safety", "High")
    ]

    def analyze_mappings(self, nodes_a, nodes_b, mappings):
        """
        Runs classification on all mappings and prints a stylized report.
        """
        # Header
        print(f"\n{self.HEADER}{self.BOLD}╔═════════════════════════════════════════════════════════════════════╗{self.ENDC}")
        print(f"{self.HEADER}{self.BOLD}║                  LHDiff Bug-Fix Detection Report                    ║{self.ENDC}")
        print(f"{self.HEADER}{self.BOLD}╚═════════════════════════════════════════════════════════════════════╝{self.ENDC}")
        print(f"{self.BOLD}{'Line Map':<20} | {'Severity':<10} | {'Pattern Detected'}{self.ENDC}")
        print("-" * 70)
        
        found_count = 0
        
        for old_idx, new_indices in mappings:
            if not new_indices: continue
            
            # Retrieve node objects
            node_a = next((n for n in nodes_a if n.original_line_number == old_idx), None)
            target_nodes = [n for n in nodes_b if n.original_line_number in new_indices]
            
            if not node_a or not target_nodes: continue
            
            # Analysis Logic
            old_text = node_a.content
            new_text = " ".join([n.content for n in target_nodes])
            
            detected = []

            # 1. Regex Patterns
            for pattern, label, severity in self.PATTERNS:
                # If pattern exists in NEW but NOT in OLD
                if re.search(pattern, new_text) and not re.search(pattern, old_text):
                    detected.append((label, severity))

            # 2. Type Changes (Heuristic)
            if "int " in old_text and "long " in new_text:
                detected.append(("Integer Overflow Fix", "High"))
            if "float " in old_text and "double " in new_text:
                detected.append(("Precision Fix", "Medium"))

            # Print Results
            if detected:
                found_count += 1
                map_str = f"{old_idx} -> {new_indices}"
                for label, severity in detected:
                    color = self.FAIL if severity == "High" else self.WARNING
                    print(f"{map_str:<20} | {color}{severity:<10}{self.ENDC} | {label}")

        print("-" * 70)
        if found_count == 0:
            print(f"{self.GREEN}No obvious bug-fix patterns detected in this file pair.{self.ENDC}")
        else:
            print(f"{self.BOLD}Total Potential Fixes Found: {found_count}{self.ENDC}")
        print("\n")