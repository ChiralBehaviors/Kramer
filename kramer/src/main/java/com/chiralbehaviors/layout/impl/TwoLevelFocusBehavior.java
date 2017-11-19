/**
 * Copyright (c) 2017 Chiral Behaviors, LLC, all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.chiralbehaviors.layout.impl;

import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.event.EventDispatcher;
import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Control;
import javafx.scene.control.PopupControl;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

/**
 * @author halhildebrand
 *
 */
@SuppressWarnings({ "deprecation", "restriction" })
public class TwoLevelFocusBehavior {

    private static final PseudoClass       EXTERNAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("external-focus");
    private static final PseudoClass       INTERNAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("internal-focus");
    /**
     * When a node gets focus, put it in external-focus mode.
     */
    final ChangeListener<Boolean>          focusListener;

    EventDispatcher                        origEventDispatcher        = null;

    /**
     * Don't allow the Node to handle a key event if it is in externalFocus
     * mode. the only keyboard actions allowed are the navigation keys......
     */
    final EventDispatcher                  preemptiveEventDispatcher;

    final EventDispatcher                  tlfEventDispatcher;

    Node                                   tlNode                     = null;

    PopupControl                           tlPopup                    = null;

    private boolean                        externalFocus              = true;

    private final EventHandler<KeyEvent>   keyEventListener           = e -> {
                                                                          postDispatchTidyup(e);
                                                                      };

    private final EventHandler<MouseEvent> mouseEventListener         = e -> {
                                                                          setExternalFocus(false);
                                                                      };

    {
        focusListener = (observable, oldVal, newVal) -> {
            if (newVal && tlPopup != null) {
                setExternalFocus(false);
            } else {
                setExternalFocus(true);
            }
        };
        preemptiveEventDispatcher = (event, tail) -> {

            // block the event from being passed down to children
            if (event instanceof KeyEvent
                && event.getEventType() == KeyEvent.KEY_PRESSED) {
                if (!((KeyEvent) event).isMetaDown()
                    && !((KeyEvent) event).isControlDown()
                    && !((KeyEvent) event).isAltDown()) {
                    if (isExternalFocus()) {
                        //
                        // don't let the behaviour leak any navigation keys when
                        // we're not in blocking mode....
                        //
                        Object obj = event.getTarget();

                        Node node = (Node) obj;
                        switch (((KeyEvent) event).getCode()) {
                            case TAB:
                                if (((KeyEvent) event).isShiftDown()) {
                                    node.impl_traverse(com.sun.javafx.scene.traversal.Direction.PREVIOUS);
                                } else {
                                    node.impl_traverse(com.sun.javafx.scene.traversal.Direction.NEXT);
                                }
                                event.consume();
                                break;
                            case UP:
                                node.impl_traverse(com.sun.javafx.scene.traversal.Direction.UP);
                                event.consume();
                                break;
                            case DOWN:
                                node.impl_traverse(com.sun.javafx.scene.traversal.Direction.DOWN);
                                event.consume();
                                break;
                            case LEFT:
                                node.impl_traverse(com.sun.javafx.scene.traversal.Direction.LEFT);
                                event.consume();
                                break;
                            case RIGHT:
                                node.impl_traverse(com.sun.javafx.scene.traversal.Direction.RIGHT);
                                event.consume();
                                break;
                            case ENTER:
                                setExternalFocus(false);
                                event.consume();
                                break;
                            default:
                                // this'll kill mnemonics.... unless!
                                Scene s = tlNode.getScene();
                                Event.fireEvent(s, event);
                                event.consume();
                                break;
                        }
                    }
                }
            }

            return event;
        };
        tlfEventDispatcher = (event, tail) -> {

            if ((event instanceof KeyEvent)) {
                if (isExternalFocus()) {
                    tail = tail.prepend(preemptiveEventDispatcher);
                    return tail.dispatchEvent(event);
                }
            }
            return origEventDispatcher.dispatchEvent(event, tail);
        };
    }

    public TwoLevelFocusBehavior() {
    }

    public TwoLevelFocusBehavior(Node node) {
        tlNode = node;
        tlPopup = null;

        tlNode.addEventHandler(KeyEvent.ANY, keyEventListener);
        tlNode.addEventHandler(MouseEvent.MOUSE_PRESSED, mouseEventListener);
        tlNode.focusedProperty()
              .addListener(focusListener);

        // block ScrollEvent from being passed down to scrollbar's skin
        origEventDispatcher = tlNode.getEventDispatcher();
        tlNode.setEventDispatcher(tlfEventDispatcher);
    }

    /**
     * Invoked by the behavior when it is disposed, so that any listeners
     * installed by the TwoLevelFocusBehavior can also be uninstalled
     */
    public void dispose() {
        tlNode.removeEventHandler(KeyEvent.ANY, keyEventListener);
        tlNode.removeEventHandler(MouseEvent.MOUSE_PRESSED, mouseEventListener);
        tlNode.focusedProperty()
              .removeListener(focusListener);
        tlNode.setEventDispatcher(origEventDispatcher);
    }

    public boolean isExternalFocus() {
        return externalFocus;
    }

    public void setExternalFocus(boolean value) {
        externalFocus = value;

        if (tlNode != null && tlNode instanceof Control) {
            tlNode.pseudoClassStateChanged(INTERNAL_PSEUDOCLASS_STATE, !value);
            tlNode.pseudoClassStateChanged(EXTERNAL_PSEUDOCLASS_STATE, value);
        } else if (tlPopup != null) {
            tlPopup.pseudoClassStateChanged(INTERNAL_PSEUDOCLASS_STATE, !value);
            tlPopup.pseudoClassStateChanged(EXTERNAL_PSEUDOCLASS_STATE, value);
        }
    }

    private Event postDispatchTidyup(Event event) {
        // block the event from being passed down to children
        if (event instanceof KeyEvent
            && event.getEventType() == KeyEvent.KEY_PRESSED) {
            if (!isExternalFocus()) {
                //
                // don't let the behaviour leak any navigation keys when
                // we're not in blocking mode....
                //
                if (!((KeyEvent) event).isMetaDown()
                    && !((KeyEvent) event).isControlDown()
                    && !((KeyEvent) event).isAltDown()) {

                    switch (((KeyEvent) event).getCode()) {
                        case TAB:
                        case UP:
                        case DOWN:
                        case LEFT:
                        case RIGHT:
                            event.consume();
                            break;
                        case ENTER:
                            setExternalFocus(true);
                            event.consume();
                            break;
                        default:
                            break;
                    }
                }
            }
        }
        return event;
    }
}
