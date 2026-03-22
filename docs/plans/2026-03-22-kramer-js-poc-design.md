---
title: "Kramer JS PoC — 2-slice feasibility validation"
status: approved
created: 2026-03-22
rdr: RDR-032
---

# Kramer JS PoC Design

## Goal

Validate the JS port feasibility (RDR-032) with a 2-slice proof of concept:
flat table rendering + nested outline with mode switching.

## Location

`poc/kramer-js/` in the Kramer repo.

## Tech Stack

- TypeScript (strict), Vite, React 19, Vitest
- Zero external dependencies for core

## Structure

```
poc/kramer-js/
  src/core/       — schema, measure, layout, justify, compress, types
  src/react/      — AutoLayout, Table, Outline components
  src/App.tsx     — course catalog demo
  test/core/      — unit tests for pipeline
  test/react/     — rendering tests
```

## Slice 1: Flat table (validates measurement + basic rendering)
## Slice 2: Nested outline (validates solver + mode switching + nested rendering)

## Validation Criteria

- Schema types port correctly
- Browser text measurement produces usable widths
- Layout/justify/compress pipeline produces correct decisions
- React renderer consumes LayoutView interface
- Resize adaptation works (TABLE ↔ OUTLINE switching)
