---
title: "Java 25 Modernization"
id: RDR-001
type: Technical Debt
status: closed
close_reason: implemented
priority: high
author: Hal Hildebrand
reviewed-by: self
created: 2026-03-08
accepted_date: 2026-03-08
closed_date: 2026-03-08
related_issues:
  - "PR #42 - Java 25 Modernization (merged)"
  - "PR #43 - Code review remediation (open)"
  - "Kramer-6lk - Implementation epic (closed)"
  - "Kramer-gh1 - Remediation epic (open)"
---

# RDR-001: Java 25 Modernization

> Revise during planning; lock at implementation.
> If wrong, abandon code and iterate RDR.

## Problem Statement

The Kramer project targets Java 11 with dependencies dating from 2016–2018. Several Maven repositories (CloudBees, jcenter) are dead, preventing fresh builds of individual modules. Dependencies carry known CVEs and miss years of improvements. The codebase uses no modern Java language features (records, sealed classes, pattern matching, switch expressions) despite compiling successfully on Java 25.

## Context

### Background

Discovered during codebase analysis: the project compiles and passes tests on Java 25 (GraalVM 25.0.1) as-is, but individual module builds fail because the dead CloudBees/jcenter repository URLs block Maven dependency resolution. The codebase analysis also identified bugs (copy-paste inset error, debug println), dead code, and ~50 Java modernization opportunities.

### Technical Environment

- **Runtime**: Java 25 (GraalVM 25.0.1+8 LTS)
- **Build**: Maven 3.9.12
- **Platform**: macOS Darwin 25.3.0
- **UI**: JavaFX (OpenJFX 14.0.1 → targeting 25 LTS)
- **No JPMS**: classpath-only, no module-info.java (confirmed — not adding)

## Research Findings

### Investigation

Full codebase analysis performed across all 4 modules (75 Java source files). Dependency trees analyzed. Build tested on Java 25. API usage patterns catalogued for all external dependencies.

#### Dependency Source Verification

| Dependency | Source Searched? | Key Findings |
| --- | --- | --- |
| Jackson 2.9.8 | Yes | Only uses JsonNode/ArrayNode/ObjectNode tree model + annotations. No ObjectMapper config, no deprecated APIs used. Upgrade to 2.18+ safe. Latest 2.x is 2.21.1. Jackson 3.0 GA also available but requires groupId change — not pursuing. |
| OpenJFX 14.0.1 | Yes | Standard controls/graphics/base usage. JavaFX 25 is LTS, requires JDK 23+. API-compatible upgrade. |
| graphql-java 2.3.0 | Yes | Uses `new Parser().parseDocument()`, `Field`, `OperationDefinition`, `Selection`, `InlineFragment`, `FragmentSpread`. Modern versions use `Parser.parse()` static method. `Definition` became `Definition<?>`. Core AST classes (`Field.getSelectionSet()`, `OperationDefinition.getOperation()`) remain stable. |
| Jersey 2.22.1 | Yes | Uses `javax.ws.rs.client.{ClientBuilder, WebTarget, Entity, Invocation.Builder}`, `javax.ws.rs.core.MediaType`, `javax.ws.rs.BadRequestException`. Jersey 3.1.x uses `jakarta.ws.rs.*` namespace — mechanical find-replace. |
| ReactFX 2.0-M5 | Yes | Used exclusively in `flowless` package for `Val`, `Var`, `MemoizationList`, `Subscription`. Library unmaintained since 2016 but functional. No newer alternative without rewriting flowless. |
| WellBehavedFX 0.3.1 | Yes | Used for `InputMap` keyboard event handling. Unmaintained. |
| ControlsFX 8.40.12 | Yes | Used only in explorer module. Version 11.2.x is Java 11+ compatible. |
| JUnit 4.11 | Yes | Standard `@Test`, `assertEquals` usage. Straightforward JUnit 5 migration. |
| TestFX 4.0.1-alpha | Yes | Used in test smoke classes. Version 4.0.18+ supports modern JavaFX. |
| Mockito 1.9.5 | Yes | Minimal usage. Modern Mockito 5.x is compatible. |

### Key Discoveries

