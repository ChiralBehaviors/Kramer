// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

class LayoutSearchTest {

    private Relation schema;
    private com.fasterxml.jackson.databind.node.ArrayNode data;

    @BeforeEach
    void setUp() {
        schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("code"));

        data = JsonNodeFactory.instance.arrayNode();
        var row0 = JsonNodeFactory.instance.objectNode();
        row0.put("name", "Alice");
        row0.put("code", "A1");
        data.add(row0);

        var row1 = JsonNodeFactory.instance.objectNode();
        row1.put("name", "Bob");
        row1.put("code", "B2");
        data.add(row1);

        var row2 = JsonNodeFactory.instance.objectNode();
        row2.put("name", "Alice B");
        row2.put("code", "A3");
        data.add(row2);
    }

    @Test
    void nullQueryReturnsEmpty() {
        var search = new LayoutSearch(schema, data);
        search.setQuery(null);
        assertEquals(Optional.empty(), search.findNext());
    }

    @Test
    void blankQueryReturnsEmpty() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("   ");
        assertEquals(Optional.empty(), search.findNext());
    }

    @Test
    void emptyQueryReturnsEmpty() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("");
        assertEquals(Optional.empty(), search.findNext());
    }

    @Test
    void findNextReturnsFirstMatch() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("Alice");
        Optional<SearchResult> result = search.findNext();
        assertTrue(result.isPresent());
        assertEquals(0, result.get().rowIndex());
        assertEquals("Alice", result.get().matchedValue());
        assertEquals(0, result.get().matchStart());
        assertEquals(5, result.get().matchLength());
    }

    @Test
    void findNextAdvancesToNextMatch() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("Alice");
        search.findNext(); // row 0, name "Alice"
        Optional<SearchResult> second = search.findNext();
        assertTrue(second.isPresent());
        // "Alice B" at row 2 is the next occurrence
        assertEquals(2, second.get().rowIndex());
        assertEquals("Alice B", second.get().matchedValue());
        assertEquals(0, second.get().matchStart());
    }

    @Test
    void findPreviousWrapsAround() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("Alice");
        // Go to first match
        search.findNext();
        // Go backward from first match should wrap to last occurrence of "Alice"
        Optional<SearchResult> prev = search.findPrevious();
        assertTrue(prev.isPresent());
        assertEquals(2, prev.get().rowIndex());
    }

    @Test
    void findNextWrapsAround() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("Alice");
        search.findNext(); // row 0
        search.findNext(); // row 2
        Optional<SearchResult> wrapped = search.findNext(); // should wrap to row 0
        assertTrue(wrapped.isPresent());
        assertEquals(0, wrapped.get().rowIndex());
    }

    @Test
    void countMatchesReturnsTotalAcrossAllRowsAndFields() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("A");
        // "Alice" in row 0 name -> "A" at index 0
        // "A1"   in row 0 code -> "A" at index 0
        // "Alice B" in row 2 name -> "A" at index 0
        // "A3"   in row 2 code -> "A" at index 0
        // Total = 4
        assertEquals(4, search.countMatches());
    }

    @Test
    void countMatchesNoMatch() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("zzz");
        assertEquals(0, search.countMatches());
    }

    @Test
    void caseSensitiveModeDistinguishesCasing() {
        var search = new LayoutSearch(schema, data);
        search.setCaseSensitive(true);
        search.setQuery("alice"); // lowercase
        assertEquals(0, search.countMatches());

        search.setQuery("Alice");
        assertEquals(2, search.countMatches()); // row0 name, row2 name
    }

    @Test
    void caseInsensitiveModeDefault() {
        var search = new LayoutSearch(schema, data);
        // default is case-insensitive
        search.setQuery("alice");
        assertEquals(2, search.countMatches());
    }

    @Test
    void nonArrayDataReturnsNoResults() {
        var objectNode = JsonNodeFactory.instance.objectNode();
        objectNode.put("name", "Alice");
        objectNode.put("code", "A1");

        var search = new LayoutSearch(schema, objectNode);
        search.setQuery("Alice");
        assertEquals(0, search.countMatches());
        assertEquals(Optional.empty(), search.findNext());
    }

    @Test
    void noMatchReturnsEmpty() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("zzz");
        assertEquals(Optional.empty(), search.findNext());
        assertEquals(Optional.empty(), search.findPrevious());
    }

    @Test
    void schemaPathIsCorrect() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("A1");
        Optional<SearchResult> result = search.findNext();
        assertTrue(result.isPresent());
        assertEquals(new SchemaPath("items").child("code"), result.get().path());
        assertEquals(0, result.get().rowIndex());
    }

    @Test
    void getQueryReflectsSetQuery() {
        var search = new LayoutSearch(schema, data);
        search.setQuery("test");
        assertEquals("test", search.getQuery());
    }

    @Test
    void wrapAroundCanBeDisabled() {
        var search = new LayoutSearch(schema, data);
        search.setWrapAround(false);
        search.setQuery("Alice");
        search.findNext(); // row 0
        search.findNext(); // row 2
        // No more matches and wrap is off
        Optional<SearchResult> result = search.findNext();
        assertEquals(Optional.empty(), result);
    }
}
