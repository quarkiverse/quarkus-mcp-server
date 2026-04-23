package io.quarkiverse.mcp.server.authorization.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.htmlunit.TextPage;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@QuarkusTest
class ServerFeaturesTest {

    @Test
    void testTool() throws Exception {
        String accessToken = getAccessToken();

        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setAdditionalHeaders(msg -> MultiMap.caseInsensitiveMultiMap()
                        .add("Authorization", "Bearer " + accessToken))
                .build()
                .connect();
        client.when()
                .toolsList(p -> {
                    assertEquals(1, p.size());
                    assertNotNull(p.findByName("alpha-user-name-provider"));
                })
                .toolsCall("alpha-user-name-provider", Map.of(), r -> {
                    assertEquals("alice", r.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    @Test
    void testToolNoToken() {
        String authServerUrl = given().get("/auth-server-url").then().statusCode(200).extract().asString();

        McpAssured.newStreamableClient()
                .setExpectConnectFailure(r -> {
                    assertEquals(401, r.statusCode());
                    String wwwAuthenticate = r.firstHeader("www-authenticate");
                    assertNotNull(wwwAuthenticate);

                    String resourceMetadataUrl = extractResourceMetadata(wwwAuthenticate);
                    assertNotNull(resourceMetadataUrl);

                    String metadata = given().get(resourceMetadataUrl).then().statusCode(200).extract().asString();
                    JsonObject metadataJson = new JsonObject(metadata);
                    JsonArray authServers = metadataJson.getJsonArray("authorization_servers");
                    assertEquals(1, authServers.size());
                    assertEquals(authServerUrl, authServers.getString(0));
                })
                .build()
                .connect();
    }

    @Test
    void testToolNoAudience() throws Exception {
        String accessToken = getAccessToken("http://localhost:8081/access-token-no-audience");

        McpAssured.newStreamableClient()
                .setAdditionalHeaders(msg -> MultiMap.caseInsensitiveMultiMap()
                        .add("Authorization", "Bearer " + accessToken))
                .setExpectConnectFailure()
                .build()
                .connect();
    }

    private String getAccessToken() throws Exception {
        return getAccessToken("http://localhost:8081/access-token");
    }

    private String getAccessToken(String url) throws Exception {
        try (WebClient webClient = new WebClient()) {
            HtmlPage page = webClient.getPage(url);

            HtmlForm loginForm = page.getForms().get(0);
            loginForm.getInputByName("username").setValueAttribute("alice");
            loginForm.getInputByName("password").setValueAttribute("alice");

            TextPage textPage = loginForm.getButtonByName("login").click();
            return textPage.getContent();
        }
    }

    private static String extractResourceMetadata(String wwwAuthenticate) {
        int idx = wwwAuthenticate.indexOf("resource_metadata=\"");
        if (idx == -1) {
            return null;
        }
        int start = idx + "resource_metadata=\"".length();
        int end = wwwAuthenticate.indexOf('"', start);
        return wwwAuthenticate.substring(start, end);
    }
}
