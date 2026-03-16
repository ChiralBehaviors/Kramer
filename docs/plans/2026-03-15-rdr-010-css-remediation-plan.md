# RDR-010: CSS Integration Remediation — Execution Plan

**Created**: 2026-03-15
**Epic**: Kramer-5zm
**RDR**: docs/rdr/RDR-010-css-integration-remediation.md
**Status**: Plan created, pending audit

---

## Executive Summary

Remediate CSS integration bugs and structural weaknesses across Style, AutoLayout, and
component CSS layers. Four phases: correctness fixes (C1/C2/C4), safety assertions (S1/S2),
maintainability improvements (S3/S4/S5), and LayoutStylesheet wiring (C3, blocked on
RDR-009 Phase B). Phases 1-3 are self-contained and unblocked.

---

## Bead Hierarchy

```
Kramer-5zm [epic] RDR-010: CSS Integration Remediation
  Kramer-a1d  Phase 1: Correctness (C2, C4a, C4b, C1)
    Kramer-jk2  C2: Make PRIMITIVE_TEXT_CLASS final         [ready]
    Kramer-8ml  C4a: AutoLayout stylesheet equality guard   [ready]
    Kramer-odd  C4b: Style single-owner assertion           [blocked by Kramer-8ml]
    Kramer-253  C1: SchemaPath CSS class sanitization       [blocked by Kramer-odd]
  Kramer-n2f  Phase 2: Safety & Extensibility (S1, S2)
    Kramer-00b  S1: JAT assertions on measurement methods   [blocked by Kramer-253]
    Kramer-dk9  S2: Protected visibility for measurement    [blocked by Kramer-253]
  Kramer-nqs  Phase 3: Maintainability (S3, S4, S5)
    Kramer-ea0  S3: CSS gradient dedup via .kramer-container [blocked by Kramer-00b]
    Kramer-pwn  S4: Extract measurement scene constants      [blocked by Kramer-00b]
    Kramer-nmt  S5: Style.clearCaches() lifecycle method     [blocked by Kramer-00b]
  Kramer-ee4  Phase 4: LayoutStylesheet wiring (C3) — BLOCKED on Kramer-53g
    Kramer-e30  C3: Wire LayoutStylesheet for algo params    [blocked externally]
```

---

## Dependency Graph

```
Kramer-jk2 (C2) ─────────────────────────────────────────┐
                                                          │ (parallel start)
Kramer-8ml (C4a) ─→ Kramer-odd (C4b) ─→ Kramer-253 (C1) ┤
                                              │           │
                                              ├─→ Kramer-00b (S1) ─→ Kramer-ea0 (S3)
                                              │                  ─→ Kramer-pwn (S4)
                                              │                  ─→ Kramer-nmt (S5)
                                              ├─→ Kramer-dk9 (S2)
                                              │
                                              └─→ Kramer-e30 (C3) ←── [BLOCKED: Kramer-53g]
```

**Critical path**: C4a → C4b → C1 → S1 → {S3, S4, S5}
**Parallelizable at start**: C2 and C4a
**Parallelizable in Phase 2**: S1 and S2
**Parallelizable in Phase 3**: S3, S4, S5

---

## Phase 1: Correctness (C2, C4a, C4b, C1)

### Kramer-jk2 — C2: Make PRIMITIVE_TEXT_CLASS final

**Context**: `PrimitiveTextStyle.PRIMITIVE_TEXT_CLASS` is `public static String` — mutable.
`PrimitiveLayoutCell.DEFAULT_STYLE` is already `public static final String` (no issue).

**Files**:
- `kramer/src/main/java/com/chiralbehaviors/layout/style/PrimitiveStyle.java`

**TDD Steps**:
1. Write test in new `CssCorrectnessTest.java`:
   - Assert `PrimitiveTextStyle.PRIMITIVE_TEXT_CLASS.equals("primitive-text")`
   - Use reflection to assert the field has `Modifier.FINAL`
