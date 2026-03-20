# Toy Application

A declarative single-page application framework using GraphQL and Kramer autolayout.

## Running

```bash
mvn package
java -jar target/toy-app-0.0.1-SNAPSHOT-phat.jar --app=src/test/resources/testApp.yml
```

## What it does

Defines applications as a network of GraphQL queries in YAML. Each page is a query rendered by the autolayout engine. Navigation between pages is driven by double-click on data rows, passing selected values as query parameters.

Features:
- Declarative page definitions in YAML
- XPath-like expressions for extracting navigation parameters
- Back/forward/refresh navigation history
- Autolayout rendering of each page's query results

## Configuration

See [testApp.yml](src/test/resources/testApp.yml) for an example. Each page specifies:
- A GraphQL query (with parameter placeholders)
- A selection name (the root field to render)
- Navigation links mapping double-click targets to other pages

## Status

This is an experimental framework for exploring declarative UI patterns over GraphQL. It works but is not production-hardened.
