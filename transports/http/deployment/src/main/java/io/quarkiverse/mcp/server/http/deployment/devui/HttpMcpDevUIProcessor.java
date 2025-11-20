package io.quarkiverse.mcp.server.http.deployment.devui;

import io.quarkiverse.mcp.server.http.runtime.devui.SseMcpJsonRPCService;
import io.quarkus.deployment.IsDevelopment;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.devui.spi.page.Page;

public class HttpMcpDevUIProcessor {

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
                .icon("font-awesome-solid:terminal")
                .componentLink("qwc-mcp-prompt-completions.js")
                .title("P completions"));

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:file")
                .componentLink("qwc-mcp-resources.js")
                .title("Resources"));

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:file-code")
                .componentLink("qwc-mcp-resource-templates.js")
                .title("Resource templates"));

        pageBuildItem.addPage(Page.webComponentPageBuilder()
                .icon("font-awesome-solid:file-code")
                .componentLink("qwc-mcp-resource-template-completions.js")
                .title("RT completions"));

        cardPages.produce(pageBuildItem);
    }

    @BuildStep
    JsonRPCProvidersBuildItem rpcProvider() {
        return new JsonRPCProvidersBuildItem(SseMcpJsonRPCService.class);
    }

}
