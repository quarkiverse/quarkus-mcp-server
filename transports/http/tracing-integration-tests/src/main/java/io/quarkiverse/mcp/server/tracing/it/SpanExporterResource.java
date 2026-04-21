package io.quarkiverse.mcp.server.tracing.it;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

@Path("/spans")
@ApplicationScoped
public class SpanExporterResource {

    @Inject
    InMemorySpanExporter spanExporter;

    @GET
    @Produces("application/json")
    public String getSpans() {
        JsonArray spans = new JsonArray();
        spanExporter.getFinishedSpanItems().stream()
                .filter(sd -> !sd.getName().contains("DELETE /spans") && !sd.getName().contains("GET /spans"))
                .forEach(span -> {
                    JsonObject obj = new JsonObject();
                    obj.put("spanId", span.getSpanId());
                    obj.put("traceId", span.getTraceId());
                    obj.put("name", span.getName());
                    obj.put("kind", span.getKind().name());
                    obj.put("statusCode", span.getStatus().getStatusCode().name());
                    obj.put("parent_spanId", span.getParentSpanId());
                    JsonObject attributes = new JsonObject();
                    span.getAttributes().forEach((key, value) -> attributes.put(key.getKey(), String.valueOf(value)));
                    obj.put("attributes", attributes);
                    JsonArray links = new JsonArray();
                    for (LinkData link : span.getLinks()) {
                        JsonObject linkObj = new JsonObject();
                        linkObj.put("traceId", link.getSpanContext().getTraceId());
                        linkObj.put("spanId", link.getSpanContext().getSpanId());
                        links.add(linkObj);
                    }
                    obj.put("links", links);
                    spans.add(obj);
                });
        return spans.encode();
    }

    @DELETE
    public void resetSpans() {
        spanExporter.reset();
    }
}
