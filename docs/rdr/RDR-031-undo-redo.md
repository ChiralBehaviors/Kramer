---
title: "Undo/Redo for Query Operations"
id: RDR-031
type: Feature
status: closed
close_reason: "implemented (all phases)"
priority: P2
author: Hal Hildebrand
created: 2026-03-19
accepted_date: 2026-03-19
closed_date: 2026-03-19
reviewed-by: self
related: RDR-027, RDR-026, RDR-029
---

# RDR-031: Undo/Redo for Query Operations

## Metadata
- **Type**: Feature
- **Status**: closed (implemented — all phases)
- **Priority**: P2
- **Created**: 2026-03-19
- **Accepted**: 2026-03-19
- **Closed**: 2026-03-19
- **Reviewed-by**: self
- **Related**: RDR-027 (Interaction Model — excluded undo/redo per critique S4), RDR-026 (QueryState), RDR-029 (Interaction UI Affordances)

---

## Problem Statement

RDR-027 explicitly excluded undo/redo: "Per critique S4, premature. `ResetAll` provides the escape hatch." With RDR-029 adding UI affordances for sort, filter, formula, aggregate, and render mode operations, users will make multi-step query modifications. `ResetAll` is too coarse — it nukes all state. Users need the ability to undo individual operations without losing all other modifications.

---

## Prior Art

- **Spreadsheets (Excel, Google Sheets)**: Linear undo stack, Ctrl+Z/Cmd+Z. Global scope. Undo reverts the last cell/formula edit. Well-understood UX pattern.
- **SIEUFERD (SIGMOD 2016)**: Does not mention undo/redo. Describes "step at a time" query building where each operation modifies the result. The absence suggests SIEUFERD's prototype may not have had undo.
- **RDR-027 critique S4**: Flagged undo/redo as premature because no interaction UI existed at the time. With RDR-029 now providing affordances, the precondition is met.

---

## Dependencies

- **RDR-027** (Interaction Model): DONE. Provides `LayoutInteraction` sealed hierarchy (11 variants including `ClearSort`) and `InteractionHandler`.
- **RDR-026** (QueryState): DONE. Provides `LayoutQueryState` with `Map<SchemaPath, FieldState>`.
- **RDR-029** (Interaction UI Affordances): Proposed (gate PASSED). Provides the UI surface that generates undoable operations.

---

## Design Decisions

Answers to the 5 design questions from bead Kramer-xkqj:

**Q1: Granularity** — Gesture-level. One undo per `InteractionHandler.apply()` call. A `SortBy` is one undoable action; a `ResetAll` is one undoable action (restores everything on redo).

**Q2: Stack model** — Linear. No branching undo tree. New actions after undo discard the redo stack. Standard spreadsheet behavior. YAGNI on tree model.

**Q3: Scope** — Global. Single undo stack for all paths. Not per-field undo. Matches how users think about undo ("undo the last thing I did").

**Q4: Persistence** — Session-only. Undo stack is cleared on schema change (new query). Survives relayout (same query, same data). Does not persist across application restarts.

**Q5: Integration** — No QueryRewriter rollback. Undo restores `LayoutQueryState` to the previous snapshot. The change listener fires, which triggers `QueryRewriter.rewrite()` with the restored state. The rewriter generates the correct query from the restored state — no separate rollback mechanism needed.

---

## Proposed Solution

### Phase 1: Undo Stack in InteractionHandler

Snapshot-based undo using `LayoutQueryState.toJson()` / `fromJson()`:

- Before each `apply()` call, capture `queryState.toJson()` and push onto `undoStack`
- After `apply()`, clear `redoStack`
- `undo()`: pop `undoStack`, push current state onto `redoStack`, restore via `queryState.fromJson()`
- `redo()`: pop `redoStack`, push current state onto `undoStack`, restore via `queryState.fromJson()`
- Stack size limit: 50 entries (prevents memory growth)
- `ResetAll` is undoable (captures full state before clearing)

### Phase 2: UI Integration

