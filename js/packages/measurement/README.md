# @kramer/measurement

Text measurement strategies for the Kramer layout pipeline.

## Strategies

| Strategy | Use case | DOM required |
|----------|----------|-------------|
| `CanvasMeasurement` | Browser — uses `canvas.measureText()` | Yes |
| `FixedMeasurement` | Testing — constant character width | No |
| `ApproximateMeasurement` | SSR — per-character width table | No |

## Usage

```typescript
import { CanvasMeasurement } from '@kramer/measurement';

const strategy = new CanvasMeasurement();
const width = strategy.textWidth('Hello', 'sans-serif', 13);
```

All strategies implement `MeasurementStrategy` from `@kramer/core`.
