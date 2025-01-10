# Weather Model Context Protocol Server

This project uses Quarkus, the Supersonic Subatomic Java Framework.

If you want to learn more about Quarkus, please visit its website: <https://quarkus.io/>.

## Packaging and running the application

The application can be packaged and installed using:

```shell script
./mvnw install
```

This builds an uber jar and you can run it directly using `jbang org.acme:weather:1.0.0-SNAPSHOT:runner`

To use in a MCP host client, you can use the following command:

```shell script
jbang --quiet org.acme:weather:1.0.0-SNAPSHOT:runner 
```

The `--quiet` flag is used to suppress any output of the jbang script.