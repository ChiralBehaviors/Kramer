// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { VERSION } from '../src/index.js';

describe('@kramer/react', () => {
  it('exports version', () => {
    expect(VERSION).toBe('0.0.1');
  });
});
