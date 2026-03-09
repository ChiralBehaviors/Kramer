# Post-Mortem: RDR-001 Java 25 Modernization

**RDR**: RDR-001
**Status**: Closed (implemented)
**Closed**: 2026-03-08
**PRs**: #42 (implementation, merged), #43 (code review remediation, open)

## Implementation Summary

All 5 phases completed successfully. Full reactor build passes on Java 25 (GraalVM 25.0.1).

## Divergences from Implementation Plan

### Version Discrepancies

| Dependency | Plan Version | Actual Version | Reason |
|-----------|-------------|---------------|--------|
| Jackson | 2.21.1 | 2.19.0 | 2.21.1 does not exist; 2.19.0 was the latest available |
| graphql-java | 25.0 | 25.0 | As planned |
| OpenJFX | 25.0.1 | 25.0.1 | As planned |
| JUnit Jupiter | 5.14.2 | 5.14.2 | As planned |
| Jersey | 3.1.11 | 3.1.11 | As planned |

### Unplanned Work

1. **ReactFX flatMap compatibility**: Not in the plan. ReactFX's `Val.flatMap` returns `Val<T>` (not `ObservableValue<T>`), requiring adapter code when binding to JavaFX properties. Discovered during compilation.

2. **TestFX removed entirely**: Plan said to "update TestFX adapter" and "remove if unused." We removed TestFX completely — no module actually depended on it for real tests.

3. **GraphQlUtil interface → final class**: Plan described updating `Parser.parse()` and handling generics. The actual conversion also changed `GraphQlUtil` from an interface (with default methods) to a `final class` with `private` constructor, adding explicit `public static` modifiers. This was a natural consequence of Java 25 best practices but wasn't called out in the plan.

4. **Async JavaFX patterns in explorer and toy-app**: Plan didn't cover the `AutoLayoutController.fetch()` synchronous-on-FX-thread issue or `SinglePageApp`'s synchronous page loading. Code review (PR #43) identified these as P4 issues and implemented async `Task<T>` / `ExecutorService` patterns with `Platform.runLater()` callbacks.

5. **CDN URL modernization**: `ide.html` CDN links updated from HTTP to HTTPS, React 15.0.1 → 15.7.0, fetch polyfill 0.9.0 → 3.6.20. Not in the original plan scope.

### Scope Expansion from Code Review

PR #43 addressed 12 additional findings from comprehensive code review that were beyond RDR-001's original scope:

- **P0**: Null pointer guards in `FocusController` (7 handler methods), `RelationLayout.getAsDouble()` → `.orElse(0.0)`
- **P1**: Integer division bugs in `SchemaNodeLayout` and `PrimitiveLayout`, null guards in `NestedTable`
- **P2**: Resource leak in `Utils.getBits()` (try-with-resources), `AutoLayout` null layout guard, `SchemaView` child accumulation bug
- **P3**: `QueryState.equals()`/`hashCode()` using `Objects.equals()`/`Objects.hash()`, FXML namespace updates
- **P4**: Async FX thread patterns, CDN modernization

### Items Completed As Planned

- Phase 1 (POM): Dead repos removed, compiler target updated, plugin versions bumped
- Phase 2 (Dependencies): javax → jakarta, JUnit 4 → 5, all version updates
- Phase 3 (Bug fixes): RelationStyle inset bug, debug println removal, dead code deletion
- Phase 4 (graphql-java): Parser API migration, generic type handling
- Phase 5 (Language): Sealed classes, records, pattern matching instanceof, switch expressions

## Lessons Learned

1. **Dependency version verification is critical**: The plan listed Jackson 2.21.1, which doesn't exist. Always verify version availability on Maven Central before finalizing the plan.

2. **Code review expands scope usefully**: The 12 additional findings from code review (null safety, integer division, resource leaks) were real bugs that predated the modernization. Including code review as a post-implementation step is valuable.

3. **Async patterns should be scoped during planning**: The FX thread blocking was visible in the original code but wasn't called out as a modernization target. Future plans should explicitly audit threading patterns.

4. **TestFX removal was the right call**: No tests actually used TestFX. Removing it simplified the dependency tree significantly.

## Beads

- **Epic**: Kramer-6lk (implementation), Kramer-gh1 (remediation)
- **Total beads**: 12 remediation beads under Kramer-gh1, all closed
