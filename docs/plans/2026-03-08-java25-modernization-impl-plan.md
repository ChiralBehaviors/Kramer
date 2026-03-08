# RDR-001: Java 25 Modernization -- Implementation Plan

**Epic**: Kramer-6lk
**RDR**: RDR-001 (accepted 2026-03-08)
**Branch**: `feature/Kramer-6lk-java25-modernization`
**Created**: 2026-03-08

## Executive Summary

Modernize the Kramer project from Java 11 / legacy dependencies to Java 25 with
current dependency versions and modern language features. The work is organized
into 5 phases across 10 beads, each with a compilation gate (`mvn clean install`
must pass). The project currently compiles and tests pass on Java 25 as-is, so
this is an incremental improvement effort, not a rescue.

Total scope: ~75 Java source files across 4 Maven modules (kramer, kramer-ql,
explorer, toy-app). Estimated effort: 2-3 focused implementation sessions.

## Dependency Graph

```
Phase 1: POM Modernization (Kramer-8lr)
    |
    v
Phase 2a: Dependency Versions (Kramer-8pw)
    |
    +---> Phase 2b: javax -> jakarta (Kramer-zrf)
    |
    +---> Phase 2c: JUnit 4 -> 5 (Kramer-4ij)
    |
    v  [compilation gate: all of Phase 2 complete]
Phase 3: Bug Fixes & Dead Code (Kramer-6o2)
    |
    v
Phase 4: graphql-java API Migration (Kramer-dyk)
    |
    v
Phase 5a: Sealed Classes & Records (Kramer-mcx)
    |
    +---> Phase 5b: Pattern Matching & Switch Expressions (Kramer-i0c)
    |
    +---> Phase 5c: Misc Modernization (Kramer-1gs)
```

**Critical notes on Phase 2 sub-steps**: Steps 2a, 2b, and 2c are NOT
independently compilable. Bumping Jersey to 3.1.11 (in 2a) breaks compilation
until javax->jakarta imports are fixed (2b). Replacing JUnit 4.11 with JUnit
Jupiter 5.14.2 (in 2a) breaks compilation until test imports are migrated (2c).
The compilation gate is at the END of Phase 2, after all three sub-steps.

## Critical Path

```
Phase 1 -> Phase 2 (2a+2b+2c atomic) -> Phase 3 -> Phase 4 -> Phase 5a -> Phase 5b
                                                                        -> Phase 5c
```

Phases 5b and 5c can execute in parallel after Phase 5a completes.

## Phase Details

---

### Phase 1: POM Modernization

**Bead**: Kramer-8lr (P1)
**Depends on**: None
**Modules touched**: root pom.xml, explorer/pom.xml, toy-app/pom.xml
**Estimated changes**: 3 files, ~40 lines

#### Changes

