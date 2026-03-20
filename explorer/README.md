# AutoLayout Explorer

Interactive application for exploring GraphQL endpoints with Kramer's autolayout.

## Running

```bash
mvn package
java -jar target/explorer-0.0.1-SNAPSHOT-phat.jar
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
- **Cmd+Shift+F** toggles the field selector panel (left sidebar with visibility checkboxes)
- **Cmd+Z / Cmd+Shift+Z** for undo/redo of query state changes
- **Drag column edges** to resize columns
- **Cmd+F** opens the search bar

### Server-side operations

The explorer introspects the GraphQL schema to detect server-side sort/filter support. When available, query state changes (sort, filter) are pushed to the server by rewriting the GraphQL query, providing server-side data operations with client-side layout.

## Architecture

`AutoLayoutController` wires together:
- `AutoLayout` (core layout engine)
- `LayoutQueryState` (user interaction state)
- `InteractionHandler` (event dispatch with undo/redo)
- `InteractionMenuFactory` (context menus)
- `ColumnSortHandler` (click-to-sort)
- `FieldSelectorPanel` (visibility checkboxes)
- `QueryRewriter` (server-side operation push-down)

The GraphiQL IDE runs in a JavaFX WebView. Query execution calls back into Java to handle same-origin restrictions.
