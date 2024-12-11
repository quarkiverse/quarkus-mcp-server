package io.quarkiverse.mcp.server.test;

import static jakarta.ws.rs.core.HttpHeaders.CONTENT_TYPE;
import static jakarta.ws.rs.core.MediaType.SERVER_SENT_EVENTS;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.client.SseEvent;

import io.smallrye.mutiny.Multi;

@Path("mcp")
@RegisterRestClient
public interface McpClient {

    @GET
    @Path("sse")
    @ClientHeaderParam(name = CONTENT_TYPE, value = SERVER_SENT_EVENTS)
    @Produces(SERVER_SENT_EVENTS)
    Multi<SseEvent<String>> init();

}
