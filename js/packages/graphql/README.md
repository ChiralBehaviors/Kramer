# @kramer/graphql

GraphQL integration for Kramer — parse queries into schemas, expand/contract AST, introspect endpoints.

## Features

- **`buildSchema(query, source?)`** — parse a GraphQL query string into a Kramer `Relation` tree
- **`addField(doc, path, name)`** / **`removeField(doc, path)`** — immutable AST modification via graphql-js `visit()`
- **`serialize(doc)`** — serialize Document back to query string via `print()`
- **`parseIntrospection(json)`** — parse `__schema` response into browseable type index
- **`baseName(typeRef)`** — unwrap LIST/NON_NULL wrappers to get base type name

## Usage

```typescript
import { buildSchema, addField, serialize, parseIntrospection } from '@kramer/graphql';
import { parse } from 'graphql';

// Query → schema
const schema = buildSchema('{ users { name email } }');

// Add a field
const doc = parse('{ users { name } }');
const modified = addField(doc, ['users'], 'email');
console.log(serialize(modified)); // { users { name email } }

// Introspection
const result = parseIntrospection(introspectionResponse);
result.userTypes.forEach(t => console.log(t.name, t.fields.length));
```

## Tests

13 tests: query parsing, nested schemas, field expansion round-trip, introspection parsing, type ref unwrapping.