- **Verified** — Project compiles and all tests pass on Java 25 without code changes
- **Verified** — CloudBees and jcenter repository URLs are dead; individual module builds fail with `Blocked mirror for repositories`
- **Documented** — `RelationStyle.getOutlineCellHorizontalInset()` returns `outlineCell.getTop() + outlineCell.getBottom()` — a copy-paste bug producing vertical insets where horizontal is expected
- **Documented** — `FocusController.setCurrent(FocusTraversalNode<?>)` (the overloaded method, not the no-arg override) contains `System.out.println` debug statement
- **Documented** — `FlyAwayScrollBar.java` is unreferenced dead code
- **Documented** — `PrimitiveLayout.variableLength` field is written but never read (`@SuppressWarnings("unused")`)
- **Documented** — `bootstrap3.css` is in main resources but only used in tests
- **Documented** — 10 instanceof-then-cast patterns convertible to pattern matching
- **Documented** — 2 sealed class hierarchy candidates (SchemaNode, SchemaNodeLayout)
- **Documented** — 2 record candidates (QueryRequest, Fold). Note: QueryException extends Exception and cannot be a record.
- **Documented** — Fold inner class requires extraction to static nested record with explicit layout field
- **Documented** — 10+ switch statements convertible to switch expressions
- **Documented** — 2 StringBuffer → StringBuilder fixes
- **Documented** — 2 missing diamond operators

### Critical Assumptions

- [x] Jackson 2.18.x tree model API (JsonNode, ArrayNode, ObjectNode, JsonNodeFactory) is backward-compatible with 2.9.8 — **Status**: Verified — **Method**: Docs Only (Jackson maintains 2.x backward compatibility for core tree model)
- [x] graphql-java modern versions still expose `Field`, `OperationDefinition`, `SelectionSet`, `Selection` in `graphql.language.*` — **Status**: Verified — **Method**: Source Search (GitHub master branch confirms)
- [x] `Parser.parseDocument(String)` replacement is `Parser.parse(String)` returning `Document` — **Status**: Verified — **Method**: Source Search
- [x] Jersey 3.1.x is a namespace-only migration (`javax.ws.rs` → `jakarta.ws.rs`) with no API shape changes for client usage — **Status**: Verified — **Method**: Docs Only
- [x] ReactFX 2.0-M5 continues to work on Java 25 — **Status**: Verified — **Method**: Spike (project compiles and tests pass)
- [x] `jackson-jaxrs-json-provider` has a Jakarta equivalent for Jersey 3.1.x — **Status**: Verified — **Method**: Source Search (artifact `com.fasterxml.jackson.jakarta.rs:jackson-jakarta-rs-json-provider` exists on Maven Central since Jackson 2.13, latest 2.21.1)
- [x] graphql-java 25.0 `Definition` is now `Definition<?>` requiring wildcard or raw type in iteration — **Status**: Verified — **Method**: Source Search (GitHub master confirms generic type parameter; `new Parser().parseDocument()` still compiles but `Parser.parse()` is idiomatic)

## Proposed Solution

### Approach

Full modernization in 5 phases, ordered by dependency (each phase must compile before the next begins):

1. **POM modernization** — fix repositories, update plugin versions, set `<release>25</release>`
2. **Dependency updates** — bump all dependency versions, migrate javax→jakarta
3. **Bug fixes and dead code removal** — fix known bugs, remove unused code
4. **graphql-java API migration** — update Parser API calls in kramer-ql
5. **Java language modernization** — sealed classes, records, pattern matching instanceof, switch expressions, var, StringBuilder

### Technical Design

**Phase 1 — POM changes (root pom.xml)**:
- Remove CloudBees and jcenter `<repository>` entries
- Update `<release>` from 11 to 25
- Remove `asm` dependency from compiler plugin (not needed for Java 25)
- Update plugin versions: maven-compiler-plugin → 3.14.0, maven-shade-plugin → 3.6.2, maven-source-plugin → 3.4.0, add maven-surefire-plugin 3.5.5
- Update javafx-maven-plugin → 0.0.8 in toy-app

**Phase 2 — Dependency version updates**:
```text
jackson.version:       2.9.8   → 2.21.1
openjfx:              14.0.1   → 25.0.1
graphql-java:          2.3.0   → 25.0
jersey:               2.22.1   → 3.1.11
controlsfx:          8.40.12   → 11.2.3
logback:               1.1.2   → 1.5.32
junit:                  4.11   → junit-jupiter 5.14.2
testfx:          4.0.1-alpha   → 4.0.18
mockito:               1.9.5   → 5.22.0
jakarta.xml.bind-api:  2.3.3   → 4.0.5
reactfx:             2.0-M5   → 2.0-M5 (unchanged — unmaintained, still works)
wellbehavedfx:         0.3.1   → 0.3.3
```

Jersey migration requires:
- `javax.ws.rs.*` → `jakarta.ws.rs.*` in imports (6 files)
- `jackson-jaxrs-json-provider` → `jackson-jakarta-rs-json-provider` (groupId: `com.fasterxml.jackson.jakarta.rs`)
- `jersey-media-json-jackson` same artifactId, new version (3.1.11 pulls in jakarta variant of Jackson provider automatically)

