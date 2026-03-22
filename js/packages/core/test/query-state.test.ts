// SPDX-License-Identifier: Apache-2.0
import { describe, it, expect } from 'vitest';
import { schemaPath } from '../src/schema.js';
import { LayoutQueryState, InteractionHandler } from '../src/query-state.js';

describe('LayoutQueryState', () => {
  it('returns default field state', () => {
    const qs = new LayoutQueryState();
    const fs = qs.getFieldState(schemaPath('name'));
    expect(fs.visible).toBe(true);
    expect(fs.sortDirection).toBeNull();
    expect(fs.filterExpression).toBeNull();
  });

  it('applies sortBy', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('departments', 'name');
    qs.apply({ kind: 'sortBy', path, descending: false });
    expect(qs.getFieldState(path).sortDirection).toBe('asc');
  });

  it('applies sortBy descending', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('name');
    qs.apply({ kind: 'sortBy', path, descending: true });
    expect(qs.getFieldState(path).sortDirection).toBe('desc');
  });

  it('clears sort', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('name');
    qs.apply({ kind: 'sortBy', path, descending: false });
    qs.apply({ kind: 'clearSort', path });
    expect(qs.getFieldState(path).sortDirection).toBeNull();
  });

  it('toggles visibility', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('name');
    expect(qs.isVisible(path)).toBe(true);
    qs.apply({ kind: 'toggleVisible', path });
    expect(qs.isVisible(path)).toBe(false);
    qs.apply({ kind: 'toggleVisible', path });
    expect(qs.isVisible(path)).toBe(true);
  });

  it('sets and clears filter', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('price');
    qs.apply({ kind: 'setFilter', path, expression: '$price > 10' });
    expect(qs.getFieldState(path).filterExpression).toBe('$price > 10');
    qs.apply({ kind: 'clearFilter', path });
    expect(qs.getFieldState(path).filterExpression).toBeNull();
  });

  it('sets formula', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('total');
    qs.apply({ kind: 'setFormula', path, expression: '$price * $qty' });
    expect(qs.getFieldState(path).formulaExpression).toBe('$price * $qty');
  });

  it('sets aggregate', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('price');
    qs.apply({ kind: 'setAggregate', path, expression: 'sum($price)' });
    expect(qs.getFieldState(path).aggregateExpression).toBe('sum($price)');
  });

  it('resets all', () => {
    const qs = new LayoutQueryState();
    const path = schemaPath('name');
    qs.apply({ kind: 'sortBy', path, descending: false });
    qs.apply({ kind: 'resetAll' });
    expect(qs.getFieldState(path).sortDirection).toBeNull();
  });

  it('increments version on change', () => {
    const qs = new LayoutQueryState();
    const v0 = qs.getVersion();
    qs.apply({ kind: 'toggleVisible', path: schemaPath('name') });
    expect(qs.getVersion()).toBeGreaterThan(v0);
  });

  it('notifies listeners', () => {
    const qs = new LayoutQueryState();
    let notified = false;
    qs.addChangeListener(() => { notified = true; });
    qs.apply({ kind: 'toggleVisible', path: schemaPath('name') });
    expect(notified).toBe(true);
  });

  it('unsubscribes listener', () => {
    const qs = new LayoutQueryState();
    let count = 0;
    const unsub = qs.addChangeListener(() => { count++; });
    qs.apply({ kind: 'toggleVisible', path: schemaPath('name') });
    expect(count).toBe(1);
    unsub();
    qs.apply({ kind: 'toggleVisible', path: schemaPath('name') });
    expect(count).toBe(1);
  });
});

describe('InteractionHandler', () => {
  it('applies interaction via handler', () => {
    const qs = new LayoutQueryState();
    const handler = new InteractionHandler(qs);
    const path = schemaPath('name');
    handler.apply({ kind: 'sortBy', path, descending: true });
    expect(qs.getFieldState(path).sortDirection).toBe('desc');
  });

  it('supports undo', () => {
    const qs = new LayoutQueryState();
    const handler = new InteractionHandler(qs);
    const path = schemaPath('name');

    expect(handler.canUndo()).toBe(false);
    handler.apply({ kind: 'toggleVisible', path });
    expect(handler.canUndo()).toBe(true);
    expect(qs.isVisible(path)).toBe(false);

    handler.undo();
    // After undo, state is reset (simplified implementation)
    expect(handler.canUndo()).toBe(false);
  });

  it('supports redo', () => {
    const qs = new LayoutQueryState();
    const handler = new InteractionHandler(qs);
    const path = schemaPath('name');

    handler.apply({ kind: 'toggleVisible', path });
    handler.undo();
    expect(handler.canRedo()).toBe(true);
    handler.redo();
    expect(handler.canRedo()).toBe(false);
  });

  it('clears redo on new action', () => {
    const qs = new LayoutQueryState();
    const handler = new InteractionHandler(qs);
    const path = schemaPath('name');

    handler.apply({ kind: 'toggleVisible', path });
    handler.undo();
    expect(handler.canRedo()).toBe(true);

    handler.apply({ kind: 'sortBy', path, descending: false });
    expect(handler.canRedo()).toBe(false);
  });

  it('clearHistory removes all undo/redo', () => {
    const qs = new LayoutQueryState();
    const handler = new InteractionHandler(qs);
    handler.apply({ kind: 'toggleVisible', path: schemaPath('name') });
    handler.clearHistory();
    expect(handler.canUndo()).toBe(false);
  });
});
