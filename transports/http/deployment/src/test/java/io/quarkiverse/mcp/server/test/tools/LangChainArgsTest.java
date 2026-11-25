package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusExtensionTest;
import io.vertx.core.json.JsonObject;

public class LangChainArgsTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusExtensionTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testArgs() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(page -> {
                    JsonObject schema = page.findByName("alpha").inputSchema();
                    JsonObject properties = schema.getJsonObject("properties");
                    assertEquals(4, properties.size());

                    JsonObject name = properties.getJsonObject("name");
                    assertNotNull(name);
                    assertEquals("The name", name.getString("description"));

                    JsonObject nickname = properties.getJsonObject("nick");
                    assertNotNull(nickname);
                    assertEquals("The nickname", nickname.getString("description"));

                    JsonObject count = properties.getJsonObject("count");
                    assertNotNull(count);
                    assertEquals(2, count.getInteger("default"));

                    JsonObject unit = properties.getJsonObject("unit");
                    assertNotNull(unit);
                    assertEquals("HOURS", unit.getString("default"));

                    // only "name" is required: "nick" has required = false, the others have a default value
                    assertEquals(1, schema.getJsonArray("required").size());
                    assertEquals("name", schema.getJsonArray("required").getString(0));
                })
                .toolsCall("alpha", Map.of("name", "foo"), r -> {
                    assertFalse(r.isError());
                    assertEquals("foo::null::2::HOURS", r.firstContent().asText().text());
                })
                .thenAssertResults();
    }

    public static class MyTools {

        @Tool
        String alpha(@P("The name") String name,
                @P(name = "nick", description = "The nickname", required = false) String nickname,
                @P(defaultValue = "2") int count,
                @P(defaultValue = "hours") TimeUnit unit) {
            return name + "::" + nickname + "::" + count + "::" + unit;
        }

    }

}
