---
title: "Schema Navigation & Discovery"
id: RDR-030
type: Feature
status: proposed
priority: P1
author: Hal Hildebrand
created: 2026-03-19
related: RDR-026, RDR-027, RDR-020, RDR-029
---

# RDR-030: Schema Navigation & Discovery

## Metadata
- **Type**: Feature
- **Status**: proposed
- **Priority**: P1
- **Created**: 2026-03-19
- **Reviewed-by**: —
- **Related**: RDR-026 (QueryState), RDR-027 (Interaction Model), RDR-020 (GraphQL AST Retention), RDR-029 (Interaction UI Affordances)

---

## Problem Statement

All four Bakke papers emphasize interactive schema browsing as a core capability of schema-independent database UIs. Kramer has basic field visibility controls (via `FieldSelectorPanel` from RDR-029) but lacks schema navigation, field properties inspection, schema visualization, or progressive query construction. Users must bring a pre-formed GraphQL query — there is no way to explore the schema, discover available fields, or construct a query from within the UI.

---

## Prior Art & Critique Findings Addressed

- **CHI 2011 (Related Worksheets)**: Schema Navigation pane allows users to browse related tables and add fields by clicking. This is the primary inspiration for Phase 1.
- **SIGMOD 2016 (SIEUFERD)**: Field selector panel with visibility toggles, sort/filter indicators, and render mode display.
- **CIDR 2011**: Schema browsing as a first-class UI activity — users should be able to explore the database schema and construct queries interactively.
- **RDR-020** (GraphQL AST Retention): Retaining the parsed AST enables query modification (adding/removing fields) without re-parsing.
- **RDR-029** (Interaction UI Affordances): Built `FieldSelectorPanel` with TreeView, visibility checkboxes, and hide-if-empty — the foundation for Phase 1.

---

## Dependencies

- **RDR-026** (QueryState): DONE. Field visibility and properties are tracked in `LayoutQueryState`.
- **RDR-027** (Interaction Model): DONE. `ToggleVisible` and other 11 `LayoutInteraction` variants drive field manipulation.
- **RDR-020** (GraphQL AST Retention): DONE. Retained AST enables programmatic query modification.
- **RDR-029** (Interaction UI Affordances): DONE. `FieldSelectorPanel` provides the base TreeView with visibility checkboxes.

---

## Proposed Solution

### Phase 1: Schema Tree Panel Enhancement (delta on FieldSelectorPanel)

`FieldSelectorPanel` (from RDR-029) already provides the TreeView with visibility checkboxes and hide-if-empty toggles. Phase 1 adds the remaining schema navigation features:

- State badges on tree nodes showing current sort direction, active filter, and render mode override
- Click-to-scroll: selecting a tree node scrolls the layout to that field's position
- Field type/cardinality display on tree nodes (scalar type for Primitives, row count for Relations)

### Phase 2: Field Properties Inspector

A detail panel that appears when a field is selected in the schema tree or the layout:

- Shows all `QueryState` properties for the selected field: sort direction, filter expression, render mode, formula, aggregate, visibility
- Inline editing of each property — changes dispatch corresponding `LayoutInteraction` events (including `SortBy`/`ClearSort` for sort, per the 11-variant sealed interface)
- Displays the field's `SchemaPath` and parent relationship context (derivable from `SchemaContext.fieldIndex()`)
- Shows field data type and cardinality
- Read-only display of computed properties (justified width, column count)

### Phase 3: Schema Diagram

A visual representation of the relation hierarchy:

- Tree diagram showing `Relation` nesting structure using JavaFX `Pane` with positioned nodes
- Nesting depth visualization (indentation or concentric containers)
- Cardinality hints (one-to-many, one-to-one) derived from schema structure
- Clickable nodes to navigate to the corresponding section in the layout

### Phase 4: GraphQL Schema Introspection

Integration with GraphQL endpoint introspection to enable progressive query construction:

- Browse available types and fields from the connected GraphQL endpoint via `__schema` introspection query
- Display type hierarchy, field types, and descriptions
- Add fields to the active query by clicking in the schema browser — requires a new `QueryExpander` component (distinct from `QueryRewriter`, which only adds arguments to existing selections; see RF-8 risk note)
- Remove fields from the active query
- Discover joinable relations — show related types that can be navigated to
- Re-execute modified query and re-layout with updated schema
- When adding a new relation, start with minimal fields visible (id + name-like fields per RF-6 heuristic)

