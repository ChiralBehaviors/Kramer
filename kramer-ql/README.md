# Kramer QL
___

GraphQL integration for Kramer
___

This is a simple integration of GraphQL into the Kramer auto layout framework.  This is basically a utility classs for generating the _Relation_ structure corresponding to a GraphQL query.  I also included a _JAXRS_ invocation of a GraphQL endpoint.  I use this in the _AutoLayout Explorer_ and the _Toy Application_ single page UI framework.

There's obviously a lot more to do here, as I could use the type information available for the primitives.  I plan to extend Kramer to beyond text nodes and that should hopefully be more straightforward.  But I'll have to provide more of a useful metadata representation of the GraphQL schema to do some of that.  Projects for another day, obviously