- Keyboard shortcuts: Ctrl+Z (undo), Ctrl+Shift+Z (redo) — attached to `AutoLayout` in `AutoLayoutController`
- Guard: `AutoLayoutController` checks `fetchInProgress` before calling `handler.undo()`/`redo()`; silently drops gesture if in-flight
- Context menu items: "Undo" / "Redo" appended to root context menu (after "Reset All")
- Disabled when stack is empty or fetch in progress

---

## Scope Exclusions

- **Branching undo tree**: YAGNI. Linear stack only.
- **Per-field undo**: Global undo only.
- **Persistent undo across sessions**: Session-only.
- **QueryRewriter rollback**: Not needed; state restoration triggers correct rewrite.
- **Undo for non-query operations**: Only `LayoutInteraction` events are undoable. Resize, scroll, focus changes are not.

---

## Implementation Plan

### Phase 1: Undo Stack
- Add `undoStack` (Deque<ObjectNode>) and `redoStack` (Deque<ObjectNode>) to `InteractionHandler`
- Modify `apply()` to snapshot before mutation
- Add `undo()` and `redo()` methods
- Add `canUndo()` / `canRedo()` query methods
- Stack size cap: 50
- Tests: apply 3 actions (including `ClearSort`) → undo 3 → state matches initial; redo restores; new action after undo clears redo stack; stack cap enforced; `undo()`/`redo()` do not double-snapshot (they push to opposite stack without re-entering `apply()`)

### Phase 2: UI Integration
- Keyboard handlers in `AutoLayoutController`: Ctrl+Z → `handler.undo()`, Ctrl+Shift+Z → `handler.redo()`
- Context menu items in `InteractionMenuFactory` root menu
- Tests: keyboard shortcut triggers undo; menu items disabled when stack empty

---

## Research Findings

### RF-1: LayoutQueryState Serialization Already Supports Undo
**Status**: verified
**Source**: Codebase analysis

`LayoutQueryState.toJson()` serializes all `FieldState` entries to an `ObjectNode`. `fromJson()` restores state from an `ObjectNode` (clears existing, then applies). Both use `suppressNotifications()` to fire exactly one change event. This is the complete snapshot/restore mechanism — no additional serialization work needed.

### RF-2: RDR-027 Exclusion Precondition Met
**Status**: verified
**Source**: RDR-027 critique S4, RDR-029 gate

RDR-027 excluded undo/redo because "no interaction UI existed." RDR-029 (gate PASSED) provides the full interaction surface. The precondition for lifting the exclusion is met.

### RF-3: ObjectNode Snapshot Size Is Small
**Status**: verified
**Source**: Codebase analysis

The implemented `FieldState` has 14 fields (RDR-026 originally specified 9; frozen, aggregatePosition, cellFormat, columnWidth, and collapseDuplicates were added post-spec). A typical session overrides <20 paths. An `ObjectNode` snapshot is ~2KB. A 50-entry stack is ~100KB — negligible memory.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| `fromJson()` fires change listener which triggers re-fetch | Low | Expected behavior — undo should re-execute the restored query |
| Undo during in-flight fetch creates race | Medium | Guard in UI layer (`AutoLayoutController`): keyboard/menu handler checks `fetchInProgress` before calling `handler.undo()`; silently drops gesture if in-flight (not queued — queuing is unsound because post-fetch state differs from pre-undo snapshot) |
| Stack grows unbounded in long sessions | Low | Cap at 50; oldest entries discarded |
| Snapshot serialization overhead per action | Low | `toJson()` is fast for small maps; no measurable latency |

---

## Success Criteria

- [ ] Ctrl+Z undoes the last query operation; Ctrl+Shift+Z redoes
- [ ] Undo/redo menu items appear in root context menu
- [ ] Menu items are disabled when stack is empty
- [ ] `ResetAll` is undoable (restores all prior state on undo)
- [ ] Stack cap of 50 is enforced
- [ ] Undo triggers re-layout with restored state
- [ ] New action after undo clears redo stack
