"""
LHDiff V2 Package
=================

This package implements the LHDiff V2 algorithm for source code differencing.
It uses a combination of SimHash (Context) and Levenshtein Distance (Content)
to accurately map lines between two versions of a file.

Modules:
    - engine: Core matching logic (Two-Pass Algorithm).
    - input_controller: Handles various input formats (Files, XML, Combined).
    - models: Data structures (LineNode).
    - optimizer: Genetic Algorithm for weight tuning.
    - utils: Similarity calculations (SimHash, Levenshtein).
"""
