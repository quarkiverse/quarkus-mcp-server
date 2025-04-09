# Secure MCP HTTP Server

This demo shows how a user can login to GitHub and copy the produced access token to use it with `curl` to access a secure MCP HTTP server.

## Register GitHub OAuth2 application

Follow simple steps at https://quarkus.io/guides/security-openid-connect-providers#github to register a GitHub OAuth2 application.
For example, you can call it `McpServer`.
Set the callbacl URL to `http://localhost:8080/login`.

Replace `${github.client.id}` and `${github.client.secret}` in `application.properties` with the generated client id and secret.

## Package and run the application

The application can be packaged and installed using:

```shell script
./mvnw install
```

This builds an uber-jar which you can run directly using `jbang org.acme:secure-http-server:1.0.0-SNAPSHOT:runner`

To utilize in an MCP client (such as Claude Desktop), you can use the following command:

```shell script
jbang --quiet org.acme:secure-http-server:1.0.0-SNAPSHOT:runner 
```

The `--quiet` flag is used to suppress any output of the jbang script.


## Access SSE endpoint without the access token

```shell script
curl -v localhost:8080/sse
```

You will get HTTP 401 error.

### Login and copy the access token

Go to `http://localhost:8080/login`, and use the `alice` name and `alice` password to login.

Copy the returned access token.

### Access SSE endpoint with the access token

```shell script
curl -v -H "Authorization: Bearer gho_..." localhost:8080/sse
```

and get an SSE response such as:

```shell script
< content-type: text/event-stream
< 
event: endpoint
data: /messages/ZTZjZDE5MzItZDE1ZC00NzBjLTk0ZmYtYThiYTgwNzI1MGJ
```
