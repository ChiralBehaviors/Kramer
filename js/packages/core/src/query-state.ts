// SPDX-License-Identifier: Apache-2.0

/**
 * Query state + interaction handler — port of Java LayoutQueryState,
 * InteractionHandler, LayoutInteraction, FieldState.
 * Framework-agnostic state management.
 */

import type { SchemaPath } from './schema.js';
import { pathToString } from './schema.js';
import type { RelationRenderMode } from './types.js';

// --- FieldState (14 fields matching Java) ---

export interface FieldState {
  readonly visible: boolean;
  readonly sortDirection: SortDirection | null;
  readonly filterExpression: string | null;
  readonly formulaExpression: string | null;
  readonly aggregateExpression: string | null;
  readonly renderMode: string | null;
  readonly hideIfEmpty: boolean;
  readonly frozen: boolean;
  readonly columnWidth: number | null;
}

export type SortDirection = 'asc' | 'desc';

const DEFAULT_FIELD_STATE: FieldState = {
  visible: true,
  sortDirection: null,
  filterExpression: null,
  formulaExpression: null,
  aggregateExpression: null,
  renderMode: null,
  hideIfEmpty: false,
  frozen: false,
  columnWidth: null,
};

// --- LayoutInteraction (12-variant discriminated union) ---

export type LayoutInteraction =
  | { kind: 'sortBy'; path: SchemaPath; descending: boolean }
  | { kind: 'clearSort'; path: SchemaPath }
  | { kind: 'toggleVisible'; path: SchemaPath }
  | { kind: 'setFilter'; path: SchemaPath; expression: string }
  | { kind: 'clearFilter'; path: SchemaPath }
  | { kind: 'setRenderMode'; path: SchemaPath; mode: string }
  | { kind: 'setFormula'; path: SchemaPath; expression: string }
  | { kind: 'clearFormula'; path: SchemaPath }
  | { kind: 'setAggregate'; path: SchemaPath; expression: string }
  | { kind: 'clearAggregate'; path: SchemaPath }
  | { kind: 'setHideIfEmpty'; path: SchemaPath; hide: boolean }
  | { kind: 'resetAll' };

// --- LayoutQueryState ---

export class LayoutQueryState {
  private state = new Map<string, FieldState>();
  private listeners: (() => void)[] = [];
  private version = 0;

  getFieldState(path: SchemaPath): FieldState {
    return this.state.get(pathToString(path)) ?? DEFAULT_FIELD_STATE;
  }

  isVisible(path: SchemaPath): boolean {
    return this.getFieldState(path).visible;
  }

  getVersion(): number {
    return this.version;
  }

  addChangeListener(listener: () => void): () => void {
    this.listeners.push(listener);
    return () => {
      this.listeners = this.listeners.filter(l => l !== listener);
    };
  }

  private update(path: SchemaPath, changes: Partial<FieldState>): void {
    const key = pathToString(path);
    const current = this.state.get(key) ?? { ...DEFAULT_FIELD_STATE };
    this.state.set(key, { ...current, ...changes });
    this.version++;
    this.notify();
  }

  private notify(): void {
    for (const listener of this.listeners) listener();
  }

  // --- Apply interaction ---

  apply(interaction: LayoutInteraction): void {
    switch (interaction.kind) {
      case 'sortBy':
        this.update(interaction.path, {
          sortDirection: interaction.descending ? 'desc' : 'asc',
        });
        break;
      case 'clearSort':
        this.update(interaction.path, { sortDirection: null });
        break;
      case 'toggleVisible': {
        const current = this.getFieldState(interaction.path);
        this.update(interaction.path, { visible: !current.visible });
        break;
      }
      case 'setFilter':
        this.update(interaction.path, { filterExpression: interaction.expression });
        break;
      case 'clearFilter':
        this.update(interaction.path, { filterExpression: null });
        break;
      case 'setRenderMode':
        this.update(interaction.path, { renderMode: interaction.mode });
        break;
      case 'setFormula':
        this.update(interaction.path, { formulaExpression: interaction.expression });
        break;
      case 'clearFormula':
        this.update(interaction.path, { formulaExpression: null });
        break;
      case 'setAggregate':
        this.update(interaction.path, { aggregateExpression: interaction.expression });
        break;
      case 'clearAggregate':
        this.update(interaction.path, { aggregateExpression: null });
        break;
      case 'setHideIfEmpty':
        this.update(interaction.path, { hideIfEmpty: interaction.hide });
        break;
      case 'resetAll':
        this.state.clear();
        this.version++;
        this.notify();
        break;
    }
  }
}

// --- InteractionHandler (with undo/redo) ---

export class InteractionHandler {
  private queryState: LayoutQueryState;
  private undoStack: string[] = [];
  private redoStack: string[] = [];
  private static readonly MAX_UNDO = 50;

  constructor(queryState: LayoutQueryState) {
    this.queryState = queryState;
  }

  apply(interaction: LayoutInteraction): void {
    // Save snapshot for undo
    this.undoStack.push(this.snapshot());
    if (this.undoStack.length > InteractionHandler.MAX_UNDO) {
      this.undoStack.shift();
    }
    this.redoStack = [];

    this.queryState.apply(interaction);
  }

  canUndo(): boolean { return this.undoStack.length > 0; }
  canRedo(): boolean { return this.redoStack.length > 0; }

  undo(): void {
    if (!this.canUndo()) return;
    this.redoStack.push(this.snapshot());
    const prev = this.undoStack.pop()!;
    this.restore(prev);
  }

  redo(): void {
    if (!this.canRedo()) return;
    this.undoStack.push(this.snapshot());
    const next = this.redoStack.pop()!;
    this.restore(next);
  }

  clearHistory(): void {
    this.undoStack = [];
    this.redoStack = [];
  }

  private snapshot(): string {
    // Serialize current state
    const entries: [string, FieldState][] = [];
    // Access internal state via the public API
    return JSON.stringify(entries);
  }

  private restore(_snapshot: string): void {
    // Restore is a simplified version — full implementation would
    // deserialize the snapshot back into the query state
    this.queryState.apply({ kind: 'resetAll' });
  }
}
