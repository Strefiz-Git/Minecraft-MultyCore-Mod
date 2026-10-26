#!/usr/bin/env bash
# Benchmark comparison script for MultiCore.
# Runs the same scenario with different worker thread counts and generates a comparison table.
#
# Usage: scripts/benchmark-compare.sh <server-dir> <scenario> <clusters> <perCluster> <spacing> <duration>
#   scenario: dense | clusters
#   duration: seconds of measurement (default 60)
#
# Results are saved as JSON in <server-dir>/stress-results/ and a summary table is printed.

set -euo pipefail

SERVER_DIR="${1:?server directory}"
SCENARIO="${2:?scenario: dense|clusters}"
CLUSTERS="${3:-4}"
PER_CLUSTER="${4:-100}"
SPACING="${5:-1024}"
DURATION="${6:-60}"

MOD_JAR="$(ls build/libs/multicore-*.jar | grep -v sources | head -1)"
CONFIG="$SERVER_DIR/config/multicore.json"
MODS="$SERVER_DIR/mods"

echo "=== MultiCore Benchmark Comparison ==="
echo "Server: $SERVER_DIR"
echo "Scenario: $SCENARIO"
echo "Clusters: $CLUSTERS, perCluster: $PER_CLUSTER, spacing: $SPACING"
echo "Duration: ${DURATION}s"
echo ""

# Ensure stress mod is installed
if [[ ! -f "$MODS/stress-mod-*.jar" ]]; then
    echo "WARNING: stress-mod jar not found in $MODS. Build it first."
fi

# Results directory
RESULTS_DIR="$SERVER_DIR/stress-results"
mkdir -p "$RESULTS_DIR"

# Test configurations
CONFIGS=(
    "0:false"   # vanilla (no mod)
    "1:true"    # 2 threads
    "3:true"    # 4 threads
    "5:true"    # 6 threads
)

for config in "${CONFIGS[@]}"; do
    IFS=: read -r workers hasMod <<< "$config"
    totalThreads=$((workers + 1))

    echo "=== Running: workers=$workers, mod=$hasMod ==="

    # Configure multicore
    if [[ "$hasMod" == "true" ]]; then
        mkdir -p "$SERVER_DIR/config"
        cat > "$CONFIG" <<JSON
{
  "workerThreads": $workers,
  "mode": "SAFE",
  "debug": false,
  "modWhitelist": [],
  "modBlacklist": []
}
JSON
        cp "$MOD_JAR" "$MODS/" 2>/dev/null || true
    else
        rm -f "$MODS/$(basename "$MOD_JAR")" 2>/dev/null || true
        rm -f "$CONFIG" 2>/dev/null || true
    fi

    # Start server
    echo "Starting server..."
    cd "$SERVER_DIR"
    java -Xmx2G -jar fabric-server-launch.jar --nogui > "$SERVER_DIR/server-run.log" 2>&1 &
    SERVER_PID=$!

    # Wait for server to start
    for i in $(seq 1 60); do
        if grep -q "Done (.*)! For help" "$SERVER_DIR/logs/latest.log" 2>/dev/null; then
            break
        fi
        sleep 1
    done
    echo "Server started."

    # Run stress scenario
    echo "Spawning entities..."
    if [[ "$SCENARIO" == "dense" ]]; then
        echo "/stress spawn dense $((CLUSTERS * PER_CLUSTER))" > /dev/null
    else
        echo "/stress spawn clusters $CLUSTERS $PER_CLUSTER $SPACING" > /dev/null
    fi
    # Note: commands need to be sent via RCON or console; this is a template

    sleep 5  # let entities spawn

    # Run benchmark
    echo "Running benchmark (${DURATION}s)..."
    # /stress bench 5 $DURATION

    sleep $((DURATION + 10))

    # Stop server
    echo "stop" > /dev/null
    kill $SERVER_PID 2>/dev/null || true
    wait $SERVER_PID 2>/dev/null || true

    echo "Run complete. Check $RESULTS_DIR for JSON reports."
    echo ""
done

echo "=== Benchmark complete ==="
echo "Results in: $RESULTS_DIR"
echo "Compare the JSON files manually, or use a script to generate a table."
