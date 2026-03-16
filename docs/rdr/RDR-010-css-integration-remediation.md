# RDR-010: CSS Integration Remediation

## Metadata
- **Type**: Bug/Architecture
- **Status**: accepted
- **Priority**: P1
- **Created**: 2026-03-15
- **Revised**: 2026-03-15 (gate findings addressed)
- **Accepted**: 2026-03-15
- **Related**: RDR-009 (stylesheet data structure), RDR-007 (bug remediation)
- **Reviewed-by**: self

## Problem Statement

A substantive critique of Kramer's CSS integration architecture identified issues across the `Style`, `AutoLayout`, and component CSS layers. The CSS engine serves a dual purpose — visual styling and layout measurement (extracting computed insets/fonts from throwaway JavaFX scenes). While the overall architecture is sound, several concrete bugs and structural weaknesses undermine correctness, maintainability, and extensibility.

RDR-009 introduced `SchemaPath`, `LayoutStylesheet`, and `DefaultLayoutStylesheet` but these are not yet fully wired into the component and measurement layer. Several issues in this RDR represent the natural completion of RDR-009's Phase C migration.

Issues span three categories:
1. **Correctness bugs** — unsanitized CSS class generation, mutable statics, stale cache after style parameter mutation, shared-Style invalidation
2. **Safety & extensibility** — no thread-safety assertions, private measurement methods
3. **Maintainability** — 10-occurrence CSS gradient duplication, unbounded caches, magic constants

---

## Issues

### C1: CSS class generation unsanitized in `SchemaPath.cssClass()` (Critical)

**Location**: `SchemaPath.cssClass()` returns `leaf()` unsanitized. All 10 `SCHEMA_CLASS_TEMPLATE` call sites in `NestedTable`, `NestedRow`, `NestedCell`, `OutlineCell`, `OutlineColumn`, `OutlineElement`, `Outline`, `Span`, `PrimitiveList` call `getField()` directly. Measurement scenes in `Style.computePrimitiveStyle()` and `computeRelationStyle()` also use `p.getField()` / `r.getField()` directly, bypassing `SchemaPath` entirely.

**Problem**: Field names used as CSS class components without sanitization. While GraphQL field names are restricted to `[_A-Za-z][_0-9A-Za-z]*` (safe for CSS), the `kramer` module accepts raw JSON schemas where field names are unconstrained. A field named `"my.field"` produces `.my.field-nested-table` — CSS interprets this as two separate class selectors. Fields named `"focused"` or `"selected"` collide with pseudo-class names.

**Impact**: Silent CSS misapplication for non-GraphQL schemas. Per-field customization fails without error.

**Remediation**: Add sanitization in `SchemaPath.cssClass()` — the single canonical point established by RDR-009. Replace `return leaf()` with `return sanitize(leaf())` where `sanitize()`:
1. Replaces non-`[a-zA-Z0-9_-]` chars with `_`
2. Prepends `_` if the result begins with a digit (CSS class names cannot start with a digit)
3. Returns `_unknown` for empty or null input

Route all CSS class generation through `SchemaPath.cssClass()` instead of direct `getField()` calls. This completes the RDR-009 Phase C migration for CSS class generation.

**Call sites requiring migration** (10 `SCHEMA_CLASS_TEMPLATE` sites + 2 measurement scenes):
- `PrimitiveList`, `NestedTable`, `NestedRow`, `NestedCell` (table package)
- `Outline`, `OutlineCell`, `OutlineColumn`, `OutlineElement`, `Span` (outline package)
- `Style.computePrimitiveStyle()`, `Style.computeRelationStyle()` (measurement)

### C2: `PrimitiveTextStyle.PRIMITIVE_TEXT_CLASS` is non-final mutable static (Critical)

**Location**: `PrimitiveStyle.java` — `public static String PRIMITIVE_TEXT_CLASS = "primitive-text";`

**Problem**: Mutable static used in both CSS measurement (`computePrimitiveStyle`) and live cell rendering. Any mutation causes divergence between measured and rendered CSS classes.

**Impact**: Hard-to-diagnose measurement errors; global state mutation across threads.

**Remediation**: Make `public static final String`. Also audit `PrimitiveLayoutCell.DEFAULT_STYLE` for the same issue.

### C3: Algorithm-tuning parameters on cached style objects bypass layout cache (Critical)

**Location**: `PrimitiveStyle` setters: `setMinValueWidth()`, `setMaxTablePrimitiveWidth()`, `setVariableLengthThreshold()`, `setOutlineSnapValueWidth()`, `setVerticalHeaderThreshold()`. `RelationStyle` setters: `setOutlineMaxLabelWidth()`, `setOutlineColumnMinWidth()`, `setBulletText()`, `setBulletWidth()`, `setIndentWidth()`.

