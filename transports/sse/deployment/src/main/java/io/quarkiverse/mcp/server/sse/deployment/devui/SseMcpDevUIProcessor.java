package io.quarkiverse.mcp.server.sse.deployment.devui;

import io.quarkiverse.mcp.server.sse.runtime.devui.SseMcpJsonRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class SseMcpDevUIProcessor {

    @BuildStep(onlyIf = IsDevelopment.class)
    void page(BuildProducer<CardPageBuildItem> cardPages) {

        CardPageBuildItem pageBuildItem = new CardPageBuildItem();

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:toolbox")
                .componentLink("qwc-mcp-tools.js")
                .title("Tools"));

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:terminal")
                .componentLink("qwc-mcp-prompts.js")
                .title("Prompts"));

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:file")
                .componentLink("qwc-mcp-resources.js")
                .title("Resources"));

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:file-code")
                .componentLink("qwc-mcp-resource-templates.js")
                .title("Resource templates"));

        cardPages.produce(pageBuildItem);
    }

    @BuildStep(onlyIf = IsDevelopment.class)
    JsonRPCProvidersBuildItem rpcProvider() {
        return new JsonRPCProvidersBuildItem(SseMcpJsonRPCService.class);
    }

}
