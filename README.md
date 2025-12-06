# comp3310-lhdiff-python
# LHDiff V2: Modular Monolith Edition
**COMP-3110 Project | Python Implementation v2.2**

## Overview
LHDiff is a tool designed to track lines of source code between two versions of a file. Unlike standard `diff` utilities that only detect insertions and deletions, LHDiff uses **Context** and **Content** similarity to identify lines that have moved, been modified, split, or merged.

This implementation solves the "Line Mapping Problem" by using a hybrid approach involving **Levenshtein Distance** (Content) and **SimHash + Hamming Distance** (Context).

## Features
* **Language Independent:** Works on Java, Python, C++, and text files.
* **Two-Pass Matching Algorithm:** Uses a "Hawk and Mouse" strategy to lock in high-confidence matches before attempting to match ambiguous lines.
* **Bi-Directional Mapping:**
    * **Split Detection:** Detects when one line splits into two (e.g., breaking long arguments).
    * **Merge Detection:** Detects when multiple lines combine into one (e.g., code cleanup).
* **Automated Optimization:** Includes a script to find the best configuration parameters for your dataset using a Genetic Algorithm.
* **Modular Architecture:** Clean separation of concerns (Engine, Input, Models, Optimizer).

---

## Installation & Setup

### Prerequisites
* Python 3.x
* No external `pip` packages required (uses standard libraries: `difflib`, `math`, `re`, `argparse`, `hashlib`, `statistics`, `random`, `xml`).

### Project Structure
```text
comp3310-lhdiff-python/
│
├── lhdiff_v2/              # The Core Package
│   ├── __init__.py         # Package initialization
│   ├── __main__.py         # Entry point (CLI)
│   ├── engine.py           # Core LHDiff Algorithm (Matrix & Two-Pass)
│   ├── input_controller.py # Input Parsing Strategy Pattern
│   ├── models.py           # Data Classes (LineNode)
│   ├── optimizer.py        # Genetic Algorithm Logic
│   └── utils.py            # Similarity Math (SimHash, Levenshtein)
│
├── evaluate_v2.py          # Batch Evaluation Script
├── optimize_v2.py          # Batch Optimization Script
│
├── data/                   # Test Dataset
└── README.md               # This file
```

---

## Usage

### 1. Running LHDiff (CLI)
You can run the tool as a module:

#### Option A: Two Separate Files (Standard)
```bash
python -m lhdiff_v2.main path/to/old_file.java path/to/new_file.java
```

#### Option B: Combined File
```bash
python -m lhdiff_v2.main path/to/combined_file.txt
```

#### Option C: XML File
```bash
python -m lhdiff_v2.main path/to/input.xml
```

**Output Format:** `OldLineNumber -> NewLineNumber(s)`

### 2. Running the Evaluation Suite
To run the tool against the provided test dataset and calculate accuracy:
```bash
python evaluate_v2.py [data_dir]
```
This will:
1. Run `lhdiff` on every test case in the `data/` folder (supports both XML and JSON ground truth).
2. Compare the results against the Ground Truth.
3. Print the accuracy for each test case and the overall average.

### 3. Optimizing Configuration
To find the best parameters (Weights, Thresholds) for your specific dataset:
```bash
python optimize_v2.py [data_dir]
```
This script uses a **Genetic Algorithm** to efficiently explore the parameter space. It will:
1. Load all test cases into memory (pre-calculating matrices for speed).
2. Evolve a population of configurations over several generations.
3. Output the best configuration found.

---

## How It Works

### The Algorithm
1. **Preprocessing:** Lines are normalized (lowercase, whitespace trimmed) and tokenized.
2. **Anchoring:** Exact matches are identified first using `difflib` to serve as "anchors".
3. **SimHash Generation:** A 64-bit SimHash fingerprint is generated for each line's **Context** (the lines surrounding it). This allows for O(1) comparison of context similarity.
4. **Matrix Construction:** A similarity matrix is built (or sparsely populated) calculating Content (Levenshtein) and Context (Hamming) scores.
5. **Two-Pass Matching:**
    *   **Pass 1 (The Hawk):** Scans for high-confidence matches (High Threshold).
    *   **Pass 2 (The Mouse):** Scans the remaining unmatched lines with a lower threshold.
6. **Split/Merge Detection:** Checks if merging neighbor lines improves the similarity score.

### Configuration Parameters
*   **CONTENT_WEIGHT:** Importance of the line's text (0.0 - 1.0).
*   **CONTEXT_WEIGHT:** Importance of the surrounding lines (1.0 - CONTENT_WEIGHT).
*   **PASS1_THRESHOLD:** Minimum score required for a match in the first pass.
*   **PASS2_THRESHOLD:** Minimum score required for a match in the second pass.

---

## Limitations & Known Issues
*   **Ground Truth Quality:** The accuracy of the evaluation script depends entirely on the quality of the provided Ground Truth files. If the Ground Truth contains errors (e.g., mapping to non-existent lines or incorrect indices), the reported accuracy will be artificially low. We recommend manually verifying any test cases with accuracy below 75%.
*   **Context Sensitivity:** Large insertions (like massive JavaDoc blocks) can temporarily disrupt the "Context" similarity for surrounding lines, potentially requiring parameter tuning (lowering `CONTEXT_WEIGHT`) for those specific files.