---

## Scope Exclusions

- **Schema editing/DDL**: Kramer is read-only; no schema modification.
- **Cross-endpoint joins**: Joining data from multiple GraphQL endpoints is out of scope.
- **Query history/bookmarks**: Useful but orthogonal; deferred.
- **Schema diff**: Comparing schema versions is out of scope.
- **Type-ahead query builder**: A full query builder UI (like GraphiQL) is not the goal — the schema browser feeds into the existing autolayout pipeline.
- **Bidirectional relationship display in Phase 1**: Kramer's `Relation`/`Primitive` tree is strictly hierarchical with no back-references. Showing reverse edges would require schema model changes. The `SchemaPath` breadcrumb on selected nodes (Phase 2 inspector) addresses the parent-context need without model changes.

---

## Implementation Plan

### Phase 1: Schema Tree Panel Enhancement
- Extend `FieldSelectorPanel` (not replace — it already provides TreeView, checkboxes, hide-if-empty)
- Add state badge rendering per tree cell: sort arrow icon, filter indicator, render mode label
- Add click-to-scroll: tree selection → find corresponding VirtualFlow cell → `showAsFirst()`
- Add field type/cardinality labels on tree nodes
- Tests: state badges update on sort/filter change; click-to-scroll navigates correctly; type labels display
- Note: `FieldSelectorPanel` tests (`FieldSelectorPanelTest.java`) cover the existing checkbox functionality; new tests cover only the delta features

### Phase 2: Field Properties Inspector
- `FieldInspectorPanel` JavaFX control with property editors
- Selection binding: tree selection or layout cell selection → inspector update
- Property editors for each `FieldState` property (sort via `SortBy`/`ClearSort`, filter, render mode, formula, aggregate)
- Edits dispatch corresponding `LayoutInteraction` events (11 variants per RDR-029)
- Parent relationship display via `SchemaPath` breadcrumb
- Tests: select field → inspector shows correct state; edit property → state updates

