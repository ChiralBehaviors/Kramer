# RDR-027: Interaction Model — Layer 7 Gestures

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P2
- **Created**: 2026-03-16
- **Related**: RDR-026 (QueryState), RDR-017 (in-layout search), RDR-015 (render modes), RDR-019 (sparklines)

---

## Problem Statement

Kramer renders structured data adaptively but provides no mechanism for users to manipulate the display interactively. There is no way to click a column header to sort, right-click to filter, hide a field, or change a rendering mode through the UI. All configuration is programmatic (LayoutStylesheet overrides or schema-level sort fields).

SIEUFERD's core insight is that display manipulation IS query manipulation — "the complete structure of a query can be encoded in the schema of the query's own result." RDR-026 (QueryState) provides the typed mutation API for Layer 2. This RDR builds the gestures (Layer 7) that drive those mutations.

---

## Prior Art & Critique Findings Addressed

- **Substantive Critique O1**: Client-side sort with cursor-based pagination produces incorrect results (only current page is sorted). This RDR must document this limitation and either gate sort interactions on non-paginated data or display a visual indicator that sort is page-local.
- **Substantive Critique S1**: FROZEN property excluded from FieldState (RDR-026). Frozen-column interaction deferred to a follow-on RDR after the interaction model is validated.
- **RDR-018 §Interaction Model**: "User gestures modify LayoutStylesheet properties, which trigger re-layout." This RDR implements that sentence.

---

## Dependencies

- **RDR-026** (QueryState): Must be implemented. Interaction events mutate `QueryState`.
- **RDR-017** (In-Layout Search): Closed. Search gestures already exist via `SearchBar` + `FocusController`. This RDR extends the gesture vocabulary, not the search infrastructure.

---

## Proposed Solution

### Typed Interaction Events

A sealed hierarchy of interaction events that represent user gestures:

```java
sealed interface LayoutInteraction permits
    SortBy, ToggleVisible, SetFilter, ClearFilter,
    SetRenderMode, SetFormula, ClearFormula,
    SetAggregate, ClearAggregate, ResetAll {

    SchemaPath path();

    record SortBy(SchemaPath path, boolean descending) implements LayoutInteraction {}
    record ToggleVisible(SchemaPath path) implements LayoutInteraction {}
    record SetFilter(SchemaPath path, String expression) implements LayoutInteraction {}
    record ClearFilter(SchemaPath path) implements LayoutInteraction {}
    record SetRenderMode(SchemaPath path, String mode) implements LayoutInteraction {}
    record SetFormula(SchemaPath path, String expression) implements LayoutInteraction {}
    record ClearFormula(SchemaPath path) implements LayoutInteraction {}
    record SetAggregate(SchemaPath path, String expression) implements LayoutInteraction {}
    record ClearAggregate(SchemaPath path) implements LayoutInteraction {}
    record ResetAll(SchemaPath path) implements LayoutInteraction {
        /** Reset for the entire tree. */
        public ResetAll() { this(null); }
    }
}
```

### InteractionHandler

Applies `LayoutInteraction` events to a `QueryState`:

```java
public class InteractionHandler {
    private final QueryState queryState;

    public void apply(LayoutInteraction event) {
        switch (event) {
            case SortBy(var path, var desc) ->
                queryState.setSortExpression(path, desc ? "-" + path.leaf() : path.leaf());
            case ToggleVisible(var path) ->
                queryState.setVisible(path, !queryState.getFieldState(path).visible());
            case SetFilter(var path, var expr) ->
                queryState.setFilterExpression(path, expr);
            // ... etc
        }
    }
}
```

`InteractionHandler` is renderer-agnostic — it lives in the `kramer` module. JavaFX bindings are in `explorer`.

### JavaFX Gesture Bindings (explorer module)

Concrete gestures wired in the `explorer` app:

