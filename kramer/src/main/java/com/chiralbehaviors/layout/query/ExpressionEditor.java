// SPDX-License-Identifier: Apache-2.0
package com.chiralbehaviors.layout.query;

import java.util.Optional;

import com.chiralbehaviors.layout.expression.ExpressionEvaluator;
import com.chiralbehaviors.layout.expression.ParseException;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

/**
 * Inline expression editor with real-time parse validation and error feedback.
 * Wraps a {@link TextField} and a validation status label.
 * <p>
 * The editor validates expressions using {@link ExpressionEvaluator#compile(String)}
 * on each keystroke. Valid expressions show a green checkmark; invalid ones
 * show the parse error message in red.
 *
 * @author hhildebrand
 */
public final class ExpressionEditor extends VBox {

    private final TextField textField;
    private final Label errorLabel;
    private final ExpressionEvaluator evaluator;
    private final ReadOnlyBooleanWrapper valid = new ReadOnlyBooleanWrapper(true);
    private final ReadOnlyStringWrapper errorMessage = new ReadOnlyStringWrapper("");

    public ExpressionEditor() {
        this(new ExpressionEvaluator());
    }

    public ExpressionEditor(ExpressionEvaluator evaluator) {
        this.evaluator = evaluator;
        this.textField = new TextField();
        this.errorLabel = new Label();
        errorLabel.getStyleClass().add("expression-error");
        errorLabel.setWrapText(true);
        getStyleClass().add("expression-editor");

        textField.textProperty().addListener((obs, oldVal, newVal) -> validate(newVal));

        getChildren().addAll(textField, errorLabel);
        setSpacing(2);
    }

    /** Get the current expression text. */
    public String getText() {
        return textField.getText();
    }

    /** Set the expression text. */
    public void setText(String text) {
        textField.setText(text);
    }

    /** Whether the current expression is valid. */
    public ReadOnlyBooleanProperty validProperty() {
        return valid.getReadOnlyProperty();
    }

    public boolean isValid() {
        return valid.get();
    }

    /** The current error message (empty when valid). */
    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessage.getReadOnlyProperty();
    }

    public String getErrorMessage() {
        return errorMessage.get();
    }

    /** Returns the backing TextField for focus management. */
    public TextField getTextField() {
        return textField;
    }

    /**
     * Show the editor as a dialog and return the result.
     * Returns empty if cancelled, or the expression text if confirmed.
     */
    public static Optional<String> showDialog(String title, String currentValue) {
        return showDialog(title, currentValue, null);
    }

    public static Optional<String> showDialog(String title, String currentValue,
                                               ExpressionEvaluator evaluator) {
        var dialog = new javafx.scene.control.Dialog<String>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        var editor = evaluator != null ? new ExpressionEditor(evaluator)
                                       : new ExpressionEditor();
        editor.setText(currentValue != null ? currentValue : "");

        dialog.getDialogPane().setContent(editor);
        dialog.getDialogPane().getButtonTypes().addAll(
            javafx.scene.control.ButtonType.OK,
            javafx.scene.control.ButtonType.CANCEL);

        // Disable OK when expression is invalid (but allow empty = clear)
        var okButton = dialog.getDialogPane().lookupButton(
            javafx.scene.control.ButtonType.OK);
        okButton.disableProperty().bind(editor.validProperty().not());

        dialog.setResultConverter(bt ->
            bt == javafx.scene.control.ButtonType.OK ? editor.getText() : null);

        return dialog.showAndWait();
    }

    private void validate(String text) {
        if (text == null || text.isBlank()) {
            valid.set(true);
            errorMessage.set("");
            errorLabel.setText("");
            errorLabel.setVisible(false);
            return;
        }

        try {
            evaluator.compile(text);
            valid.set(true);
            errorMessage.set("");
            errorLabel.setText("\u2713");
            errorLabel.setStyle("-fx-text-fill: green;");
            errorLabel.setVisible(true);
        } catch (ParseException e) {
            valid.set(false);
            errorMessage.set(e.getMessage());
            errorLabel.setText(e.getMessage());
            errorLabel.setStyle("-fx-text-fill: red;");
            errorLabel.setVisible(true);
        }
    }
}