2. Add `final` to the field declaration
3. Verify compilation and all tests pass

**Acceptance Criteria**:
- [ ] Field is `public static final String`
- [ ] Reflection test confirms finality
- [ ] All existing tests pass

---

### Kramer-8ml — C4a: AutoLayout stylesheet equality guard

**Context**: AutoLayout constructor (line 82-89) unconditionally calls
`model.setStyleSheets(getStylesheets())` on any ListChangeListener event,
including the initial add of `default.css`. This clears all caches unnecessarily.

**Files**:
- `kramer/src/main/java/com/chiralbehaviors/layout/AutoLayout.java`

**TDD Steps**:
1. Write test (requires JavaFX toolkit init):
   - Create Style, populate caches via `style(primitive)`
   - Create AutoLayout with that Style — constructor fires listener
   - Assert caches are NOT cleared (since stylesheet list is the same)
   - Alternative: verify `setStyleSheets` is called 0 times when list equals current
2. Add equality guard in listener:
   ```java
   if (!model.styleSheets().equals(new ArrayList<>(getStylesheets()))) {
       model.setStyleSheets(getStylesheets());
       layout = null;
       measureResult = null;
       if (getData() != null) { autoLayout(); }
   }
   ```
3. Verify all tests pass

**Acceptance Criteria**:
- [ ] Equality guard prevents redundant setStyleSheets on construction
- [ ] Stylesheet changes still trigger cache invalidation
- [ ] All existing tests pass

---

### Kramer-odd — C4b: Style single-owner assertion

**Depends on**: Kramer-8ml (C4a — both modify AutoLayout constructor area)

**Context**: If two AutoLayout instances share a Style, B's constructor
overwrites A's stylesheet list, corrupting caches.

**Files**:
- `kramer/src/main/java/com/chiralbehaviors/layout/style/Style.java`
- `kramer/src/main/java/com/chiralbehaviors/layout/AutoLayout.java`

**TDD Steps**:
1. Write test:
   - Create a Style instance
   - Call `style.setStyleSheets(list, ownerA)` — succeeds
   - Call `style.setStyleSheets(list, ownerB)` — throws IllegalStateException
2. Add `private Object owner` field to Style
3. Modify `setStyleSheets` to accept owner parameter (or use overload):
   ```java
   public void setStyleSheets(List<String> stylesheets, Object owner) {
       if (this.owner != null && this.owner != owner) {
           throw new IllegalStateException("Style is owned by another AutoLayout");
       }
       this.owner = owner;
       // existing logic
   }
   ```
4. Update AutoLayout to pass `this` as owner
5. Verify all tests pass

**Acceptance Criteria**:
- [ ] Style enforces single-ownership via owner assertion
- [ ] IllegalStateException on second owner
- [ ] AutoLayout passes `this` as owner
- [ ] All existing tests pass

---

### Kramer-253 — C1: SchemaPath CSS class sanitization

**Depends on**: Kramer-odd (C4b — both touch Style.java, avoids merge conflicts)

**Context**: `SchemaPath.cssClass()` returns `leaf()` unsanitized. 12 CSS class
generation sites use `getField()` directly instead of routing through SchemaPath.

**Files**:
- `kramer/src/main/java/com/chiralbehaviors/layout/SchemaPath.java`
- 10 SCHEMA_CLASS_TEMPLATE sites:
  - `PrimitiveList.java`, `NestedTable.java`, `NestedRow.java`, `NestedCell.java`
  - `Outline.java`, `OutlineCell.java`, `OutlineColumn.java`, `OutlineElement.java`
  - (Span.java uses getField() in constructor delegation, not SCHEMA_CLASS_TEMPLATE directly)
- 2 measurement sites: `Style.computePrimitiveStyle()`, `Style.computeRelationStyle()`

