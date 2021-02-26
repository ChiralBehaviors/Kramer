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

package com.chiralbehaviors.layout.explorer;

import java.io.IOException;

import com.chiralbehaviors.utils.Utils;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * A class to allow me to explore the autolayout. I hate UIs.
 * 
 * @author hhildebrand
 *
 */
public class AutoLayoutExplorer extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    public void initRootLayout(Stage primaryStage) throws IOException {
        QueryState queryState = new QueryState();
        queryState.setTargetURL("http://localhost:5000/api/workspace/pNi_Y_WJVqO-vMMjaicSNw");
        queryState.setQuery(Utils.getDocument(getClass().getResourceAsStream("/testQuery.gql")));
        queryState.setSelection("workspaces");
        AutoLayoutController controller = new AutoLayoutController(queryState);
        primaryStage.setScene(new Scene(controller.getRoot(), 800, 800));
        primaryStage.show();
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        initRootLayout(primaryStage);
    }

}
