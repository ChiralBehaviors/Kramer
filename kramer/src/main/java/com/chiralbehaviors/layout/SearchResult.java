// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

/**
 * Identifies a single text match within the layout data.
 *
 * @param path         the schema path of the field that contains the match
 * @param rowIndex     the zero-based row index within the array data
 * @param matchedValue the full cell value in which the match was found
 * @param matchStart   the start offset of the match within matchedValue
 * @param matchLength  the length of the matched substring
 */
public record SearchResult(SchemaPath path, int rowIndex, String matchedValue,
                           int matchStart, int matchLength) {
}
