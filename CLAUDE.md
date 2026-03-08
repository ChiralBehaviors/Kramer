# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kramer is an automatic layout framework for structured hierarchical JSON data, built with JavaFX. It implements the algorithm from "Automatic Layout of Structured Hierarchical Reports" (Bakke, InfoVis 2013), adaptively switching between outline and nested table views to produce dense, multicolumn hybrid layouts.

Licensed under Apache 2.0.

## Build Commands

Requires Maven 3.3+ and Java 11+.

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

Four Maven modules under parent `kramer.app` (`com.chiralbehaviors.layout`):

- **kramer** — Core autolayout engine. Schema-driven layout of JSON data into JavaFX controls. No external service dependencies.
- **kramer-ql** — GraphQL integration. Parses GraphQL queries to build Kramer schemas automatically, executes queries via JAX-RS client. Depends on `kramer`.
- **explorer** — Interactive JavaFX app for exploring GraphQL endpoints with autolayout. Depends on `kramer-ql`. Entry point: `Launcher` (delegates to `AutoLayoutExplorer`).
- **toy-app** — Declarative single-page app framework using GraphQL + autolayout. Depends on `kramer-ql`. Entry point: `Launcher` (delegates to `SinglePageApp`).

Both `explorer` and `toy-app` produce fat jars via `maven-shade-plugin` (classifier: `phat`).

## Architecture

### Schema Layer (`kramer` — `schema` package)
- `SchemaNode` — abstract base for schema tree nodes, works with Jackson `JsonNode`
- `Relation` — composite node with children (maps to JSON objects/arrays); supports auto-folding
- `Primitive` — leaf node (maps to scalar JSON values)

### Layout Engine (`kramer` — root + `style` packages)
- `AutoLayout` — main JavaFX control (`AnchorPane`). Accepts a `SchemaNode` root and `JsonNode` data, auto-layouts on resize.
- `SchemaNodeLayout` — computed layout for a schema node at a measured width
- `Style` — factory for layout cells; controls CSS stylesheets and creates `PrimitiveLayout`/`RelationLayout`
- Layout adapts between two rendering modes:
  - **Outline** (`outline` package) — vertical list with `OutlineColumn`, `OutlineElement`, `Span`
  - **Nested Table** (`table` package) — `NestedTable`, `NestedRow`, `NestedCell`, `ColumnHeader`

### Flowless Virtualization (`kramer` — `flowless` package)
Custom virtualized `VirtualFlow` for efficient rendering of large lists (forked/adapted from Flowless library).

### Cell System (`kramer` — `cell` package)
`LayoutCell<T>` interface with implementations: `VerticalCell`, `HorizontalCell`, `AnchorCell`, `RegionCell`, `PrimitiveList`. Focus/selection via `cell.control` subpackage (`FocusController`, `MultipleCellSelection`).

### GraphQL Integration (`kramer-ql`)
- `GraphQlUtil` — parses GraphQL query strings into `Relation` schema trees; executes queries against endpoints via JAX-RS
- `QueryRoot` — special `Relation` subclass for multi-root queries

### CSS Styling
Layout appearance is driven by CSS. Each component has a co-located `.css` file. User stylesheets override `default.css`. See `kramer/src/main/resources/com/chiralbehaviors/layout/default.css` for stylable class names.

## Key Dependencies

- Jackson (2.9.8) — JSON data model (`JsonNode` tree)
- OpenJFX 14 — UI toolkit
- graphql-java (2.3.0) — GraphQL query parsing
- Jersey (2.22.1) — JAX-RS HTTP client for GraphQL endpoints
- JUnit 4 / TestFX — testing
