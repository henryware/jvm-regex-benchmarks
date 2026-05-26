# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JVM regex library benchmarking suite comparing performance of various regex engines: JavaUtil, DkBrics, MonqJFA, RE2/J, Florian, Joni, KMY, JiTrex, and BricsScreen. Uses JMH for benchmarking and Scala 3 with scala-cli as the build tool.

## Commands

```bash
# Start REPL
scala repl .

# Run tests (munit)
scala test .

# Run JMH benchmarks (slow - generates CSV output)
scala run --power --jmh . -- -rf csv

# Generate plots from benchmark CSV
echo "worldofregex.Graph.plot()" | scala repl .
```

Plots are written to `./plots/` and linked from `./plots/index.html`.

## Architecture

### Core Abstractions (`src/main/scala/WorldOfRegex.scala`)
- `RegexEngine` trait: Stateless factory with `compile(pattern: String): Regex`
- `Regex` trait: Compiled regex with `hasWholeMatch`, `hasPartialMatch`, `locateFirstMatchIn`, `locateAllMatchIn`
- `Location`: Match result with start/end positions and optional subregions

### Engine Adapters
Each regex library has an adapter implementing `RegexEngine`:
- `StandardEngine` trait: Common adapter for libraries mimicking `java.util.regex` API (used by JavaUtil, Florian, Joni, KMY, JiTrex, Re2J)
- Custom adapters for DFA-based engines: `DkBrics`, `MonqJFA`, `BricsScreen`

### Benchmarks (`src/main/scala/Benchmarks.scala`)
Three JMH benchmark classes with different characteristics:
- `ZBig`: Throughput tests on varying text lengths (normal speed)
- `ZSmall`: Throughput tests that can be very slow
- `TSmall`: Average time tests for backtracking torture tests (exponentially slow)

Test constants and patterns are in `src/main/scala/Constants.scala`.

## Code Style (from CONVENTIONS.md)
- 4-space indentation
- Use munit for tests, scalacheck where appropriate
- Self-documenting code over comments

Don't mention co-authored by Claude in commit messages.