**Problem**: These are **algorithm-tuning parameters**, not CSS-derived insets. The CSS-derived insets (table, row, rowCell, outline, etc.) are already effectively immutable — `RelationStyle` has no setters for them. But the algorithm-tuning parameters can be mutated after a `PrimitiveStyle`/`RelationStyle` is cached in `Style.primitiveStyleCache`/`relationStyleCache`. The layout pipeline reads these parameters from cached style objects, so mutations take effect inconsistently — they affect future layouts that happen to re-read the cached style, but not layouts already computed and cached in `layoutCache`.

Note: `Style.setStyleSheets()` already clears all three caches, so the "alternative" remediation of adding `invalidateLayoutCache()` already exists. The real problem is that algorithm-tuning parameters are mutable fields on cached objects.

**Impact**: Silent stale layout calculations. Users adjusting style parameters see inconsistent or no effect.

**Prerequisite**: This issue depends on RDR-009 Phase B completion (immutable result records, bead Kramer-53g). Cannot be implemented until Phase B is done.

**Remediation**: Complete the RDR-009 `LayoutStylesheet` wiring (Phase C). Algorithm-tuning parameters should be read from `LayoutStylesheet` (which is immutable per layout cycle) rather than from mutable fields on cached `PrimitiveStyle`/`RelationStyle` objects. Concretely:
1. Move algorithm-tuning parameter reads in the layout pipeline from `style.getMaxTablePrimitiveWidth()` etc. to `stylesheet.getDouble(path, "max-table-primitive-width", DEFAULT)`.
2. Remove the setters from `PrimitiveStyle`/`RelationStyle` — these become pure CSS-measurement records (insets + fonts only).
3. `DefaultLayoutStylesheet` already supports per-path overrides via `setOverride()` — this replaces the scattered setters.
4. During the transition (before full LayoutStylesheet wiring), parameter reads without explicit overrides fall through to Java-level defaults — this matches current behavior since no callers currently set overrides.

**Known limitation**: `DefaultLayoutStylesheet.primitiveStyle()`/`relationStyle()` currently create throwaway `new Primitive(path.leaf())` / `new Relation(path.leaf())` instances to look up cached styles. This is a design smell from partial Phase C implementation that should be resolved when `AutoLayout` transitions from accepting `Style` to accepting `LayoutStylesheet`.

### C4: Style invalidation bugs — self-invalidation and shared-instance corruption (Critical)

**Location**: `AutoLayout` constructor, `ListChangeListener` on `getStylesheets()`

**Problem**: Two distinct bugs:

**C4a — Self-invalidation**: When `AutoLayout` adds `default.css` in its constructor, the `ListChangeListener` fires immediately, calling `model.setStyleSheets(getStylesheets())`. This clears all caches — including any that might have been populated earlier. The equality guard fixes this: don't call `setStyleSheets` if the new list equals `model.styleSheets()`.

**C4b — Shared-instance corruption**: If two `AutoLayout` instances share a `Style` and have different stylesheet lists (e.g., instance A has `[default.css, custom.css]`, instance B has `[default.css]`), B's constructor triggers `setStyleSheets([default.css])`, which clears all caches including A's work. The lists are NOT equal, so the equality guard does NOT prevent this.

**Impact**: C4a causes unnecessary cache invalidation on construction. C4b causes cross-instance corruption, performance degradation, and potential layout flicker.

**Remediation**:
- C4a: Add equality guard in the listener — `if (!model.styleSheets().equals(new ArrayList<>(getStylesheets()))) { model.setStyleSheets(getStylesheets()); ... }`
- C4b: Enforce that `Style` is instance-private to a single `AutoLayout`. Add an `owner` field on `Style` that asserts single-ownership: the first `AutoLayout` to call `setStyleSheets()` sets itself as owner; subsequent calls from a different instance throw `IllegalStateException`. The `AutoLayout(Relation, Style)` constructor should document this contract. Assertion-based enforcement is the primary remediation — documentation alone leaves the bug in place for future callers.

### S1: No thread-safety enforcement on JAT-bound measurement (Significant)

**Location**: `Style.computePrimitiveStyle()`, `computeRelationStyle()`

**Problem**: Create scene graph objects requiring the JavaFX Application Thread, with no assertion and no documentation. `IdentityHashMap` caches are not concurrent.

