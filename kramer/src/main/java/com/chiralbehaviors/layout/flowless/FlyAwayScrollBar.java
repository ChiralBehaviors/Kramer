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

package com.chiralbehaviors.layout.flowless;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.ScrollBar;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

/**
 * @author halhildebrand
 *
 */
@SuppressWarnings("unused")
public class FlyAwayScrollBar extends ScrollBar {
    private static final int     FADE_IN_TIME  = 300;
    private static final int     FADE_OUT_TIME = 1000;
    private static final int     STAY_TIME     = 2000;

    private final FadeTransition fadeIn;

    private final FadeTransition fadeOut;
    private final Timeline       hideTimer;
    private final Timeline       leftTimer     = new Timeline();

    public FlyAwayScrollBar() {
        this(FADE_IN_TIME, FADE_OUT_TIME, STAY_TIME);
    }

    public FlyAwayScrollBar(int fadeInTime, int fadeOutTime, int stayTime) {
        fadeIn = new FadeTransition();
        fadeIn.setNode(this);
        fadeIn.setDuration(new Duration(fadeInTime));
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);

        fadeOut = new FadeTransition();
        fadeOut.setNode(this);
        fadeOut.setDuration(new Duration(fadeOutTime));
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);

        hideTimer = new Timeline();
        hideTimer.getKeyFrames()
                 .add(new KeyFrame(new Duration(stayTime)));
        hideTimer.setOnFinished(e -> fadeOut());

        addEventHandler(MouseEvent.MOUSE_ENTERED, (MouseEvent event) -> {
            setVisible(true, true);
        });
        addEventHandler(MouseEvent.MOUSE_EXITED, (MouseEvent event) -> {
            setVisible(false, false);
        });

    }

    public void foo() {
        setVisible(false);
    }

    public void setVisible(boolean visible, boolean mouseOver) {
        if (visible) {
            fadeIn(mouseOver);
        } else {
            fadeOut();
        }
    }

    private void fadeIn(boolean mouseOver) {
        fadeOut.stop();
        fadeIn.play();
        if (mouseOver) {
            hideTimer.stop();
        } else {
            hideTimer.play();
        }
    }

    private void fadeOut() {
        fadeIn.stop();
        hideTimer.stop();
        fadeOut.play();
    }
}
