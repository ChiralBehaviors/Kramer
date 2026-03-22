# Kramer QL

GraphQL integration for the Kramer layout engine.

## What it does

Parses GraphQL query strings into Kramer `Relation` schema trees, so the layout engine can render any GraphQL query result without manual schema construction. Also provides HTTP client utilities for executing queries against GraphQL endpoints.

## Key classes

- `GraphQlUtil` — parses a GraphQL query string into a `Relation` tree. Handles nested selections, fragments, and multi-root queries. Also provides `evaluate()` for executing queries via Jakarta RS.
- `QueryRoot` — `Relation` subclass for multi-root queries (multiple top-level selections).
- `SchemaContext` — bundles the parsed schema with query metadata for downstream use.
- `SchemaIntrospector` — discovers server capabilities (sorting, filtering support) from the GraphQL schema.
- `QueryRewriter` — rewrites queries to push sort/filter operations to the server when supported.
- `TypeIntrospector` — executes `__schema` introspection queries and parses results into a browseable type tree (`IntrospectedType`, `IntrospectedField`, `TypeRef`).
- `QueryExpander` — adds/removes `Field` nodes in GraphQL Document AST using immutable `transform()` pattern. Creates minimal sub-selections for relation fields (id + name-like fields per RF-6 heuristic). Distinct from `QueryRewriter` which only injects arguments into existing selections.

## Usage

```java
// Parse a GraphQL query into a schema
SchemaContext ctx = GraphQlUtil.buildContext(queryString, selectionName);
Relation schema = ctx.schema();

// Execute against an endpoint
String result = GraphQlUtil.evaluate(webTarget, queryString);
```

## Dependencies

- graphql-java (25.0) — query parsing
- Jersey (3.1.11) — Jakarta RS HTTP client
- Kramer core
