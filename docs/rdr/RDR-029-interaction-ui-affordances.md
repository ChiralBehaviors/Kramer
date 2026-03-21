---
title: "Interaction UI Affordances"
id: RDR-029
type: Feature
status: closed
priority: P0
author: Hal Hildebrand
created: 2026-03-19
accepted_date: 2026-03-19
closed_date: 2026-03-19
close_reason: implemented
reviewed-by: self
related: RDR-027, RDR-026, RDR-021, RDR-028, RDR-005, RDR-020
---

# RDR-029: Interaction UI Affordances

## Metadata
- **Type**: Feature
- **Status**: closed
- **Priority**: P0
- **Created**: 2026-03-19
- **Accepted**: 2026-03-19
- **Closed**: 2026-03-19
- **Close Reason**: implemented
- **Reviewed-by**: self
- **Related**: RDR-027 (Interaction Model), RDR-026 (QueryState), RDR-021 (Expression Language), RDR-028 (Query Rewriter), RDR-005 (Keyboard Navigation), RDR-020 (GraphQL AST Retention)

---

## Problem Statement

Kramer has complete backend infrastructure for interactive query manipulation — QueryState (Layer 2, RDR-026), InteractionHandler (Layer 7, RDR-027), QueryRewriter (Layer 1↔2 bridge, RDR-028), and the Expression Language (RDR-021) — but ZERO user-facing UI controls. Gap analysis (2026-03-19) found approximately 5% SIEUFERD UI coverage despite 100% backend infrastructure readiness.

The feedback loop between user intent and query modification is entirely missing. Users cannot sort, filter, hide fields, set formulas, or change render modes through the UI. All manipulation requires programmatic configuration.

---

## Prior Art & Critique Findings Addressed

- **SIGMOD 2016 (SIEUFERD)**: Tables 1–6 define the complete set of UI affordances for schema-independent database interaction. Column header sort indicators, context menus for query operations, field selector panels, and formula editors are all specified.
- **CIDR 2011**: Schema-independent UI vision requires that all query operations be accessible through direct manipulation of the result display.
- **RDR-027 RF-1**: Explorer module has zero context menus, click handlers, or mouse event handlers. All interaction code must be built from scratch.
- **RDR-027 RF-3**: ColumnHeader has zero interaction — no event handlers, no sort indicators, no visual state.

---

## Dependencies

- **RDR-027** (Interaction Model): DONE. Provides `LayoutInteraction` sealed hierarchy and `InteractionHandler`.
- **RDR-026** (QueryState): DONE. Provides `LayoutQueryState` typed mutation API.
- **RDR-021** (Expression Language): DONE. Provides parser for filter/formula/aggregate expressions.
- **RDR-028** (Query Rewriter): DONE. Provides Layer 1↔2 bridge for pushing operations to GraphQL.
- **RDR-020** (GraphQL AST Retention): DONE. Transitive via RDR-028; required if server-side rewrite is active. `SchemaContext.fieldIndex()` provides retained AST.
- **RDR-005** (Keyboard Navigation): DONE. Provides `FocusController` for cell-level focus. Phase 5 attaches `KeyEvent` handlers in `explorer` module. Note: `ColumnHeader` does not participate in `FocusController`'s focus graph — Phase 5 shortcuts operate on the selected column header (via mouse click state), not `FocusController` focus.

---

## Proposed Solution

### Phase 1: Column Header Interactions

Sort arrows and active-state indicators on `ColumnHeader` in table mode:

- Sort arrow icons (▲/▼) rendered alongside column label text
- Click-to-sort state machine: no sort → ascending → descending → clear (consistent with RDR-027 sort direction encoding)
- Active sort state visualization: highlighted header background, bold label, directional arrow
- Visual indicator for page-local sort when pagination is active AND the backend lacks `SORT` capability (per `ServerCapabilities` from RDR-028); suppressed when sort is server-side

Implementation attaches handlers post-creation in `NestedTable` (per RDR-027 RF-3, option (a)).

### Phase 2: Context Menu System

Right-click context menus on headers and cells, mapping 1:1 to the `LayoutInteraction` sealed hierarchy:

**Header context menu items:**
- Sort Ascending / Sort Descending → `SortBy`
- Clear Sort → `ClearSort`
- Filter... → `SetFilter` (opens dialog)
- Clear Filter → `ClearFilter`
- Hide → `ToggleVisible`
- Set Formula... → `SetFormula` (opens dialog)
- Clear Formula → `ClearFormula`
- Set Aggregate... → `SetAggregate` (opens dialog)
- Clear Aggregate → `ClearAggregate`
- Render Mode → submenu (Table / Outline / Sparkline) → `SetRenderMode` (Relation headers only)

**Cell context menu items:**
- Filter by This Value → `SetFilter(path, "= <cell_value>")`
- Copy Value

**Root context menu:**
- Reset All → `ResetAll()`

Menu items are enabled/disabled based on node type (Primitive vs Relation) and current state. Context menus attach uniformly across container types via `ContextMenuEvent.CONTEXT_MENU_REQUESTED` (per RDR-027 RF-6).

