---
title: Layout E2E Test Framework
date: 2026-03-20
status: approved
bead: none
---

# Layout E2E Test Framework

## Problem

Layout tests are scattered across 5 files with different approaches,
inconsistent assertions, and no shared infrastructure. Each test
reinvents schema building, pipeline execution, and scene graph traversal.

## Approach: Layered test harness

### Layer 1: `LayoutTestHarness`
Reusable infrastructure class (not a test). Takes schema, data, width,
height. Runs synchronous pipeline on FXAT. Returns `LayoutTestResult`.

### Layer 2: `LayoutTestResult`
Value object with query methods: isTableMode, getRenderedTexts,
getDataTexts, getFieldWidths, getZeroWidthLabels, getTruncatedLabels.

### Layer 3: `LayoutFixtures`
Schema + data builders: flat, nested, deep, wide.

### Layer 4: Test classes
- `LayoutModeSelectionTest` — table/outline chosen correctly
- `LayoutDataVisibilityTest` — data values present and non-zero width
- `LayoutWidthFairnessTest` — no starvation, no domination
- `LayoutResizeTest` — single instance resize, data survives

## Invariants

1. No exception at any width
2. Scene graph is non-empty
3. Data texts present (not just headers)
4. No data label has width <= 0
5. Table mode: every child justified >= labelWidth
6. Table mode: no child > 80% of total width
7. Mode transition is monotonic
8. Outline fieldWidth >= 0

## Replaces

FullPipelineLayoutTest, OutlineDataVisibilityTest, WidthJustificationTest,
LayoutTransitionTest. OutlineFieldWidthTest stays (headless column math).