### Phase 3: Schema Diagram
- `SchemaDiagramView` using JavaFX `Pane` with positioned `Label`/`Region` nodes
- Simple top-down tree layout algorithm (no external library needed for Kramer's typically shallow nesting)
- Click-to-navigate wiring
- Tests: diagram nodes correspond to schema structure; click navigates correctly

### Phase 4: GraphQL Schema Introspection
- Introspection query execution via `GraphQlUtil` (kramer-ql)
- `TypeIntrospector` class (not `SchemaIntrospector`, which is taken for argument detection per RF-2)
- `IntrospectionTreePanel` displaying available types and fields
- `QueryExpander` class for AST modification (adding/removing `Field` nodes in selection sets) — distinct from `QueryRewriter` which only injects arguments into existing selections. This is a structurally different operation requiring new `Field` node creation with type information from the introspection result.
- Re-execution pipeline: expand AST → serialize to query string → execute → re-layout
- Spike/design step recommended before implementation to validate AST round-trip
- Tests: introspection query returns types; adding field modifies AST; round-trip validation (modify → serialize → parse → compare); re-execution produces updated layout

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

**Gap for Phase 1**: Current `SchemaView` controls fold/unfold, not field visibility. `FieldSelectorPanel` (RDR-029) supersedes this for visibility control. `SchemaView` remains as a separate fold-toggle tab.

### RF-2: SchemaIntrospector Exists — But Does Argument Inspection, Not Schema Browsing
**Status**: verified
**Source**: Codebase analysis, `kramer-ql/src/main/java/.../SchemaIntrospector.java`

`SchemaIntrospector.discover()` inspects `Field.getArguments()` in a `SchemaContext` to detect server capabilities (filter/sort/page argument names). It does NOT perform GraphQL `__schema` introspection queries against the endpoint.

**Gap for Phase 4**: True schema introspection (browsing available types/fields from an endpoint) requires:
1. Execute `{ __schema { types { name fields { name type { name } } } } }` query
2. Parse the result into a browseable type tree
3. Map type fields to GraphQL `Field` AST nodes for query modification

The name `SchemaIntrospector` is already taken. Phase 4 should use `TypeIntrospector`.

### RF-3: Related Worksheets — Show/Hide Columns Tree Design (CHI 2011 §3)
**Status**: verified
**Source**: related_worksheets_chi2011.pdf §3

The Show/Hide Columns feature is "a recursive selection of columns from the referenced row, configured through the Show/Hide Columns tree of checkboxes, similar to the Schema Navigation pane." The tree shows "forward and reverse references" — both parent→child and child→parent navigation.

**Design implication**: The bidirectional relationship display from CHI 2011 requires back-references that don't exist in Kramer's hierarchical schema model. Phase 2's `SchemaPath` breadcrumb provides parent context without model changes. Full bidirectional display is deferred as out of scope.

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
**Status**: superseded by RF-9
**Source**: Plan audit 2026-03-19, Sig-4

Originally confirmed Phase 1 could run independently of RDR-029 Phase 1. Now superseded: Phase 1 is a delta on `FieldSelectorPanel` which was built in RDR-029 (closed).

### RF-8: Retained AST Available for Phase 4 (RDR-020)
**Status**: verified
**Source**: Codebase analysis, `SchemaContext.fieldIndex()`

RDR-020 (GraphQL AST Retention) is closed. `SchemaContext` retains the parsed AST and provides `fieldIndex()` — a `Map<SchemaPath, Field>` mapping every schema path to its GraphQL `Field` node. `QueryRewriter` already modifies this AST (adding arguments).

**Risk**: `QueryRewriter.rewrite()` adds arguments to existing field selections — it does NOT add new `Field` nodes to a `SelectionSet`. Phase 4 requires a distinct operation (`QueryExpander`) that creates new `Field` nodes with type information from the introspection result. This is structurally different from argument injection and may require a spike to validate AST round-trip correctness.

### RF-9: Phase 1 Largely Implemented via RDR-029 FieldSelectorPanel
**Status**: verified
**Source**: RDR-029 Phase 3 implementation, 2026-03-19

`FieldSelectorPanel` was built as part of RDR-029 (Interaction UI Affordances) and already provides:
- `TreeView<SchemaNode>` showing full hierarchy (both Relations and Primitives)
- Per-node visibility checkbox dispatching `ToggleVisible` via `InteractionHandler`
- Per-Relation hide-if-empty checkbox dispatching `SetHideIfEmpty`
- `field-hidden` CSS class for dimmed/struck-through hidden nodes
- Integrated into `AutoLayoutController` as collapsible left sidebar (Cmd+Shift+F toggle)

**Remaining Phase 1 scope** (delta features only):
1. State badges showing sort direction, filter presence, render mode per node
2. Click-to-scroll navigation to field position in the layout
3. Field type/cardinality display on tree nodes

### RF-10: Layout E2E Test Framework Available for Phase Testing
**Status**: verified
**Source**: Kramer-4whh implementation, 2026-03-20

The `LayoutTestHarness` + `LayoutFixtures` framework provides infrastructure for testing pipeline behavior after schema/interaction changes. Testing `FieldSelectorPanel` checkbox interactions requires TestFX (JavaFX test runner), which is already used by the existing `FieldSelectorPanelTest`.

---

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Large schemas produce unwieldy trees | Medium | Lazy loading of tree children; search/filter within tree |
| GraphQL introspection may be disabled on some endpoints | Medium | Graceful degradation — Phase 4 features disabled when introspection unavailable |
| AST modification for query construction is fragile | High | Spike before Phase 4; `QueryExpander` separate from `QueryRewriter`; round-trip validation tests |
| Performance of re-execution on field add/remove | Low | Debounce rapid changes; show loading indicator |
| Phase 3 diagram layout algorithm complexity | Low | Kramer schemas are typically shallow (2-4 levels); simple top-down tree layout suffices |

---

## Success Criteria

- [x] Schema tree panel displays the full `SchemaNode` hierarchy with correct nesting _(implemented in RDR-029 FieldSelectorPanel)_
- [x] Visibility checkboxes in the tree toggle field display and reflect current state _(implemented in RDR-029 FieldSelectorPanel)_
- [ ] Tree nodes show state badges for sort, filter, and render mode
- [ ] Click on a tree node scrolls the layout to that field
- [ ] Field properties inspector shows all `QueryState` properties for the selected field
- [ ] Inline property editing dispatches correct `LayoutInteraction` events (11 variants)
- [ ] Schema diagram visualizes relation hierarchy with nesting depth
- [ ] GraphQL introspection browses available types and fields from the endpoint
- [ ] Adding a field from the introspection browser modifies the active query via `QueryExpander` and re-layouts
- [ ] Removing a field from the active query updates both the layout and the schema tree
- [ ] All panels work with both outline and table rendering modes
