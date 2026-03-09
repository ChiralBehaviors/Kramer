# Codebase Structure

## kramer (core)
```
com.chiralbehaviors.layout
├── AutoLayout          — Main JavaFX control (AnchorPane), entry point for layout
├── SchemaNodeLayout    — Computed layout for a schema node at measured width
├── PrimitiveLayout     — Layout for leaf/scalar values
├── RelationLayout      — Layout for composite/relation nodes
├── Column / ColumnSet  — Column computation for table layouts
├── LayoutLabel         — Styled label component
├── schema/
│   ├── SchemaNode      — Abstract base for schema tree
│   ├── Relation        — Composite node (children, auto-fold support)
│   └── Primitive       — Leaf node (scalar values)
├── style/
│   ├── Style           — Factory for layouts and cells, CSS management
│   ├── PrimitiveStyle  — Style config for primitives
│   ├── RelationStyle   — Style config for relations
│   ├── LabelStyle      — Style config for labels
│   └── NodeStyle       — Base style interface
├── cell/
│   ├── LayoutCell<T>   — Generic cell interface
│   ├── VerticalCell, HorizontalCell, AnchorCell, RegionCell
│   ├── PrimitiveList   — List of primitive cells
│   ├── LayoutContainer — Container with focus traversal
│   └── control/        — Focus, selection, mouse handling
├── flowless/
│   └── VirtualFlow     — Virtualized scrolling (custom Flowless fork)
├── outline/
│   └── Outline, OutlineCell, OutlineColumn, OutlineElement, Span
└── table/
    └── NestedTable, NestedRow, NestedCell, ColumnHeader, TableHeader
```

## kramer-ql
```
com.chiralbehaviors.layout.graphql
├── GraphQlUtil     — Parses GraphQL → Relation schema, executes queries
└── QueryRoot       — Relation subclass for multi-root queries
```

## explorer
```
com.chiralbehaviors.layout.explorer
├── Launcher                — Entry point (delegates to AutoLayoutExplorer)
├── AutoLayoutExplorer      — JavaFX Application
├── AutoLayoutController    — Main controller with GraphQL + autolayout
├── QueryState              — Holds query/endpoint state
├── SchemaView / SchemaViewSkin — Schema editor control
└── GraphiqlController      — GraphiQL-style query editor
```

## toy-app
```
com.chiralbehaviors.layout.toy
├── Launcher            — Entry point
├── SinglePageApp       — JavaFX Application
├── GraphqlApplication  — App configuration
├── Page                — Page definition
└── PageContext         — Page runtime context
```
