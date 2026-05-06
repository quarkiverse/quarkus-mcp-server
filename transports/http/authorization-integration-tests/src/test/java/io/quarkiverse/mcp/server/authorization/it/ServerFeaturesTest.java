package io.quarkiverse.mcp.server.authorization.it;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.awaitility.Awaitility;
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
                    assertEquals(2, p.size());
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

    @Test
    void testToolExpiredToken() throws Exception {
        String accessToken = getAccessToken();

        Awaitility.await().atMost(6, java.util.concurrent.TimeUnit.SECONDS)
                .pollInterval(1, java.util.concurrent.TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    McpAssured.newStreamableClient()
                            .setAdditionalHeaders(msg -> MultiMap.caseInsensitiveMultiMap()
                                    .add("Authorization", "Bearer " + accessToken))
                            .setExpectConnectFailure(r -> {
                                assertEquals(401, r.statusCode());
                            })
                            .build()
                            .connect();
                });
    }

    @Test
    void testToolInvalidSignature() throws Exception {
        String accessToken = getAccessToken();
        String invalidToken = accessToken.substring(0, accessToken.lastIndexOf('.')) + ".invalid-signature";

        McpAssured.newStreamableClient()
                .setAdditionalHeaders(msg -> MultiMap.caseInsensitiveMultiMap()
                        .add("Authorization", "Bearer " + invalidToken))
                .setExpectConnectFailure(r -> {
                    assertEquals(401, r.statusCode());
                })
                .build()
                .connect();
    }

    @Test
    void testToolPhoneScopeAfterConnect() throws Exception {
        String accessToken = getAccessToken("http://localhost:8081/access-token-phone-scope");

        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setAdditionalHeaders(msg -> MultiMap.caseInsensitiveMultiMap()
                        .add("Authorization", "Bearer " + accessToken))
                .build()
                .connect();
        client.when()
                .toolsCall("phone-scope-provider", Map.of(), r -> {
                    assertEquals("phone-data:alice", r.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    @Test
    void testToolUnsupportedScopeAfterConnect() throws Exception {
        String accessToken = getAccessToken();

        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setAdditionalHeaders(msg -> MultiMap.caseInsensitiveMultiMap()
                        .add("Authorization", "Bearer " + accessToken))
                .build()
                .connect();
        client.when()
                .toolsCall("phone-scope-provider")
                .withErrorAssert(error -> {
                    assertTrue(error.message().contains("ForbiddenException"));
                })
                .send()
                .thenAssertResults();
    }

    @Test
    void testBetaToolWithPhoneScope() throws Exception {
        String accessToken = getAccessToken("http://localhost:8081/access-token-phone-scope");

        McpStreamableTestClient client = McpAssured.newStreamableClient()
                .setMcpPath("/beta/mcp")
                .setAdditionalHeaders(msg -> MultiMap.caseInsensitiveMultiMap()
                        .add("Authorization", "Bearer " + accessToken))
                .build()
                .connect();
        client.when()
                .toolsCall("beta-user-name-provider", Map.of(), r -> {
                    assertEquals("alice", r.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    @Test
    void testBetaToolWithoutPhoneScope() throws Exception {
        String accessToken = getAccessToken();

        McpAssured.newStreamableClient()
                .setMcpPath("/beta/mcp")
                .setAdditionalHeaders(msg -> MultiMap.caseInsensitiveMultiMap()
                        .add("Authorization", "Bearer " + accessToken))
                .setExpectConnectFailure(r -> {
                    assertEquals(403, r.statusCode());
                })
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
