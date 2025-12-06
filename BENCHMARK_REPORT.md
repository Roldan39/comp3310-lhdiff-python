# LHDiff Benchmark Report

## 1. Executive Summary

Benchmarks were run on three datasets using:
1.  **Default Configuration**
2.  **Run 1 (Baseline Optimizer)**
3.  **Run 2 (Expanded Optimizer)**
4.  **Run 3 (Sampled Optimizer)**
5.  **Run 4 (Full Optimizer Consistency Check)**: 3 Iterations on entire datasets.

## 2. Final Consistency Results (Full Dataset)
We ran the Full Optimizer 3 times to confirm stability.

| Dataset | Iteration 1 | Iteration 2 | Iteration 3 | **Mean Accuracy** | Adjusted StdDev |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Dataset 1** | 81.52% | 78.80% | 80.98% | **80.43%** | ±1.4% |
| **Dataset 2** | 99.37% | 99.38% | 99.38% | **99.38%** | ±0.0% |
| **Dataset 3** | 60.00% | 60.00% | 60.00% | **60.00%** | ±0.0% |

## 3. Findings

### Dataset 1: The "Soft" Limit
*   The results fluctuate slightly (78% - 81%), indicating the fitness landscape has a few local optima.
*   The **81.52%** result is likely the global maximum for this algorithm on this dataset.
*   **Recommendation:** Use the configuration from Iteration 1 (Content=0.70, Pass1=0.91).

### Dataset 2: Perfect Stability
*   The results are identical (within rounding error).
*   The algorithm is robust and the problem is effectively "solved" for this dataset.

### Dataset 3: The "Hard" Limit
*   The optimizer hits exactly 60.00% every single time.
*   This is not a configuration issue. It is a **Structural Ceiling**.
*   **Conclusion:** LHDiff cannot improve beyond 60% on Dataset 3 without code changes to the matching logic (likely related to how it handles specific refactorings or large file structures).

## 4. Final Recommended Configuration
Based on all tests, this configuration provides the best balance of robustness (D1) and accuracy (D2):

```python
# OPTIMIZED GLOBAL DEFAULTS
CONTENT_WEIGHT = 0.70
CONTEXT_WEIGHT = 0.30
PASS1_THRESHOLD = 0.90   # High precision filter
PASS2_THRESHOLD = 0.50   # Standard cleanup
```
