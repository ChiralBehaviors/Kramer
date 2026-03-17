# RDR-026: QueryState — Layer 2 Query Model

## Metadata
- **Type**: Architecture
- **Status**: proposed
- **Priority**: P2
- **Created**: 2026-03-16
- **Related**: RDR-018 (query-semantic stylesheet), RDR-021 (expression language), RDR-014 (filter/sort), RDR-015 (render modes), RDR-023 (reactive invalidation)

---

## Problem Statement

Kramer's 7-layer architecture (derived from Bakke's SIEUFERD papers) has two missing layers: Layer 2 (Query Model) and Layer 7 (Interaction). Currently, `LayoutStylesheet` stores per-`SchemaPath` property overrides, but there is no typed abstraction representing *user intent* — what the user wants to see, how they want it filtered, sorted, and displayed. Code that manipulates the stylesheet does so via raw string keys (`setOverride(path, "filter-expression", "...")`), which is error-prone, untyped, and not serializable as a coherent state.

SIEUFERD (SIGMOD 2016) defines 17 per-field properties that unify query semantics and display configuration. The RDR-018 critique (C1) established that `LayoutStylesheet + SchemaPath` is isomorphic to SIEUFERD's model at the **storage layer only** — the execution model differs fundamentally (SIEUFERD pushes to server; Kramer evaluates client-side). This RDR addresses the storage and mutation gap without conflating execution models.

---

## Prior Art & Critique Findings Addressed

- **RDR-018 Critique C1**: Isomorphism is storage-only. This RDR scopes QueryState to client-side properties; server-push is deferred to RDR-028.
- **RDR-018 Critique C2**: Expression language was unspecified. Resolved by RDR-021 (parser + evaluator + pipeline wiring).
- **RDR-018 Critique S5**: Client-side scalability limitation acknowledged. QueryState does not claim server-side equivalence.
- **Substantive Critique C2**: QueryState must not create dual representation with `DefaultLayoutStylesheet`. Resolved: QueryState **implements** `LayoutStylesheet`, wrapping `DefaultLayoutStylesheet` internally. Single source of truth.
- **Substantive Critique C3**: Re-layout trigger unspecified. Resolved: QueryState fires change listeners on mutation. The caller (e.g., explorer app) subscribes and calls `AutoLayout.autoLayout()`. AutoLayout's existing version-check during layout passes serves as fallback.
- **Substantive Critique S1**: Only fully-specified properties included. 9 properties enumerated; cellFormat, frozen, collapseDuplicates excluded.
- **Substantive Critique S2**: RDR-021 open questions listed as explicit pre-conditions.
- **Substantive Critique S4**: Undo/diff removed. Only `reset()` provided.
- **Substantive Critique O3**: Expression cache flush on any mutation documented as known performance characteristic.

---

## Pre-conditions

The following RDR-021 open questions must be resolved (documented in `LayoutPropertyKeys` javadoc or a follow-on RDR) before `FieldState` schema is finalized:

1. **cell-format interaction with formula results** — type coercion, `IllegalFormatException` handling. Blocks `cellFormat` field addition to FieldState.
2. **Aggregate result render position** — `aggregate-position` property value space undefined. FieldState does not include `aggregatePosition` until this is resolved.
3. **Formula column JsonNode injection** — shallow ObjectNode overlay strategy confirmed in RDR-021 Step C. No further action needed.
4. **sort-expression vs sort-fields precedence** — resolved in RDR-021 (sort-expression wins). Documented in `LayoutPropertyKeys` javadoc.

---

## Proposed Solution

### QueryState as LayoutStylesheet Implementation

`QueryState` implements `LayoutStylesheet`, wrapping a `DefaultLayoutStylesheet` internally. All `getXxx()` calls delegate to the inner stylesheet. Typed mutation methods update the inner stylesheet and fire change listeners. This eliminates dual representation — `QueryState` IS the stylesheet that `AutoLayout` receives.

```
AutoLayout ──receives──► QueryState (implements LayoutStylesheet)
                              │
                              ├── getDouble/getString/getBoolean → delegate to inner DefaultLayoutStylesheet
                              ├── setFilter(path, expr) → inner.setOverride(path, "filter-expression", expr)
                              ├── getFieldState(path) → snapshot of current per-path properties
                              └── addChangeListener(listener) → notified on any mutation
```

### FieldState Record

Immutable snapshot of user intent for a single `SchemaPath`. Contains only fully-specified, currently-implemented properties:

```java
public record FieldState(
    Boolean visible,            // default: true (LayoutPropertyKeys.VISIBLE)
    String renderMode,          // default: null = auto (LayoutPropertyKeys.RENDER_MODE)
    Boolean hideIfEmpty,        // default: false (LayoutPropertyKeys.HIDE_IF_EMPTY)
    String sortFields,          // default: null (LayoutPropertyKeys.SORT_FIELDS)
    String filterExpression,    // default: null (LayoutPropertyKeys.FILTER_EXPRESSION)
    String formulaExpression,   // default: null (LayoutPropertyKeys.FORMULA_EXPRESSION)
    String aggregateExpression, // default: null (LayoutPropertyKeys.AGGREGATE_EXPRESSION)
    String sortExpression,      // default: null (LayoutPropertyKeys.SORT_EXPRESSION)
    String pivotField           // default: null (pivot-field for crosstab)
) {}
```

9 properties. All have corresponding `LayoutPropertyKeys` constants and consumers in the existing pipeline. Nullable fields mean "use default / not set."

**Excluded properties** (not yet specified):
- `cellFormat` — blocked on RDR-021 Open Question 1 (type coercion)
- `frozen` — requires interaction model (RDR-027)
- `collapseDuplicates` — requires its own specification per RDR-018 deferral
- `aggregatePosition` — blocked on RDR-021 Open Question 2

### getFieldState Contract

`getFieldState(SchemaPath)` returns a `FieldState` where each field is `null` if no override has been set for that property at the given path. Callers must treat null `Boolean` fields as their documented defaults before unboxing (e.g., `visible` defaults to `true` when null). A convenience method `getVisibleOrDefault(SchemaPath)` returning `boolean` (never null) is provided for common cases.

### Re-Layout Notification Protocol

`QueryState` maintains a list of `Runnable` change listeners. Any mutation (setter call, reset) fires all listeners synchronously. An internal `suppressNotifications` flag enables batch operations — `fromJson()` suppresses notifications during restoration and fires exactly one notification on completion. The caller subscribes and triggers re-layout:

```java
queryState.addChangeListener(() -> autoLayout.autoLayout());
```

`AutoLayout` remains decoupled — it receives a `LayoutStylesheet` and does not know about `QueryState`. The existing version-check during layout passes (`LayoutDecisionKey` includes stylesheet version) ensures correctness even without the listener — the listener provides immediacy.

### Serialization

`QueryState.toJson()` serializes all non-default `FieldState` entries as a JSON object keyed by `SchemaPath.toString()` (format: `"segment1/segment2/..."`). `QueryState.fromJson(String, Style)` restores state with notifications suppressed — exactly one change notification fires on completion. This enables saving/loading user configurations.

**Serialization key stability**: `SchemaPath.toString()` uses `/`-delimited segments. This format is a stability dependency for saved configurations — changes to the format would silently break deserialization (missing overrides, no errors). The format is documented here as the contract.

### Known Performance Characteristic