### Phase 3: Field Selector Panel

A collapsible side panel with a checkbox tree mirroring the `SchemaNode` hierarchy:

- Each node has a visibility checkbox dispatching `ToggleVisible`
- Badges/icons show current state: active sort direction, active filter, render mode override, formula/aggregate presence
- Tree structure reflects `Relation`/`Primitive` nesting
- Panel toggle via toolbar button or keyboard shortcut

### Phase 4: Formula/Filter Editor

Replaces the `TextInputDialog` placeholder (RDR-027 scope exclusion) with purpose-built editors:

- **Filter expression editor**: text field with field name autocomplete from schema, syntax validation against the RDR-021 parser, error highlighting
- **Formula editor**: inline formula editor for computed fields, same autocomplete and validation
- **Aggregate editor**: dropdown for common aggregates (SUM, COUNT, AVG, MIN, MAX) plus free-form expression entry
- All editors validate expressions against the RDR-021 parser before dispatching to `InteractionHandler`

### Phase 5: Keyboard Shortcuts

Keyboard shortcuts for common query operations, integrated with `FocusController` (RDR-005):

- Last-clicked column header + shortcut key → sort toggle
- Focused cell (via FocusController) + shortcut → filter by value
- Global shortcuts for field selector toggle, reset all
- Shortcut hints displayed in context menu items

---

## Scope Exclusions

- **Drag-to-reorder columns**: Requires column position tracking. Deferred.
- **Drag-to-resize columns**: Requires direct manipulation of `justifiedWidth`. Deferred (per RDR-027).
- **Inline cell editing**: Requires GraphQL mutation infrastructure. Out of scope.
- **Undo/redo stack**: `ResetAll` remains the escape hatch (per RDR-027).
- **Frozen columns**: Deferred (per RDR-027).

---

## Implementation Plan

### Phase 1: Column Header Interactions
- Add sort arrow rendering to `ColumnHeader` (▲/▼ `Label` or `Region` with CSS)
- Wire click handler on `ColumnHeader` dispatching `SortBy` through `InteractionHandler`
- CSS classes for active sort state (`sorted-asc`, `sorted-desc`)
- Pagination-aware sort indicator (warning badge when page-local)
- Tests: click sequence produces correct sort state transitions

### Phase 2: Context Menu System
- **Already exists** (RF-4): `InteractionMenuFactory` with Sort, Filter, Hide, Formula, Aggregate, Render Mode, Reset All menu items. Wired via `CONTEXT_MENU_REQUESTED` on `AutoLayout` in `AutoLayoutController`. Uses `TextInputDialog` placeholders for expression entry.
- **Delta**: Add `ClearSort` menu item wiring (11th variant). Validate enable/disable logic per node type (Primitive vs Relation). Add quick-action "Add SUM/COUNT" item (RF-2). Verify outline-mode context menu coverage.
- Tests: menu item selection dispatches correct `LayoutInteraction` event; `ClearSort` item appears when sort is active

### Phase 3: Field Selector Panel
- `FieldSelectorPanel` JavaFX control with `TreeView<SchemaNode>`
- Bidirectional binding: checkbox state ↔ `QueryState.getVisibleOrDefault()`
- State badges updated on `QueryState` change
- Tests: toggle checkbox → field hidden → re-layout

### Phase 4: Formula/Filter Editor
- `ExpressionEditor` control with `TextField` + validation feedback
- Autocomplete popup listing schema field names
- Integration with RDR-021 parser for syntax validation
- Tests: valid expression accepted, invalid expression rejected with error message

### Phase 5: Keyboard Shortcuts
- Key binding registration in `FocusController` extension
- Shortcut → `LayoutInteraction` dispatch
- Shortcut hints in context menu item text (e.g., "Sort Ascending  ⌘↑")
- Tests: key press on focused column triggers sort

---

## Research Findings

### RF-1: SIEUFERD User Study — Unhide Discoverability (SIGMOD 2016 §4.2)
**Status**: verified
**Source**: sieuferd_sigmod2016.pdf §4.2

Users IKN specifically looked for an action named "Unhide", like in Excel. User G also noted this issue. The paper concludes: "our user interface needs a more visible affordance for showing hidden fields." This directly motivates Phase 3 (Field Selector Panel) — the checkbox tree must show hidden fields prominently, not bury them.

**Design implication**: Hidden fields should appear in the field selector with a struck-through or dimmed style, not be removed from the tree.

### RF-2: SIEUFERD User Study — Formula Discoverability (SIGMOD 2016 §4.2)
**Status**: verified
**Source**: sieuferd_sigmod2016.pdf §4.2

Users attempted multiple incorrect approaches to create aggregates: user F tried to hide every field other than the one to be summed; user G tried to invoke the HIDE action on the header. Users expected aggregate creation to work like Excel SUM formulas. The paper suggests the system "automatically generate a sum formula above the nearest one-to-many relationship, which would then serve as an example."

**Design implication**: Phase 4 (Formula/Filter Editor) should include a quick-action "Add SUM/COUNT" affordance, not just free-form expression entry. The `SetAggregate` interaction already supports this — surface it as a one-click action in the context menu (Phase 2).

