---
title: "Render-Context Interface (LayoutView) Design"
status: approved
created: 2026-03-22
rdr: RDR-032
---

# Render-Context Interface Design

## Goal

Extract a `LayoutView` interface from `RelationLayout` that captures all state
renderers need. This decouples the compute pipeline from rendering, enabling
pluggable renderers (React, DOM, etc.) for the JS port (RDR-032).

## Approach: Interface Extraction (Approach B)

1. Define `LayoutView` interface with read-only accessors for all fields that
   `buildControl()`, `buildOutline()`, `buildNestedTable()`, and
   `buildColumnHeader()` read from `RelationLayout`.

2. `RelationLayout` implements `LayoutView` — zero behavior change for existing
   Java renderers.

3. Refactor renderer constructors (`NestedTable`, `Outline`, `OutlineCell`,
   `TableHeader`) to accept `LayoutView` instead of `RelationLayout`.

4. The `LayoutView` interface becomes the TypeScript type definition for the
   JS port's render protocol.

## Interface Definition

```java
public interface LayoutView {
    boolean isUseTable();
    boolean isCrosstab();
    double getJustifiedWidth();
    double getCellHeight();
    double getLabelWidth();
    int getResolvedCardinality();
    double getColumnHeaderHeight();
    List<ColumnSet> getColumnSets();
    List<SchemaNodeLayout> getChildren();
    RelationStyle getStyle();
    JsonNode extractFrom(JsonNode datum);
    String getLabel();
    String getCssClass();
    SchemaPath getSchemaPath();
}
```

## Files Changed

- NEW: `LayoutView.java` — interface definition
- MODIFY: `RelationLayout.java` — `implements LayoutView`
- MODIFY: `NestedTable.java` — constructor takes `LayoutView`
- MODIFY: `Outline.java` — constructor takes `LayoutView`
- MODIFY: `OutlineCell.java` — constructor takes `LayoutView`
- MODIFY: `TableHeader.java` — constructor takes `LayoutView`

## Validation

- All 1140 existing tests pass unchanged (RelationLayout implements the interface)
- New test: verify LayoutView captures all fields needed by renderers