`ExpressionEvaluator` caches ASTs per expression string, invalidated when `LayoutStylesheet.getVersion()` changes. A single `QueryState` mutation (e.g., changing one field's filter) increments the version and flushes **all** expression caches — not just the affected path. For schemas with many expression-bearing paths, this is a full cache rebuild. Acceptable for interactive use (sub-millisecond parse times); documented here for awareness.

---

## FieldState ↔ LayoutPropertyKeys Mapping

| FieldState field | LayoutPropertyKey | Source RDR |
|------------------|-------------------|------------|
| `visible` | `VISIBLE` | RDR-014 |
| `renderMode` | `RENDER_MODE` | RDR-015 |
| `hideIfEmpty` | `HIDE_IF_EMPTY` | RDR-014 |
| `sortFields` | `SORT_FIELDS` | RDR-014 |
| `filterExpression` | `FILTER_EXPRESSION` | RDR-021 |
| `formulaExpression` | `FORMULA_EXPRESSION` | RDR-021 |
| `aggregateExpression` | `AGGREGATE_EXPRESSION` | RDR-021 |
| `sortExpression` | `SORT_EXPRESSION` | RDR-021 |
| `pivotField` | `"pivot-field"` (not yet in LayoutPropertyKeys) | RDR-015 |

---

## Alternatives Considered

### Alt A: QueryState generates overrides to a separate DefaultLayoutStylesheet

QueryState as a separate object that calls `setOverride()` on a `DefaultLayoutStylesheet`.

**Rejected** (Critique C2): Creates dual representation. Two objects hold the same state. Nothing prevents direct stylesheet mutation bypassing QueryState, causing silent divergence.

### Alt B: AutoLayout accepts QueryState directly (replacing LayoutStylesheet parameter)

**Rejected**: Couples AutoLayout to QueryState. AutoLayout should work with any `LayoutStylesheet` implementation — including headless pipelines that don't need a query model.

### Alt C: Extend DefaultLayoutStylesheet with typed methods

Add typed setters directly to `DefaultLayoutStylesheet`.

**Rejected**: Violates single-responsibility. `DefaultLayoutStylesheet` is a storage layer. QueryState adds semantic intent, change notification, serialization, and reset — concerns that don't belong in a stylesheet.

---

## Research Findings

### RF-1: LayoutStylesheet Contract Is Clean (Confidence: HIGH)
`LayoutStylesheet` is a 7-method interface: `getVersion`, `getDouble`, `getInt`, `getString`, `getBoolean`, `primitiveStyle`, `relationStyle`. No default methods, no static methods, no hidden contracts. QueryState implementing this interface requires exactly these 7 methods, all delegating to the wrapped `DefaultLayoutStylesheet`. Verified at `LayoutStylesheet.java`.

### RF-2: Single-Thread Assumption Valid (Confidence: HIGH)
`DefaultLayoutStylesheet` has zero synchronization — `version` is not volatile, `overrides` is a plain `HashMap`. `AutoLayout` enforces FX Application Thread access via `Platform.runLater()` and `Platform.isFxApplicationThread()` guards. QueryState does not need thread safety.

### RF-3: Substitution Points Identified (Confidence: HIGH)
Style holds LayoutStylesheet. AutoLayout holds Style. The ownership chain:
- `AutoLayout` → `Style model` (field)
- `Style` creates `DefaultLayoutStylesheet(this)` by default, or accepts external via `Style(LayoutObserver, LayoutStylesheet)` or `Style.setStylesheet(LayoutStylesheet)`
- `PrimitiveLayout.measure()` and `RelationLayout.measure()` read via `model.getStylesheet()`

**Substitution**: `new Style(observer, queryState)` at construction, or `style.setStylesheet(queryState)` at runtime. No changes to AutoLayout needed.

### RF-4: Re-Layout Trigger Is autoLayout(), Not requestLayout() (Confidence: HIGH)
`AutoLayout extends AnchorPane` but does NOT override `requestLayout()`. The correct external trigger is `autoLayout.autoLayout()` (public method, line 126), which clears caches and posts `Platform.runLater()`. The change listener protocol should call `autoLayout.autoLayout()`, not `requestLayout()`. Design note updated.

### RF-5: Zero Production Callers of setOverride (Confidence: HIGH)
`DefaultLayoutStylesheet.setOverride()` has zero callers in production code (`kramer/src/main/java`, `kramer-ql`, `explorer`, `toy-app`). All 60+ calls are in test code only. QueryState typed setters will be the first production-code callers. No migration of existing call sites needed.

### RF-6: pivot-field Confirmed (Confidence: HIGH)
`RelationLayout.java` line 984: `stylesheet.getString(myPath, "pivot-field", "")`. String property, empty default. When non-empty, names a JSON field whose distinct values become pivot columns. Listed in `LayoutPropertyKeys` javadoc as deferred consolidation. QueryState should expose `setPivotField(SchemaPath, String)`.

### RF-7: ExpressionEvaluator Isolation Confirmed (Confidence: HIGH)
`ExpressionEvaluator` lives on `Style` (field), not on the stylesheet. The only stylesheet interaction is read-only: `evaluator.syncVersion(stylesheet.getVersion())` and `stylesheet.getString()` in `RelationLayout.measure()`. Both go through the `LayoutStylesheet` interface. QueryState's delegation will work transparently. No direct coupling needed.

---

## Implementation Plan

### Phase 1: Core Records and Interface
- `FieldState` record (9 properties, all nullable)
- `QueryState implements LayoutStylesheet` with delegation to `DefaultLayoutStylesheet`
- Typed setters: `setVisible(path, bool)`, `setFilter(path, expr)`, etc.
- `getFieldState(path)` returning current snapshot
- `reset()` clearing all overrides
- Change listener support
- Unit tests: mutation → getFieldState roundtrip, version increment, listener firing

### Phase 2: Serialization
- `toJson()` / `fromJson()` for QueryState
- Tests: serialize → deserialize → equality

### Phase 3: Integration
- Add `pivotField` to `LayoutPropertyKeys` (consolidation)
- Wire QueryState into `explorer` app as a demonstration
- Integration test: QueryState mutation → AutoLayout re-layout via listener

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| FieldState schema changes when deferred properties are specified | Low | FieldState is a record; adding fields is backward-compatible via defaults |
| Expression cache flush on single mutation | Low | Sub-ms parse times; document as known characteristic |
| Existing code bypasses QueryState via raw stylesheet | Medium | QueryState IS the stylesheet; no separate object to bypass |

---

## Success Criteria

- [ ] `QueryState implements LayoutStylesheet` passes all existing tests when substituted for `DefaultLayoutStylesheet`
- [ ] Typed setters produce correct `LayoutPropertyKeys` overrides (verified via getFieldState roundtrip)
- [ ] Change listener fires on every mutation
- [ ] `reset()` clears all overrides and fires listener
- [ ] JSON serialization/deserialization preserves all non-default FieldState entries
- [ ] No changes to AutoLayout, RelationLayout, or PrimitiveLayout APIs
- [ ] Zero new external dependencies
