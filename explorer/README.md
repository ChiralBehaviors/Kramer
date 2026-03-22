# AutoLayout Explorer

Interactive application for exploring GraphQL endpoints with Kramer's autolayout.

## Running

```bash
# Explorer (requires GraphQL endpoint)
mvn package
java -jar target/explorer-0.0.1-SNAPSHOT-phat.jar

# Standalone demo (no external services)
mvn package
java -cp target/explorer-0.0.1-SNAPSHOT-phat.jar com.chiralbehaviors.layout.explorer.StandaloneDemo
```

## Features

The app has three views, switched by buttons at the bottom:

- **Query** — embedded GraphiQL IDE for editing and executing GraphQL queries
- **Schema** — tree view of the parsed schema
- **Layout** — autolayout rendering of the query results

### Interaction

When viewing the layout:

- **Right-click** any field for a context menu: sort, filter, visibility, formula, aggregate, render mode
- **Click column headers** to cycle sort: ascending → descending → clear
- **Cmd+Shift+F** toggles the field selector panel (left sidebar with visibility checkboxes, state badges, click-to-scroll)
- **Cmd+Shift+I** toggles the field inspector panel (right sidebar with all field properties and inline editing)
- **Cmd+Shift+B** toggles the schema browse panel (introspection tree with add/remove field buttons)
- **Cmd+Z / Cmd+Shift+Z** for undo/redo of query state changes
- **Drag column edges** to resize columns
- **Cmd+F** opens the search bar

### Schema Discovery

When connected to a GraphQL endpoint:

- **Introspection browser** — browse available types and fields from the endpoint via `__schema` introspection
- **Add/remove fields** — click in the introspection tree to modify the active query via `QueryExpander`
- **Re-execution** — modified queries are serialized and re-executed, producing updated layouts
- **Minimal fields** — new relations start with id + name-like fields (RF-6 heuristic)

### Server-side operations

The explorer introspects the GraphQL schema to detect server-side sort/filter support. When available, query state changes (sort, filter) are pushed to the server by rewriting the GraphQL query, providing server-side data operations with client-side layout.

## Standalone Demo

`StandaloneDemo` is a self-contained course catalog demo (departments → courses → sections, 3-level nesting) with the full interactive pipeline wired. No external services required — all data is inline.

## Architecture

`AutoLayoutController` wires together:
- `AutoLayout` (core layout engine)
- `LayoutQueryState` (user interaction state)
- `InteractionHandler` (event dispatch with undo/redo)
- `InteractionMenuFactory` (context menus)
- `ColumnSortHandler` (click-to-sort)
- `FieldSelectorPanel` (visibility checkboxes, state badges, click-to-scroll)
- `FieldInspectorPanel` (field properties inspector with inline editing)
- `IntrospectionTreePanel` (schema browse with add/remove field actions)
- `QueryRewriter` (server-side operation push-down)
- `QueryExpander` (AST field addition/removal for progressive query construction)
- `TypeIntrospector` (GraphQL `__schema` introspection parsing)
- `SchemaDiagramView` (visual relation hierarchy)

The GraphiQL IDE runs in a JavaFX WebView. Query execution calls back into Java to handle same-origin restrictions.
