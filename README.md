# Kramer
Automatic layout of structured hierarchical data

### Building
Kramer requires [maven 3.3+](https://maven.apache.org), Java 1.8+.  To build Kramer, cd to the top level directory:

    mvn clean install

### License
Kramer is licensed under Apache 2.0 license

## What it does
Kramer is based on the system described in the paper [Automatic Layout of Structured Hierarchical Reports](http://people.csail.mit.edu/ebakke/research/reportlayout_infovis2013.pdf).  Kramer uses a schema to automatically layout structured JSON.  The layout is adaptive between outline and nested table views, providing multicolumn hybrids that are dense and highly usable.

Kramer uses JSON, via the excellent [Jackson library](https://github.com/FasterXML/jackson).  Combined with the schema that describes the JSON data, Kramer provides a simple JavaFX control that exposes the automatic layout.

For more information, see the [Kramer Wiki](https://github.com/ChiralBehaviors/Kramer/wiki)