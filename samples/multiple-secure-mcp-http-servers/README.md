# Multiple Secure MCP HTTP Servers

This demo shows how [https://github.com/modelcontextprotocol/inspector](MCP Inspector) can use OAuth2 to access multiple Quarkus MCP HTTP servers, with each MCP HTTP server endpoint secured by a unique Keycloak realm configuration.

## Start application in the dev mode

Start the application in the dev mode:

```shell script
./mvnw quarkus:dev
```

It also starts a [Keycloak Dev Services container](https://quarkus.io/guides/security-openid-connect-dev-services#dev-services-for-keycloak) and uploads two Keycloak realm definitions, `alpha` and `bravo` realms.

## Launch MCP Inspector:

Launch MCP Inspector:

```shell script
npx @modelcontextprotocol/inspector
```

### Use OAuth2 Flow to connect to Quarkus MCP Alpha Streamable HTTP Server

Choose `Streamable HTTP` Transport Type and set an `http://localhost:8080/mcp` URL address.

Select the Authentication OAuth 2.0 Flow, set the `alpha-client` Client ID, and Scope to `openid quarkus-mcp-alpha`.

Press `Connect`, you will be redirected to the Keycloak `Alpha` realm authentication form, use `alice` as a user name and password to login.

Select and run the `alpha-user-name-provider` tool, and you should get `alice` returned.

### Use OAuth2 Flow to connect to Quarkus MCP Bravo Streamable HTTP Server

Choose `Streamable HTTP` Transport Type and set an `http://localhost:8080/bravo/mcp` URL address.

Select the Authentication OAuth 2.0 Flow, set the `bravo-client` Client ID, and Scope to `openid quarkus-mcp-bravo`.

Press `Connect`, you will be redirected to the Keycloak `Bravo` realm authentication form, use `jdoe` as a user name and password to login.

Select and run the `bravo-user-name-provider` tool, and you should get `bravo` returned.

