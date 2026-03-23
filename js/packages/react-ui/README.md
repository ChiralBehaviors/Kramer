# @kramer/react-ui

React interaction panels for the Kramer autolayout engine.

## Components

| Component | Description |
|-----------|------------|
| `<FieldSelector>` | TreeView with visibility checkboxes, child counts, field selection callback |
| `<FieldInspector>` | Property detail panel showing all FieldState properties |

## Usage

```tsx
import { FieldSelector, FieldInspector } from '@kramer/react-ui';
import { LayoutQueryState, schemaPath } from '@kramer/core';

const queryState = new LayoutQueryState();

<FieldSelector
  schema={schema}
  queryState={queryState}
  onFieldSelected={(path) => setSelectedPath(path)}
/>

<FieldInspector
  path={selectedPath}
  queryState={queryState}
/>
```

## Tests

7 tests: checkbox rendering, visibility toggle, field selection callback, inspector placeholder/properties/state reflection.
