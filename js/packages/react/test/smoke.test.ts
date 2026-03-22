// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { AutoLayout, Table, VirtualOutline } from '../src/index.js';

describe('@kramer/react', () => {
  it('exports AutoLayout', () => {
    expect(AutoLayout).toBeDefined();
  });

  it('exports Table', () => {
    expect(Table).toBeDefined();
  });

  it('exports VirtualOutline', () => {
    expect(VirtualOutline).toBeDefined();
  });
});