**root pom.xml:**
1. Remove 3 `<repository>` blocks:
   - `chiralbehaviors-snapshots` (http://repository-chiralbehaviors.forge.cloudbees.com/snapshot/)
   - `chiralbehaviors-release` (http://repository-chiralbehaviors.forge.cloudbees.com/release/)
   - `jcenter` (https://jcenter.bintray.com/)
2. Change `<release>11</release>` to `<release>25</release>` (line 189)
3. Remove `<dependencies>` block from compiler plugin containing ASM 6.2 (lines 190-196)
4. Update `<version>3.7.0</version>` to `<version>3.14.0</version>` for maven-compiler-plugin in pluginManagement (line 224)
5. Update `<version>2.2.1</version>` to `<version>3.4.0</version>` for maven-source-plugin in `<build><plugins>` (NOT pluginManagement -- line 202)
6. Add maven-surefire-plugin 3.5.5 to pluginManagement:
   ```xml
   <plugin>
       <groupId>org.apache.maven.plugins</groupId>
       <artifactId>maven-surefire-plugin</artifactId>
       <version>3.5.5</version>
   </plugin>
   ```

**explorer/pom.xml:**
7. Update maven-shade-plugin `<version>2.4.3</version>` to `<version>3.6.2</version>` (line 51)

**toy-app/pom.xml:**
8. Update javafx-maven-plugin `<version>0.0.5</version>` to `<version>0.0.8</version>` (line 43)
9. Update maven-shade-plugin `<version>2.4.3</version>` to `<version>3.6.2</version>` (line 50)

#### Acceptance Criteria

- [ ] All 3 dead repository blocks removed from root pom.xml
- [ ] `<release>25</release>` set in compiler plugin configuration
- [ ] ASM dependency removed from compiler plugin
- [ ] maven-compiler-plugin 3.14.0, maven-source-plugin 3.4.0, maven-surefire-plugin 3.5.5
- [ ] maven-shade-plugin 3.6.2 in explorer and toy-app
- [ ] javafx-maven-plugin 0.0.8 in toy-app
- [ ] `mvn clean install` passes
- [ ] `mvn -pl kramer-ql test` succeeds (verifies individual module builds work without dead repos)

#### Test Strategy

```bash
mvn clean install                    # Full reactor build
mvn -pl kramer-ql test              # Individual module build (was broken by dead repos)
mvn -pl kramer clean compile        # Individual module compile
```

---

### Phase 2a: Update Dependency Versions in Root POM

**Bead**: Kramer-8pw (P1)
**Depends on**: Kramer-8lr (Phase 1)
**Modules touched**: root pom.xml, kramer/pom.xml, kramer-ql/pom.xml
**Estimated changes**: 3 files, ~50 lines
**NOTE**: Project will NOT compile after this step alone. Phase 2b and 2c must follow.

#### Changes

**root pom.xml `<properties>`:**
1. `jackson.version`: `2.9.8` -> `2.21.1`
2. Remove `testfx.version` property (no module uses TestFX)

**root pom.xml `<dependencyManagement>`:**
3. `wellbehavedfx`: `0.3.1` -> `0.3.3`
4. Remove `openjfx-monocle` entry (Java 8 artifact, incompatible with Java 25)
5. Remove `testfx-core` and `testfx-junit` entries (no module depends on TestFX)
6. `graphql-java`: `2.3.0` -> `25.0`
7. `controlsfx`: `8.40.12` -> `11.2.3`
8. `logback-classic`: `1.1.2` -> `1.5.32`
9. `jersey-client`: `2.22.1` -> `3.1.11`
10. `jersey-media-json-jackson`: `2.22.1` -> `3.1.11`
11. `mockito-core`: `1.9.5` -> `5.22.0`
12. `javafx-controls`: `14.0.1` -> `25.0.1`
13. `javafx-fxml`: `14.0.1` -> `25.0.1`
14. `javafx-web`: `14.0.1` -> `25.0.1`
15. `jakarta.xml.bind-api`: `2.3.3` -> `4.0.5`
16. Replace JUnit 4 entry:
    ```xml
    <!-- REMOVE -->
    <dependency>
        <groupId>junit</groupId>
        <artifactId>junit</artifactId>
        <version>4.11</version>
        <scope>test</scope>
    </dependency>
    <!-- ADD -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.14.2</version>
        <scope>test</scope>
    </dependency>
    ```
17. Replace `jackson-jaxrs-json-provider`:
    ```xml
    <!-- REMOVE -->
    <dependency>
        <groupId>com.fasterxml.jackson.jaxrs</groupId>
        <artifactId>jackson-jaxrs-json-provider</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    <!-- ADD -->
    <dependency>
        <groupId>com.fasterxml.jackson.jakarta.rs</groupId>
        <artifactId>jackson-jakarta-rs-json-provider</artifactId>
        <version>${jackson.version}</version>
    </dependency>
    ```
18. **NEW DEPENDENCY** -- Add `jersey-hk2` for Jersey 3.x injection support:
    ```xml
    <dependency>
        <groupId>org.glassfish.jersey.inject</groupId>
        <artifactId>jersey-hk2</artifactId>
        <version>3.1.11</version>
    </dependency>
    ```
    **Note**: Jersey 3.x requires an explicit InjectionManager provider. Without
    this, Jersey client calls throw `IllegalStateException: InjectionManagerFactory
    not found`. This is a new dependency not in the original RDR. Verify whether
    `jersey-media-json-jackson` 3.1.11 pulls it transitively; if so, skip.

**kramer/pom.xml:**
19. Replace `junit` dependency with `junit-jupiter`:
    ```xml
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    ```

**kramer-ql/pom.xml:**
20. Replace `jackson-jaxrs-json-provider`:
    ```xml
    <dependency>
        <groupId>com.fasterxml.jackson.jakarta.rs</groupId>
        <artifactId>jackson-jakarta-rs-json-provider</artifactId>
    </dependency>
    ```
21. Replace `junit` dependency with `junit-jupiter`:
    ```xml
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    ```
22. Add `jersey-hk2` dependency (if not transitively provided):
    ```xml
    <dependency>
        <groupId>org.glassfish.jersey.inject</groupId>
        <artifactId>jersey-hk2</artifactId>
    </dependency>
    ```

#### Acceptance Criteria

- [ ] All dependency versions updated per the table above
- [ ] openjfx-monocle removed from dependencyManagement
- [ ] JUnit 4 -> JUnit Jupiter in dependencyManagement and module POMs
- [ ] jackson-jaxrs-json-provider -> jackson-jakarta-rs-json-provider
- [ ] jersey-hk2 added if needed (verify transitivity first)
- [ ] **DO NOT run `mvn compile` here** -- compilation will fail until Phase 2b and 2c complete

---

### Phase 2b: Migrate javax.ws.rs to jakarta.ws.rs

**Bead**: Kramer-zrf (P1)
**Depends on**: Kramer-8pw (Phase 2a)
**Modules touched**: kramer-ql, explorer, toy-app
**Estimated changes**: 4 Java files, ~12 import lines

#### Changes

**Files requiring import migration** (4 files, not 6 as RDR states -- the RDR
counted import lines, not files):

1. **kramer-ql/src/main/java/com/chiralbehaviors/layout/graphql/GraphQlUtil.java**:
   ```
   javax.ws.rs.BadRequestException      -> jakarta.ws.rs.BadRequestException
   javax.ws.rs.client.Entity            -> jakarta.ws.rs.client.Entity
   javax.ws.rs.client.Invocation.Builder -> jakarta.ws.rs.client.Invocation.Builder
   javax.ws.rs.client.WebTarget         -> jakarta.ws.rs.client.WebTarget
   javax.ws.rs.core.MediaType           -> jakarta.ws.rs.core.MediaType
   ```

2. **explorer/src/main/java/com/chiralbehaviors/layout/explorer/AutoLayoutController.java**:
   ```
   javax.ws.rs.client.ClientBuilder -> jakarta.ws.rs.client.ClientBuilder
   javax.ws.rs.client.WebTarget     -> jakarta.ws.rs.client.WebTarget
   ```

3. **toy-app/src/main/java/com/chiralbehaviors/layout/toy/SinglePageApp.java**:
   ```
   javax.ws.rs.client.ClientBuilder -> jakarta.ws.rs.client.ClientBuilder
   javax.ws.rs.client.WebTarget     -> jakarta.ws.rs.client.WebTarget
   ```

4. **toy-app/src/main/java/com/chiralbehaviors/layout/toy/PageContext.java**:
   ```
   javax.ws.rs.client.WebTarget -> jakarta.ws.rs.client.WebTarget
   ```

#### Acceptance Criteria

- [ ] Zero occurrences of `javax.ws.rs` in any Java source file (verify: `grep -r "javax.ws.rs" --include="*.java"` returns empty)
- [ ] All 4 files compile with jakarta.ws.rs imports
- [ ] No compilation expected yet alone (Phase 2c also required)

---

### Phase 2c: Migrate JUnit 4 to JUnit 5

**Bead**: Kramer-4ij (P1)
**Depends on**: Kramer-8pw (Phase 2a)
**Modules touched**: kramer-ql/src/test
**Estimated changes**: 1 test file

#### Changes

**kramer-ql/src/test/java/com/chiralbehaviors/layout/graphql/TestGraphQlUtil.java:**

1. Replace imports:
   ```java
   // REMOVE
   import static org.junit.Assert.assertEquals;
   import static org.junit.Assert.assertNotNull;
   import org.junit.Test;

   // ADD
   import static org.junit.jupiter.api.Assertions.assertEquals;
   import static org.junit.jupiter.api.Assertions.assertNotNull;
   import org.junit.jupiter.api.Test;
   ```

2. Note: `assertEquals(expected, actual)` argument order is the same in JUnit 5.
   No `assertEquals(message, expected, actual)` calls in this file, so no
   argument reordering needed.

3. Note: No `@Before`/`@After`, `@Ignore`, or `@Rule` usage in this file.

#### Acceptance Criteria

- [ ] TestGraphQlUtil uses JUnit Jupiter imports
- [ ] Zero occurrences of `org.junit.Test` or `org.junit.Assert` in test sources
- [ ] **COMPILATION GATE**: `mvn clean install` passes (all of Phase 2 complete)
- [ ] `mvn -pl kramer-ql test` passes -- TestGraphQlUtil.testFragments() succeeds

#### Phase 2 Test Strategy

```bash
# After ALL of 2a+2b+2c are complete:
mvn clean install                    # Full reactor build -- THE compilation gate
mvn -pl kramer-ql test              # Verify TestGraphQlUtil passes
mvn dependency:tree -pl kramer-ql   # Verify jersey-hk2 is in dependency tree
```

---

### Phase 3: Bug Fixes and Dead Code Removal

**Bead**: Kramer-6o2 (P2)
**Depends on**: Kramer-zrf (Phase 2b), Kramer-4ij (Phase 2c)
**Modules touched**: kramer
**Estimated changes**: 4 files modified, 1 file deleted, 1 file moved

#### Changes

1. **Fix RelationStyle.getOutlineCellHorizontalInset() bug** (line 91):
   ```java
   // BEFORE (bug: returns vertical inset, not horizontal)
   public double getOutlineCellHorizontalInset() {
       return outlineCell.getTop() + outlineCell.getBottom();
   }

   // AFTER (correct: returns horizontal inset)
   public double getOutlineCellHorizontalInset() {
       return outlineCell.getLeft() + outlineCell.getRight();
   }
   ```
   File: `kramer/src/main/java/com/chiralbehaviors/layout/style/RelationStyle.java`

2. **Remove debug System.out.println from FocusController** (lines 142-145):
   ```java
   // BEFORE
   public void setCurrent(FocusTraversalNode<?> focused) {
       System.out.println(String.format("Setting current: %s",
                                        focused.getContainer()
                                               .getClass()
                                               .getSimpleName()));
       current = focused;
   }

   // AFTER
   public void setCurrent(FocusTraversalNode<?> focused) {
       current = focused;
   }
   ```
   File: `kramer/src/main/java/com/chiralbehaviors/layout/cell/control/FocusController.java`

3. **Delete FlyAwayScrollBar.java** (dead code, unreferenced):
   ```
   DELETE: kramer/src/main/java/com/chiralbehaviors/layout/flowless/FlyAwayScrollBar.java
   ```

4. **Remove unused variableLength field from PrimitiveLayout** (lines 49-50):
   ```java
   // REMOVE these two lines:
   @SuppressWarnings("unused")
   private boolean                variableLength;
   ```
   Also remove the assignment at line 192:
   ```java
   // REMOVE:
   if (maxWidth > averageWidth) {
       variableLength = true;
   }
   ```
   File: `kramer/src/main/java/com/chiralbehaviors/layout/PrimitiveLayout.java`

5. **Move bootstrap3.css from main to test resources**:
   ```
   MOVE: kramer/src/main/resources/bootstrap3.css
     TO: kramer/src/test/resources/bootstrap3.css
   ```
   Verify: no main-scope code references bootstrap3.css.

#### Acceptance Criteria

- [ ] `getOutlineCellHorizontalInset()` returns `outlineCell.getLeft() + outlineCell.getRight()`
- [ ] No `System.out.println` in FocusController.setCurrent(FocusTraversalNode)
- [ ] FlyAwayScrollBar.java does not exist
- [ ] PrimitiveLayout has no `variableLength` field
- [ ] bootstrap3.css is in `src/test/resources`, not `src/main/resources`
- [ ] `mvn clean install` passes

#### Test Strategy

```bash
mvn clean install
# Manual: Verify FlyAwayScrollBar.java is gone
find kramer/src -name "FlyAwayScrollBar.java"    # should return nothing
# Manual: Verify bootstrap3.css location
find kramer/src -name "bootstrap3.css"            # should show test/resources only
```

---

### Phase 4: graphql-java API Migration

**Bead**: Kramer-dyk (P2)
**Depends on**: Kramer-6o2 (Phase 3)
**Modules touched**: kramer-ql
**Estimated changes**: 1 file, ~10 lines

#### Changes

**kramer-ql/src/main/java/com/chiralbehaviors/layout/graphql/GraphQlUtil.java:**

1. **Update Parser API** (2 locations):
   ```java
   // Line 133: BEFORE
   new Parser().parseDocument(query)
   // AFTER
   Parser.parse(query)

   // Line 169: BEFORE
   new Parser().parseDocument(query)
   // AFTER
   Parser.parse(query)
   ```

2. **Add wildcard to Definition generic type** (line 169):
   ```java
   // BEFORE
   for (Definition definition : new Parser().parseDocument(query)
   // AFTER
   for (Definition<?> definition : Parser.parse(query)
   ```

3. **Add wildcard to Selection generic types** (4 locations):
   ```java
   // Line 88: BEFORE
   for (Selection selection : parentField.getSelectionSet()
   // AFTER
   for (Selection<?> selection : parentField.getSelectionSet()

   // Line 110: BEFORE
   for (Selection selection : fragment.getSelectionSet()
   // AFTER
   for (Selection<?> selection : fragment.getSelectionSet()

   // Line 143: BEFORE
   for (Selection selection : operation.getSelectionSet()
   // AFTER
   for (Selection<?> selection : operation.getSelectionSet()

   // Line 175: BEFORE
   for (Selection selection : operation.getSelectionSet()
   // AFTER
   for (Selection<?> selection : operation.getSelectionSet()
   ```

4. **Fix stream filter on Definition** (lines 136-137):
   ```java
   // BEFORE (line 136-137): uses raw Definition in stream
   .filter(d -> d instanceof OperationDefinition)
   .map(d -> (OperationDefinition) d)
   // AFTER: no change needed, this works with Definition<?>
   ```

#### Acceptance Criteria

- [ ] Zero occurrences of `new Parser()` in GraphQlUtil.java
- [ ] All `Definition` types use `Definition<?>` wildcard
- [ ] All `Selection` types use `Selection<?>` wildcard
- [ ] TestGraphQlUtil.testFragments() passes
- [ ] `mvn clean install` passes
- [ ] `mvn -pl kramer-ql test` passes

#### Test Strategy

```bash
mvn -pl kramer-ql test              # TestGraphQlUtil is the key test
mvn clean install                    # Full reactor
```

---

### Phase 5a: Seal Class Hierarchies and Convert to Records

**Bead**: Kramer-mcx (P3)
**Depends on**: Kramer-dyk (Phase 4)
**Modules touched**: kramer, kramer-ql
**Estimated changes**: 6 files, ~30 lines

#### Changes

1. **Seal SchemaNode hierarchy**:
   ```java
   // BEFORE (kramer/src/.../schema/SchemaNode.java, line 32)
   abstract public class SchemaNode {

   // AFTER
   public abstract sealed class SchemaNode permits Primitive, Relation {
   ```
   File: `kramer/src/main/java/com/chiralbehaviors/layout/schema/SchemaNode.java`

   Note: Primitive and Relation are in the same package. They extend SchemaNode
   directly. No other subclasses exist. Both must be marked `non-sealed` or
   `final`. Primitive has no subclasses, so `final`. Relation has QueryRoot as a
   subclass (in kramer-ql), so `non-sealed`.

   ```java
   // Primitive.java (line 23) -- add final
   public final class Primitive extends SchemaNode {

   // Relation.java (line 28) -- add non-sealed
   public non-sealed class Relation extends SchemaNode {
   ```

   **Wait**: QueryRoot extends Relation and is in kramer-ql module
   (`kramer-ql/src/.../graphql/QueryRoot.java`). Since `Relation` is `non-sealed`,
   QueryRoot can still extend it without being listed in `permits`.

2. **Seal SchemaNodeLayout hierarchy**:
   ```java
   // BEFORE (kramer/src/.../SchemaNodeLayout.java, line 39)
   abstract public class SchemaNodeLayout {

   // AFTER
   public abstract sealed class SchemaNodeLayout permits PrimitiveLayout, RelationLayout {
   ```
   File: `kramer/src/main/java/com/chiralbehaviors/layout/SchemaNodeLayout.java`

   ```java
   // PrimitiveLayout.java (line 44) -- add final
   public final class PrimitiveLayout extends SchemaNodeLayout {

   // RelationLayout.java (line 49) -- add final
   public final class RelationLayout extends SchemaNodeLayout {
   ```

   Note: Verify no other classes extend SchemaNodeLayout.

3. **Convert QueryRequest to record**:
   ```java
   // BEFORE (GraphQlUtil.java, lines 66-84)
   static class QueryRequest {
       public String              operationName;
       public String              query;
       public Map<String, Object> variables = Collections.emptyMap();
       public QueryRequest() { }
       public QueryRequest(String query, Map<String, Object> variables) { ... }
       @Override public String toString() { ... }
   }

   // AFTER
   record QueryRequest(String operationName, String query, Map<String, Object> variables) {
       QueryRequest(String query, Map<String, Object> variables) {
           this(null, query, variables);
       }
       QueryRequest() {
           this(null, null, Collections.emptyMap());
       }
   }
   ```

   **CRITICAL**: Verify Jackson serialization/deserialization works with this
   record. Jackson 2.12+ supports records, but the original QueryRequest uses
   public field access, not getters. The record will expose accessor methods
   (`query()`, `variables()`, `operationName()`), which Jackson 2.12+ recognizes.

   Test by writing a small verification:
   ```java
   ObjectMapper mapper = new ObjectMapper();
   String json = mapper.writeValueAsString(new QueryRequest("q", Map.of()));
   QueryRequest deserialized = mapper.readValue(json, QueryRequest.class);
   ```

4. **Convert Fold to static nested record**:
   ```java
   // BEFORE (SchemaNodeLayout.java, lines 41-54)
   public class Fold {
       public final int      averageCardinality;
       public final JsonNode datum;
       Fold(JsonNode datum, int averageCardinality) {
           assert averageCardinality > 0;
           this.datum = datum;
           this.averageCardinality = averageCardinality;
       }
       public SchemaNodeLayout getLayout() {
           return SchemaNodeLayout.this;
       }
   }

   // AFTER
   public record Fold(JsonNode datum, int averageCardinality, SchemaNodeLayout layout) {
       public Fold {
           assert averageCardinality > 0;
       }
   }
   ```

   **Update construction sites** -- every `new Fold(datum, cardinality)` becomes
   `new Fold(datum, cardinality, this)`:

   a. `SchemaNodeLayout.fold(JsonNode)` (line 300):
      ```java
      return new Fold(aggregate, ..., this);
      ```

   b. Verify `RelationLayout.fold(...)` method -- it calls `fold(datum)` which
      goes to the base class. But it also calls `model.layout(fold).fold(...)`
      which returns a Fold from a different layout instance -- this is fine since
      `this` in that context refers to the correct layout.

   c. Check all callers of `fold.getLayout()` and change to `fold.layout()`.

#### Acceptance Criteria

- [ ] SchemaNode is `sealed` with `permits Primitive, Relation`
- [ ] Primitive is `final`, Relation is `non-sealed`
- [ ] SchemaNodeLayout is `sealed` with `permits PrimitiveLayout, RelationLayout`
- [ ] PrimitiveLayout is `final`, RelationLayout is `final`
- [ ] QueryRequest is a record; Jackson serialization verified
- [ ] Fold is a static nested record with explicit `layout` field
- [ ] All Fold construction sites pass `this` as layout argument
- [ ] All `fold.getLayout()` calls changed to `fold.layout()`
- [ ] `mvn clean install` passes

#### Test Strategy

```bash
mvn clean install
# Verify sealed exhaustiveness: temporarily add a hypothetical third SchemaNode subclass
# and confirm compiler error (manual check, then revert)
```

#### Risks

- **QueryRequest Jackson compatibility**: If Jackson deserialization fails, keep
  QueryRequest as a class and document why. Records with default values
  (Collections.emptyMap()) require careful handling.
- **Fold extraction**: Missing a construction site will cause a compile error
  (fail-safe). Search for `new Fold(` across codebase.

---

### Phase 5b: Pattern Matching instanceof and Switch Expressions

**Bead**: Kramer-i0c (P3)
**Depends on**: Kramer-mcx (Phase 5a)
**Modules touched**: kramer, kramer-ql
**Estimated changes**: ~8 files, ~60 lines

#### Changes

**Pattern matching instanceof** (10 locations):

1. **GraphQlUtil.java** -- 6 locations:
   - Line 90: `if (selection instanceof Field)` then `Field field = (Field) selection;`
     -> `if (selection instanceof Field field)`
   - Line 100: `else if (selection instanceof InlineFragment)` then cast on line 101
     -> `else if (selection instanceof InlineFragment inlineFragment)` and update call
   - Line 112: same pattern as line 90
   - Line 136-137: `filter(d -> d instanceof OperationDefinition).map(d -> (OperationDefinition) d)`
     -> `filter(d -> d instanceof OperationDefinition).map(d -> (OperationDefinition) d)` OR use
     `.filter(OperationDefinition.class::isInstance).map(OperationDefinition.class::cast)`
     (note: pattern matching instanceof not applicable in lambda filter/map chains)
   - Line 145: `if (selection instanceof Field)` then `children.add(buildSchema((Field) selection))`
     -> `if (selection instanceof Field field)` then `children.add(buildSchema(field))`
   - Line 171-172: `if (definition instanceof OperationDefinition)` then cast
     -> `if (definition instanceof OperationDefinition operation)`
   - Line 177-178: `if (selection instanceof Field)` then cast
     -> `if (selection instanceof Field field)`

2. **Style.java** -- 1 location (line 99):
   ```java
   // BEFORE
   if (value instanceof ArrayNode) {
   // AFTER
   if (value instanceof ArrayNode arrayNode) {
   ```
   Note: the existing code doesn't cast after this check, it just uses `value`
   as JsonNode. Check if cast is actually used downstream.

3. **Style.java** -- 1 location (line 149):
   ```java
   // BEFORE
   return n instanceof Primitive ? layout((Primitive) n) : layout((Relation) n);
   // AFTER
   return n instanceof Primitive p ? layout(p) : layout((Relation) n);
   ```

4. **MultipleCellSelection.java** -- 1 location (line 742):
   ```java
   // BEFORE
   if (o instanceof Number) {
       Number n = (Number) o;
   // AFTER
   if (o instanceof Number n) {
   ```

5. **SchemaNodeLayout.java** -- 1 location (line 292):
   ```java
   // BEFORE
   if (sub instanceof ArrayNode) {
       aggregate.addAll((ArrayNode) sub);
   // AFTER
   if (sub instanceof ArrayNode subArray) {
       aggregate.addAll(subArray);
   ```

6. **Relation.java** -- 2 locations (lines 46-47, 80-81):
   ```java
   // Line 46-47 BEFORE
   && children.get(children.size() - 1) instanceof Relation
       ? (Relation) children.get(0)
   // AFTER
   && children.get(children.size() - 1) instanceof Relation r
       ? r    // Note: only if children.size() == 1, so get(size-1) == get(0)

   // Line 80-81: similar pattern in setFold
   ```
   **CAUTION**: Line 46 checks `children.get(children.size() - 1)` but then
   casts `children.get(0)`. This only works because the `children.size() == 1`
   check precedes it. With pattern matching, the bound variable would be the
   last element. Since `size() == 1`, last == first, so it's correct.

**Switch expressions** (13+ locations):

7. **FocusController.java** -- 4 switch statements (down, left, right, up methods):
   ```java
   // BEFORE (example: down() method, lines 170-180)
   switch (current.bias) {
       case HORIZONTAL:
           current.traverseNext();
           break;
       case VERTICAL:
           current.selectNext();
           break;
       default:
           break;
   }
   // AFTER
   switch (current.bias) {
       case HORIZONTAL -> current.traverseNext();
       case VERTICAL -> current.selectNext();
       default -> {}
   }
   ```

8. **SchemaNodeLayout.Indent** -- 5 switch statements in indent() methods:
   Convert each to switch expression returning Insets value.

9. **PrimitiveLayout.nestTableColumn** -- 1 switch statement (line 207):
   Convert to switch expression.

10. **MouseHandler** -- 1 switch statement (line 60):
    Convert to switch expression.

11. **VirtualFlow** -- 2 switch statements (lines 563, 575):
    Convert to switch expressions.

#### Acceptance Criteria

- [ ] Zero instanceof-then-cast patterns remain (all use pattern matching)
- [ ] All switch statements in FocusController, SchemaNodeLayout.Indent, PrimitiveLayout, MouseHandler, VirtualFlow converted to switch expressions
- [ ] `mvn clean install` passes

#### Test Strategy

```bash
mvn clean install
```

---

### Phase 5c: Miscellaneous Java Modernization

**Bead**: Kramer-1gs (P3)
**Depends on**: Kramer-mcx (Phase 5a)
**Modules touched**: kramer, kramer-ql
**Estimated changes**: ~5 files, ~15 lines
**Note**: Can execute in parallel with Phase 5b after Phase 5a completes.

#### Changes

1. **StringBuffer -> StringBuilder** (2 locations):
   - `kramer/src/.../schema/Relation.java` line 92:
     ```java
     // BEFORE
     StringBuffer buf = new StringBuffer();
     // AFTER
     StringBuilder buf = new StringBuilder();
     ```
   - `kramer/src/.../utils/Utils.java` lines 762, 777:
     ```java
     // BEFORE
     StringBuffer keyBuffer = null;
     ...
     keyBuffer = new StringBuffer();
     // AFTER
     StringBuilder keyBuffer = null;
     ...
     keyBuffer = new StringBuilder();
     ```

2. **Diamond operators** (2+ locations):
   - `kramer-ql/src/.../graphql/GraphQlUtil.java` line 131:
     ```java
     // BEFORE
     List<Relation> children = new ArrayList<Relation>();
     // AFTER
     List<Relation> children = new ArrayList<>();
     ```
   - `kramer/src/.../cell/control/MultipleCellSelection.java` line 539:
     ```java
     // BEFORE
     final List<Integer> actualSelectedRows = new ArrayList<Integer>();
     // AFTER
     final List<Integer> actualSelectedRows = new ArrayList<>();
     ```

3. **Switch expression in Utils.java** (line 765):
   Convert `switch(state)` over `ParsingState` enum to switch expression.
   File: `kramer/src/main/java/com/chiralbehaviors/layout/utils/Utils.java`

4. **var for local variables** (conservative application):
   Apply `var` only where the type is obvious from the right-hand side:
   ```java
   // Example candidates:
   var aggregate = JsonNodeFactory.instance.arrayNode();     // type obvious: ArrayNode
   var mapper = new ObjectMapper();                          // type obvious: ObjectMapper
   var builder = new StringBuilder();                        // type obvious: StringBuilder
   ```
   Apply conservatively -- skip any case where `var` reduces readability.

#### Acceptance Criteria

- [ ] Zero `StringBuffer` usage (all converted to `StringBuilder`)
- [ ] Zero redundant type arguments in diamond-eligible `new` expressions
- [ ] `var` applied conservatively (only where type is obvious from RHS)
- [ ] `mvn clean install` passes

#### Test Strategy

```bash
mvn clean install
```

---

## Parallelization Opportunities

| Phase | Can Parallelize With | Rationale |
|-------|---------------------|-----------|
| 2b | 2c | Both depend on 2a; independent code changes (different files) |
| 5b | 5c | Both depend on 5a; touch different code patterns |

All other phases are strictly sequential.

## Risk Register

| Risk | Severity | Likelihood | Mitigation |
|------|----------|------------|------------|
| Jersey 3.1 requires jersey-hk2 (new dep) | High | High | Add jersey-hk2 to dependencyManagement; verify with `mvn dependency:tree` |
| Jackson record deserialization fails for QueryRequest | Medium | Low | Jackson 2.12+ supports records; if fails, keep as class |
| graphql-java 25.0 has undocumented API changes | Medium | Low | TestGraphQlUtil covers parse path; compile errors are fail-safe |
| OpenJFX 25 CSS rendering changes | Low | Medium | Visual-only; no automated test possible; manual smoke test |
| ControlsFX 11.2.3 API incompatibility | Medium | Low | Only used in explorer; compile error is fail-safe |
| Fold record extraction misses construction site | Low | Very Low | Compile error is fail-safe; search for `new Fold(` |
| Sealed class breaks unknown subclass | Low | Very Low | Compile error is fail-safe; Relation is non-sealed for QueryRoot |

## Bead Reference Table

| Bead ID | Phase | Title | Priority | Dependencies |
|---------|-------|-------|----------|-------------|
| Kramer-6lk | Epic | RDR-001: Java 25 Modernization | P1 | -- |
| Kramer-8lr | 1 | POM Modernization | P1 | Kramer-6lk |
| Kramer-8pw | 2a | Update dependency versions | P1 | Kramer-8lr |
| Kramer-zrf | 2b | Migrate javax -> jakarta | P1 | Kramer-8pw |
| Kramer-4ij | 2c | Migrate JUnit 4 -> 5 | P1 | Kramer-8pw |
| Kramer-6o2 | 3 | Bug fixes and dead code removal | P2 | Kramer-zrf, Kramer-4ij |
| Kramer-dyk | 4 | graphql-java API migration | P2 | Kramer-6o2 |
| Kramer-mcx | 5a | Sealed classes and records | P3 | Kramer-dyk |
| Kramer-i0c | 5b | Pattern matching and switch expressions | P3 | Kramer-mcx |
| Kramer-1gs | 5c | Misc modernization (StringBuilder, diamond, var) | P3 | Kramer-mcx |

## Implementation Notes

### Branching Strategy

Create a single feature branch for the entire epic:
```bash
git checkout -b feature/Kramer-6lk-java25-modernization
```

Commit after each phase with a descriptive message:
```
Phase 1: POM modernization -- remove dead repos, update plugins, target Java 25

References: Kramer-8lr, RDR-001
```

Create a PR after all phases are complete, or after Phase 2 (major milestone)
and Phase 5 (completion).

### Compilation Gates

Every phase (and Phase 2 as a whole) must pass:
```bash
mvn clean install
```

If a phase fails, do not proceed to the next phase. Debug and fix within the
current phase.

### Agent Execution Guidance

When implementing each phase:
1. Use `mcp__sequential-thinking__sequentialthinking` for complex changes (especially Fold extraction and sealed class decisions)
2. Write tests FIRST where applicable (Phase 5a: verify Jackson record serialization before converting)
3. Run `mvn clean install` after each phase before proceeding
4. SPAWN parallel agents for Phase 5b and 5c (independent after 5a)
5. Update continuation state after each phase milestone

### Bead Dependency Setup

Run these commands to establish the execution order in beads:

```bash
bd dep add Kramer-8pw Kramer-8lr    # Phase 2a blocked by Phase 1
bd dep add Kramer-zrf Kramer-8pw    # Phase 2b blocked by Phase 2a
bd dep add Kramer-4ij Kramer-8pw    # Phase 2c blocked by Phase 2a
bd dep add Kramer-6o2 Kramer-zrf    # Phase 3 blocked by Phase 2b
bd dep add Kramer-6o2 Kramer-4ij    # Phase 3 blocked by Phase 2c
bd dep add Kramer-dyk Kramer-6o2    # Phase 4 blocked by Phase 3
bd dep add Kramer-mcx Kramer-dyk    # Phase 5a blocked by Phase 4
bd dep add Kramer-i0c Kramer-mcx    # Phase 5b blocked by Phase 5a
bd dep add Kramer-1gs Kramer-mcx    # Phase 5c blocked by Phase 5a
```

### Out of Scope

- JPMS module-info.java (confirmed not needed)
- ReactFX/WellBehavedFX replacement (functional, not worth rewrite effort)
- Jackson 3.0 migration (would require groupId changes across project)
- Gradle migration (Maven works fine)

## Audit Trail

**Audit date**: 2026-03-08
**Outcome**: GO
**Auditor**: plan-auditor agent (nx:plan-auditor)

All file paths, line references, and assumptions verified against actual codebase.
No critical issues found. Four non-blocking items addressed in this revision:

1. TestFX decision made explicit: REMOVE (no module depends on it)
2. Utils.java switch statement added to Phase 5c scope
3. maven-source-plugin placement clarified (in `<build><plugins>`, not pluginManagement)
4. Phase 2a explicit "DO NOT compile" language added

### Pre-flight Requirement

Before starting Phase 2a, verify target dependency versions are available on Maven Central:
```bash
mvn dependency:resolve -DincludeArtifactIds=jackson-databind -Dartifact=com.fasterxml.jackson.core:jackson-databind:2.21.1
```
