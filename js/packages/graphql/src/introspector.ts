// SPDX-License-Identifier: Apache-2.0

/**
 * TypeIntrospector — parse GraphQL __schema introspection results.
 * Port of Java TypeIntrospector.
 */

export interface IntrospectedType {
  readonly name: string;
  readonly kind: string;
  readonly fields: IntrospectedField[];
}

export interface IntrospectedField {
  readonly name: string;
  readonly type: TypeRef;
  readonly description: string | null;
}

export interface TypeRef {
  readonly name: string | null;
  readonly kind: string;
  readonly ofType: TypeRef | null;
}

export interface TypeIntrospectorResult {
  readonly typeIndex: Map<string, IntrospectedType>;
  readonly userTypes: IntrospectedType[];
}

/**
 * Parse a GraphQL __schema introspection response into a type index.
 */
export function parseIntrospection(json: unknown): TypeIntrospectorResult {
  const data = (json as any)?.data?.__schema?.types;
  if (!Array.isArray(data)) {
    throw new Error('Invalid introspection response: expected data.__schema.types array');
  }

  const typeIndex = new Map<string, IntrospectedType>();
  const userTypes: IntrospectedType[] = [];

  for (const raw of data) {
    if (isSystemType(raw.name) || raw.kind === 'SCALAR') continue;

    const fields: IntrospectedField[] = (raw.fields ?? []).map((f: any) => ({
      name: f.name,
      type: parseTypeRef(f.type),
      description: f.description ?? null,
    }));

    const type: IntrospectedType = {
      name: raw.name,
      kind: raw.kind,
      fields,
    };

    typeIndex.set(raw.name, type);
    userTypes.push(type);
  }

  return { typeIndex, userTypes };
}

function parseTypeRef(raw: any): TypeRef {
  if (!raw) return { name: null, kind: 'SCALAR', ofType: null };
  return {
    name: raw.name ?? null,
    kind: raw.kind ?? 'SCALAR',
    ofType: raw.ofType ? parseTypeRef(raw.ofType) : null,
  };
}

function isSystemType(name: string): boolean {
  return name.startsWith('__');
}

/**
 * Get the base type name, unwrapping LIST/NON_NULL wrappers.
 */
export function baseName(ref: TypeRef): string | null {
  if (ref.name != null) return ref.name;
  return ref.ofType ? baseName(ref.ofType) : null;
}

/**
 * The standard introspection query.
 */
export const INTROSPECTION_QUERY = `{
  __schema {
    types {
      name
      kind
      fields {
        name
        description
        type {
          name
          kind
          ofType {
            name
            kind
            ofType {
              name
              kind
            }
          }
        }
      }
    }
  }
}`;