JUnit 4 → 5 migration requires:
- `org.junit.Test` → `org.junit.jupiter.api.Test`
- `org.junit.Assert.*` → `org.junit.jupiter.api.Assertions.*`
- `assertEquals(message, expected, actual)` → `assertEquals(expected, actual, message)` (argument order change)
- `@Before`/`@After` → `@BeforeEach`/`@AfterEach`
- `@Ignore` → `@Disabled`
- TestFX JUnit 5 adapter (`testfx-junit5` artifact)
- Surefire 3.5.5 auto-detects JUnit 5 — no provider config needed

**Phase 3 — Bug fixes**:
- Fix `RelationStyle.getOutlineCellHorizontalInset()`: change `getTop() + getBottom()` → `getLeft() + getRight()`
- Remove `System.out.println` from `FocusController.setCurrent(FocusTraversalNode<?>)` (the overloaded method at ~line 141, not the no-arg override)
- Delete `FlyAwayScrollBar.java`
- Remove `variableLength` field and `@SuppressWarnings("unused")` from `PrimitiveLayout`
- Move `bootstrap3.css` from `src/main/resources` to `src/test/resources`

**Phase 4 — graphql-java API migration** (2.3.0 → 25.0):
- `new Parser().parseDocument(query)` → `Parser.parse(query)` (static method)
- Handle `Definition<?>` and `Selection<?>` generic type parameters (both became generic in modern versions; raw types used in 4 for-each loops at lines 88, 110, 143, 175)
- `Field.getSelectionSet()`, `OperationDefinition.getOperation()`, `Operation.QUERY` — all stable, no changes needed
- Note: graphql-java 22+ has stricter `parseValue` coercion, but Kramer only uses the parser AST, not execution — no impact

**Phase 5 — Java language modernization**:
- `SchemaNode` → `sealed abstract class SchemaNode permits Primitive, Relation`
- `SchemaNodeLayout` → `sealed abstract class SchemaNodeLayout permits PrimitiveLayout, RelationLayout`
- `QueryRequest` inner class → record (verify Jackson record serialization compatibility for POST body)
- `Fold` inner class → static nested record with explicit `SchemaNodeLayout layout` field (currently references enclosing instance via `SchemaNodeLayout.this`; update all construction sites in `fold()` methods)
- 10 instanceof casts → pattern matching instanceof
- 10+ switch statements → switch expressions
- 2 StringBuffer → StringBuilder
- 2 missing diamond operators
- `var` for local variables with redundant type (conservative — only where type is obvious from RHS)

### Existing Infrastructure Audit

| Proposed Component | Existing Module | Decision |
| --- | --- | --- |
| Updated POM | pom.xml (root + 4 modules) | Extend: update in-place |
| Jakarta imports | kramer-ql, explorer, toy-app | Extend: mechanical import replacement |
| JUnit 5 tests | kramer/test, kramer-ql/test | Extend: migrate existing tests |

### Decision Rationale

Approach C (full modernization) chosen over minimal version bumps because:
1. The codebase is small (~75 files) — risk of broad changes is low
2. Sealed classes + pattern matching provide real safety benefits for the SchemaNode hierarchy
3. Records eliminate boilerplate in data holders
4. Switch expressions make the mode-dispatch code more readable
5. User explicitly requested the full modernization path

## Alternatives Considered

### Alternative A: Minimal — Version updates only

**Description**: Update dependency versions and fix dead repositories without code changes.

**Pros**:
- Lowest risk of regressions
- Fastest to implement

**Cons**:
- Leaves known bugs in place
- Misses language modernization benefits
- Dead code remains

**Reason for rejection**: User explicitly chose Approach C.

### Alternative B: Version update + bug fixes only

**Description**: Everything in A plus fixing bugs and removing dead code, without language modernization.

**Pros**:
- Fixes real issues
- Lower risk than full modernization

**Cons**:
- Misses language improvement opportunities
- Leaves verbose pre-Java-16 patterns

**Reason for rejection**: User explicitly chose Approach C.

### Briefly Rejected

- **Migrate to Gradle**: Out of scope — build system works fine, only dead repos need fixing
- **Add JPMS module-info.java**: User confirmed not needed at this time
- **Replace ReactFX/WellBehavedFX**: Would require rewriting the flowless package — disproportionate effort for libraries that still function

## Trade-offs

### Consequences

