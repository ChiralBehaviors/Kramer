---
title: "Schema Navigation & Discovery"
id: RDR-030
type: Feature
status: proposed
priority: P1
author: Hal Hildebrand
created: 2026-03-19
related: RDR-026, RDR-027, RDR-020
---

# RDR-030: Schema Navigation & Discovery

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P1
- **Created**: 2026-03-19
- **Reviewed-by**: —
- **Related**: RDR-026 (QueryState), RDR-027 (Interaction Model), RDR-020 (GraphQL AST Retention)

---

## Problem Statement

All four Bakke papers emphasize interactive schema browsing as a core capability of schema-independent database UIs. Kramer has no schema navigation. Users must bring a pre-formed GraphQL query — there is no way to explore the schema, discover available fields, or progressively construct a query from within the UI.

This gap prevents Kramer from functioning as a true schema-independent database interface. The user must already know the data model to use the tool, which contradicts the CIDR 2011 vision of a UI that enables exploration of unfamiliar data.

---

## Prior Art & Critique Findings Addressed

- **CHI 2011 (Related Worksheets)**: Schema Navigation pane allows users to browse related tables and add fields by clicking. This is the primary inspiration for Phase 1.
- **SIGMOD 2016 (SIEUFERD)**: Field selector panel with visibility toggles, sort/filter indicators, and render mode display.
- **CIDR 2011**: Schema browsing as a first-class UI activity — users should be able to explore the database schema and construct queries interactively.
- **RDR-020** (GraphQL AST Retention): Retaining the parsed AST enables query modification (adding/removing fields) without re-parsing.

---

## Dependencies

- **RDR-026** (QueryState): DONE. Field visibility and properties are tracked in `LayoutQueryState`.
- **RDR-027** (Interaction Model): DONE. `ToggleVisible` and other interactions drive field manipulation.
- **RDR-020** (GraphQL AST Retention): DONE. Retained AST enables programmatic query modification.

---

## Proposed Solution

### Phase 1: Schema Tree Panel

A collapsible panel displaying the `SchemaNode` hierarchy as an interactive tree:

- Tree nodes mirror the `Relation`/`Primitive` structure of the current schema
- Each node has a visibility checkbox that dispatches `ToggleVisible` through `InteractionHandler`
- Click on a node scrolls the layout to that field's position
- Tree nodes show field type information (scalar type for Primitives, cardinality for Relations)
- Collapsible sections for nested Relations
- Panel position: left sidebar (collapsible) or top-level tab

### Phase 2: Field Properties Inspector

A detail panel that appears when a field is selected in the schema tree or the layout:

- Shows all `QueryState` properties for the selected field: sort direction, filter expression, render mode, formula, aggregate, visibility
- Inline editing of each property — changes dispatch corresponding `LayoutInteraction` events
- Displays the field's `SchemaPath` for reference
- Shows field data type and cardinality
- Read-only display of computed properties (justified width, column count)

### Phase 3: Schema Diagram

A visual representation of the relation hierarchy:

- Graph or tree diagram showing `Relation` nesting structure
- Nesting depth visualization (indentation or concentric containers)
- Cardinality hints (one-to-many, one-to-one) derived from schema structure
- Clickable nodes to navigate to the corresponding section in the layout
- Minimap-style overview for deeply nested schemas

### Phase 4: GraphQL Schema Introspection

Integration with GraphQL endpoint introspection to enable progressive query construction:

- Browse available types and fields from the connected GraphQL endpoint via introspection query
- Display type hierarchy, field types, and descriptions
- Add fields to the active query by clicking in the schema browser (modifies retained AST from RDR-020)
- Remove fields from the active query
- Discover joinable relations — show related types that can be navigated to
- Re-execute modified query and re-layout with updated schema

---

## Scope Exclusions

- **Schema editing/DDL**: Kramer is read-only; no schema modification.
- **Cross-endpoint joins**: Joining data from multiple GraphQL endpoints is out of scope.
- **Query history/bookmarks**: Useful but orthogonal; deferred.
- **Schema diff**: Comparing schema versions is out of scope.
- **Type-ahead query builder**: A full query builder UI (like GraphiQL) is not the goal — the schema browser feeds into the existing autolayout pipeline.

---

## Implementation Plan

### Phase 1: Schema Tree Panel
- `SchemaTreePanel` JavaFX control wrapping `TreeView<SchemaNode>`
- `SchemaTreeItem` extending `TreeItem` with lazy children loading for Relations
- Visibility checkbox binding to `QueryState.getVisibleOrDefault(path)`
- Checkbox toggle dispatches `ToggleVisible` through `InteractionHandler`
- Panel integrated into `AutoLayoutController` as a collapsible sidebar
- Tests: tree structure matches schema; checkbox toggle hides/shows field

