/**
 * Copyright (c) 2016 Chiral Behaviors, LLC, all rights reserved.
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

package com.chiralbehaviors.layout.schema;

import static com.chiralbehaviors.layout.cell.control.SelectionEvent.DOUBLE_SELECT;

import java.net.URL;

import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;

import com.chiralbehaviors.layout.AutoLayout;
import com.chiralbehaviors.layout.StyleProvider.LayoutModel;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.fasterxml.jackson.databind.JsonNode;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

/**
 * @author halhildebrand
 *
 */
public class Smoke extends Application {
    public static void main(String[] argv) {
        launch(argv);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        LayoutModel model = new LayoutModel() {
            @Override
            public <T extends LayoutCell<?>> void apply(VirtualFlow<T> list,
                                                        Relation relation) {
                Nodes.addInputMap(list,
                                  InputMap.consume(DOUBLE_SELECT,
                                                   e -> System.out.println("event: "
                                                                           + list.getSelectionModel()
                                                                                 .getSelectedItem())));
            }
        };
        AutoLayout layout = new AutoLayout(Util.build(), model);
        URL url = getClass().getResource("/smoke.css");
        if (url != null) {
            layout.getStylesheets()
                  .add(url.toExternalForm());
        }
        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);
        AnchorPane root = new AnchorPane();
        root.getChildren()
            .add(layout);
        primaryStage.setScene(new Scene(root, 800, 800));
        primaryStage.show();

        JsonNode data = Util.testData();
        layout.measure(data);
        layout.updateItem(data);
        layout.autoLayout();
    }

}