- **Positive**: Modern Java idioms improve readability and type safety
- **Positive**: Sealed classes make SchemaNode hierarchy exhaustive in switch expressions
- **Positive**: Dead repositories removed — builds work from clean state
- **Positive**: Known bugs fixed
- **Negative**: ReactFX and WellBehavedFX remain on old/unmaintained versions (functional but no upstream fixes)
- **Negative**: Java 25 minimum requirement eliminates Java 11–24 compatibility

### Risks and Mitigations

- **Risk**: graphql-java API changes beyond Parser.parse() that we haven't identified
  **Mitigation**: Phase 4 is isolated; compile errors will surface immediately; TestGraphQlUtil covers the parse path

- **Risk**: Jackson 2.18 behavior change in tree model edge cases
  **Mitigation**: Kramer uses only basic JsonNode/ArrayNode/ObjectNode operations; Smoke tests cover data flow

- **Risk**: Jersey 3.1 client behavior differences beyond namespace change
  **Mitigation**: The JAX-RS client API (`ClientBuilder`, `WebTarget`, `Entity`) is stable; explorer connects to live endpoints for validation

- **Risk**: `netscape.javascript.JSObject` bridge in explorer's `AutoLayoutController` may behave differently under OpenJFX 25 WebKit update
  **Mitigation**: Manual validation — launch explorer module and confirm GraphiQL panel interactions function. This won't surface in `mvn clean install`.

### Failure Modes

- **Build failure on individual modules**: If any dependency is unavailable on Maven Central, `mvn -pl <module> install` will fail. Recovery: check dependency coordinates, fall back to reactor build.
- **JavaFX runtime failure**: If OpenJFX 25 has breaking CSS or control changes, the auto-layout may render incorrectly. Diagnosis: run explorer, visually compare layout. Recovery: pin specific JavaFX version.
- **graphql-java parse failure**: If AST classes changed shape, `GraphQlUtil.buildSchema()` will throw at parse time. Diagnosis: `TestGraphQlUtil` will fail. Recovery: adapt to new AST API.

## Implementation Plan

### Prerequisites

- [x] All Critical Assumptions verified (7/7 verified)
- [x] Java 25 and Maven 3.9+ installed
- [x] Current build passes on Java 25 (`mvn clean install` succeeds)

### Minimum Viable Validation

Full reactor build passes: `mvn clean install` with all tests green on Java 25 after all 5 phases complete.

### Phase 1: POM Modernization

#### Step 1: Remove dead repositories
Remove CloudBees and jcenter `<repository>` blocks from root pom.xml.

#### Step 2: Update compiler target
Change `<release>11</release>` to `<release>25</release>`. Remove `asm` dependency from compiler plugin config.

#### Step 3: Update plugin versions
maven-compiler-plugin → 3.14.0, maven-shade-plugin → 3.6.2, maven-source-plugin → 3.4.0, add surefire 3.5.5, javafx-maven-plugin → 0.0.8.

### Phase 2: Dependency Updates

