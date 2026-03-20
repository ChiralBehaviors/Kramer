# Design: Frozen Property + AggregatePosition Rendering

**Date**: 2026-03-19
**Beads**: Kramer-t36s (Frozen), Kramer-knh6 (AggregatePosition)
**Status**: Approved

## Frozen Property (Kramer-t36s)

**Purpose**: Guard property that prevents user mutation of a field's query state.

**Design decisions**:
- Guard-only in InteractionHandler — no new LayoutInteraction variant
- Set programmatically via `LayoutQueryState.setFrozen(path, true)`, not via user gesture
- InteractionHandler checks `frozen` before applying any mutating interaction
- InteractionMenuFactory disables menu items when frozen
- No visual indicator beyond disabled menu items

**Changes**:
1. `FieldState` — add `Boolean frozen` (10th field, default null = false)
2. `LayoutPropertyKeys` — add `FROZEN = "frozen"`
3. `LayoutQueryState` — add `setFrozen(SchemaPath, Boolean)` following existing setter pattern
4. `InteractionHandler.apply()` — add frozen guard before all mutating cases
5. `InteractionMenuFactory` — disable items when `frozen == true`
6. Tests: frozen field rejects SortBy, ToggleVisible, SetFilter, SetFormula, SetAggregate, SetRenderMode

## AggregatePosition Rendering (Kramer-knh6)

**Purpose**: Display computed aggregate results (from `RelationLayout.aggregateResults`) as a footer row in NestedTable.

**Design decisions**:
- Footer row below VirtualFlow in NestedTable
- Styled via CSS class `aggregate-footer` / `aggregate-cell`
- Only renders when aggregateResults is non-empty AND position is "footer"
- Single rendering position for now (footer only) — YAGNI on header annotation
- Aggregate values formatted via `Object.toString()` initially

**Changes**:
1. `FieldState` — add `String aggregatePosition` (11th field, default null = hidden)
2. `LayoutPropertyKeys` — add `AGGREGATE_POSITION = "aggregate-position"`
3. `LayoutQueryState` — add `setAggregatePosition(SchemaPath, String)`
4. `NestedTable` — after VirtualFlow, conditionally add footer HBox with aggregate cells
5. `RelationLayout` — expose aggregate results to NestedTable constructor
6. CSS — add `aggregate-footer` and `aggregate-cell` styles
7. Tests: aggregate expression produces footer row with correct values; null position hides footer

## Combined FieldState Change

```java
public record FieldState(
    Boolean visible,
    String renderMode,
    Boolean hideIfEmpty,
    String sortFields,
    String filterExpression,
    String formulaExpression,
    String aggregateExpression,
    String sortExpression,
    String pivotField,
    Boolean frozen,              // NEW
    String aggregatePosition     // NEW
)
```

`EMPTY` sentinel updated to 11 nulls.
