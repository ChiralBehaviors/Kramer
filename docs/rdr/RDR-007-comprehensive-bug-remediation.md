---
title: "Comprehensive Layout Engine Bug Remediation"
id: RDR-007
type: Bug
status: accepted
accepted_date: 2026-03-10
priority: P1
author: Hal Hildebrand
reviewed-by: self
created: 2026-03-09
related_issues:
  - "RDR-001 through RDR-006 (all closed)"
  - "debug-kramer-comprehensive-bug-inventory-2026-03-09 (T3)"
---

# RDR-007: Comprehensive Layout Engine Bug Remediation

## Context

A 4-agent parallel deep analysis of the Kramer `kramer` module (2026-03-09) identified **26 unique bugs** across the layout pipeline, VirtualFlow/cell system, Style/CSS measurement, and rendering controls. Several bugs were independently confirmed by multiple agents. The goal is systematic remediation — not ad-hoc patches — organized into three phases by dependency and risk.

## Problem Statement

The Kramer autolayout engine has accumulated correctness bugs across its 7-year history. While the core algorithm faithfully implements the Bakke 2013 paper, implementation-level issues cause:
- **Memory leaks** from missing dispose() calls (grows with every window resize)
- **Measurement inflation** from double-counted padding and two-line height measurement
- **Layout errors** from inset stacking, wrong text-wrapping width, and header sizing
- **Interaction bugs** from coordinate-space errors and incomplete listener management

These bugs are currently masked by three factors: (1) default CSS sets most insets to 0, (2) measurement inflation is uniform so layouts look proportional, and (3) most users don't customize CSS. But they prevent correct behavior with custom stylesheets and cause progressive memory degradation.

## Research Findings

### Finding 1: Resource Leaks (Confidence: HIGH)
- **Source**: VirtualFlow agent + Style/CSS agent (cross-confirmed)
- **Classification**: Verified
- PrimitiveList.dispose() never calls super.dispose() — leaks navigator, sizeTracker, cellListManager
- VirtualFlow.dispose() doesn't unbind mouseHandler
- OutlineElement has no dispose() — inner cells never cleaned up
- FocusTraversalNode listeners never removed

### Finding 2: LabelStyle Measurement Inflation (Confidence: HIGH)
- **Source**: Two independent Style/CSS agents (cross-confirmed)
- **Classification**: Verified
- getLineHeight() measures a 2-line string, inflating all heights ~2x
- Constructor double-counts padding via `Style.add(getInsets(), getPadding())`
- System has been implicitly tuned around the inflation

### Finding 3: Table Height Inset Stacking (Confidence: >95%)
- **Source**: All 3 non-paper agents (triple-confirmed)
- **Classification**: Verified
- rowVerticalInset counted (N+2) times instead of once
- tableVerticalInset counted 2x instead of once
- Latent with default CSS (insets=0), manifests with custom CSS

### Finding 4: Text Wrapping Height Underestimate (Confidence: >95%)
- **Source**: Layout pipeline agent
- **Classification**: Verified
- calculateRowHeight() passes parent's justifiedWidth to primitives
- Text wrapping line count computed against full table width, not column width
- Causes text clipping when content wider than column but narrower than table

### Finding 5: Hit-Testing Coordinate Error (Confidence: HIGH)
- **Source**: VirtualFlow agent + Style/CSS agent (cross-confirmed)
- **Classification**: Verified
- LayoutContainer.hit(x,y) uses parent coords where local coords expected
- Affects click targeting for all non-first children in containers

## Decision

Systematic three-phase remediation:

### Phase 1: Resource Leaks (4 bugs, low-risk, independent)
Each fix is 1-5 lines. No interaction between fixes. No visual regression risk.

| Bug | Fix |
|-----|-----|
| L1: PrimitiveList.dispose() | Replace entire body with `super.dispose()` (VirtualFlow.dispose handles scrollHandler; after L2, mouseHandler too). Remove redundant explicit unbind calls to avoid double-unbind. |
| L2: VirtualFlow.dispose() mouseHandler | Add `mouseHandler.unbind()` to VirtualFlow.dispose(). Note: L1 must be implemented aware of L2 to avoid double-unbind. |
| L3: OutlineElement dispose | Add dispose() override |
| L4: FocusTraversalNode listeners | Track and remove in unbind() |

### Phase 2: Layout & Interaction Correctness (12 bugs, incremental)
Fix in sub-phases to isolate regressions:

**Phase 2A — Table layout (T1-T6):**
- T1: calculateRowHeight → use `child.getJustifiedWidth()`
- T2: Remove rowVerticalInset from calculateRowHeight, single-site inset addition. **Also fix NestedTable constructor** (lines 63-65) which adds tableVerticalInset + rowVerticalInset on top of layout.getHeight() — must remove this duplicate addition to complete the fix.
- T3+T4: Fix `calculateTableHeight()` line 431 which bypasses `columnHeaderHeight()` by writing directly to the field without including own label height. Then fix ColumnHeader proportional split (line 78: `height/2.0` → allocate `labelStyle.getHeight()` to label, remainder to nested).
- T5: Add nestedHorizontalInset to table mode decision
- T6: Fix adjustHeight threshold consistency

**Phase 2B — Interaction (I1-I6):**
- I1: Fix mouse click coordinate path in LayoutContainer.bind() handlers — convert mouseEvent coordinates to target cell's local space before passing to hit(). Note: hitScene() (scene-coordinate path) already works correctly via sceneToLocal(). The bug is in the non-scene path used by singleClick/doubleClick/tripleClick handlers.
- I2: Update cursorState on mouse click
- I3: Wrap ListChangeListener body in `while (c.next())`
- I4: Page scroll by viewport height
- I5: Guard against empty cell list in hit()
- I6: Guard stale indices in clearSelection()