**Impact**: Off-JAT callers get undefined behavior — possible NPE, corrupted cache, or JavaFX internal exceptions.

**Remediation**: Add `assert Platform.isFxApplicationThread()` at top of both methods. Add class-level Javadoc on `Style` stating it is single-threaded and JAT-bound.

### S2: Private measurement methods block extensibility (Significant)

**Location**: `Style.computePrimitiveStyle()`, `computeRelationStyle()` — both `private`

**Problem**: Cannot override for headless testing, alternative measurement strategies, or server-side rendering.

**Remediation**: Change both to `protected`.

### S3: 10-occurrence CSS background-color duplication (Significant)

**Location**: 8 component CSS files (`outline-cell.css`, `outline-column.css`, `outline-element.css`, `outline.css`, `span.css`, `nested-cell.css`, `nested-row.css`, `nested-table.css`) plus 2 occurrences in `default.css` (`.primitive` base and `.primitive:hover` selectors) = 10 total uses of the same gradient pattern.

**Problem**: Identical `linear-gradient(...)` background declaration duplicated across 10 locations. The `default.css` `.primitive` selector uses the same gradient but adds font-size, text-fill, border-radius — it is stylistically the same but serves a different component. The `.primitive:hover` selector uses a variation with additional layers.

**Note**: JavaFX CSS looked-up color variables (`-fx-*`) can only replace individual color values, not entire `linear-gradient()` expressions. A looked-up color variable cannot centralize this duplication.

**Remediation**: Add a shared `.kramer-container` selector in `default.css` containing the common background gradient declaration. Add `"kramer-container"` to the style class list in `LayoutCell.initialize()`. Each component CSS file then only needs to declare its unique properties (padding, hover effects, selection states). Remove the duplicated gradient from all 8 component CSS files. The `.primitive` selector in `default.css` should also reference `.kramer-container` for its base gradient (it adds additional properties on top). The `.primitive:hover` variant remains separate since it uses a distinct multi-layer background.

### S4: Hardcoded 800×600 measurement scene is a magic constant (Minor — downgraded)

**Location**: `Style.computePrimitiveStyle()`, `computeRelationStyle()` — `new Scene(root, 800, 600)`

**Problem**: The 800×600 is undocumented magic. However, JavaFX CSS insets (`-fx-padding`, `-fx-border-width`, etc.) are specified in **logical pixels**, not device pixels. The JavaFX rendering pipeline applies HiDPI scaling internally. `Region.getInsets()` returns logical pixel values regardless of display scale. Therefore the original claim that "layout calculations are off by the device pixel ratio" is **incorrect**.

**Actual impact**: Code smell — magic constant with no documented rationale. No confirmed correctness impact.

**Remediation**: Extract `800, 600` to named constants (e.g., `MEASUREMENT_SCENE_WIDTH`, `MEASUREMENT_SCENE_HEIGHT`) with a comment explaining the values are in logical pixels and the scene size does not affect CSS property extraction. Verify via test that scene size does not affect inset extraction.

### S5: Unbounded cache growth with no eviction (Significant)

**Location**: `Style.java` — `primitiveStyleCache`, `relationStyleCache`, `layoutCache` (all `IdentityHashMap`)

**Problem**: All three caches hold strong references to schema node instances. No eviction except `setStyleSheets()`. Long-running apps (e.g., the explorer cycling through many schemas) accumulate entries forever.

**Note**: `WeakHashMap` is the wrong replacement — it uses `.equals()` for key comparison, but these caches require **identity-based** key semantics (keyed on the specific `Primitive`/`Relation` instance). The JDK does not provide a `WeakIdentityHashMap`. A custom implementation adds unnecessary complexity.

**Remediation**: Add an explicit `Style.clearCaches()` public method for lifecycle management. Call it when a schema is discarded (e.g., when `AutoLayout.setRoot()` changes the schema tree). Document the lifecycle requirement: callers must call `clearCaches()` or replace the `Style` instance when done with a schema tree. If cache size becomes a measured problem, consider `WeakReference` values (not keys) with periodic cleanup — but defer until evidence warrants.

---

## Implementation Phases

### Phase 1: Correctness — self-contained fixes (C1, C2, C4)

| Issue | Change | Risk |
|-------|--------|------|
| C2 | Add `final` to `PRIMITIVE_TEXT_CLASS` (and audit similar) | Trivial |
| C4a | Equality guard in `AutoLayout` stylesheet listener | Low |
| C4b | Enforce single-ownership of `Style` via owner assertion | Low |
| C1 | Sanitize in `SchemaPath.cssClass()`, route all CSS class gen through it | Medium — 12 call sites |

