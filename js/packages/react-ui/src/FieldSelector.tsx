// SPDX-License-Identifier: Apache-2.0

import React from 'react';
import type { SchemaNode, Relation, SchemaPath } from '@kramer/core';
import { schemaPath, childPath, isRelation, type LayoutQueryState } from '@kramer/core';

interface FieldSelectorProps {
  schema: Relation;
  queryState: LayoutQueryState;
  onFieldSelected?: (path: SchemaPath) => void;
  rootPath?: SchemaPath;
}

/**
 * TreeView with visibility checkboxes per field.
 * Port of Java FieldSelectorPanel.
 */
export function FieldSelector({ schema, queryState, onFieldSelected, rootPath }: FieldSelectorProps) {
  const path = rootPath ?? schemaPath(schema.field);

  return (
    <div className="kramer-field-selector" style={{ fontSize: '0.9em' }}>
      <FieldNode
        node={schema}
        path={path}
        queryState={queryState}
        onFieldSelected={onFieldSelected}
        depth={0}
      />
    </div>
  );
}

interface FieldNodeProps {
  node: SchemaNode;
  path: SchemaPath;
  queryState: LayoutQueryState;
  onFieldSelected?: (path: SchemaPath) => void;
  depth: number;
}

function FieldNode({ node, path, queryState, onFieldSelected, depth }: FieldNodeProps) {
  const visible = queryState.isVisible(path);

  const handleToggle = () => {
    queryState.apply({ kind: 'toggleVisible', path });
  };

  const handleClick = () => {
    onFieldSelected?.(path);
  };

  return (
    <div style={{ marginLeft: depth * 16 }}>
      <div
        className="kramer-field-node"
        style={{
          display: 'flex', alignItems: 'center', gap: 4, padding: '2px 0',
          opacity: visible ? 1 : 0.5,
          cursor: 'pointer',
        }}
        onClick={handleClick}
      >
        <input
          type="checkbox"
          checked={visible}
          onChange={handleToggle}
          onClick={e => e.stopPropagation()}
        />
        <span style={{ fontWeight: isRelation(node) ? 'bold' : 'normal' }}>
          {node.label}
        </span>
        {isRelation(node) && (
          <span style={{ color: '#999', fontSize: '0.8em' }}>
            {' '}{`{${node.children.length}}`}
          </span>
        )}
      </div>
      {isRelation(node) && node.children.map(child => (
        <FieldNode
          key={child.field}
          node={child}
          path={childPath(path, child.field)}
          queryState={queryState}
          onFieldSelected={onFieldSelected}
          depth={depth + 1}
        />
      ))}
    </div>
  );
}