### RF-3: Related Worksheets — Show/Hide Columns Discoverability (CHI 2011 §5)
**Status**: verified
**Source**: related_worksheets_chi2011.pdf §5

Only 18% of users discovered the Show/Hide Columns feature (3 out of 17 users found it). The Firefox-style search interface had similarly low discoverability (53%). The paper notes "limited discoverability" as a key usability finding.

**Design implication**: The field selector (Phase 3) must be visually prominent — a toolbar button with icon, not just a menu item. Consider showing it by default for first-time users or when the schema has many hidden fields.

### RF-4: Existing Infrastructure Audit (Plan Audit 2026-03-19)
**Status**: verified
**Source**: Plan audit, codebase analysis

The audit discovered that several Phase 1-2 deliverables already exist:
- `InteractionMenuFactory` — context menu builder with Sort, Filter, Hide, Formula, Aggregate, Render Mode, Reset All items. Uses `TextInputDialog` placeholders for expression entry.
- `ColumnSortHandler` — click-to-sort state cycling (no sort → asc → desc → clear) with bounds-checking `hitColumn()`.
- `AutoLayoutController` — full wiring: `layoutQueryState`, `interactionHandler`, `menuFactory`, `queryRewriter`, context menu event handler via `CONTEXT_MENU_REQUESTED`.

**Remaining Phase 1 work**: Sort arrow rendering (▲/▼), `sorted-asc`/`sorted-desc` CSS classes on `ColumnHeader`, filter indicator icons. The click behavior and context menus are wired but have no visual state feedback.

### RF-5: hitSchemaPath Blocker — Fixed (Plan Audit 2026-03-19)
**Status**: resolved
**Source**: Plan audit blocker 1

`hitSchemaPathRecursive` had no bounds checking — every right-click returned the same field. Fixed: rewrote to walk JavaFX scene graph with `sceneToLocal()` + `contains()`, checking `VirtualFlow.getSchemaPath()` and `ColumnHeader.getUserData()` for `SchemaPath`.

### RF-6: ColumnSortHandler Wiring — Fixed (Plan Audit 2026-03-19)
**Status**: resolved
**Source**: Plan audit blocker 2

`ColumnSortHandler.install()` was never called — dead code. Fixed: added `postLayoutCallback` to `AutoLayout`, wired `ColumnSortHandler` in `AutoLayoutController` via `installSortHandlers()` scene-graph tree-walk. `TableHeader` now stores `SchemaPath` as `userData` on each `ColumnHeader` child node.

### RF-7: Handler Accumulation Risk (Follow-up Audit 2026-03-19)
**Status**: open
**Source**: Follow-up audit New-2

On the `buildAndInstallControl()` cache-hit path, `postLayoutCallback` fires and `installSortHandlers()` may add duplicate event handlers if `TableHeader` instances are reused. In practice idempotent (same sort cycle fires N times), but a clear-and-reinstall guard should be added during Phase 1 implementation.

### RF-8: LayoutInteraction Variant Count (Plan Audit 2026-03-19)
**Status**: verified
**Source**: Plan audit Sig-1

`LayoutInteraction` has 11 variants (not 10): `SortBy`, `ClearSort`, `ToggleVisible`, `SetFilter`, `ClearFilter`, `SetRenderMode`, `SetFormula`, `ClearFormula`, `SetAggregate`, `ClearAggregate`, `ResetAll`. `ClearSort` was added as a distinct variant beyond the original 10 in RDR-027. Success criteria updated to reference 11.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Context menus clutter the UI | Low | Standard JavaFX pattern; menu items contextually enabled/disabled |
| Field selector panel consumes layout width | Medium | Collapsible panel; starts hidden |
| Autocomplete performance on large schemas | Low | Schema trees are typically small (<100 fields) |
| Keyboard shortcut conflicts with OS/JavaFX defaults | Medium | Use modifier keys (Ctrl/Cmd+); test on macOS and Linux |
| Phase ordering creates partial UI states | Low | Each phase is independently useful; Phase 4 coexists with Phase 2's TextInputDialog until it ships |
| Duplicate sort handlers on layout cache hit (RF-7) | Medium | Clear-and-reinstall guard in `ColumnSortHandler.install()` before Phase 1 ships |

---

## Success Criteria

- [x] Column headers show sort direction indicators (▲/▼) reflecting current sort state
- [x] Click-to-sort on column headers cycles through ascending → descending → clear
- [x] Right-click context menus appear on headers and cells with appropriate menu items
- [x] All 11 `LayoutInteraction` event types are reachable from the UI
- [x] Field selector panel shows schema tree with visibility checkboxes
- [x] Formula/filter editors validate expressions against the RDR-021 parser
- [x] Keyboard shortcuts trigger query operations on focused elements
- [x] All affordances work in both outline and table rendering modes
- [x] All SIEUFERD properties 1–4, 9 (Visible, Formula, Filter, Sort, HideParentIfEmpty) are reachable from the UI without programmatic configuration
