package io.quarkiverse.mcp.server.test.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkiverse.mcp.server.test.McpAssured;
import io.quarkiverse.mcp.server.test.McpAssured.McpStreamableTestClient;
import io.quarkiverse.mcp.server.test.McpServerTest;
import io.quarkus.test.QuarkusUnitTest;
import io.vertx.core.json.JsonObject;

public class ToolRecursiveArgumentTest extends McpServerTest {

    @RegisterExtension
    static final QuarkusUnitTest config = defaultConfig()
            .withApplicationRoot(
                    root -> root.addClasses(MyTools.class));

    @Test
    public void testRecursiveArgument() {
        McpStreamableTestClient client = McpAssured.newConnectedStreamableClient();
        client.when()
                .toolsList(page -> {
                    JsonObject schema = page.findByName("analyzeTree").inputSchema();
                    assertNotNull(schema);

                    // $defs must be at the schema root, not nested under properties.root
                    JsonObject defs = schema.getJsonObject("$defs");
                    assertNotNull(defs, "$defs should be at the schema root");

                    JsonObject nodeDef = defs.getJsonObject("Node");
                    assertNotNull(nodeDef, "Node type should be defined in $defs");
                    JsonObject nodeProps = nodeDef.getJsonObject("properties");
                    assertNotNull(nodeProps);
                    assertNotNull(nodeProps.getJsonObject("name"));
                    JsonObject childrenProp = nodeProps.getJsonObject("children");
                    assertNotNull(childrenProp);
                    assertEquals("array", childrenProp.getString("type"));
                    // children items must $ref back to the root-level Node definition
                    JsonObject items = childrenProp.getJsonObject("items");
                    assertNotNull(items);
                    assertEquals("#/$defs/Node", items.getString("$ref"));

                    JsonObject properties = schema.getJsonObject("properties");
                    assertNotNull(properties);

                    JsonObject rootProp = properties.getJsonObject("root");
                    assertNotNull(rootProp);
                    assertEquals("#/$defs/Node", rootProp.getString("$ref"));
                    assertNull(rootProp.getJsonObject("$defs"),
                            "$defs should not be nested under properties.root");

                    JsonObject verboseProp = properties.getJsonObject("verbose");
                    assertNotNull(verboseProp);
                    assertEquals("boolean", verboseProp.getString("type"));
                })
                .toolsCall("analyzeTree",
                        Map.of("root", new Node("parent", List.of(new Node("child", List.of()))),
                                "verbose", true),
                        r -> {
                            assertEquals("parent:1", r.firstContent().asText().text());
                        })
                .thenAssertResults();
    }

    public record Node(String name, List<Node> children) {
    }

    public static class MyTools {

        @Tool
        String analyzeTree(@ToolArg(description = "tree root") Node root,
                @ToolArg(description = "verbose output") boolean verbose) {
            return root.name() + ":" + root.children().size();
        }
    }

}
