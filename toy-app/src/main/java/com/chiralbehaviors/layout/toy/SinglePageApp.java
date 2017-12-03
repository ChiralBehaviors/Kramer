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

package com.chiralbehaviors.layout.toy;

import static com.chiralbehaviors.layout.cell.SelectionEvent.DOUBLE_SELECT;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.StringTokenizer;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;

import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chiralbehaviors.layout.StyleProvider;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.control.AutoLayout;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.graphql.GraphQlUtil.QueryException;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.toy.Page.Route;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.hellblazer.utils.Utils;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * 
 * @author hhildebrand
 *
 */
public class SinglePageApp extends Application
        implements StyleProvider.LayoutModel {
    private static final Logger log = LoggerFactory.getLogger(SinglePageApp.class);

    public static void main(String[] args) {
        launch(args);
    }

    private AnchorPane               anchor;
    private GraphqlApplication       application;
    private final Stack<PageContext> back    = new Stack<>();
    private Button                   backButton;
    private WebTarget                endpoint;
    private final Stack<PageContext> forward = new Stack<>();
    private Button                   forwardButton;
    private AutoLayout               layout;
    private Stage                    primaryStage;
    private Button                   reloadButton;

    @Override
    public <T extends LayoutCell<?>> void apply(VirtualFlow<JsonNode, T> list,
                                                Relation relation) {

        Nodes.addInputMap(list, InputMap.consume(DOUBLE_SELECT, e -> {
            Route route = back.peek()
                              .getRoute(relation);
            if (route == null) {
                return;
            }
            JsonNode item = list.getSelectionModel()
                                .getSelectedItem();
            if (item == null) {
                return;
            }
            try {
                push(extract(route, item));
            } catch (QueryException ex) {
                log.error("Unable to push page: %s", route.getPath(), ex);
            }
        }));
    }

    public void initRootLayout(Stage ps) throws IOException, URISyntaxException,
                                         QueryException {
        primaryStage = ps;
        anchor = new AnchorPane();
        VBox vbox = new VBox(locationBar(), anchor);
        primaryStage.setScene(new Scene(vbox, 800, 600));
        Map<String, String> parameters = getParameters().getNamed();
        application = new ObjectMapper(new YAMLFactory()).readValue(Utils.resolveResource(getClass(),
                                                                                          parameters.get("app")),
                                                                    GraphqlApplication.class);
        endpoint = ClientBuilder.newClient()
                                .target(application.getEndpoint()
                                                   .toURI());
        push(new PageContext(application.getRoot()));
        primaryStage.show();
    }

    @Override
    public void start(Stage primaryStage) throws IOException,
                                          URISyntaxException, QueryException {
        initRootLayout(primaryStage);
    }

    private JsonNode apply(JsonNode node, String path) {
        StringTokenizer tokens = new StringTokenizer(path, "/");
        JsonNode current = node;
        while (tokens.hasMoreTokens()) {
            node = current.get(tokens.nextToken());
        }
        return node;
    }

    private void back() {
        forward.push(back.pop());
        displayCurrentPage();
    }

    private Button button(String imageResource) {
        Button button = new Button();
        Image image = new Image(getClass().getResourceAsStream(imageResource));
        button.graphicProperty()
              .set(new ImageView(image));
        return button;
    }

    private void displayCurrentPage() {
        updateLocationBar();
        PageContext pageContext = back.peek();
        primaryStage.setTitle(pageContext.getPage()
                                         .getTitle());
        anchor.getChildren()
              .clear();
        try {
            layout = layout(pageContext);
        } catch (QueryException e) {
            log.error("Unable to display page", e);
            return;
        }
        anchor.getChildren()
              .add(layout);
    }

    private PageContext extract(Route route, JsonNode item) {
        Map<String, Object> variables = new HashMap<>();
        route.getExtract()
             .entrySet()
             .stream()
             .forEach(entry -> {
                 variables.put(entry.getKey(), apply(item, entry.getValue()));
             });

        Page target = application.route(route.getPath());
        return new PageContext(target, variables);
    }

    private void forward() {
        back.push(forward.pop());
        displayCurrentPage();
    }

    private AutoLayout layout(PageContext pageContext) throws QueryException {
        AutoLayout layout = new AutoLayout(pageContext.getRoot(), this);
        layout.getStylesheets()
              .add(getClass().getResource("/non-nested.css")
                             .toExternalForm());
        JsonNode data = pageContext.evaluate(endpoint);
        layout.updateItem(data);
        layout.measure(data);
        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);
        return layout;
    }

    private HBox locationBar() {
        HBox hbox = new HBox();

        backButton = button("/back.png");
        forwardButton = button("/forward.png");
        reloadButton = button("/reload.png");

        backButton.setOnAction(e -> back());
        forwardButton.setOnAction(e -> forward());
        reloadButton.setOnAction(e -> reload());

        hbox.getChildren()
            .addAll(backButton, forwardButton, reloadButton);

        return hbox;
    }

    private void push(PageContext pageContext) throws QueryException {
        back.push(pageContext);
        forward.clear();
        displayCurrentPage();
    }

    private void reload() {
        displayCurrentPage();
    }

    private void updateLocationBar() {
        backButton.setDisable(back.size() <= 1);
        forwardButton.setDisable(forward.isEmpty());
    }
}
