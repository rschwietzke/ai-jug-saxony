# demo5 Documentation

This folder documents the two code areas living in the `demo5` module.

| Document | Content |
|---|---|
| [`FastHashMap-spec.md`](FastHashMap-spec.md) | Spec & implementation plan for `org.jugsaxony.demo5.FastHashMap` (and its sibling demos). |
| [`xlt-util-architecture.md`](xlt-util-architecture.md) | Everything one must know to understand the `com.xceptance.xlt.report.util` package: purpose, data model, algorithms, invariants, and pitfalls. |
| [`XLT-DATA.md`](XLT-DATA.md) | **Proposal only** – how the XLT data collection classes could be re-designed/improved. Not implemented. |

## Where is the code?

```
src/main/java/org/jugsaxony/demo5/            demo classes (FastHashMap, LRUClockMap)
src/main/java/com/xceptance/xlt/report/util/  XLT report data-collection utilities (this doc set)
```

## Test suites

Tests live next to the code under `src/test/java` and are run with `mvn test` from
the `demo5` folder (Maven multi-module project, JUnit Jupiter + AssertJ).

A few tests that pin down **known defects** are compiled but `@Disabled`; their
reasons point to [`XLT-DATA.md`](XLT-DATA.md).