| Gesture | UI Element | Interaction Event |
|---------|-----------|-------------------|
| Click column header | `ColumnHeader` in table mode | `SortBy(path, toggle)` |
| Right-click → "Hide" | Context menu on any field | `ToggleVisible(path)` |
| Right-click → "Filter..." | Context menu on Primitive | `SetFilter(path, dialog)` |
| Right-click → "Sort ascending/descending" | Context menu on Primitive | `SortBy(path, asc/desc)` |
| Right-click → "Show as sparkline/table/outline" | Context menu on Relation | `SetRenderMode(path, mode)` |
| Right-click → "Add formula..." | Context menu on Primitive | `SetFormula(path, dialog)` |
| Right-click → "Reset all" | Context menu on root | `ResetAll()` |

Dialog-based gestures (filter expression entry, formula entry) use a simple `TextInputDialog` for v1. No expression editor or autocomplete in this RDR.

### Pagination-Sort Limitation

**Documented limitation**: When data is fetched via cursor-based pagination (kramer-ql), client-side sort operates on the current page only. The user sees a sorted page, not a globally sorted result set.

**Mitigation in this RDR**: When `SortBy` is applied and the data source is paginated, the UI displays a visual indicator (e.g., sort icon with a warning badge, or tooltip "sorted within current page"). The interaction handler does not gate the sort — it applies it and documents the limitation. Correct server-side sort requires RDR-028 (Query Rewriter) to push ORDER BY to the GraphQL endpoint.

**Detection**: The `explorer` app knows whether pagination is active (it manages the GraphQL query lifecycle). A `PaginationContext` flag is passed to the gesture binding layer.

---

## Scope Exclusions

- **Frozen columns**: Requires column reordering and scroll-position locking. Deferred to a follow-on RDR after the interaction model is validated.
- **Drag-to-resize columns**: Requires direct manipulation of `justifiedWidth`. Deferred.
- **Edit mode**: Requires GraphQL mutation infrastructure (SIEUFERD property 17). Out of scope.
- **Undo/redo**: Per critique S4, premature. `ResetAll` provides the escape hatch.
- **Expression autocomplete**: Dialog uses plain text input. Autocomplete is a UX enhancement for later.

---

## Implementation Plan

### Phase 1: Event Model + Handler (kramer module)
- `LayoutInteraction` sealed hierarchy
- `InteractionHandler` applying events to `QueryState`
- Unit tests: event → QueryState mutation → FieldState verification

### Phase 2: JavaFX Context Menus (explorer module)
- Context menu factory for Primitive and Relation nodes
- Gesture bindings for sort, hide, filter, render mode
- Column header click-to-sort in table mode
- Integration test: gesture → event → QueryState → re-layout

### Phase 3: Pagination Awareness
- `PaginationContext` flag
- Visual indicator for page-local sort
- Documentation of the limitation

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Context menus on JavaFX controls are intrusive | Low | Standard JavaFX pattern; can be disabled |
| Expression input via TextInputDialog is poor UX | Medium | Acceptable for v1; autocomplete in follow-on |
| Pagination-sort confusion for users | Medium | Visual indicator + tooltip; server-side sort in RDR-028 |
| Gesture vocabulary grows unbounded | Low | Sealed interface constrains; new gestures require code change |

---

## Success Criteria

- [ ] All 10 interaction events in the sealed hierarchy are handled by `InteractionHandler`
- [ ] Column header click sorts ascending, click again sorts descending, click again clears sort
- [ ] Right-click context menu on any field offers Hide, Sort, Filter (where applicable)
- [ ] Right-click on Relation offers render mode choices (table, outline, sparkline where applicable)
- [ ] `ResetAll` clears all QueryState overrides and re-layouts to default
- [ ] Pagination-sort limitation is visually indicated when applicable
- [ ] All gestures work with both outline and table rendering modes
- [ ] Zero changes to `AutoLayout`, `RelationLayout`, or `PrimitiveLayout` core
