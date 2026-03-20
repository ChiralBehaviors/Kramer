# Design: Drag-to-resize Columns + CollapseDuplicates

**Date**: 2026-03-19
**Beads**: Kramer-i1kz (Drag-to-resize), Kramer-iqkr (CollapseDuplicates)
**Status**: Approved

## Drag-to-resize (Kramer-i1kz)

**Approach**: Stylesheet override via `column-width` property.

- Add `Double columnWidth` to FieldState (13th field)
- Add `COLUMN_WIDTH = "column-width"` to LayoutPropertyKeys
- Add `setColumnWidth(SchemaPath, Double)` to LayoutQueryState
- In `PrimitiveLayout.justify()`: check stylesheet for column-width override; if set, use it instead of computed justifiedWidth
- Mouse handlers installed via postLayoutCallback on AutoLayout (same pattern as sort handlers)
- Detect column border: mouse within 4px of ColumnHeader right edge
- Cursor: H_RESIZE on hover in grab zone
- Drag: track startX + startWidth, compute delta
- Release: call setColumnWidth(path, newWidth), triggers relayout
- Minimum width: 20px enforced

## CollapseDuplicates (Kramer-iqkr)

**Approach**: Data-level dedup in RelationLayout.measure(), matching SIEUFERD behavior.

- Add `Boolean collapseDuplicates` to FieldState (14th field, Relation-scope)
- Add `COLLAPSE_DUPLICATES = "collapse-duplicates"` to LayoutPropertyKeys
- In RelationLayout.measure(), after sort but before child measurement: if collapseDuplicates=true, remove consecutive duplicate rows
- Duplicate = adjacent rows with identical values on all visible primitive children (compared via SchemaNode.asText())
- Hidden rows are removed from the data list entirely (VirtualFlow sees fewer rows)
- No expand/collapse UI, no count badge — matches SIEUFERD exactly

## Combined FieldState Change

FieldState grows from 12 to 14 fields. EMPTY sentinel: 14 nulls.

## Pipeline Position

```
extractFrom(datum)
  → sort (RDR-014)
  → filter (RDR-014: hideIfEmpty)
  → collapse duplicates (NEW — after sort, before pivot/stats)
  → collect pivot values (RDR-015)
  → accumulate statistics (RDR-013)
  → measure children
```
