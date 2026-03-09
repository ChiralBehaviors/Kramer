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

import static com.chiralbehaviors.layout.cell.control.SelectionEvent.DOUBLE_SELECT;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.StringTokenizer;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chiralbehaviors.layout.AutoLayout;
import com.chiralbehaviors.layout.cell.LayoutCell;
import com.chiralbehaviors.layout.flowless.VirtualFlow;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.style.Style;
import com.chiralbehaviors.layout.style.Style.LayoutObserver;
import com.chiralbehaviors.layout.toy.Page.Route;
import com.chiralbehaviors.utils.Utils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import javafx.application.Application;
import javafx.concurrent.Task;
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
public class SinglePageApp extends Application implements LayoutObserver {
    private static final Logger log = LoggerFactory.getLogger(SinglePageApp.class);

    public static void main(String[] args) {
        launch(args);
    }

    private AnchorPane                  anchor;
    private GraphqlApplication          application;
    private final Deque<PageContext>    back    = new ArrayDeque<>();
    private Button                      backButton;
    private WebTarget                   endpoint;
    private Client                      httpClient;
    private final Deque<PageContext>    forward = new ArrayDeque<>();
    private Button                      forwardButton;
    private AutoLayout               layout;
    private Stage                    primaryStage;
    private Button                   reloadButton;

    @Override
    public <T extends LayoutCell<?>> void apply(VirtualFlow<T> list,
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
            push(extract(route, item));
        }));
    }

    public void initRootLayout(Stage ps) throws IOException, URISyntaxException {
        primaryStage = ps;
        anchor = new AnchorPane();
        VBox vbox = new VBox(locationBar(), anchor);
        primaryStage.setScene(new Scene(vbox, 800, 600));
        Map<String, String> parameters = getParameters().getNamed();
        String app = parameters.get("app");
        URL url = Utils.resolveResourceURL(getClass(), app);
        if (url == null) {
            throw new IllegalArgumentException(String.format("App resource not found: %s",
                                                             app));
        }
        application = new ObjectMapper(new YAMLFactory()).readValue(url.openStream(),
                                                                    GraphqlApplication.class);
        httpClient = ClientBuilder.newClient();
        endpoint = httpClient.target(application.getEndpoint().toURI());
        Page root = application.getRoot();
        Objects.requireNonNull(root,
                               "No root page found; check that the 'root' key in the YAML matches a defined route");
        push(new PageContext(root));
        primaryStage.show();
    }

    @Override
    public void start(Stage primaryStage) throws IOException, URISyntaxException {
        initRootLayout(primaryStage);
    }

    @Override
    public void stop() throws Exception {
        if (httpClient != null) {
            httpClient.close();
        }
    }

    private JsonNode traversePath(JsonNode node, String path) {
        StringTokenizer tokens = new StringTokenizer(path, "/");
        while (tokens.hasMoreTokens()) {
            node = node.get(tokens.nextToken());
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

        backButton.setDisable(true);
        forwardButton.setDisable(true);
        reloadButton.setDisable(true);

        Task<JsonNode> loadTask = new Task<>() {
            @Override
            protected JsonNode call() throws Exception {
                return pageContext.evaluate(endpoint);
            }
        };

        loadTask.setOnSucceeded(event -> {
            JsonNode data = loadTask.getValue();
            AutoLayout newLayout = new AutoLayout(pageContext.getRoot(), new Style(SinglePageApp.this));
            newLayout.updateItem(data);
            newLayout.measure(data);
            AnchorPane.setTopAnchor(newLayout, 0.0);
            AnchorPane.setLeftAnchor(newLayout, 0.0);
            AnchorPane.setBottomAnchor(newLayout, 0.0);
            AnchorPane.setRightAnchor(newLayout, 0.0);
            anchor.getChildren().clear();
            anchor.getChildren().add(newLayout);
            layout = newLayout;
            updateLocationBar();
        });

        loadTask.setOnFailed(event -> {
            log.error("Failed to load page", loadTask.getException());
            updateLocationBar();
        });

        Thread thread = new Thread(loadTask);
        thread.setDaemon(true);
        thread.start();
    }

    private PageContext extract(Route route, JsonNode item) {
        Map<String, Object> variables = new HashMap<>();
        route.getExtract()
             .entrySet()
             .stream()
             .forEach(entry -> {
                 variables.put(entry.getKey(), traversePath(item, entry.getValue()));
             });

        Page target = application.route(route.getPath());
        return new PageContext(target, variables);
    }

    private void forward() {
        back.push(forward.pop());
        displayCurrentPage();
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

    private void push(PageContext pageContext) {
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
