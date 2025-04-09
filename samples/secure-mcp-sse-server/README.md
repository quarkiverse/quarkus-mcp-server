# Secure MCP SSE Server

This demo shows how a user can login to Keycloak and use the access token to access a secure MCP SSE server and tools.

## Start Quarkus in the dev mode

The application can be packaged and installed using:

```shell script
./mvnw quarkus:dev
```

## Access SSE endpoint without the access token

```shell script
curl -v localhost:8080/mcp/sse
```

You will get HTTP 401 error.

### Login and copy the access token

Use https://quarkus.io/guides/security-openid-connect-dev-services#dev-services-for-keycloak[OIDC DevUI] to login to Keycloak as a user `alice` with a password `alice` and copy an acquired access token.

### Access SSE endpoint with the access token

```shell script
curl -v -H "Authorization: Bearer ey..." localhost:8080/mcp/sse
```

and get an SSE response such as:

```shell script
< content-type: text/event-stream
< 
event: endpoint
data: /messages/ZTZjZDE5MzItZDE1ZC00NzBjLTk0ZmYtYThiYTgwNzI1MGJ
```

Now open another window and use the same access token to access the tool, using the value of the `data` property,
for example:

```shell script
url -v -H "Authorization: Bearer ey..." -H "Content-Type: application/json" --data @call.json http://localhost:8080/mcp/messages/ZTZjZDE5MzItZDE1ZC00NzBjLTk0ZmYtYThiYTgwNzI1MGJ
```

where a `call.json` looks like this:

```json
{
  "jsonrpc": "2.0",
  "id": 2,
  "method": "tools/call",
  "params": {
    "name": "user-name-provider",
    "arguments": {
    }
  }
}
```

Now look in the first curl window and see a response such as:

```shell script
event: message
data: {"jsonrpc":"2.0","id":2,"result":{"isError":false,"content":[{"text":"alice","type":"text"}]}}
```
