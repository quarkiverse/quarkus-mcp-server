package io.quarkiverse.mcp.server.test.streamablehttp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.RestAssured;

public class UnsupportedSseTest extends StreamableHttpTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testFailure() {
        RestAssured.given()
                .when()
                .get(messageEndpoint)
                .then()
                .statusCode(405);
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "" + price * 42;
        }
    }

}
