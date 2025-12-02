#!/bin/bash

echo "========================================"
echo "   LHDiff V2 - Professor's Demo"
echo "========================================"

# 1. Run Unit Tests
echo ""
echo "[1/3] Running Unit & Acceptance Tests..."
python3 -m unittest discover tests
if [ $? -ne 0 ]; then
    echo "Tests Failed!"
    exit 1
fi

# 2. Run Regression/Evaluation
echo ""
echo "[2/3] Running Full Evaluation on Data..."
python3 evaluate_v2.py
if [ $? -ne 0 ]; then
    echo "Evaluation Failed!"
    exit 1
fi

# 3. Demo Auto-Calibration (if XML exists)
# Find first XML file in data
XML_FILE=$(find data -name "*.xml" | head -n 1)

if [ -n "$XML_FILE" ]; then
    DIR=$(dirname "$XML_FILE")
    # Assuming standard naming convention: file.xml -> file_1.java, file_2.java
    # We can just find the java files in that dir
    JAVA_1=$(find "$DIR" -name "*_1.java" | head -n 1)
    JAVA_2=$(find "$DIR" -name "*_2.java" | head -n 1)

    if [ -n "$JAVA_1" ] && [ -n "$JAVA_2" ]; then
        echo ""
        echo "[3/3] Demo: Auto-Calibration on $DIR"
        echo "Running: python3 -m lhdiff_v2 $JAVA_1 $JAVA_2 --calibrate"
        python3 -m lhdiff_v2 "$JAVA_1" "$JAVA_2" --calibrate
    else
        echo "Skipping Auto-Calibration: Could not find Java files in $DIR"
    fi
else
    echo ""
    echo "[3/3] Skipped Auto-Calibration Demo (No XML found in data/)"
fi

echo ""
echo "========================================"
echo "   Demo Complete - Ready for Submission"
echo "========================================"
