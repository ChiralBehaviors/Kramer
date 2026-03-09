# Suggested Commands

## Building
```bash
# Full build (all modules)
mvn clean install

# Build single module
cd kramer && mvn clean install
cd kramer-ql && mvn clean install

# Skip tests
mvn clean install -DskipTests
```

## Testing
```bash
# Run all tests
mvn test

# Run tests for a single module
mvn -pl kramer test
mvn -pl kramer-ql test

# Run a single test class
mvn -pl kramer-ql test -Dtest=TestGraphQlUtil
mvn -pl kramer test -Dtest=Smoke
```

## Running Applications
```bash
# Explorer app (build fat jar first)
cd explorer && mvn package
java -jar target/explorer-0.0.1-SNAPSHOT-phat.jar

# Toy app via javafx plugin
cd toy-app && mvn javafx:run
```

## Entry Points
- Explorer: `com.chiralbehaviors.layout.explorer.Launcher`
- Toy App: `com.chiralbehaviors.layout.toy.Launcher`

## System (macOS/Darwin)
- `git`, `ls`, `cd`, `grep`, `find` — standard unix commands
- `mvn` — Maven build tool
