# Toy Application

A toy single page UI application framework
___

This is a toy single page UI framework that uses GraphQL and the Kramer AutoLayout framework to provide a declaritive way to build applications against a GraphQL endpoint.  To run:

    java -jar target/toy-app-0.0.1-SNAPSHOT-phat.jar --app=src/test/resources/testApp.yml
    
The above example assumes you're running this from the current directory.

Because it's a toy, I'm not keen on providing a lot of documentation.  There's an example in the src/test/resources directory, _testApp.yml_.  Hopefully, it should be obvious that the system is basically a network of GraphQL queries.  We provide simple XPath like expressions to retrieve data and pass that into parameterized queries of the target page.  Provides a history of pages and back/forward/refresh buttons.

This framework is kind of interesting in that you're not navigating a hierarchical URL space.  Rather, it's just a network of pages, linked through double click actions.  Currently just navigation is provided - double click on a given node in the view and it will replace the page with the target.  I'll add CRUD operations and such eventually.

Anyways, I've had this idea for a while and it's been fun to develop it.  I'm sure that someone with greater than brain one in matters UI and UIX could do a far better job.  Lord knows my CSS skills are hillariously primitive.  I'll probably really have to wait for a port to a JavaScript framework to make this all work out as slick as I imagine.  Right now JavaFX has no way to take advantage of the browser in any real sense (other than running embedded, which is lame).  So that's going to limit things until that happens.  Still, it's quite easy to build stuff with and provides a nice test bed for ideas that I have been working on wrt GraphQL endpoints in general.

