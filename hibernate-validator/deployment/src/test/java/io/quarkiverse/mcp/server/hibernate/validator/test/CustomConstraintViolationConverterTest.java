package io.quarkiverse.mcp.server.hibernate.validator.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import jakarta.inject.Singleton;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.Min;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolCallException;
import io.quarkiverse.mcp.server.hibernate.validator.ConstraintViolationConverter;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpSseTestClient;
import io.quarkus.test.QuarkusUnitTest;

public class CustomConstraintViolationConverterTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class, MyConstraintViolationConverter.class));

    @Test
    public void testError() {
        McpSseTestClient client = McpAssured.newConnectedSseClient();
        client.when()
                .toolsCall("bravo")
                .withArguments(Map.of("price", 1))
                .withAssert(toolResponse -> {
                    assertTrue(toolResponse.isError());
                    assertEquals("must be greater than or equal to 5", toolResponse.content().get(0).asText().text());
                })
                .send()
                .thenAssertResults();
    }

    @Singleton
    public static class MyConstraintViolationConverter implements ConstraintViolationConverter {

        @Override
        public Exception convert(ConstraintViolationException exception) {
            return new ToolCallException(exception.getConstraintViolations().iterator().next().getMessage());
        }

    }

    public static class MyTools {

        @Tool
        TextContent bravo(@Min(5) Integer price) {
            throw new IllegalStateException();
        }

    }

}
