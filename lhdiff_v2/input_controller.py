import os
import re
import xml.etree.ElementTree as ET
from abc import ABC, abstractmethod
from typing import List, Tuple
from .models import LineNode
from .utils import SimilarityCalculator

class InputParser(ABC):
    """Abstract base class for input parsers."""
    
    @abstractmethod
    def parse(self, source_a: str, source_b: str = None) -> Tuple[List[str], List[str]]:
        """
        Parses the input source(s) into two lists of strings.
        
        Args:
            source_a (str): The first source path.
            source_b (str, optional): The second source path.

        Returns:
            Tuple[List[str], List[str]]: (lines_from_a, lines_from_b)
        """
        pass

    def preprocess_line(self, line: str) -> str:
        """
        Standard preprocessing: trim, lower, pad symbols.
        
        Args:
            line (str): The raw line of code.
            
        Returns:
            str: The normalized line.
        """
        # Binary Check: If line contains null bytes, it's likely binary
        if '\0' in line:
            return ""
            
        line = line.strip().lower()
        line = re.sub(r'([^\w\s])', r' \1 ', line)
        return " ".join(line.split())

class RawFileParser(InputParser):
    """Parses two separate raw text/code files."""
    def parse(self, source_a: str, source_b: str = None) -> Tuple[List[str], List[str]]:
        if not source_b:
            raise ValueError("RawFileParser requires two files.")
        return self._read_file(source_a), self._read_file(source_b)

    def _read_file(self, filepath: str) -> List[str]:
        lines = []
        try:
            with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
                for line in f:
                    processed = self.preprocess_line(line)
                    if processed: # Skip empty/binary lines
                        lines.append(processed)
        except FileNotFoundError:
            print(f"Warning: File not found: {filepath}")
            return []
        return lines

class CombinedFileParser(InputParser):
    """Parses a single file containing both versions separated by delimiters."""
    DELIMITER_OLD = "--- OLD FILE ---"
    DELIMITER_NEW = "--- NEW FILE ---"

    def parse(self, source_a: str, source_b: str = None) -> Tuple[List[str], List[str]]:
        lines_a, lines_b = [], []
        current_section = None
        found_old = False
        found_new = False
        
        try:
            with open(source_a, 'r', encoding='utf-8', errors='ignore') as f:
                for line in f:
                    stripped = line.strip()
                    if stripped == self.DELIMITER_OLD:
                        current_section = "OLD"
                        found_old = True
                        continue
                    elif stripped == self.DELIMITER_NEW:
                        current_section = "NEW"
                        found_new = True
                        continue
                    
                    processed = self.preprocess_line(line)
                    if not processed: continue

                    if current_section == "OLD":
                        lines_a.append(processed)
                    elif current_section == "NEW":
                        lines_b.append(processed)
                        
            if not found_old or not found_new:
                print(f"Warning: Missing delimiters in {source_a}. Found OLD: {found_old}, NEW: {found_new}")
                
        except FileNotFoundError:
            print(f"Warning: File not found: {source_a}")
            return [], []
        return lines_a, lines_b

class XMLInputParser(InputParser):
    """Parses an XML file containing <old> and <new> elements."""
    def parse(self, source_a: str, source_b: str = None) -> Tuple[List[str], List[str]]:
        lines_a, lines_b = [], []
        try:
            tree = ET.parse(source_a)
            root = tree.getroot()
            old_elem = root.find(".//old")
            new_elem = root.find(".//new")
            
            if old_elem is not None and old_elem.text:
                for line in old_elem.text.splitlines():
                    lines_a.append(self.preprocess_line(line))
            if new_elem is not None and new_elem.text:
                for line in new_elem.text.splitlines():
                    lines_b.append(self.preprocess_line(line))
        except ET.ParseError:
            print(f"Error: Failed to parse XML file: {source_a}")
            return [], []
        except FileNotFoundError:
            print(f"Warning: File not found: {source_a}")
            return [], []
        return lines_a, lines_b

class InputController:
    """
    Orchestrates parsing and conversion to LineNode objects.
    """
    
    def __init__(self, window_size: int = 8):
        """
        Args:
            window_size (int): The context window size for SimHash calculation.
        """
        self.window_size = window_size

    def parse(self, source_a: str, source_b: str = None) -> Tuple[List[LineNode], List[LineNode]]:
        """
        Parses inputs and converts them to LineNode objects with SimHash.

        Args:
            source_a (str): First source path.
            source_b (str, optional): Second source path.

        Returns:
            Tuple[List[LineNode], List[LineNode]]: Nodes for old and new files.
        """
        # 1. Select Parser
        parser = self._get_parser(source_a, source_b)
        
        # 2. Parse raw strings
        lines_a_str, lines_b_str = parser.parse(source_a, source_b)
        
        # 3. Convert to LineNode with SimHash
        nodes_a = self._create_nodes(lines_a_str)
        nodes_b = self._create_nodes(lines_b_str)
        
        return nodes_a, nodes_b

    def _get_parser(self, source_a: str, source_b: str = None) -> InputParser:
        if source_b: return RawFileParser()
        if source_a.endswith('.xml'): return XMLInputParser()
        return CombinedFileParser()

    def _create_nodes(self, lines: List[str]) -> List[LineNode]:
        nodes = []
        for i, content in enumerate(lines):
            # Context for SimHash
            start = max(0, i - self.window_size)
            end = min(len(lines), i + self.window_size + 1)
            context_text = " ".join(lines[start:end])
            simhash = SimilarityCalculator.get_simhash(context_text)
            
            nodes.append(LineNode(
                line_number=i + 1,
                content=content,
                tokens=content.split(),
                simhash=simhash
            ))
        return nodes
