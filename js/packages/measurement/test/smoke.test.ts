// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { FixedMeasurement, ApproximateMeasurement } from '../src/index.js';

describe('FixedMeasurement', () => {
  const m = new FixedMeasurement(7);

  it('measures text width by character count', () => {
    expect(m.textWidth('hello', 'sans-serif', 13)).toBe(35); // 5 * 7
    expect(m.textWidth('ab', 'sans-serif', 13)).toBe(14);    // 2 * 7
  });

  it('lineHeight is 1.2x fontSize', () => {
    expect(m.lineHeight('sans-serif', 13)).toBe(16); // ceil(13 * 1.2)
    expect(m.lineHeight('sans-serif', 20)).toBe(24);
  });

  it('empty string has zero width', () => {
    expect(m.textWidth('', 'sans-serif', 13)).toBe(0);
  });
});

describe('ApproximateMeasurement', () => {
  const m = new ApproximateMeasurement();

  it('produces non-zero width for text', () => {
    const w = m.textWidth('Hello World', 'sans-serif', 13);
    expect(w).toBeGreaterThan(0);
  });

  it('wider text produces larger width', () => {
    const short = m.textWidth('Hi', 'sans-serif', 13);
    const long = m.textWidth('Hello World', 'sans-serif', 13);
    expect(long).toBeGreaterThan(short);
  });

  it('larger fontSize produces larger width', () => {
    const small = m.textWidth('test', 'sans-serif', 10);
    const large = m.textWidth('test', 'sans-serif', 20);
    expect(large).toBeGreaterThan(small);
  });

  it('spaces are narrower than letters', () => {
    const spaces = m.textWidth('   ', 'sans-serif', 13);
    const letters = m.textWidth('aaa', 'sans-serif', 13);
    expect(spaces).toBeLessThan(letters);
  });
});
