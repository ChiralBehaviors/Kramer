# Code Style and Conventions

## Java Style
- Standard Java conventions (camelCase methods/fields, PascalCase classes)
- Java 11 language level (no var usage observed, traditional style)
- Apache 2.0 license headers on all source files
- `@author hhildebrand` Javadoc tag on classes
- Package structure: `com.chiralbehaviors.layout.*`
- Minimal Javadoc — brief `@author` tags, few method-level docs
- `java.util.logging` used (not SLF4J) in core module; logback in explorer/toy-app
- Fields aligned with spaces for readability
- Jackson annotations used sparingly (`@JsonProperty`)

## Design Patterns
- Schema tree: composite pattern (`SchemaNode` → `Relation` / `Primitive`)
- Layout cells: generic interface `LayoutCell<T extends Region>`
- Style as factory: `Style` creates layout instances and cell factories
- CSS-driven styling with co-located `.css` files per component
- Separate `Launcher` classes delegate to `Application` subclass (JavaFX module system workaround)

## Project Conventions
- No Java module-info files (classpath-based, not JPMS modular)
- Fat jars via `maven-shade-plugin` with classifier `phat`
- Test classes named `Smoke`, `SmokeTable`, `TestGraphQlUtil` (no strict naming convention)
