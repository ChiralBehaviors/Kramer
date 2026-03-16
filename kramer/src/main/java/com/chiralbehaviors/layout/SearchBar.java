// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout;

import java.net.URL;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/**
 * A compact search bar overlay composed of a query {@link TextField}, a match
 * count {@link Label}, and previous/next {@link Button}s.
 *
 * <p>Callers wire actions via {@link #setOnFindPrevious(Runnable)} and
 * {@link #setOnFindNext(Runnable)}, and update the display via
 * {@link #setMatchInfo(int, int)}.
 *
 * <p>Styled by the co-located {@code search-bar.css}; the root node carries
 * the CSS style class {@code search-bar}.
 */
public class SearchBar extends HBox {

    private static final String STYLE_SHEET  = "search-bar.css";
    private static final String STYLE_CLASS  = "search-bar";

    private final TextField searchField;
    private final Label     matchCount;
    private final Button    prevButton;
    private final Button    nextButton;

    private Runnable onFindNext;
    private Runnable onFindPrevious;

    public SearchBar() {
        getStyleClass().add(STYLE_CLASS);
        URL cssUrl = getClass().getResource(STYLE_SHEET);
        if (cssUrl != null) {
            getStylesheets().add(cssUrl.toExternalForm());
        }

        searchField = new TextField();
        searchField.setPromptText("Search…");
        HBox.setHgrow(searchField, Priority.ALWAYS);

        matchCount = new Label("No matches");
        matchCount.getStyleClass().add("search-match-count");
        matchCount.setMinWidth(80);
        matchCount.setAlignment(Pos.CENTER);

        prevButton = new Button("<");
        prevButton.getStyleClass().add("search-prev");
        prevButton.setFocusTraversable(false);
        prevButton.setOnAction(e -> {
            if (onFindPrevious != null) {
                onFindPrevious.run();
            }
        });

        nextButton = new Button(">");
        nextButton.getStyleClass().add("search-next");
        nextButton.setFocusTraversable(false);
        nextButton.setOnAction(e -> {
            if (onFindNext != null) {
                onFindNext.run();
            }
        });

        // Enter in the text field triggers findNext — TextField consumes KEY_PRESSED for Enter internally and fires ActionEvent.
        searchField.setOnAction(e -> {
            if (onFindNext != null) {
                onFindNext.run();
            }
        });

        setAlignment(Pos.CENTER_LEFT);
        setSpacing(4);
        getChildren().addAll(searchField, matchCount, prevButton, nextButton);
    }

    /** Returns the query {@link TextField}. */
    public TextField getSearchField() {
        return searchField;
    }

    /**
     * Updates the match count display.
     *
     * @param current 1-based index of the current match (0 means none selected)
     * @param total   total number of matches
     */
    public void setMatchInfo(int current, int total) {
        if (total == 0 || current == 0) {
            matchCount.setText("No matches");
        } else {
            matchCount.setText(current + " of " + total);
        }
    }

    /**
     * Sets the action invoked when the user presses the next button or Enter
     * in the search field.
     *
     * @param action the runnable to call, or {@code null} to clear
     */
    public void setOnFindNext(Runnable action) {
        this.onFindNext = action;
    }

    /**
     * Sets the action invoked when the user presses the previous button.
     *
     * @param action the runnable to call, or {@code null} to clear
     */
    public void setOnFindPrevious(Runnable action) {
        this.onFindPrevious = action;
    }
}
