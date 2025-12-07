# comp3310-lhdiff-python
# LHDiff V2: Modular Monolith Edition
**COMP-3110 Project | Python Implementation v2.3**

## Overview
LHDiff is a tool designed to track lines of source code between two versions of a file. Unlike standard `diff` utilities that only detect insertions and deletions, LHDiff uses **Context** and **Content** similarity to identify lines that have moved, been modified, split, or merged.

This implementation solves the "Line Mapping Problem" by using a hybrid approach involving **Levenshtein Distance** (Content) and **SimHash + Hamming Distance** (Context).

## Features
* **Language Independent:** Works on Java, Python, C++, and text files.
* **Two-Pass Matching Algorithm:** Uses a "Hawk and Mouse" strategy to lock in high-confidence matches before attempting to match ambiguous lines.
* **Bi-Directional Mapping:**
    * **Split Detection:** Detects when one line splits into two (e.g., breaking long arguments).
    * **Merge Detection:** Detects when multiple lines combine into one (e.g., code cleanup).
* **Automated Optimization (Genetic Algorithm):**
    * **Smart Sampling:** Automatically trains on a random sample of 10 files to find optimal weights quickly.
    * **Full Mode:** Can optionally optimize on the entire dataset using the `--full` flag.
    * **Robustness:** Uses Standard Deviation Pruning (Bell Curve) to avoid local optima and handles massive files safely.
*   **Visualizer:** Generates a side-by-side HTML report (`lhdiff_report.html`) to visually inspect mappings, splits, and merges.
*   **Bug Classifier:** Analyzes mappings to detect potential bug-fix patterns (e.g., null checks, type changes) and prints a report.
*   **Modular Architecture:** Clean separation of concerns (Engine, Input, Models, Optimizer, Visualizer, Bug Classifier).

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
│   ├── visualizer.py       # HTML Generator
│   ├── bug_classifier.py   # Heuristic Bug Detector
│   └── utils.py            # Similarity Math (SimHash, Levenshtein)
│
├── evaluate_v2.py          # Batch Evaluation Script
├── optimize_v2.py          # Batch Optimization Script
│
├── data/                   # Test Datasets
│   ├── dataset1/           # "ASTResolving" style
│   ├── dataset2/           # "AbstractOrigin" style
│   └── dataset3/           # "pair_XX" style
│
└── BENCHMARK_REPORT.md     # Detailed performance analysis
└── README.md               # This file
```

---

## Usage

### 1. Running LHDiff (CLI)
You can run the tool as a module on any pair of files:

```bash
python -m lhdiff_v2.main path/to/old_file.java path/to/new_file.java
```

**Output:**
1.  **Standard System Output:** `OldLineNumber -> NewLineNumber(s)`
2.  **HTML Report:** Generates `lhdiff_report.html` in the current directory.
3.  **Bug Report:** Prints a "Bug Fix Detection Report" to stderr if potential fixes are found.

### 2. Running the Evaluation Suite
To run the tool against a specific dataset and calculate accuracy:
```bash
python evaluate_v2.py data/dataset1
```
*   `data/dataset1`: Runs on Dataset 1 (Default).
*   `data/dataset2`: Runs on Dataset 2.
*   `data/dataset3`: Runs on Dataset 3.

This will:
1. Run `lhdiff` on every test case in the specified folder.
2. Compare the results against the Ground Truth (JSON or XML).
3. Print the accuracy for each test case and the overall average.

### 3. Optimizing Configuration
To find the best parameters (Weights, Thresholds) for your dataset:

#### Default (Fast & Safe)
```bash
python optimize_v2.py data/dataset1
```
*   **Behavior:** Selects a **random sample of 10 files**.
*   **Safety:** Automatically skips massive files (>2000 lines) to prevent memory crashes.
*   **Speed:** Fast (~30 seconds).

#### Full Mode (Exhaustive)
```bash
python optimize_v2.py data/dataset1 --full
```
*   **Behavior:** Runs on **every valid file** in the directory.
*   **Safety:** Still skips massive files (>2000 lines).
*   **Speed:** Slower (depends on dataset size).

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
The Genetic Algorithm optimizes these four parameters:
*   **CONTENT_WEIGHT:** Importance of the line's text (0.0 - 1.0).
*   **CONTEXT_WEIGHT:** Importance of the surrounding lines (1.0 - CONTENT_WEIGHT).
*   **PASS1_THRESHOLD:** Minimum score required for a match in the first pass.
*   **PASS2_THRESHOLD:** Minimum score required for a match in the second pass.

---

## Latest Benchmarks (v2.3)
See `BENCHMARK_REPORT.md` for full details.

| Dataset | Accuracy | Optimal Config |
| :--- | :--- | :--- |
| **Dataset 1** | ~80% | `Content=0.70`, `Context=0.30` |
| **Dataset 2** | ~99% | `Content=0.45`, `Context=0.55` |
| **Dataset 3** | 60% (Ceiling) | *Any* |