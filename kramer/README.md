# Kramer Core

The core autolayout engine. Takes a schema tree and JSON data, measures content, and produces a JavaFX control tree with adaptive table/outline layout.

## Architecture

### Schema (`schema` package)

- `SchemaNode` — sealed abstract base. Two variants:
  - `Relation` — composite node with children. Maps to JSON objects/arrays. Supports auto-folding of single-child relations.
  - `Primitive` — leaf node. Maps to scalar JSON values.
- `SchemaPath` — immutable path addressing a node in the schema tree. Solves field-name uniqueness across nesting levels.

### Layout engine

The pipeline runs in `SchemaNodeLayout.autoLayout()`:

1. **Measure** (`PrimitiveLayout.measure()`, `RelationLayout.measure()`) — walks the data, computes P90 content widths via statistical sampling, determines variable-length vs fixed-length classification.

2. **Layout** (`RelationLayout.layout()`) — decides table vs outline mode. A constraint solver (`ExhaustiveConstraintSolver`) enumerates render-mode assignments globally, or a greedy per-node check falls back when the solver isn't available. The `readableTableWidth()` threshold ensures table mode is only chosen when columns have enough room for content.

3. **Justify** (`RelationLayout.justifyColumn()`) — two-pass minimum-guarantee distribution. All children get at least their label width, then surplus is distributed proportionally based on measured content width above the minimum.

4. **Compress** (`ColumnSet.compress()`) — outline mode packs fields into multicolumn layouts. DP-optimal column partitioning via `ColumnPartitioner` (painter's partition problem, O(N²K)) minimizes max column height. Relations always get their own full-width column set.

5. **Build** (`RelationLayout.buildControl()`) — produces the JavaFX control tree. Table mode creates `NestedTable` with `ColumnHeader`, `NestedRow`, `NestedCell`. Outline mode creates `Outline` with `Span`, `OutlineColumn`, `OutlineElement`. Labels rotate vertical when width-constrained.

### Layout types

- `SchemaNodeLayout` — sealed base for computed layouts
  - `PrimitiveLayout` — layout for scalar values. Handles text, badges, bar charts, sparklines.
  - `RelationLayout` — layout for composite nodes. Chooses table, outline, or crosstab mode.

### AutoLayout control

`AutoLayout` is the top-level JavaFX control (`AnchorPane`). It manages the full pipeline:
- Accepts a `SchemaNode` root and `JsonNode` data
- Runs measure → solver → layout → justify → compress → build on resize
- Caches layout decisions for stable widths
- Supports incremental re-measure when data changes (P90 bucket comparison)

### Query state (`query` package)

A 7-layer interaction model (SIEUFERD-inspired):

- `LayoutQueryState` — per-field overrides for visibility, sort, filter, formula, aggregate, render mode, and more. Implements `LayoutStylesheet` so the layout engine reads overrides transparently.
- `FieldState` — immutable snapshot of user intent for one schema path (14 fields).
- `InteractionHandler` — dispatches `LayoutInteraction` events to query state. Supports undo/redo via JSON snapshots.
- `LayoutInteraction` — sealed event hierarchy: SortBy, ClearSort, ToggleVisible, SetFilter, ClearFilter, SetRenderMode, SetFormula, ClearFormula, SetAggregate, ClearAggregate, SetHideIfEmpty, ResetAll.
- `InteractionMenuFactory` — builds context menus from query state.
- `ColumnSortHandler` — click-to-sort on column headers with visual indicators.
- `FieldSelectorPanel` — TreeView with visibility checkboxes, state badges, click-to-scroll.
- `FieldInspectorPanel` — detail panel for all field properties with inline editing.
- `ExpressionEditor` — inline expression editor with real-time parse validation.

### Expression language (`expression` package)

A simple expression language for filter, formula, sort, and aggregate operations:
- Field references: `$fieldName`
- Arithmetic: `+`, `-`, `*`, `/`
- Comparison: `>`, `<`, `>=`, `<=`, `==`, `!=`
- Aggregates: `sum()`, `count()`, `avg()`, `min()`, `max()`
- Parsed by `ExpressionParser`, evaluated by `ExpressionEvaluator` with AST caching.

### Virtualization (`flowless` package)

Custom `VirtualFlow` for efficient rendering of large lists. Forked from the Flowless library with modifications for Kramer's cell model (focus traversal, selection, keyboard navigation).

### Styling (`style` package)

- `Style` — factory for layout cells. Creates `PrimitiveStyle`/`RelationStyle` by measuring invisible JavaFX scenes.
- `PrimitiveStyle` — cell factories for text, badges, bars, sparklines.
- `RelationStyle` — insets, cardinality limits, outline column parameters.
- CSS-driven: each component has a co-located `.css` file. User stylesheets override `default.css`.

## Testing

1017 tests covering layout algorithm, width distribution, mode selection, resize adaptation, data visibility, expression evaluation, query state, interaction handling, DP column partitioning, and contract fixture generation.

```bash
mvn test                              # all tests
mvn test -Dtest=LayoutModeSelectionTest  # specific test
```

The E2E test framework (`com.chiralbehaviors.layout.test`) provides:
- `LayoutTestHarness` — runs the full synchronous pipeline and captures rendered output
- `LayoutTestResult` — snapshot with query methods for data visibility, field widths, truncation
- `LayoutFixtures` — 4 schema/data fixtures (flat, nested, deep, wide)
