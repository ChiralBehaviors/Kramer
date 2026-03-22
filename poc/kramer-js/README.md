# Kramer JS — Proof of Concept

TypeScript/React port of the Kramer autolayout engine, validating the feasibility of the JavaScript port (RDR-032).

## What it does

Takes a schema (Relation/Primitive tree) and JSON data, runs the Bakke autolayout pipeline (measure → layout → justify → compress), and renders an adaptive table/outline layout that switches mode based on available width.

## Quick start

```bash
# Dev server (interactive demo)
npm install
npm run dev

# Run tests
npm test

# Or via Maven
mvn test -pl :kramer-js-poc
```

## Architecture

```
src/core/           Pure TypeScript — zero DOM dependency
  schema.ts         SchemaNode, Relation, Primitive, SchemaPath, extractField
  measure.ts        MeasurementStrategy interface + browser/test implementations
  layout.ts         Layout decision (table vs outline), justify, DP partitioner
  types.ts          LayoutResult, CompressResult, MeasurementStrategy

src/react/          React renderer
  AutoLayout.tsx    Top-level component with ResizeObserver
  Table.tsx         Nested table renderer (headers + rows)
  Outline.tsx       Outline renderer (label:value pairs + nested tables)

src/App.tsx         Course catalog demo (departments → courses)
```

### Core pipeline (portable, no DOM)

1. **Measure** — compute P90 text widths per field using `MeasurementStrategy`
2. **Layout** — decide table vs outline based on `readableTableWidth` vs available width
3. **Justify** — two-pass minimum-guarantee width distribution (Bakke §3.3)
4. **Compress** — DP-optimal column partitioning (painter's partition, O(N²K))

### React renderer

- `<AutoLayout>` observes container width via `ResizeObserver`, re-runs pipeline on resize
- At wide widths → `<Table>` with column headers and data rows
- At narrow widths → `<Outline>` with label:value pairs and nested tables
- Mode switches automatically at the `readableTableWidth` threshold

## Tests

34 tests across 4 files:

| Suite | Tests | What it validates |
|-------|-------|-------------------|
| `schema.test.ts` | 7 | Schema types, path building, field extraction |
| `layout.test.ts` | 10 | Mode decisions, threshold switching, justify, DP partitioner |
| `rendering.test.tsx` | 10 | Table/Outline/AutoLayout render data values in DOM |
| `mode-switching.test.tsx` | 7 | TABLE↔OUTLINE at threshold, different DOM structures, nested table-inside-outline |

## Relationship to Java Kramer

This PoC ports the core algorithm from the Java `kramer` module. The `LayoutView` interface (extracted in Kramer-49hy) defines the portable render-context protocol that both the Java and TypeScript renderers consume.

| Java | TypeScript |
|------|-----------|
| `SchemaNode` (sealed) | `SchemaNode` (discriminated union) |
| `MeasurementStrategy` | `MeasurementStrategy` |
| `ExhaustiveConstraintSolver` | `decideMode()` (greedy, PoC-scoped) |
| `RelationLayout.justifyColumn()` | `justifyChildren()` |
| `ColumnPartitioner.dpOptimal()` | `dpPartition()` |
| `NestedTable` / `Outline` (JavaFX) | `<Table>` / `<Outline>` (React) |

## What's NOT included (PoC scope)

- VirtualFlow / virtualization (uses simple DOM list)
- GraphQL integration
- Interaction (sort, filter, context menus, undo/redo)
- Expression language
- Crosstab rendering
- Exhaustive constraint solver (uses greedy per-node)
