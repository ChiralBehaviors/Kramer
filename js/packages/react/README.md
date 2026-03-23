# @kramer/react

React renderer for the Kramer autolayout engine.

## Components

| Component | Description |
|-----------|------------|
| `<AutoLayout>` | Top-level — ResizeObserver, constraint solver, mode switching |
| `<Table>` | Nested table with column headers and data rows |
| `<VirtualOutline>` | Virtualized outline via @tanstack/react-virtual |
| `<ColumnHeader>` | Sort indicators (▲/▼), vertical rotation, click handler |
| `<NestedRow>` / `<NestedCell>` | Table row rendering with even/odd styling |

## Usage

```tsx
import { AutoLayout } from '@kramer/react';
import { relation, primitive } from '@kramer/core';

const schema = relation('items', primitive('name'), primitive('price'));
const data = [{ name: 'Widget', price: 9.99 }];

function App() {
  return <AutoLayout schema={schema} data={data} />;
}
```

Resize the container to see automatic table ↔ outline mode switching.

## Demo

```bash
pnpm --filter @kramer/react dev
```

Course catalog demo with FieldSelector + FieldInspector panels.

## Tests

17 tests: component exports, Table rendering, ColumnHeader sort/rotation, NestedRow values/classes, VirtualOutline mounting, AutoLayout mode switching.
