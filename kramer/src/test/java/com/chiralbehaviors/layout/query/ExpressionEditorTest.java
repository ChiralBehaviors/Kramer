// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.testfx.framework.junit5.ApplicationExtension;
import org.testfx.framework.junit5.Start;
import org.testfx.util.WaitForAsyncUtils;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

/**
 * Tests for ExpressionEditor (Kramer-fxk5 T4.1).
 */
@ExtendWith(ApplicationExtension.class)
class ExpressionEditorTest {

    @Start
    void start(Stage stage) {
        stage.setScene(new Scene(new Pane(), 400, 300));
        stage.show();
    }

    @Test
    void emptyExpressionIsValid() {
        var ref = new AtomicReference<Boolean>();
        Platform.runLater(() -> {
            var editor = new ExpressionEditor();
            editor.setText("");
            ref.set(editor.isValid());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get(), "Empty expression should be valid");
    }

    @Test
    void validExpressionIsValid() {
        var ref = new AtomicReference<Boolean>();
        Platform.runLater(() -> {
            var editor = new ExpressionEditor();
            editor.setText("$x + 1");
            ref.set(editor.isValid());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get(), "Valid expression '$x + 1' should be valid");
    }

    @Test
    void invalidExpressionShowsError() {
        var ref = new AtomicReference<ExpressionEditor>();
        Platform.runLater(() -> {
            var editor = new ExpressionEditor();
            editor.setText("$x +");  // incomplete expression
            ref.set(editor);
        });
        WaitForAsyncUtils.waitForFxEvents();

        var editor = ref.get();
        assertFalse(editor.isValid(), "Incomplete expression should be invalid");
        assertFalse(editor.getErrorMessage().isEmpty(), "Should have error message");
    }

    @Test
    void correctedExpressionBecomesValid() {
        var ref = new AtomicReference<ExpressionEditor>();
        Platform.runLater(() -> {
            var editor = new ExpressionEditor();
            editor.setText("$x +");  // invalid
            assertFalse(editor.isValid());

            editor.setText("$x + 1");  // fixed
            ref.set(editor);
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get().isValid(), "Corrected expression should be valid");
        assertEquals("", ref.get().getErrorMessage());
    }

    @Test
    void hasStyleClass() {
        var ref = new AtomicReference<Boolean>();
        Platform.runLater(() -> {
            var editor = new ExpressionEditor();
            ref.set(editor.getStyleClass().contains("expression-editor"));
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertTrue(ref.get(), "Should have expression-editor style class");
    }

    @Test
    void getTextReturnsCurrentValue() {
        var ref = new AtomicReference<String>();
        Platform.runLater(() -> {
            var editor = new ExpressionEditor();
            editor.setText("sum($price)");
            ref.set(editor.getText());
        });
        WaitForAsyncUtils.waitForFxEvents();

        assertEquals("sum($price)", ref.get());
    }
}
