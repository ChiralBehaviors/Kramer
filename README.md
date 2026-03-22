# Kramer

Automatic layout of structured hierarchical JSON data in JavaFX.

[![Java CI](https://github.com/ChiralBehaviors/Kramer/actions/workflows/maven.yml/badge.svg)](https://github.com/ChiralBehaviors/Kramer/actions/workflows/maven.yml)
[![License](https://img.shields.io/github/license/ChiralBehaviors/Kramer)](LICENSE)

Kramer implements the algorithm from [Automatic Layout of Structured Hierarchical Reports](http://people.csail.mit.edu/ebakke/research/reportlayout_infovis2013.pdf) (Bakke, InfoVis 2013). Given a schema and JSON data, it produces an adaptive layout that switches between outline and nested table views based on available width. The result is a dense, multicolumn hybrid that handles arbitrary nesting depth.

![Autolayout demo](media/autolayout.png)

## Building

Requires Maven 3.3+ and Java 25+.

```bash
mvn clean install
```

Individual modules can be built independently (`mvn -pl kramer install`).

## Modules

| Module | Description |
|--------|------------|
| [kramer](kramer/) | Core layout engine. Schema-driven layout of JSON into JavaFX controls. |
| [kramer-ql](kramer-ql/) | GraphQL integration. Parses queries into Kramer schemas, executes via Jakarta RS. |
| [explorer](explorer/) | Interactive app for exploring GraphQL endpoints with autolayout. Includes `StandaloneDemo`. |
| [toy-app](toy-app/) | Declarative single-page app framework using GraphQL + autolayout. |
| [poc/kramer-js](poc/kramer-js/) | **TypeScript/React proof of concept** — validates JS port feasibility (RDR-032). |

## How it works

The layout pipeline:

1. **Schema** — a tree of `Relation` (composite) and `Primitive` (leaf) nodes describing the JSON structure
2. **Measure** — statistical content-width measurement (P90) determines natural column widths
3. **Layout decision** — a constraint solver chooses table or outline mode per relation, maximizing information density while ensuring readability
4. **Justify** — two-pass width distribution guarantees every column gets at least its label width before distributing surplus proportionally
5. **Compress** — outline mode packs fields into multicolumn layouts with height balancing
6. **Build** — JavaFX control tree with virtualized scrolling via a custom VirtualFlow

The layout is fully reactive — resizing the window re-runs the pipeline and may switch between table and outline modes at different nesting levels.

## Key features

- Adaptive table/outline hybrid layout
- Constraint solver for global render-mode optimization
- Expression language for per-row filter, formula, sort, and aggregate operations
- Interactive query state (sort, filter, visibility, render mode) with undo/redo
- Context menus, field selector panel, keyboard shortcuts
- Virtualized scrolling for large datasets
- CSS-driven styling with schema-aware class names

## Running the explorer

```bash
cd explorer && mvn package
java -jar target/explorer-0.0.1-SNAPSHOT-phat.jar
```

Point it at any GraphQL endpoint. The app provides a GraphiQL editor, schema viewer, and autolayout view.

## Using as a library

```xml
<repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/ChiralBehaviors/Kramer</url>
</repository>

<dependency>
    <groupId>com.chiralbehaviors.layout</groupId>
    <artifactId>kramer</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

For GraphQL integration, use `kramer-ql` instead.

## JavaScript port

The layout algorithm is being ported to TypeScript/React. The [PoC](poc/kramer-js/) validates feasibility with a 2-slice proof of concept:

```bash
cd poc/kramer-js && npm install && npm run dev
```

The core pipeline (schema → measure → layout → justify → compress) is pure TypeScript with zero DOM dependency. The renderer is pluggable — React is the first target, with vanilla DOM and other frameworks possible via the `LayoutView` interface.

See [RDR-032](docs/rdr/RDR-032-javascript-port-feasibility-spike.md) for the full feasibility analysis.

## License

Apache 2.0 — see [LICENSE](LICENSE).
