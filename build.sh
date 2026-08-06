#!/usr/bin/env bash
#
# Build and run without Maven. Finds all sources, compiles with javac,
# and runs the CLI entry point, passing along any arguments.
#
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$PROJECT_DIR/src/main/java"
BUILD_DIR="$PROJECT_DIR/build"

# Clean previous build.
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"

# Find all Java sources.
SOURCES=$(find "$SRC_DIR" -name '*.java')

echo "Compiling..."
javac --release 17 -d "$BUILD_DIR" $SOURCES

echo "Running..."
java -cp "$BUILD_DIR" io.github.netha6571.orchestrator.cli.Main "$@"