### Phase 2: Field Properties Inspector
- `FieldInspectorPanel` JavaFX control with property editors
- Selection binding: tree selection or layout cell selection → inspector update
- Property editors for each `FieldState` property (sort, filter, render mode, formula, aggregate)
- Edits dispatch corresponding `LayoutInteraction` events
- Tests: select field → inspector shows correct state; edit property → state updates

### Phase 3: Schema Diagram
- `SchemaDiagramView` using JavaFX `Canvas` or `Pane` with positioned nodes
- Layout algorithm for tree/graph visualization (simple top-down tree layout)
- Click-to-navigate wiring
- Tests: diagram nodes correspond to schema structure; click navigates correctly

### Phase 4: GraphQL Schema Introspection
- Introspection query execution via `GraphQlUtil` (kramer-ql)
- `IntrospectionTreePanel` displaying available types and fields
- AST modification: add/remove `Field` nodes in retained GraphQL AST (RDR-020)
- Re-execution pipeline: modify AST → serialize to query string → execute → re-layout
- Tests: introspection query returns types; adding field modifies AST; re-execution produces updated layout

---

## Research Findings

### RF-1: SchemaView Already Exists — CheckTreeView with Fold Toggle
**Status**: verified
**Source**: Codebase analysis, `explorer/src/main/java/.../SchemaView.java`

A `SchemaView` class already exists in the `explorer` module. It builds a `CheckTreeView<String>` from the `Relation` hierarchy with:
- Recursive `buildItem(Relation)` that creates `CheckBoxTreeItem` per node
- `item.setIndependent(true)` for independent checkbox state per node
- Checkbox toggle drives `node.setFold(!c)` — controls auto-fold, NOT `ToggleVisible`
- Only shows `Relation` nodes (filters `c instanceof Relation`), skips `Primitive` leaves
- Has a `SchemaViewSkin` (empty shell — default `SkinBase`)

**Gap for Phase 1**: Current `SchemaView` controls fold/unfold, not field visibility. Must be extended or replaced to:
1. Show `Primitive` nodes (not just Relations)
2. Dispatch `ToggleVisible` instead of `setFold()`
3. Show state badges (sort, filter, render mode)
4. Integrate with `InteractionHandler` + `LayoutQueryState`

The existing `SchemaView` is already wired into `AutoLayoutController` as a tab ("showSchema" radio button, line 177-179). This means the sidebar infrastructure exists — Phase 1 can extend it.

### RF-2: SchemaIntrospector Exists — But Does Argument Inspection, Not Schema Browsing
**Status**: verified
**Source**: Codebase analysis, `kramer-ql/src/main/java/.../SchemaIntrospector.java`

`SchemaIntrospector.discover()` inspects `Field.getArguments()` in a `SchemaContext` to detect server capabilities (filter/sort/page argument names). It does NOT perform GraphQL `__schema` introspection queries against the endpoint.

**Gap for Phase 4**: True schema introspection (browsing available types/fields from an endpoint) requires:
1. Execute `{ __schema { types { name fields { name type { name } } } } }` query
2. Parse the result into a browseable type tree
3. Map type fields to GraphQL `Field` AST nodes for query modification

The name `SchemaIntrospector` is already taken by the argument-detection class. Phase 4 should use a different name (e.g., `EndpointSchemaExplorer` or `TypeIntrospector`).

### RF-3: Related Worksheets — Show/Hide Columns Tree Design (CHI 2011 §3)
**Status**: verified
**Source**: related_worksheets_chi2011.pdf §3

The Show/Hide Columns feature is "a recursive selection of columns from the referenced row, configured through the Show/Hide Columns tree of checkboxes, similar to the Schema Navigation pane." The tree shows "forward and reverse references" — both parent→child and child→parent navigation.

**Design implication**: Phase 1 tree should show bidirectional relationships where available. In GraphQL context, this means showing both a Relation's children AND which parent Relations reference it.

### RF-4: SIEUFERD — Field Selector Shows Join Conditions (SIGMOD 2016 §3)
**Status**: verified
**Source**: sieuferd_sigmod2016.pdf §3

"The field selector can also be used to see the exact join conditions between READINGS and COURSES." The field selector is not just a visibility toggle — it shows relationship semantics.

**Design implication**: Phase 2 (Field Properties Inspector) should show how the field relates to its parent (which GraphQL field/argument connects them). This is derivable from `SchemaContext.fieldIndex()`.

### RF-5: SIEUFERD User Study — Schema Diagram Demand (SIGMOD 2016 §4.2)
**Status**: verified
**Source**: sieuferd_sigmod2016.pdf §4.2

"Users JM also commented that they would have liked to see a schema diagram." Users wanted "to get an overview of the complete database schema from within the query interface." The paper notes this as a gap in SIEUFERD itself.

