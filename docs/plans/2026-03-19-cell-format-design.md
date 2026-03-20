# Design: CellFormat Property

**Date**: 2026-03-19
**Bead**: Kramer-8ic2
**Status**: Approved

## Design

- Add `String cellFormat` to `FieldState` (12th field), `CELL_FORMAT` to `LayoutPropertyKeys`
- Add `setCellFormat(SchemaPath, String)` to `LayoutQueryState` + serialization
- Cache `cellFormat` in `PrimitiveLayout` during `measure()` via stylesheet read
- Apply format in `PrimitiveStyle.build()` `updateItem`:
  - `p.getCellFormat() != null` → `String.format(cellFormat, parseValue(item))`
  - `parseValue`: `isNumber()` → `numberValue()`, `isBoolean()` → `booleanValue()`, else `asText()`
  - Catch `IllegalFormatException` → log WARN, fall back to `SchemaNode.asText(item)`
- Null cellFormat → raw value, no formatting

## Changes

1. `FieldState` — add `String cellFormat` (12th field), update EMPTY to 12 nulls
2. `LayoutPropertyKeys` — add `CELL_FORMAT = "cell-format"`
3. `LayoutQueryState` — add setter, update all 11 existing setters to pass-through, update serialization
4. `PrimitiveLayout` — cache `cellFormat` during `measure()`, add `getCellFormat()` accessor
5. `PrimitiveStyle.build()` — apply format in `updateItem` with error handling
6. Tests — format works, mismatch caught, null format passes through
