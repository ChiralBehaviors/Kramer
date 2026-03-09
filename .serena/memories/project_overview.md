# Kramer - Project Overview

## Purpose
Automatic layout framework for structured hierarchical JSON data, built with JavaFX. Implements the algorithm from "Automatic Layout of Structured Hierarchical Reports" (Bakke, InfoVis 2013). Adaptively switches between outline and nested table views for dense, multicolumn hybrid layouts.

## Tech Stack
- **Language**: Java 11+
- **Build**: Maven 3.3+ (multi-module POM)
- **UI**: JavaFX (OpenJFX 14)
- **JSON**: Jackson 2.9.8 (JsonNode tree model)
- **GraphQL**: graphql-java 2.3.0 (query parsing)
- **HTTP**: Jersey 2.22.1 (JAX-RS client)
- **Testing**: JUnit 4, TestFX, Mockito
- **License**: Apache 2.0

## Modules (parent: `kramer.app`, group: `com.chiralbehaviors.layout`)
1. **kramer** — Core autolayout engine, no external service deps
2. **kramer-ql** — GraphQL integration, depends on kramer
3. **explorer** — Interactive JavaFX GraphQL explorer app, depends on kramer-ql
4. **toy-app** — Declarative single-page app framework, depends on kramer-ql

## Key Architecture
- **Schema layer**: `SchemaNode` (abstract) → `Relation` (composite) / `Primitive` (leaf)
- **Layout engine**: `AutoLayout` control → `SchemaNodeLayout` → outline or nested table rendering
- **Style system**: `Style` class factories layout cells, CSS-driven appearance
- **Flowless**: Custom virtualized `VirtualFlow` for efficient large list rendering
- **GraphQL**: `GraphQlUtil` parses queries into `Relation` schema trees automatically
