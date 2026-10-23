package io.quarkiverse.mcp.server.schema.validator.test.initialize;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.JsonRpcErrorCodes;
import io.quarkiverse.mcp.server.McpMethod;
import io.quarkiverse.mcp.server.McpProtocolVersion;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.schema.validator.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.restassured.specification.RequestSpecification;
import io.vertx.core.json.JsonObject;

/**
 * The {@code initialize} request must be schema-validated too, even though the connection is not initialized
 * yet and the negotiated protocol version is therefore not available. The version is derived from the request
 * itself - see {@link io.quarkiverse.mcp.server.schema.validator.JsonSchemaValidator#validate}.
 */
public class InitializeSchemaValidationTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testInitializeSchemaValidation() {
        // A valid protocolVersion (so the correct schema is loaded) but the required "clientInfo" is missing
        JsonObject invalidInit = new JsonObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", McpMethod.INITIALIZE.jsonRpcName())
                .put("params", new JsonObject()
                        .put("protocolVersion", McpProtocolVersion.V_2025_06_18.version())
                        .put("capabilities", new JsonObject()));

        request()
                .body(invalidInit.encode())
                .post("/mcp")
                .then()
                .body("error.code", equalTo(JsonRpcErrorCodes.INVALID_REQUEST))
                .body("error.message", startsWith("Schema validation failed"));
    }

    private static RequestSpecification request() {
        return given()
                .contentType("application/json")
                .accept("application/json, text/event-stream")
                .header("Mcp-Method", McpMethod.INITIALIZE.jsonRpcName());
    }

    public static class MyTools {

        @Tool
        String bravo(int price) {
            return "foo" + price;
        }

    }

}
