# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kramer is an automatic layout framework for structured hierarchical JSON data, built with JavaFX. It implements the algorithm from "Automatic Layout of Structured Hierarchical Reports" (Bakke, InfoVis 2013), adaptively switching between outline and nested table views to produce dense, multicolumn hybrid layouts.

Licensed under Apache 2.0.

## Build Commands

Requires Maven 3.3+ and Java 25+.

```bash
# Build all modules
mvn clean install

# Build a single module
cd kramer && mvn clean install

# Run tests for a single module
cd kramer-ql && mvn test

# Run a single test class
mvn -pl kramer-ql test -Dtest=TestGraphQlUtil

# Run explorer app (fat jar via shade plugin)
cd explorer && mvn package && java -jar target/explorer-0.0.1-SNAPSHOT-phat.jar

# Run toy-app via javafx-maven-plugin
cd toy-app && mvn javafx:run
```

## Module Structure

Six Maven modules under parent `kramer.app` (`com.chiralbehaviors.layout`):

- **kramer** — Core autolayout engine. Schema-driven layout of JSON data into JavaFX controls. No external service dependencies.
- **kramer-ql** — GraphQL integration. Parses GraphQL queries to build Kramer schemas automatically, executes queries via Jakarta RS client. Depends on `kramer`.
- **explorer** — Interactive JavaFX app for exploring GraphQL endpoints with autolayout. Includes `StandaloneDemo` (self-contained course catalog demo). Depends on `kramer-ql`. Entry point: `Launcher` (delegates to `AutoLayoutExplorer`); standalone: `StandaloneDemo.Main`.
- **toy-app** — Declarative single-page app framework using GraphQL + autolayout. Depends on `kramer-ql`. Entry point: `Launcher` (delegates to `SinglePageApp`).
- **js** — TypeScript/React port (pnpm workspaces). 5 packages: `@kramer/core`, `@kramer/measurement`, `@kramer/graphql`, `@kramer/react`, `@kramer/react-ui`. Maven integration via `frontend-maven-plugin` with corepack.
- **poc/kramer-js** — Original proof-of-concept for JS port feasibility (RDR-032).

Both `explorer` and `toy-app` produce fat jars via `maven-shade-plugin` (classifier: `phat`).

## Architecture

### Schema Layer (`kramer` — `schema` package)
- `SchemaNode` — sealed abstract base for schema tree nodes, works with Jackson `JsonNode`
- `Relation` — non-sealed composite node with children (maps to JSON objects/arrays); supports auto-folding
- `Primitive` — final leaf node (maps to scalar JSON values)
- `SchemaPath` — immutable path addressing a node in the schema tree, solves field-name uniqueness across nesting levels

