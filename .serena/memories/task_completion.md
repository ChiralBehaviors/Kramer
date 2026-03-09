# Task Completion Checklist

When a coding task is completed, run the following:

1. **Build**: `mvn clean install` (from project root) — compiles and runs all tests
2. **Verify tests pass**: Check test output for failures
3. **Check affected module**: If changes are in a single module, at minimum run `mvn -pl <module> test`

No separate linting or formatting tools are configured. The Maven compiler plugin handles compilation checks.
