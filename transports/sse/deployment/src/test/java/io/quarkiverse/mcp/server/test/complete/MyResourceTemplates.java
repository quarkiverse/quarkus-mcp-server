package io.quarkiverse.mcp.server.test.complete;

import java.util.List;

import io.quarkiverse.mcp.server.CompleteArg;
import io.quarkiverse.mcp.server.CompleteResourceTemplate;
import io.quarkiverse.mcp.server.RequestUri;
import io.quarkiverse.mcp.server.ResourceTemplate;
import io.quarkiverse.mcp.server.TextResourceContents;
import io.quarkus.logging.Log;

public class MyResourceTemplates {

    static final List<String> NAMES = List.of("Martin", "Lu", "Jachym", "Vojtik", "Onda");

    @ResourceTemplate(uriTemplate = "file:///{foo}/{bar}")
    TextResourceContents foo_template(String foo, String bar, RequestUri uri) {
        return TextResourceContents.create(uri.value(), foo + ":" + bar);
    }

    @CompleteResourceTemplate("foo_template")
    List<String> completeFoo(@CompleteArg(name = "foo") String val) {
        Log.infof("Complete foo: %s", val);
        return NAMES.stream().filter(n -> n.startsWith(val)).toList();
    }

    @CompleteResourceTemplate("foo_template")
    String completeBar(String bar) {
        Log.infof("Complete bar: %s", bar);
        return "_bar";
    }
}
