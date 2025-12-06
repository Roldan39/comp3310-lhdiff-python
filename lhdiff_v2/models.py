from dataclasses import dataclass
from typing import List

@dataclass
class LineNode:
    """
    Represents a single line from a source file.
    
    Attributes:
        line_number (int): The original 1-based line number.
        content (str): The preprocessed content of the line.
        tokens (List[str]): Tokenized version of the content.
        simhash (int): The 64-bit SimHash fingerprint of the line context.
    """
    original_line_number: int
    content: str
    tokens: List[str]
    simhash: int