### Layout Engine (`kramer` — root + `style` packages)
- `AutoLayout` — main JavaFX control (`AnchorPane`). Accepts a `SchemaNode` root and `JsonNode` data, auto-layouts on resize.
- `SchemaNodeLayout` — sealed hierarchy: `PrimitiveLayout` (text, badges, bars, sparklines) and `RelationLayout` (table, outline, crosstab)
- Pipeline: measure → layout → justify → compress → buildControl, fully synchronous within `autoLayout()`
- `ExhaustiveConstraintSolver` — enumerates render-mode assignments globally; `readableTableWidth()` threshold ensures table mode only when columns can display content readably
- `ColumnPartitioner` — DP-optimal column partitioning (painter's partition problem, O(N²K)); replaces greedy slideRight with globally optimal height balancing. Strategy interface with `dpOptimal()` and `greedy()` implementations.
- `justifyColumn()` — two-pass minimum-guarantee width distribution; all children get at least label width before surplus is distributed proportionally
- Layout adapts between rendering modes:
  - **Outline** (`outline` package) — vertical list with `OutlineColumn`, `OutlineElement`, `Span`, multicolumn packing. Relations always get their own full-width column set. Labels rotate vertical when width-constrained.
  - **Nested Table** (`table` package) — `NestedTable`, `NestedRow`, `NestedCell`, `ColumnHeader` with sort indicators and vertical header rotation
  - **Crosstab** (`table` package) — pivot-based cross-tabulation when configured

### Query State (`kramer` — `query` package)
- `LayoutQueryState` — per-field overrides (visibility, sort, filter, formula, aggregate, render mode, etc). Implements `LayoutStylesheet` so the layout engine reads overrides transparently.
- `FieldState` — immutable record with 14 fields capturing user intent per `SchemaPath`
- `InteractionHandler` — dispatches sealed `LayoutInteraction` events with undo/redo via JSON snapshots
- `InteractionMenuFactory` — builds context menus (sort, filter, aggregate, copy, filter-by-value)
- `ColumnSortHandler` — click-to-sort on column headers; idempotent installation guard
- `FieldSelectorPanel` — TreeView with visibility checkboxes, state badges, hide-if-empty per relation, click-to-scroll
- `FieldInspectorPanel` — detail panel showing all `FieldState` properties for the selected field with inline editing
- `ExpressionEditor` — inline expression editor with real-time parse validation

### Expression Language (`kramer` — `expression` package)
- Field references (`$fieldName`), arithmetic, comparison, aggregates (`sum()`, `count()`, `avg()`, `min()`, `max()`)
- `ExpressionParser` → `Expr` AST → `ExpressionEvaluator` with LRU cache

### Flowless Virtualization (`kramer` — `flowless` package)
Custom virtualized `VirtualFlow` for efficient rendering of large lists (forked/adapted from Flowless library).

### Cell System (`kramer` — `cell` package)
`LayoutCell<T>` interface with implementations: `VerticalCell`, `HorizontalCell`, `AnchorCell`, `RegionCell`, `PrimitiveList`. Focus/selection via `cell.control` subpackage (`FocusController`, `MultipleCellSelection`).

### GraphQL Integration (`kramer-ql`)
- `GraphQlUtil` — parses GraphQL query strings into `Relation` schema trees; executes queries against endpoints via Jakarta RS
- `QueryRoot` — special `Relation` subclass for multi-root queries
- `SchemaIntrospector` — discovers server capabilities for sort/filter push-down
- `QueryRewriter` — rewrites queries to push operations server-side when supported
- `TypeIntrospector` — parses GraphQL `__schema` introspection results into a browseable type tree (`IntrospectedType`, `IntrospectedField`, `TypeRef`)
- `QueryExpander` — adds/removes `Field` nodes in GraphQL Document AST using immutable `transform()` pattern; creates minimal sub-selections for relation fields (RF-6 heuristic)

### CSS Styling
Layout appearance is driven by CSS. Each component has a co-located `.css` file. User stylesheets override `default.css`. See `kramer/src/main/resources/com/chiralbehaviors/layout/default.css` for stylable class names.

### Explorer UI (`explorer`)
- `AutoLayoutController` — full interactive wiring: query state, context menus, field selector/inspector, sort handlers, column resize, undo/redo, keyboard shortcuts, schema introspection panel
- `IntrospectionTreePanel` — TreeView of types/fields from GraphQL introspection with Add/Remove buttons; wires QueryExpander for AST modification and re-execution
- `StandaloneDemo` — self-contained course catalog demo (departments → courses → sections, 3-level nesting) with full interactive pipeline; no external services
- `SchemaDiagramView` — visual representation of the relation hierarchy

### Testing (`kramer` — `test` package)
E2E test framework: `LayoutTestHarness` runs the full synchronous pipeline, `LayoutTestResult` captures rendered scene graph snapshots, `LayoutFixtures` provides 4 schema/data sets. `AutoLayoutResizeAdaptationTest` verifies resize adaptation, mode switching, column widths, and rendered data presence. `ContractFixtureGeneratorTest` emits JSON fixtures for cross-language validation. 1300 tests total (1017 kramer + 106 kramer-ql + 20 explorer + 157 TypeScript).

## Key Dependencies

- Jackson (2.19.0) — JSON data model (`JsonNode` tree)
- OpenJFX (25.0.1) — UI toolkit
- graphql-java (25.0) — GraphQL query parsing
- Jersey (3.1.11) — Jakarta RS HTTP client for GraphQL endpoints
- JUnit Jupiter (5.14.2) — testing
