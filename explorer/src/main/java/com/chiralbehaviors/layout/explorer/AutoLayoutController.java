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
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chiralbehaviors.layout.AutoLayout;
import com.chiralbehaviors.layout.SchemaPath;
import com.chiralbehaviors.layout.graphql.GraphQlUtil;
import com.chiralbehaviors.layout.query.ColumnSortHandler;
import com.chiralbehaviors.layout.query.InteractionHandler;
import com.chiralbehaviors.layout.query.InteractionMenuFactory;
import com.chiralbehaviors.layout.query.LayoutQueryState;
import com.chiralbehaviors.layout.schema.Primitive;
import com.chiralbehaviors.layout.schema.Relation;
import com.chiralbehaviors.layout.schema.SchemaNode;
import com.chiralbehaviors.layout.style.Style;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import javafx.application.Platform;
import javafx.concurrent.Worker.State;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;

public class AutoLayoutController {
    public class ActiveState extends QueryState {
        private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "graphql-fetch");
            t.setDaemon(true);
            return t;
        });

        public ActiveState() {
            super();
        }

        public void fetchAsync(String query) {
            executor.submit(() -> {
                String result;
                try {
                    result = GraphQlUtil.evaluate(endpoint, query);
                } catch (Exception e) {
                    log.error("GraphQL fetch failed", e);
                    result = "{}";
                }
                final String finalResult = result;
                Platform.runLater(() -> {
                    setData(finalResult);
                    JSObject window = (JSObject) webEngine.executeScript("window");
                    window.call("onFetchComplete", finalResult);
                });
            });
        }

        @Override
        public void setData(String data) {
            super.setData(data);
            setQueryState(new QueryState(this));
        }

        @Override
        public void setTargetURL(String targetURL) {
            String previous = getTargetURL();
            super.setTargetURL(targetURL);
            if (targetURL != null && !targetURL.equals(previous)) {
                URI uri;
                try {
                    uri = new URI(targetURL);
                } catch (URISyntaxException e) {
                    log.error("Invalid target URL: {}", targetURL, e);
                    return;
                }
                endpoint = ClientBuilder.newClient()
                                        .target(uri);
            }
        }
    }

    private static final String DATA        = "data";
    private static final String ERRORS      = "errors";
    private static final Logger log         = LoggerFactory.getLogger(AutoLayoutController.class);

    private final ActiveState   activeQuery = new ActiveState();
    @FXML
    private AnchorPane          anchor;
    private WebTarget           endpoint;
    private AutoLayout          layout;
    private LayoutQueryState    layoutQueryState;
    private InteractionHandler  interactionHandler;
    private InteractionMenuFactory menuFactory;
    private WebEngine           webEngine;
    @FXML
    private ToggleGroup         page;
    private QueryState          queryState;
    @FXML
    private BorderPane          root;
    private SchemaView          schemaView;
    @FXML
    private RadioButton         showLayout;
    @FXML
    private RadioButton         showQuery;

    @FXML
    private RadioButton         showSchema;

    public AutoLayoutController(QueryState queryState) throws IOException {
        initialize();
        this.activeQuery.initializeFrom(queryState);
        this.queryState = queryState;
        load();
        Node graphiql = constructGraphiql();
        anchor.getChildren()
              .add(graphiql);
        showQuery.setSelected(true);
        page.selectedToggleProperty()
            .addListener((o, p, c) -> {
                try {
                    RadioButton prev = (RadioButton) p;
                    RadioButton current = (RadioButton) c;

                    if (prev == showSchema) {
                        layout.autoLayout();
                    }

                    if (current == showLayout) {
                        anchor.getChildren()
                              .setAll(layout);
                    } else if (current == showSchema) {
                        anchor.getChildren()
                              .setAll(schemaView);
                    } else if (current == showQuery) {
                        anchor.getChildren()
                              .setAll(graphiql);
                    } else {
                        throw new IllegalStateException(String.format("Invalid radio button: %s",
                                                                      current));
                    }
                } catch (Exception e) {
                    log.error("exception processing toggle", e);
                }
            });
    }

    public AutoLayout getLayout() {
        return layout;
    }

    public Parent getRoot() {
        return root;
    }

    public void setQueryState(QueryState state) {
        String previousDataString = queryState.getData();
        if (state == null) {
            state = new QueryState();
        }
        if (queryState.equals(state)) {
            return;
        }
        queryState = state;
        if (Objects.equals(previousDataString, state.getData())) {
            return;
        }
        JsonNode data;
        try {
            data = new ObjectMapper().readTree(queryState.getData());
        } catch (IOException e) {
            log.warn("Cannot deserialize json data {}", queryState.getData());
            data = JsonNodeFactory.instance.arrayNode();
        }

        JsonNode errors = data.get(ERRORS);
        if ((errors != null && errors.size() != 0) || !data.has(DATA)
            || !data.get(DATA)
                    .has(queryState.getSelection())) {
            queryState.setData(null);
            data = JsonNodeFactory.instance.arrayNode();
        } else {
            data = data.get(DATA)
                       .get(queryState.getSelection());
        }
        setData(SchemaNode.asArray(data));
    }

    private Node constructGraphiql() throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("graphiql.fxml"));
        Node graphiql = loader.load();
        AnchorPane.setTopAnchor(graphiql, 0.0);
        AnchorPane.setLeftAnchor(graphiql, 0.0);
        AnchorPane.setBottomAnchor(graphiql, 0.0);
        AnchorPane.setRightAnchor(graphiql, 0.0);
        GraphiqlController controller = loader.getController();
        WebEngine engine = controller.webview.getEngine();
        webEngine = engine;
        engine.getLoadWorker()
              .stateProperty()
              .addListener((o, oldState, newState) -> {
                  if (newState == State.SUCCEEDED) {
                      JSObject jsobj = (JSObject) engine.executeScript("window");
                      jsobj.call("setApp", activeQuery);
                  }
              });
        initialize(engine);
        controller.url.setText(queryState.getTargetURL());
        controller.url.textProperty()
                      .addListener((o, p, c) -> {
                          if (c != null) {
                              activeQuery.setTargetURL(c);
                              JSObject jsobj = (JSObject) engine.executeScript("window");
                              jsobj.setMember("app", activeQuery);
                              engine.reload();
                          }
                      });

        controller.selection.setText(queryState.getSelection());
        controller.selection.textProperty()
                            .addListener((o, p, c) -> {
                                if (c != null) {
                                    activeQuery.setSelection(c);
                                }
                            });

        return graphiql;
    }

    private void initialize() {
        // Wire LayoutQueryState into AutoLayout's Style for interaction support
        Style style = new Style();
        layoutQueryState = new LayoutQueryState(style);
        style.setStylesheet(layoutQueryState);
        layout = new AutoLayout(null, style);
        layoutQueryState.addChangeListener(() -> layout.autoLayout());

        // Interaction handler and context menu factory
        interactionHandler = new InteractionHandler(layoutQueryState);
        menuFactory = new InteractionMenuFactory(interactionHandler, layoutQueryState);

        // Single context menu handler on the AutoLayout root — avoids
        // VirtualFlow cell recycling issues (audit finding Blocker 2)
        layout.addEventHandler(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            SchemaPath hitPath = layout.hitSchemaPath(event.getX(), event.getY());
            if (hitPath != null) {
                var contextMenu = isRelationPath(hitPath)
                    ? menuFactory.buildRelationMenu(hitPath)
                    : menuFactory.buildPrimitiveMenu(hitPath);
                contextMenu.show(layout, event.getScreenX(), event.getScreenY());
                event.consume();
            }
        });

        AnchorPane.setTopAnchor(layout, 0.0);
        AnchorPane.setLeftAnchor(layout, 0.0);
        AnchorPane.setBottomAnchor(layout, 0.0);
        AnchorPane.setRightAnchor(layout, 0.0);

        schemaView = new SchemaView();
        AnchorPane.setTopAnchor(schemaView, 0.0);
        AnchorPane.setLeftAnchor(schemaView, 0.0);
        AnchorPane.setBottomAnchor(schemaView, 0.0);
        AnchorPane.setRightAnchor(schemaView, 0.0);
    }

    private boolean isRelationPath(SchemaPath path) {
        SchemaNode root = layout.getRoot();
        if (root == null) return false;
        // Walk path segments to find the target schema node
        SchemaNode current = root;
        for (int i = 1; i < path.segments().size(); i++) {
            if (current instanceof Relation r) {
                SchemaNode child = r.getChild(path.segments().get(i));
                if (child == null) return false;
                current = child;
            } else {
                return false;
            }
        }
        return current instanceof Relation;
    }

    private void initialize(WebEngine engine) {
        engine.load(getClass().getResource("ide.html")
                              .toExternalForm());
    }

    private void load() throws IOException {
        FXMLLoader loader = new FXMLLoader();
        loader.setController(this);
        loader.setLocation(getClass().getResource("autolayout.fxml"));
        loader.load();
    }

    private void setData(ArrayNode data) {
        assert data != null;
        if (queryState.getQuery() == null) {
            return;
        }
        Relation schema = (Relation) GraphQlUtil.buildSchema(queryState.getQuery(),
                                                             queryState.getSelection());
        schemaView.setRoot(schema);
        layout.setRoot(schema);
        layout.measure(data); 
        layout.updateItem(data);
    }
}
