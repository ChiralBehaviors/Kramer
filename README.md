# Kramer Umbrella application
Automatic layout of structured hierarchical data, graphQL generic single page applications, and a floor wax.

### Building
Kramer requires [maven 3.3+](https://maven.apache.org), Java 1.8+.  To build Kramer, cd to the top level directory:

    mvn clean install
    
Individual modules may be built independently.

### License
Kramer is licensed under [Apache 2.0 license](LICENSE)

## What it does
Kramer is based on the system described in the paper [Automatic Layout of Structured Hierarchical Reports](http://people.csail.mit.edu/ebakke/research/reportlayout_infovis2013.pdf).  Kramer uses a schema to automatically layout structured JSON.  The layout is adaptive between outline and nested table views, providing multicolumn hybrids that are dense and highly usable.

Kramer uses JSON, via the excellent [Jackson library](https://github.com/FasterXML/jackson).  Combined with the schema that describes the JSON data, Kramer provides a simple JavaFX control that exposes the automatic layout.

For more information, see the [Kramer Wiki](https://github.com/ChiralBehaviors/Kramer/wiki)

## Using Kramer

From maven, you'll need to add the Chiral Behaviors repository:
    
	<repository>
		<id>chiralbehaviors-snapshots</id>
		<url>http://repository-chiralbehaviors.forge.cloudbees.com/snapshot/</url>
		<snapshots>
			<enabled>true</enabled>
		</snapshots>
	</repository>

For [Kramer core](kramer/README.md):

    
	<dependency>
		<groupId>com.chiralbehaviors.layout</groupId>
		<artifactId>kramer</artifactId>
		<version>0.0.1-SNAPSHOT</version>
	</dependency>

For [Kramer QL](kramer-ql/README.md):

	<dependency>
		<groupId>com.chiralbehaviors.layout</groupId>
		<artifactId>kramer-ql</artifactId>
		<version>0.0.1-SNAPSHOT</version>
	</dependency>

For [Kramer AutoLayoutExplorer](explorer/README.md):

	<dependency>
		<groupId>com.chiralbehaviors.layout</groupId>
		<artifactId>explorer</artifactId>
		<version>0.0.1-SNAPSHOT</version>
	</dependency>

For [Kramer Toy Single Page UI Application Framework](toy-app/README.md):

	<dependency>
		<groupId>com.chiralbehaviors.layout</groupId>
		<artifactId>toy-app</artifactId>
		<version>0.0.1-SNAPSHOT</version>
	</dependency>