**TDD Steps**:
1. Write tests in `SchemaPathTest.java` (extend existing):
   - `cssClass()` with `"my.field"` returns `"my_field"`
   - `cssClass()` with `"123abc"` returns `"_123abc"`
   - `cssClass()` with `""` returns `"_unknown"`
   - `cssClass()` with `null` leaf returns `"_unknown"` (if possible via API)
   - `cssClass()` with `"valid-name_ok"` returns `"valid-name_ok"` (passthrough)
   - `cssClass()` with `"a b c"` returns `"a_b_c"`
2. Add `private static String sanitize(String raw)` to SchemaPath:
   ```java
   private static String sanitize(String raw) {
       if (raw == null || raw.isEmpty()) return "_unknown";
       String cleaned = raw.replaceAll("[^a-zA-Z0-9_-]", "_");
       if (Character.isDigit(cleaned.charAt(0))) cleaned = "_" + cleaned;
       return cleaned;
   }
   ```
3. Update `cssClass()`: `return sanitize(leaf());`
4. Migrate 10 SCHEMA_CLASS_TEMPLATE call sites:
   - Each component constructor takes `String field` — change to use
     `SchemaPath.sanitize(field)` or add a `public static String sanitizeCssClass(String)`
     utility on SchemaPath since components don't have a SchemaPath instance.
   - Alternative: add `SchemaPath.sanitizeCssClass(String)` as public static method,
     have `cssClass()` call it, and have components call it.
5. Migrate 2 measurement sites in Style.java:
   - `computePrimitiveStyle`: change `p.getField()` usages for CSS class to
     `SchemaPath.sanitizeCssClass(p.getField())`
   - `computeRelationStyle`: change `r.getField()` usages for CSS class to
     `SchemaPath.sanitizeCssClass(r.getField())`

**Acceptance Criteria**:
- [ ] SchemaPath.cssClass() sanitizes all non-CSS-safe characters
- [ ] All 12 call sites route through sanitization
- [ ] Edge cases (leading digit, empty, special chars) handled
- [ ] All existing tests pass
- [ ] New sanitization tests pass

---

## Phase 2: Safety & Extensibility (S1, S2)

### Kramer-00b — S1: JAT assertions on measurement methods

**Depends on**: Kramer-253 (C1 — C1 modifies same methods)

**Files**:
- `kramer/src/main/java/com/chiralbehaviors/layout/style/Style.java`

**TDD Steps**:
1. Write test (needs JavaFX toolkit init and S2's protected visibility for clean testing,
   but can use reflection if S2 not yet done):
   - Call computePrimitiveStyle from a background thread with assertions enabled (-ea)
   - Assert AssertionError is thrown
2. Add `assert Platform.isFxApplicationThread() : "Style measurement must run on JAT";`
   at top of `computePrimitiveStyle()` and `computeRelationStyle()`
3. Add class-level Javadoc:
   ```java
   /**
    * Style factory — measures CSS insets by creating throwaway JavaFX scenes.
    * <p>Single-threaded, must be used on the JavaFX Application Thread.
    * Not safe for concurrent access.</p>
    */
   ```
4. Verify all tests pass (ensure test runner uses -ea)

**Acceptance Criteria**:
- [ ] Both methods have JAT assertion as first statement
- [ ] Class-level Javadoc documents JAT-bound contract
- [ ] Test verifies assertion fires on background thread
- [ ] All existing tests pass (they already run on JAT)

---

### Kramer-dk9 — S2: Protected visibility for measurement methods

**Depends on**: Kramer-253 (C1 — avoids editing same methods concurrently)

**Files**:
- `kramer/src/main/java/com/chiralbehaviors/layout/style/Style.java`

**TDD Steps**:
1. Write test:
   - Create a Style subclass that overrides `computePrimitiveStyle` to return
     a known PrimitiveStyle instance
   - Call `style(primitive)` on the subclass — verify the overridden method was invoked
2. Change `private` to `protected` on both methods
3. Verify compilation and all tests pass