#### Step 1: Update dependency versions in root POM
Bump all managed dependency versions per the version table above. Remove or update `openjfx-monocle:1.8.0_20` from dependencyManagement (Java 8 artifact, incompatible with Java 25). Remove TestFX entries from dependencyManagement if no module actually uses them (only `TestGraphQlUtil` is a real test, and it doesn't use TestFX).

#### Step 2: Migrate javax → jakarta
Find-replace `javax.ws.rs` → `jakarta.ws.rs` across 6 Java files. Update `jackson-jaxrs-json-provider` → `jackson-jakarta-rs-json-provider`.

#### Step 3: Migrate JUnit 4 → JUnit 5
Update test imports, assertion argument order, TestFX adapter. Update surefire for JUnit 5 discovery.

### Phase 3: Bug Fixes and Dead Code Removal

#### Step 1: Fix RelationStyle inset bug
#### Step 2: Remove debug println from FocusController.setCurrent(FocusTraversalNode<?>)
#### Step 3: Delete FlyAwayScrollBar.java
#### Step 4: Remove unused variableLength field from PrimitiveLayout
#### Step 5: Move bootstrap3.css to test resources

### Phase 4: graphql-java API Migration

#### Step 1: Update Parser API calls in GraphQlUtil.java
#### Step 2: Handle Definition<?> and Selection<?> generics
#### Step 3: Run TestGraphQlUtil to verify

### Phase 5: Java Language Modernization

#### Step 1: Seal SchemaNode and SchemaNodeLayout hierarchies
#### Step 2: Convert QueryRequest and Fold to records
#### Step 3: Apply pattern matching instanceof (10 locations)
#### Step 4: Convert switch statements to switch expressions (10+ locations)
#### Step 5: StringBuffer → StringBuilder, diamond operators, var

### New Dependencies

All dependencies are existing — version updates only. No new third-party dependencies introduced. All remain Apache 2.0 or compatible licenses.

## Test Plan

- **Scenario**: Full reactor build after each phase — **Verify**: `mvn clean install` succeeds with all tests passing
- **Scenario**: Individual module builds after Phase 1 — **Verify**: `mvn -pl kramer-ql test` no longer fails with repository errors
- **Scenario**: GraphQL parsing after Phase 4 — **Verify**: `TestGraphQlUtil` passes with new Parser API
- **Scenario**: Sealed class exhaustiveness — **Verify**: Adding a hypothetical third SchemaNode subclass produces a compiler error

## Validation

### Testing Strategy

1. **Scenario**: Reactor build passes after each phase
   **Expected**: `mvn clean install` exits 0, all tests pass

2. **Scenario**: Smoke test layout renders correctly
   **Expected**: `Smoke` and `PrimArraySmoke` tests pass (JavaFX layout produces expected structure)

3. **Scenario**: GraphQL query parsing produces correct schema tree
   **Expected**: `TestGraphQlUtil` tests pass with identical behavior

4. **Scenario**: SmokeTable test produces correct table layout from GraphQL
   **Expected**: `SmokeTable` test passes

### Performance Expectations

No performance impact expected. Dependency updates are version bumps; language modernization changes syntax, not algorithms.

## Finalization Gate

### Contradiction Check

Checked: research findings vs. proposed solution, dependency versions vs. API usage, record candidate eligibility vs. Java language rules, generic type changes vs. Phase 4 scope. Gate critique found `QueryException` cannot be a record (corrected) and `Selection<?>` was missing from Phase 4 (corrected). No remaining contradictions.

### Assumption Verification

All 7 critical assumptions verified via source search and documentation.

#### API Verification

| API Call | Library | Verification |
| --- | --- | --- |
| `Parser.parse(String)` | graphql-java 22.x | Source Search (GitHub master) |
| `Field.getSelectionSet()` | graphql-java 22.x | Source Search (GitHub master) |
| `OperationDefinition.getOperation()` | graphql-java 22.x | Source Search (GitHub master) |
| `jakarta.ws.rs.client.ClientBuilder` | Jersey 3.1.x | Docs Only |
| `JsonNodeFactory.instance.arrayNode()` | Jackson 2.18.x | Docs Only |

### Scope Verification

Minimum Viable Validation (`mvn clean install` passing on Java 25 after all phases) is fully in scope and will be executed at every phase boundary.

### Cross-Cutting Concerns

- **Versioning**: Project stays at 0.0.1-SNAPSHOT
- **Build tool compatibility**: Maven 3.9+ required (already installed)
- **Licensing**: All dependencies remain Apache 2.0 or compatible
- **Deployment model**: N/A — library project
- **IDE compatibility**: IntelliJ IDEA project files in .idea/ — will work with Java 25 SDK
- **Incremental adoption**: Each phase compiles independently; can stop after any phase
- **Secret/credential lifecycle**: N/A
- **Memory management**: N/A

### Proportionality

Document is appropriately sized for a multi-phase modernization touching all 4 modules and ~75 source files.

## References

- [Automatic Layout of Structured Hierarchical Reports](http://people.csail.mit.edu/ebakke/research/reportlayout_infovis2013.pdf) — the paper Kramer implements
- [Jackson Release Notes](https://github.com/FasterXML/jackson/wiki/Jackson-Releases) — 2.9 → 2.18 migration notes
- [graphql-java Upgrade Notes](https://www.graphql-java.com/documentation/upgrade-notes/) — Parser API changes
- [graphql-java Parser.java source](https://github.com/graphql-java/graphql-java/blob/master/src/main/java/graphql/parser/Parser.java) — confirms `parse()` static method
- [Jersey 3.1 Migration Guide](https://eclipse-ee4j.github.io/jersey/) — javax → jakarta namespace
- [OpenJFX 25 Release Notes](https://gluonhq.com/products/javafx/openjfx-25-release-notes/) — LTS, requires JDK 23+
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/) — migration from JUnit 4

## Revision History

- 2026-03-08: Initial draft from codebase analysis and dependency research
- 2026-03-08: Updated all dependency versions from deep research agent (Jackson 2.21.1, graphql-java 25.0, JUnit 5.14.2, Mockito 5.22.0, Logback 1.5.32, etc.). All 7 critical assumptions now verified.
- 2026-03-08: Gate round 1 — fixed 2 critical issues (QueryException not a valid record; Selection<?> missing from Phase 4), 5 significant issues (Fold extraction, openjfx-monocle, TestFX scope, FocusController method name, JSObject risk).
