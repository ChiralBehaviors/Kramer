// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.testfx.framework.junit5.ApplicationTest;

import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

/**
 * Tests for SearchBar component.
 *
 * Runs under TestFX with Monocle headless toolkit so JavaFX nodes
 * can be instantiated and inspected without a display.
 */
class SearchBarTest extends ApplicationTest {

    private SearchBar searchBar;

    @Override
    public void start(Stage stage) {
        searchBar = new SearchBar();
    }

    @Test
    void constructionCreatesTextField() {
        assertNotNull(searchBar.getSearchField(),
                      "getSearchField() must return a non-null TextField");
        assertInstanceOf(TextField.class, searchBar.getSearchField());
    }

    @Test
    void constructionCreatesMatchCountLabel() {
        // find the Label child in the HBox
        long labelCount = searchBar.getChildren().stream()
                                   .filter(n -> n instanceof Label)
                                   .count();
        assertTrue(labelCount >= 1, "SearchBar must contain at least one Label for match count");
    }

    @Test
    void constructionCreatesNextAndPrevButtons() {
        long buttonCount = searchBar.getChildren().stream()
                                    .filter(n -> n instanceof Button)
                                    .count();
        assertEquals(2, buttonCount, "SearchBar must contain exactly two Buttons (prev and next)");
    }

    @Test
    void setMatchInfoDisplaysCurrentOfTotal() {
        searchBar.setMatchInfo(3, 17);
        Label matchLabel = findMatchCountLabel();
        assertEquals("3 of 17", matchLabel.getText());
    }

    @Test
    void setMatchInfoZeroDisplaysNoMatches() {
        searchBar.setMatchInfo(0, 0);
        Label matchLabel = findMatchCountLabel();
        assertEquals("No matches", matchLabel.getText());
    }

    @Test
    void setMatchInfoZeroTotalDisplaysNoMatches() {
        searchBar.setMatchInfo(0, 5);
        Label matchLabel = findMatchCountLabel();
        // 0 current with non-zero total still means "No matches" (nothing found yet)
        assertEquals("No matches", matchLabel.getText());
    }

    @Test
    void setMatchInfoOneOfOneDisplaysCorrectly() {
        searchBar.setMatchInfo(1, 1);
        Label matchLabel = findMatchCountLabel();
        assertEquals("1 of 1", matchLabel.getText());
    }

    @Test
    void searchFieldIsIncludedInChildren() {
        assertTrue(searchBar.getChildren().contains(searchBar.getSearchField()),
                   "SearchField must be a direct child of the SearchBar HBox");
    }

    @Test
    void setOnFindNextInvokesRunnable() {
        boolean[] called = { false };
        searchBar.setOnFindNext(() -> called[0] = true);

        // Fire the next button
        Button nextButton = findNextButton();
        nextButton.fire();

        assertTrue(called[0], "setOnFindNext runnable must be called when next button fires");
    }

    @Test
    void setOnFindPreviousInvokesRunnable() {
        boolean[] called = { false };
        searchBar.setOnFindPrevious(() -> called[0] = true);

        Button prevButton = findPrevButton();
        prevButton.fire();

        assertTrue(called[0], "setOnFindPrevious runnable must be called when prev button fires");
    }

    @Test
    void findNextHandlerCanBeReplacedWithNull() {
        searchBar.setOnFindNext(() -> { /* noop */ });
        assertDoesNotThrow(() -> searchBar.setOnFindNext(null));

        Button nextButton = findNextButton();
        assertDoesNotThrow(nextButton::fire, "Null find-next handler must not throw on button fire");
    }

    @Test
    void findPreviousHandlerCanBeReplacedWithNull() {
        searchBar.setOnFindPrevious(() -> { /* noop */ });
        assertDoesNotThrow(() -> searchBar.setOnFindPrevious(null));

        Button prevButton = findPrevButton();
        assertDoesNotThrow(prevButton::fire, "Null find-prev handler must not throw on button fire");
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Label findMatchCountLabel() {
        return searchBar.getChildren().stream()
                        .filter(n -> n instanceof Label)
                        .map(n -> (Label) n)
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("No Label found in SearchBar"));
    }

    /** Returns the button whose text starts with ">" or is identified as the next button. */
    private Button findNextButton() {
        var buttons = searchBar.getChildren().stream()
                               .filter(n -> n instanceof Button)
                               .map(n -> (Button) n)
                               .toList();
        assertEquals(2, buttons.size(), "Expected exactly 2 buttons");
        // By convention next is the last button
        return buttons.get(1);
    }

    private Button findPrevButton() {
        var buttons = searchBar.getChildren().stream()
                               .filter(n -> n instanceof Button)
                               .map(n -> (Button) n)
                               .toList();
        assertEquals(2, buttons.size(), "Expected exactly 2 buttons");
        // By convention prev is the first button
        return buttons.get(0);
    }
}
