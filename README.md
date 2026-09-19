# AI JUG Saxony — AI Model Performance & Quality Evaluation

A comparative benchmark evaluating AI coding assistants and LLMs implementing high-performance Java data structures: `FastHashMap` (an open-addressing high-speed map) and `LRUClockMap` (a concurrent Clock-eviction cache map).

## Submodule Overview

| Module | AI Model / Source | Tooling / Environment | Storage Strategy |
| :--- | :--- | :--- | :--- |
| **`demo0`** | Human Baseline / Reference | Manual implementation & test suite | Flat parallel `Object[]` arrays |
| **`demo1`** | Gemini 3.7 Flash High | Antigravity (VS Code) | Flat parallel `Object[]` arrays |
| **`demo2`** | Kimi K3 | Kilo Code Max (VS Code) | Flat parallel `Object[]` arrays |
| **`demo3`** | OpenAI 5.6 Sol Max | Kilo Code (VS Code) | Flat parallel `Object[]` arrays |
| **`demo4`** | Gemma 4 31B Thinking | Kilo Code (VS Code) | Flat parallel `Object[]` arrays + Tombstones |
| **`demo5`** | DeepSeek V4 Flash Max | Kilo Code (VS Code) | Chained Node/Entry Object table |
| **`demo6`** | Claude Opus 5 Ultra | Claude Code | Flat parallel `Object[]` arrays |
| **`demo7`** | Qwen 38 max XHigh | Kilo Code (VS Code) | Chained Node/Entry Object table |
| **`demo8`** | Gemini 3.7 Flash High | Kilo Code (VS Code) | Chained Node/Entry Object table |
| **`demo9`** | Gemini 3.8 Flash High | Antigravity (VS Code) | Flat parallel `Object[]` arrays |
| **`demo11`** | Gemini 3.7 Flash High | Antigravity Rework | 100% Mutation Killed |

---

## Running the Test Suite & Generating Reports

The project uses Maven multi-module architecture with a dedicated `coverage-report` module that aggregates metrics and generates interactive executive reports.

### Step 1: Run Unit Tests & Collect JaCoCo Coverage
Runs all unit tests across all reactor modules (`demo0` through `demo9`, `demo11`, and `coverage-report`), creates Surefire test result records, and generates JaCoCo test execution data per submodule:
```bash
mvn clean test
```

### Step 2: Generate Aggregated JaCoCo Code Coverage Report
Aggregates coverage metrics across all submodules into a unified multi-module report:
```bash
mvn verify -pl coverage-report
```
- Aggregated Report: `coverage-report/target/site/jacoco-aggregate/index.html`

### Step 3: Run PIT Mutation Coverage (Optional / Full Evaluation)
Executes PIT mutation testing across submodules to produce `mutations.csv` and mutation analysis reports:
```bash
mvn test-compile pitest:mutationCoverage
```
- Module Output: `<module>/target/pit-reports/`

### Step 4: Run JMH Cross-Project Microbenchmarks
Executes the microbenchmarks comparing all `FastHashMap` implementations (Demo 0 serves as the baseline):

- **Quick Sanity Run (~30s)**:
  ```bash
  mvn test-compile exec:java \
    -Dexec.mainClass="org.jugsaxony.report.GlobalJmhBenchmark" \
    -Dexec.classpathScope="test" \
    -pl coverage-report \
    -Dexec.args="--quick"
  ```

- **Full Measurement Run**:
  ```bash
  mvn test-compile exec:java \
    -Dexec.mainClass="org.jugsaxony.report.GlobalJmhBenchmark" \
    -Dexec.classpathScope="test" \
    -pl coverage-report
  ```
- Generates `target/reports/jmh-results.json`, `target/reports/jmh-report.html`, and `target/reports/jmh-report.md`.

### Step 5: Generate JOL Memory Footprint & Master Executive Dashboard
Runs Java Object Layout (JOL) memory analysis, parses test counts, JaCoCo coverage XMLs, mutation scores, and JMH metrics, then generates the consolidated master dashboard:
```bash
mvn test -pl coverage-report
```
Alternatively, invoke the generator directly:
```bash
mvn test-compile exec:java \
  -Dexec.mainClass="org.jugsaxony.report.GlobalDashboardGenerator" \
  -Dexec.classpathScope="test" \
  -pl coverage-report
```

---

## Generated Reports Overview

All consolidated reports are saved in `target/reports/`:

| Report | File | Description |
| :--- | :--- | :--- |
| **Master Dashboard** | `target/reports/index.html` | Complete interactive dashboard aggregating Quality, JOL memory footprint, and JMH throughput |
| **Markdown Summary** | `target/reports/global-dashboard.md` | Executive summary formatted for Markdown documentation & GitHub |
| **JOL Report** | `target/reports/jol-report.html` | Shallow/deep retained memory and object layout analysis |
| **JMH Report** | `target/reports/jmh-report.html` | Read hit/miss and write throughput comparison charts & tables |
| **Coverage Aggregate** | `target/reports/coverage-aggregate/index.html` | Aggregated multi-module JaCoCo coverage drill-down |

---

## End-to-End Execution One-Liner

To run the standard verification, aggregated coverage, quick JMH benchmark, and dashboard generation in a single command:

```bash
mvn clean test && \
mvn verify -pl coverage-report && \
mvn test-compile exec:java -Dexec.mainClass="org.jugsaxony.report.GlobalJmhBenchmark" -Dexec.classpathScope="test" -pl coverage-report -Dexec.args="--quick" && \
mvn test -pl coverage-report
```