**Design implication**: Phase 3 (Schema Diagram) addresses a gap that even SIEUFERD acknowledged. This validates Phase 3's inclusion despite being lower priority.

### RF-6: Auto-Join Heuristic — Only PK Fields Shown Initially (SIGMOD 2016 §3.1)
**Status**: verified
**Source**: sieuferd_sigmod2016.pdf §3.1

"For auto joins, only primary key fields were shown initially" — when a new table is joined, SIEUFERD shows minimal fields by default, letting the user expand visibility. The paper also notes: "several proposed attribute ranking algorithms could be used to configure this setting automatically."

**Design implication**: Phase 4 (GraphQL introspection) should show all fields when browsing, but when a user adds a new relation to the query, start with minimal fields visible (id + name-like fields). Use a heuristic or let the user select which fields to include.

### RF-7: Plan Audit — Phase 1 Independence Confirmed
**Status**: verified
**Source**: Plan audit 2026-03-19, Sig-4

RDR-030 Phase 1 does NOT depend on RDR-029 Phase 1 (sort indicators). It only needs `InteractionHandler` + `LayoutQueryState`, both of which exist. Can run in parallel with Wave 6.

### RF-9: Phase 1 Largely Implemented via RDR-029 FieldSelectorPanel
**Status**: verified
**Source**: RDR-029 Phase 3 implementation, 2026-03-19

`FieldSelectorPanel` was built as part of RDR-029 (Interaction UI Affordances) and already provides:
- `TreeView<SchemaNode>` showing full hierarchy (both Relations and Primitives)
- Per-node visibility checkbox dispatching `ToggleVisible` via `InteractionHandler`
- Per-Relation hide-if-empty checkbox dispatching `SetHideIfEmpty`
- `field-hidden` CSS class for dimmed/struck-through hidden nodes
- Integrated into `AutoLayoutController` as collapsible left sidebar (Cmd+Shift+F toggle)

**Gap remaining for Phase 1**: The `FieldSelectorPanel` does NOT yet provide:
1. State badges showing sort direction, filter presence, render mode per node (RF-1 gap #3)
2. Click-to-scroll navigation to field position in the layout
3. Field type/cardinality display on tree nodes
4. Bidirectional relationship display (RF-3 design implication)

The existing `SchemaView` (fold toggle, Relations-only) remains separate and is wired to the Schema tab, not the sidebar. `FieldSelectorPanel` replaces what was planned as `SchemaTreePanel` in the Phase 1 implementation plan.

### RF-10: Layout E2E Test Framework Available for Phase Testing
**Status**: verified
**Source**: Kramer-4whh implementation, 2026-03-20

The `LayoutTestHarness` + `LayoutFixtures` framework provides infrastructure for testing any new UI panels:
- Synchronous pipeline runner that can verify data visibility after schema/interaction changes
- 4 fixture schemas including nested and deep structures
- Can verify that toggling visibility in FieldSelectorPanel correctly hides/shows fields in the rendered output

### RF-8: Retained AST Available for Phase 4 (RDR-020)
**Status**: verified
**Source**: Codebase analysis, `SchemaContext.fieldIndex()`

RDR-020 (GraphQL AST Retention) is closed. `SchemaContext` retains the parsed AST and provides `fieldIndex()` — a `Map<SchemaPath, Field>` mapping every schema path to its GraphQL `Field` node. `QueryRewriter` already modifies this AST (adding arguments). Phase 4 can extend this to add/remove `Field` nodes from selection sets.

**Risk**: AST modification for field addition requires creating new `Field` nodes with correct type information from the introspection result. Round-trip test (modify → serialize → parse → compare) is essential.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Schema tree panel adds UI complexity | Low | Collapsible sidebar; starts hidden |
| Large schemas produce unwieldy trees | Medium | Lazy loading of tree children; search/filter within tree |
| GraphQL introspection may be disabled on some endpoints | Medium | Graceful degradation — Phase 4 features disabled when introspection unavailable |
| AST modification for query construction is fragile | High | Comprehensive tests; round-trip validation (modify → serialize → parse → compare) |
| Performance of re-execution on field add/remove | Low | Debounce rapid changes; show loading indicator |

---

## Success Criteria

- [ ] Schema tree panel displays the full `SchemaNode` hierarchy with correct nesting
- [ ] Visibility checkboxes in the tree toggle field display and reflect current state
- [ ] Field properties inspector shows all `QueryState` properties for the selected field
- [ ] Inline property editing dispatches correct `LayoutInteraction` events
- [ ] Schema diagram visualizes relation hierarchy with nesting depth
- [ ] GraphQL introspection browses available types and fields from the endpoint
- [ ] Adding a field from the introspection browser modifies the active query and re-layouts
- [ ] Removing a field from the active query updates both the layout and the schema tree
- [ ] All panels work with both outline and table rendering modes
