<div align="center">

# 🔍 LHDiff V2
### Intelligent Source Code Line Tracking & Analysis

![Python](https://img.shields.io/badge/Python-3.x-blue?style=for-the-badge&logo=python&logoColor=white)
![Status](https://img.shields.io/badge/Status-Completed-success?style=for-the-badge)

**COMP-3110 Project | Python Implementation v2.3**

</div>

---

## 📋 Table of Contents
- [Overview](#-overview)
- [Team](#-team)
- [Features](#-features)
- [Technologies](#️-technologies-built-with)
- [Installation](#-installation--setup)
- [Usage](#-usage)
- [How It Works](#-how-it-works)
- [Benchmarks](#-benchmarks)
- [Project Structure](#-project-structure)

---

## 🌟 Overview

LHDiff is a sophisticated tool designed to track lines of source code between two versions of a file. Unlike standard `diff` utilities that only detect insertions and deletions, LHDiff uses **Context** and **Content** similarity to identify lines that have moved, been modified, split, or merged.

This implementation solves the "Line Mapping Problem" by using a hybrid approach involving **Levenshtein Distance** (Content) and **SimHash + Hamming Distance** (Context).

---

## 👥 Team

<table>
  <tr>
    <td align="center">
      <b>Roldan Nduhukire</b>
    </td>
    <td align="center">
      <b>Hassan Zafar</b>
    </td>
    <td align="center">
      <b>William Lubitz</b>
    </td>
    <td align="center">
      <b>Stanley Ly</b>
    </td>
    <td align="center">
      <b>Victor Tekigerwa</b>
    </td>
  </tr>
</table>

---

## ✨ Features

- 🌐 **Language Independent** — Works on Java, Python, C++, and text files
- 🎯 **Two-Pass Matching Algorithm** — "Hawk and Mouse" strategy for high-confidence matching
- 🔀 **Bi-Directional Mapping**
  - **Split Detection:** Identifies when one line splits into multiple
  - **Merge Detection:** Detects when multiple lines combine into one
- 🧬 **Automated Optimization (Genetic Algorithm)**
  - Smart sampling with random file selection
  - Standard Deviation Pruning to avoid local optima
  - Safe handling of massive files (>2000 lines)
- 🎨 **Professional HTML Visualizer**
  - Side-by-side diff view
  - Dark/Light theme toggle
  - Color-coded status indicators
- 🐛 **Bug Pattern Classifier**
  - Heuristic detection of bug fixes
  - ANSI color-coded CLI output
  - Pattern recognition for null checks, type changes, and more
- 🏗️ **Modular Architecture** — Clean separation of concerns

---

## 🛠️ Technologies Built With

### Core Algorithms
![Levenshtein](https://img.shields.io/badge/Algorithm-Levenshtein_Distance-yellow?style=flat-square)
![SimHash](https://img.shields.io/badge/Algorithm-SimHash_Context-yellow?style=flat-square)
![Genetic](https://img.shields.io/badge/Optimization-Genetic_Algorithm-red?style=flat-square)

### Development Stack
![Python](https://img.shields.io/badge/Language-Python_3-blue?style=flat-square&logo=python)
![HTML5](https://img.shields.io/badge/Visualizer-HTML5_%2F_CSS3-orange?style=flat-square&logo=html5)
![CLI](https://img.shields.io/badge/Interface-ANSI_CLI-black?style=flat-square&logo=terminal)

---

## 📦 Installation & Setup

### Prerequisites
- Python 3.x
- No external `pip` packages required (uses standard libraries only)

### Quick Start
```bash
# Clone the repository
git clone https://github.com/Roldan39/comp3310-lhdiff-python.git
cd comp3310-lhdiff-python

# Run on any two files
python -m lhdiff_v2 path/to/old_file.java path/to/new_file.java
```

---

## 🚀 Usage

### 1. Basic Comparison
```bash
python -m lhdiff_v2 old_file.java new_file.java
```

**Output:**
- 📊 Standard output: `OldLineNumber -> NewLineNumber(s)`
- 📄 HTML Report: `lhdiff_report.html` (with dark mode!)
- 🐛 Bug Report: Color-coded console output

### 2. Dataset Evaluation
```bash
# Evaluate accuracy against ground truth
python evaluate_v2.py data/dataset1
```

### 3. Parameter Optimization

**Fast Mode (Recommended):**
```bash
python optimize_v2.py data/dataset1
```
- Samples 10 random files
- Completes in ~30 seconds
- Automatically skips massive files

**Full Mode:**
```bash
python optimize_v2.py data/dataset1 --full
```
- Runs on entire dataset
- More thorough but slower

---

## 🔬 How It Works

### The Algorithm

1. **Preprocessing** — Lines are normalized and tokenized
2. **Anchoring** — Exact matches identified using `difflib`
3. **SimHash Generation** — 64-bit context fingerprints for O(1) comparison
4. **Matrix Construction** — Similarity scores calculated for Content and Context
5. **Two-Pass Matching**
   - **Pass 1 (Hawk):** High-confidence matches (strict threshold)
   - **Pass 2 (Mouse):** Remaining matches (relaxed threshold)
6. **Split/Merge Detection** — Multi-line relationship analysis

### Configuration Parameters

The Genetic Algorithm optimizes four key parameters:

| Parameter | Description | Range |
|-----------|-------------|-------|
| `CONTENT_WEIGHT` | Importance of line text similarity | 0.0 - 1.0 |
| `CONTEXT_WEIGHT` | Importance of surrounding lines | 1.0 - CONTENT_WEIGHT |
| `PASS1_THRESHOLD` | First pass minimum score | 0.0 - 1.0 |
| `PASS2_THRESHOLD` | Second pass minimum score | 0.0 - 1.0 |

---

## 📊 Benchmarks

See [`BENCHMARK_REPORT.md`](BENCHMARK_REPORT.md) for full details.

| Dataset | Accuracy | Optimal Config |
|---------|----------|----------------|
| **Dataset 1** | ~80% | Content=0.70, Context=0.30 |
| **Dataset 2** | ~99% | Content=0.45, Context=0.55 |
| **Dataset 3** | 60% | Any configuration |

---

## 📁 Project Structure
```text
comp3310-lhdiff-python/
│
├── lhdiff_v2/              # Core Package
│   ├── __init__.py         # Package initialization
│   ├── __main__.py         # CLI entry point
│   ├── engine.py           # LHDiff algorithm implementation
│   ├── input_controller.py # Input parsing strategies
│   ├── models.py           # Data models (LineNode)
│   ├── optimizer.py        # Genetic algorithm
│   ├── visualizer.py       # HTML report generator
│   ├── bug_classifier.py   # Bug pattern detector
│   └── utils.py            # Similarity calculations
│
├── evaluate_v2.py          # Batch evaluation script
├── optimize_v2.py          # Batch optimization script
│
├── data/                   # Test datasets
│   ├── dataset1/           # ASTResolving style
│   ├── dataset2/           # AbstractOrigin style
│   └── dataset3/           # pair_XX style
│
├── BENCHMARK_REPORT.md     # Performance analysis
└── README.md               # This file
```



<div align="center">

### 🎓 University of Windsor | COMP-3110

</div>
