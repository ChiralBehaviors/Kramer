# AutoLayout Explorer

A simple application for exploring GraphQL endpoints with the AutoLayout framework
___

This has been my test application throughout the development of the Kramer framework.  There's probably a lot more I could have done from a testing side of Kramer, but testing UI frameworks is - frankly - the most stupidest problem in the world.  I certainlhy can't solve it.  And so I needed a quick way to iterate on the _AutoLayoutView_ and this is it.

## Running the AutoLayoutExplorer

The explorer can be run from the shaded jar built by this module by simply running:

    java -jar target/explorer-0.0.1-SNAPSHOT-phat.jar
    
This will bring up the explorer application.  The application has three panes, the [GraphiQL IDE](https://github.com/graphql/graphiql), the Schema and the resulting AutoLayout view.  The three different panes are switched by the buttons.

The GraphiQL IDE is a JavaScript framework for exploring GraphQL endpoints.  This javascript is run in a JavaFX WebView and is a pretty slick bit of kit if I do say so myself.  I found out that I had "same orgin" problems with running the graph QL "fetcher" function with the "fetch" library as recommended. So I actually call back into Java from the JavaScript and use the Kramer QL utility to make the POST to the graphQL endpoint.  Sneaky and works.

Obviously, not a lot of feature work here, as this is really just a test app.  Lots of obvious extensions and polish to be done.  I plan on using the concept in an editor for my toy single page app framework, but that will likely be a scavenging, rather than a refactoring.