**Order rationale**: C2 and C4a/C4b are trivial/low-risk and go first. C1 touches 12 call sites but is mechanically straightforward. C4b should use the assertion approach (not documentation-only) to prevent future sharing violations.

### Phase 2: Safety & Extensibility (S1–S2)

| Issue | Change | Risk |
|-------|--------|------|
| S1 | Add JAT assertions + class Javadoc | Trivial |
| S2 | `private` → `protected` on measurement methods | Trivial |

### Phase 3: Maintainability (S3–S5)

| Issue | Change | Risk |
|-------|--------|------|
| S3 | Add `.kramer-container` to `default.css`, refactor 8+ CSS files | Low — visual |
| S4 | Extract magic constants, add verification test | Trivial |
| S5 | Add `Style.clearCaches()`, document lifecycle | Low |

### Phase 4: LayoutStylesheet wiring (C3) — blocked on RDR-009 Phase B

| Issue | Change | Risk | Blocker |
|-------|--------|------|---------|
| C3 | Wire `LayoutStylesheet` for algorithm params, remove style setters | Medium | RDR-009 Phase B (Kramer-53g) |

**Note**: C3 cannot start until RDR-009 Phase B (immutable result records) is complete. It is separated into its own phase to avoid blocking Phases 1–3, which are all self-contained.

---

## Observations (non-blocking, no issue number)

- Redundant `applyCss()`/`layout()` calls — once on root, then again on each child (idempotent but confusing)
- Static utilities (`add`, `relax`, `snap`, `textWidth`, `toString`) on `Style` have no instance dependency — belong in a utility class
- `auto-layout.css` is empty with no documentation of purpose
- `smoke.css` is entirely commented out — false impression of test coverage
- `NodeStyle` base class adds no polymorphism — candidate for removal
- `LabelStyle.LAYOUT_LABEL` constant appears dead
- `DefaultLayoutStylesheet` exists but `AutoLayout` still takes `Style`, not `LayoutStylesheet` — wiring gap from RDR-009 Phase C

---

## Gate History

### Gate 1 (2026-03-15) — BLOCKED
3 critical, 2 significant findings:
1. C4 remediation (equality guard) didn't fix the shared-instance problem it described → **Fixed**: split into C4a (self-invalidation) and C4b (shared-instance), with distinct remediations
2. C1 bypassed RDR-009's `SchemaPath.cssClass()` canonical sanitization point → **Fixed**: remediation now routes through `SchemaPath.cssClass()` and identifies all 12 call sites
3. C3 conflated CSS-derived insets with algorithm-tuning parameters → **Fixed**: narrowed to algorithm-tuning params only, remediation completes RDR-009 `LayoutStylesheet` wiring
4. S4 HiDPI impact claim was factually incorrect (JavaFX uses logical pixels) → **Fixed**: downgraded to Minor, restated as magic constant code smell
5. S3 file count was 7, actually 8; looked-up color variables can't replace `linear-gradient()` → **Fixed**: count corrected, remediation specifies shared `.kramer-container` class only
6. S5 `WeakHashMap` breaks identity-keyed cache semantics → **Fixed**: remediation specifies explicit `clearCaches()` lifecycle method

### Gate 2 (2026-03-15) — BLOCKED
1 critical, 2 significant findings:
1. C1 sanitization spec incomplete — didn't cover leading-digit, empty-string edge cases → **Fixed**: added 3-part sanitization contract (replace invalid chars, prepend `_` for leading digits, `_unknown` for empty)
2. C3 has hidden prerequisite on RDR-009 Phase B (Kramer-53g) — cannot implement in isolation → **Fixed**: moved C3 to separate Phase 4 with explicit blocker; added DefaultLayoutStylesheet throwaway-instance limitation note
3. S3 `default.css` occurrence count wrong — 2 occurrences, not 1 "9th" → **Fixed**: corrected to 10 total occurrences, clarified `.primitive` vs `.primitive:hover` handling

---

## Success Criteria

1. All CSS class generation routes through `SchemaPath.cssClass()` with sanitization
2. No mutable statics in the style system
3. Algorithm-tuning parameters read from `LayoutStylesheet`, not mutable style fields
4. `Style` is documented as single-owner; self-invalidation guard prevents redundant cache clears
5. JAT violations are caught in debug/test builds via assertions
6. CSS background gradient declared in exactly one file (`default.css` via `.kramer-container`)
7. Measurement scene constants are named and documented
8. `Style.clearCaches()` exists for explicit lifecycle management
9. Tests cover each remediated issue
