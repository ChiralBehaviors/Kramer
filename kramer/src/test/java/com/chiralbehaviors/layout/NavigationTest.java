// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.cell.control.FocusController;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.scene.layout.Pane;

/**
 * Tests for LayoutSearch.navigateToResult — verifies VirtualFlow and
 * FocusController integration without requiring a live JavaFX scene.
 */
class NavigationTest {

    private Relation                                       schema;
    private com.fasterxml.jackson.databind.node.ArrayNode data;

    @BeforeEach
    void setUp() {
        schema = new Relation("items");
        schema.addChild(new Primitive("name"));
        schema.addChild(new Primitive("code"));

        data = JsonNodeFactory.instance.arrayNode();
        for (int i = 0; i < 5; i++) {
            var row = JsonNodeFactory.instance.objectNode();
            row.put("name", "Name" + i);
            row.put("code", "C" + i);
            data.add(row);
        }
    }

    // -----------------------------------------------------------------------
    // navigateToResult calls vf.show(rowIndex) — NOT showAsFirst
    // -----------------------------------------------------------------------

    @Test
    void navigateToResult_callsShowOnVirtualFlow() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf = mock(VirtualFlow.class);
        FocusController<LayoutCell<?>> fc = new FocusController<>(new Pane());

        var search = new LayoutSearch(schema, data, vf, fc);

        // Build a SearchResult directly to test navigateToResult in isolation
        var path = new SchemaPath("items").child("name");
        var result = new SearchResult(path, 2, "Name2", 0, 5);

        search.navigateToResult(result);

        verify(vf).show(2);               // MinDistanceTo semantics
        verify(vf, never()).showAsFirst(2); // must NOT use showAsFirst
    }

    // -----------------------------------------------------------------------
    // navigateToResult calls focusController.navigateTo(vf, rowIndex)
    // -----------------------------------------------------------------------

    @Test
    void navigateToResult_callsNavigateToOnFocusController() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf = mock(VirtualFlow.class);
        @SuppressWarnings("unchecked")
        FocusController<LayoutCell<?>> fc = mock(FocusController.class);

        // Use synchronous runLater so deferred focusController.navigateTo fires immediately
        List<Runnable> deferred = new ArrayList<>();
        var search = new LayoutSearch(schema, data, vf, fc, deferred::add);

        var path = new SchemaPath("items").child("name");
        var result = new SearchResult(path, 3, "Name3", 0, 5);

        search.navigateToResult(result);
        deferred.forEach(Runnable::run);

        verify(fc).navigateTo(vf, 3);
    }

    // -----------------------------------------------------------------------
    // navigateToResult with no VirtualFlow is a no-op
    // -----------------------------------------------------------------------

    @Test
    void navigateToResult_noVirtualFlow_isNoOp() {
        // Original two-arg constructor → no VF, no FC
        var search = new LayoutSearch(schema, data);
        var path = new SchemaPath("items").child("name");
        var result = new SearchResult(path, 0, "Name0", 0, 5);

        // Must not throw
        assertDoesNotThrow(() -> search.navigateToResult(result));
    }

    // -----------------------------------------------------------------------
    // findNext/findPrevious auto-navigate when a result is found
    // -----------------------------------------------------------------------

    @Test
    void findNext_autoNavigates() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf = mock(VirtualFlow.class);
        @SuppressWarnings("unchecked")
        FocusController<LayoutCell<?>> fc = mock(FocusController.class);

        // Use synchronous runLater so deferred focusController.navigateTo fires immediately
        List<Runnable> deferred = new ArrayList<>();
        var search = new LayoutSearch(schema, data, vf, fc, deferred::add);
        search.setQuery("Name1");
        Optional<SearchResult> result = search.findNext();
        assertTrue(result.isPresent());
        deferred.forEach(Runnable::run);

        verify(vf).show(1);
        verify(fc).navigateTo(vf, 1);
    }

    @Test
    void findPrevious_autoNavigates() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf = mock(VirtualFlow.class);
        @SuppressWarnings("unchecked")
        FocusController<LayoutCell<?>> fc = mock(FocusController.class);

        // Use synchronous runLater so deferred focusController.navigateTo fires immediately
        List<Runnable> deferred = new ArrayList<>();
        var search = new LayoutSearch(schema, data, vf, fc, deferred::add);
        search.setQuery("Name4");
        search.findNext(); // advance to row 4
        deferred.clear();  // discard deferred tasks from findNext
        reset(vf, fc);

        Optional<SearchResult> prev = search.findPrevious();
        assertTrue(prev.isPresent());
        int row = prev.get().rowIndex();
        deferred.forEach(Runnable::run);
        verify(vf).show(row);
        verify(fc).navigateTo(vf, row);
    }

    @Test
    void findNext_noMatch_doesNotCallVf() {
        @SuppressWarnings("unchecked")
        VirtualFlow<LayoutCell<?>> vf = mock(VirtualFlow.class);
        FocusController<LayoutCell<?>> fc = new FocusController<>(new Pane());

        var search = new LayoutSearch(schema, data, vf, fc);
        search.setQuery("zzz");
        Optional<SearchResult> result = search.findNext();
        assertFalse(result.isPresent());

        verifyNoInteractions(vf);
    }
}
