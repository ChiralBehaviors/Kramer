# @kramer/core

Core autolayout pipeline — pure TypeScript, zero DOM dependency.

## What's included

- **Schema** — `Relation`, `Primitive`, `SchemaPath` (discriminated unions)
- **Measure** — P90 statistical width measurement, variable-length detection
- **Layout** — `ExhaustiveConstraintSolver` (enumerate 2^N mode assignments), `readableTableWidth` threshold
- **Justify** — two-pass minimum-guarantee width distribution
- **Compress** — DP-optimal column partitioning (painter's partition, O(N²K))
- **Expression** — parser, AST, evaluator, aggregates (`sum`, `count`, `avg`, `min`, `max`)
- **Query state** — `LayoutQueryState` (12 interaction types), `InteractionHandler` (undo/redo)
- **Types** — `LayoutDecisionNode`, `MeasureResult`, `LayoutResult`, `CompressResult` (portable protocol)

## Usage

```typescript
import { relation, primitive, measureRelation, buildConstraintTree, solveConstraints, justifyChildren } from '@kramer/core';

const schema = relation('departments', primitive('name'), primitive('building'));
const data = [{ name: 'CS', building: 'Gates' }];

const measure = measureRelation(schema, data, strategy);
const constraint = buildConstraintTree(schema, measure, availableWidth);
const assignments = solveConstraints(constraint);
// assignments.get('departments') → 'TABLE' or 'OUTLINE'
```

## Tests

113 tests: schema (23), types (4), expression (36), pipeline (22), query state (17), contract (11).