**Phase 2C — Style/rendering (S1-S2):**
- S1: Add autoLayout() call in stylesheet change listener
- S2: Subtract bulletWidth from label in OutlineElement

### Phase 3: Measurement Foundation (2 bugs, systemic)
Fix M1 + M2 together. This changes ALL spacing globally.

**Phase ordering rationale**: Phase 3 changes the measurement baseline that Phase 2A fixes operate on (e.g., T4's proportional split uses `labelStyle.getHeight()` which Phase 3 corrects). Two viable approaches: (a) execute Phase 3 first in an isolated branch, establish correct measurement baseline, then apply Phase 2A fixes against correct values — cleaner but higher initial risk; (b) retain Phase 1→2→3 order but re-run all Phase 2A visual regression after Phase 3. **Preferred: option (a)** — do Phase 3 measurement correction first, then Phases 1 and 2 against the corrected baseline. Phase 1 (resource leaks) and Phase 2B/2C (interaction, style) are measurement-independent and can be done in any order.

**Revised execution order**: Phase 3 → Phase 1 → Phase 2A → Phase 2B → Phase 2C.

Steps for Phase 3:
1. Verify OpenJFX 25 `Label.getInsets()` vs `Label.getPadding()` contract — confirm M2 diagnosis is correct for this JavaFX version before applying fix
2. LabelStyle.getLineHeight(): Use single-line measurement string
3. LabelStyle constructor: Use `label.getInsets()` alone (remove getPadding() addition)
4. Run StandaloneDemo, visually verify all layouts
5. Adjust default CSS padding values if needed to restore desired spacing — note this affects ALL users, not just custom-CSS users
6. Document the new (correct) measurement semantics

## Consequences

### Positive
- Eliminates progressive memory degradation
- Enables correct behavior with custom CSS stylesheets
- Fixes text clipping in table columns
- Fixes mouse click targeting in all container types
- Brings measurements to correct values (removes hidden inflation)

### Negative
- Phase 3 changes visual output globally — requires careful regression testing
- Some users may have CSS values tuned to the inflated measurements

### Risks
- **M2 platform dependency**: Whether `Label.getInsets()` includes padding depends on OpenJFX version. Must verify against OpenJFX 25 before applying fix — if getInsets() does NOT include padding in this version, M2's fix would remove legitimate inset accounting. Mitigated by Test Plan verification step.
- Phase 3 measurement fix affects ALL users (including default CSS), not just custom-CSS users. Default CSS padding values may need adjustment to maintain visual parity.
- Interaction fixes (Phase 2B) should be tested with keyboard + mouse workflows
- T5 (nestedHorizontalInset in table decision) interacts with RDR-006's UseTable comparison fix — combined effect needs verification against sample schemas

## Test Plan

### Phase 3 (Measurement Foundation)
- **M2 verification**: Before fixing, run test program that prints `label.getInsets()` and `label.getPadding()` for a styled Label in OpenJFX 25. Confirm padding appears in both (double-count) or only in getInsets() (no double-count). Proceed only if double-count confirmed.
- **Visual regression**: Screenshot StandaloneDemo at 1000x700 before and after. Compare table header heights, outline label widths, row heights. Document CSS adjustment delta if defaults change.
- **Measurement assertions**: Add unit test verifying `LabelStyle.getHeight()` ≈ single-line-height + insets (not 2x).

### Phase 1 (Resource Leaks)
- **Memory verification**: Run StandaloneDemo, resize window 50 times, check that VirtualFlow cell count and listener count remain bounded (not growing linearly with resize count).
- **Dispose correctness**: Verify `PrimitiveList.dispose()` calls through to `VirtualFlow.dispose()` — no double-unbind exceptions.

### Phase 2A (Table Layout)
- **T1 text wrapping**: Create schema with 5 columns, one with long text (~150px). Verify text wraps to 2 lines in 100px column (not clipped to 1).
- **T2 inset stacking**: Apply custom CSS `.nested-row { -fx-padding: 5; }`. Verify table height = N×(elementHeight + rowCellVI) + headerHeight + rowVI + tableVI (no N× multiplier on rowVI).
- **T3+T4 header sizing**: 2-level nested table. Verify parent relation label and child headers are proportionally sized (not 50/50).
- **T5 table mode decision**: At boundary width where table barely fits, verify no column clipping.
- **T6 adjustHeight**: Table with 10 rows and delta=3. Verify total height increase ≈ 3px, not 10px.

### Phase 2B (Interaction)
- **I1 hit-testing**: Click on second, third, fourth columns in a table row. Verify correct cell is selected (not always first).
- **I2 mouse→keyboard**: Click a cell, then press arrow key. Verify navigation starts from clicked cell.
- **I3 batch changes**: Call setAll() on items list. Verify all changes processed (no silent drops).
- **I4 paging**: Press PAGE_DOWN. Verify scroll distance ≈ viewport height.
- **I5 empty flow**: Click on VirtualFlow with no items. Verify no exception.
- **I6 clear selection**: Change data, then clear selection. Verify no exception.

### Phase 2C (Style/Rendering)
- **S1 stylesheet reload**: Change stylesheet programmatically. Verify layout updates immediately without requiring resize.
- **S2 bullet overflow**: Enable bullets. Verify outline rows don't overflow column width.

## Implementation Plan

See T3 knowledge store entries for detailed fix instructions per bug:
- `debug-kramer-comprehensive-bug-inventory-2026-03-09`
- `debug-kramer-resource-leaks-inventory`
- `debug-kramer-labelstyle-measurement-bugs`
- `debug-kramer-text-wrapping-height-bug`
- `debug-kramer-table-height-inset-stacking`
- `debug-kramer-interaction-bugs-inventory`
- `debug-kramer-style-rendering-bugs`