**Acceptance Criteria**:
- [ ] Both methods are `protected`
- [ ] Subclass can override measurement
- [ ] All existing tests pass

---

## Phase 3: Maintainability (S3, S4, S5)

All three tasks can be parallelized — they touch different files/methods.

### Kramer-ea0 — S3: CSS gradient deduplication via .kramer-container

**Depends on**: Kramer-00b (S1 — clean sequencing after Style changes)

**Files**:
- `kramer/src/main/resources/com/chiralbehaviors/layout/default.css`
- `kramer/src/main/java/com/chiralbehaviors/layout/cell/LayoutCell.java`
- 8 component CSS files:
  - `outline-cell.css`, `outline-column.css`, `outline-element.css`, `outline.css`, `span.css`
  - `nested-cell.css`, `nested-row.css`, `nested-table.css`

**TDD Steps**:
1. Write test:
   - Initialize a LayoutCell implementation
   - Assert `getNode().getStyleClass().contains("kramer-container")`
   - (Visual regression: compare rendered gradient before/after — manual verification)
2. Add `.kramer-container` selector in `default.css`:
   ```css
   .kramer-container {
       -fx-background-color: linear-gradient(to bottom, derive(-fx-text-box-border, -10%), -fx-text-box-border),
           linear-gradient(from 0px 0px to 0px 5px, derive(-fx-control-inner-background, -9%), -fx-control-inner-background);
   }
   ```
3. Add `"kramer-container"` to style class in `LayoutCell.initialize()`:
   ```java
   node.getStyleClass().add("kramer-container");
   ```
4. Remove `-fx-background-color: linear-gradient(...)` from all 8 component CSS files
5. Update `.primitive` in `default.css` to reference `.kramer-container` for base gradient
   (`.primitive:hover` remains separate — distinct multi-layer background)
6. Verify visual correctness and all tests pass

**Acceptance Criteria**:
- [ ] Gradient declared only in `default.css` `.kramer-container`
- [ ] All LayoutCell nodes have `kramer-container` style class
- [ ] 8 component CSS files no longer contain gradient
- [ ] Visual appearance unchanged
- [ ] All existing tests pass

---

### Kramer-pwn — S4: Extract measurement scene magic constants

**Depends on**: Kramer-00b (S1 — same methods modified)

**Files**:
- `kramer/src/main/java/com/chiralbehaviors/layout/style/Style.java`

**TDD Steps**:
1. Write test:
   - Create a Primitive and Relation with known field names
   - Measure with scene size 800x600 (default), record insets
   - Measure with scene size 400x300, record insets
   - Measure with scene size 1600x1200, record insets
   - Assert all three produce identical insets
2. Extract constants:
   ```java
   /** Measurement scene width in logical pixels. Scene size does not affect CSS property extraction. */
   private static final int MEASUREMENT_SCENE_WIDTH = 800;
   /** Measurement scene height in logical pixels. Scene size does not affect CSS property extraction. */
   private static final int MEASUREMENT_SCENE_HEIGHT = 600;
   ```
3. Replace `new Scene(root, 800, 600)` with
   `new Scene(root, MEASUREMENT_SCENE_WIDTH, MEASUREMENT_SCENE_HEIGHT)` in both methods
4. Verify all tests pass

**Acceptance Criteria**:
- [ ] Constants named and documented
- [ ] Test proves scene size invariance
- [ ] All existing tests pass

---

### Kramer-nmt — S5: Style.clearCaches() lifecycle method

**Depends on**: Kramer-00b (S1 — clean sequencing)

**Files**:
- `kramer/src/main/java/com/chiralbehaviors/layout/style/Style.java`

**TDD Steps**:
1. Write test (extend `StyleCacheTest.java`):
   - Create Style, populate caches via `style(primitive)` and `style(relation)`
   - Assert cache sizes > 0 via existing size accessor methods
   - Call `clearCaches()`
   - Assert all three cache sizes == 0
2. Add method:
   ```java
   /**
    * Clears all style and layout caches. Call when discarding a schema tree
    * or when the Style instance will be reused with a different schema.
    * <p>Callers must call this or replace the Style instance when done
    * with a schema tree to prevent unbounded cache growth.</p>
    */
   public void clearCaches() {
       primitiveStyleCache.clear();
       relationStyleCache.clear();
       layoutCache.clear();
   }
   ```
3. Verify all tests pass

**Acceptance Criteria**:
- [ ] Public `clearCaches()` method exists
- [ ] Lifecycle documented in Javadoc
- [ ] Test verifies all three caches cleared
- [ ] All existing tests pass

---

## Phase 4: LayoutStylesheet Wiring (C3) — BLOCKED

### Kramer-e30 — C3: Wire LayoutStylesheet for algorithm-tuning parameters

**BLOCKED on**: Kramer-53g (RDR-009 Phase B — immutable result records, deferred)

**Cannot plan detailed steps until RDR-009 Phase B is complete.**

**High-level scope**:
1. Move algorithm-tuning parameter reads from `PrimitiveStyle`/`RelationStyle` getters
   to `LayoutStylesheet.getDouble(path, key, default)` lookups
2. Remove 10 setters from PrimitiveStyle (5) and RelationStyle (5)
3. `DefaultLayoutStylesheet.setOverride()` replaces scattered setters
4. Resolve the throwaway-instance design smell in DefaultLayoutStylesheet

**Files** (anticipated):
- PrimitiveStyle.java, RelationStyle.java, DefaultLayoutStylesheet.java
- All layout pipeline consumers that call the removed setters

---

## Risk Factors

| Risk | Mitigation |
|------|------------|
| C1 12-site migration introduces regressions | Mechanical change; each site is `String.format(TEMPLATE, field)` → `String.format(TEMPLATE, SchemaPath.sanitizeCssClass(field))` |
| C4b owner assertion breaks existing code that shares Style | Audit callers — currently AutoLayout(Relation) creates its own Style, only AutoLayout(Relation, Style) accepts external. Document in constructor Javadoc. |
| S3 visual regression from CSS refactor | Manual visual check with explorer app |
| Phase 4 blocked indefinitely | Phases 1-3 deliver standalone value; C3 is architectural debt reduction, not correctness |

---

## Parallelization Opportunities

- **Phase 1 start**: C2 and C4a can run in parallel (different files entirely)
- **Phase 2**: S1 and S2 can run in parallel (same file but different edits)
- **Phase 3**: S3, S4, S5 can all run in parallel (different files/methods)
- **Across phases**: Each phase must complete before next starts (method-level conflicts)

---

## Testing Strategy

All tasks follow TDD — failing test before implementation.

JavaFX-dependent tests (C4a, C4b, S1, S3, S4) require toolkit initialization.
Existing test infrastructure already handles this (see `StyleCacheTest`, `TestLayouts`).

Test file locations:
- `CssCorrectnessTest.java` — new file for C2, C4a, C4b tests
- `SchemaPathTest.java` — extend existing for C1 sanitization
- `StyleCacheTest.java` — extend existing for S1, S5
- `StyleExtensibilityTest.java` — new file for S2
- `CssDeduplicationTest.java` — new file for S3
- `MeasurementSceneTest.java` — new file for S4

---

## Success Criteria (from RDR-010)

1. [ ] All CSS class generation routes through `SchemaPath.cssClass()` with sanitization
2. [ ] No mutable statics in the style system
3. [ ] Algorithm-tuning parameters read from `LayoutStylesheet` (Phase 4, blocked)
4. [ ] `Style` is documented as single-owner; equality guard prevents redundant cache clears
5. [ ] JAT violations caught via assertions in debug/test builds
6. [ ] CSS background gradient declared in exactly one file
7. [ ] Measurement scene constants named and documented
8. [ ] `Style.clearCaches()` exists for explicit lifecycle management
9. [ ] Tests cover each remediated issue
