# Quarkus MCP Server
<!-- ALL-CONTRIBUTORS-BADGE:START - Do not remove or modify this section -->
[![All Contributors](https://img.shields.io/badge/all_contributors-13-orange.svg?style=flat-square)](#contributors-)
<!-- ALL-CONTRIBUTORS-BADGE:END -->
[![Version](https://img.shields.io/maven-central/v/io.quarkiverse.mcp/quarkus-mcp-server-parent?logo=apache-maven&style=flat-square)](https://central.sonatype.com/artifact/io.quarkiverse.mcp/quarkus-mcp-server-parent)

This Quarkus extension provides both declarative and programmatic APIs that enable developers to easily implement [MCP](https://modelcontextprotocol.io/)[^1] server features.

[^1]: _"[Model Context Protocol](https://modelcontextprotocol.io/) (MCP) is an open protocol that enables seamless integration between LLM applications and external data sources and tools."_ 

> [!NOTE]  
> The [LangChain4j](https://github.com/langchain4j/langchain4j) project provides the MCP client functionality, either as a low-level programmatic API or as a full-fledged integration into AI-infused applications.

## Get Started

### Step #1 

Add the following dependency to your POM file:

```xml
<dependency>
    <groupId>io.quarkiverse.mcp</groupId>
    <artifactId>quarkus-mcp-server-sse</artifactId>
    <version>${quarkus-mcp-server-version}</version>
</dependency>
```

> [!NOTE]  
> This dependency includes the HTTP/SSE transport. Use the `quarkus-mcp-server-stdio` artifactId if you want to use the STDIO transport instead. See also the [Supported transports](https://docs.quarkiverse.io/quarkus-mcp-server/dev/#_supported_transports) section in the docs for more information.


### Step #2 

Add server features (prompts, resources and tools) represented by _annotated business methods_ of CDI beans.

```java
import jakarta.inject.Inject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import io.quarkiverse.mcp.server.BlobResourceContents;
import io.quarkiverse.mcp.server.Prompt;
import io.quarkiverse.mcp.server.PromptArg;
import io.quarkiverse.mcp.server.PromptMessage;
import io.quarkiverse.mcp.server.Resource;
import io.quarkiverse.mcp.server.TextContent;
import io.quarkiverse.mcp.server.Tool;

// This class is automatically registered as a @Singleton CDI bean
public class MyServerFeatures {

    @Inject
    CodeService codeService;

    @Tool(description = "Converts the string value to lower case")
    String toLowerCase(String value) {
        return value.toLowerCase();
    }

    @Prompt(name = "code_assist")
    PromptMessage codeAssist(@PromptArg(name = "lang") String language) {
        return PromptMessage.withUserRole(new TextContent(codeService.assist(language)));
    }

    @Resource(uri = "file:///project/alpha")
    BlobResourceContents alpha(RequestUri uri) throws IOException{
        return BlobResourceContents.create(uri.value(), Files.readAllBytes(Path.of("alpha.txt")));
    }

}
```

### Step #3

Run your Quarkus app and have fun!

## Documentation

The full documentation is available at https://quarkiverse.github.io/quarkiverse-docs/quarkus-mcp-server/dev/index.html.

## Contributors ✨

Thanks goes to these wonderful people ([emoji key](https://allcontributors.org/docs/en/emoji-key)):

<!-- ALL-CONTRIBUTORS-LIST:START - Do not remove or modify this section -->
<!-- prettier-ignore-start -->
<!-- markdownlint-disable -->
<table>
  <tbody>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/mkouba"><img src="https://avatars.githubusercontent.com/u/913004?v=4?s=100" width="100px;" alt="Martin Kouba"/><br /><sub><b>Martin Kouba</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=mkouba" title="Code">💻</a> <a href="#maintenance-mkouba" title="Maintenance">🚧</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/geoand"><img src="https://avatars.githubusercontent.com/u/4374975?v=4?s=100" width="100px;" alt="Georgios Andrianakis"/><br /><sub><b>Georgios Andrianakis</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=geoand" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://xam.dk"><img src="https://avatars.githubusercontent.com/u/54129?v=4?s=100" width="100px;" alt="Max Rydahl Andersen"/><br /><sub><b>Max Rydahl Andersen</b></sub></a><br /><a href="#example-maxandersen" title="Examples">💡</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://twitter.com/r_svoboda"><img src="https://avatars.githubusercontent.com/u/925259?v=4?s=100" width="100px;" alt="Rostislav Svoboda"/><br /><sub><b>Rostislav Svoboda</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=rsvoboda" title="Code">💻</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/gastaldi"><img src="https://avatars.githubusercontent.com/u/54133?v=4?s=100" width="100px;" alt="George Gastaldi"/><br /><sub><b>George Gastaldi</b></sub></a><br /><a href="#infra-gastaldi" title="Infrastructure (Hosting, Build-Tools, etc)">🚇</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/jmartisk"><img src="https://avatars.githubusercontent.com/u/937315?v=4?s=100" width="100px;" alt="Jan Martiska"/><br /><sub><b>Jan Martiska</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=jmartisk" title="Documentation">📖</a></td>
      <td align="center" valign="top" width="14.28%"><a href="http://iocanel.com"><img src="https://avatars.githubusercontent.com/u/402008?v=4?s=100" width="100px;" alt="Ioannis Canellos"/><br /><sub><b>Ioannis Canellos</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=iocanel" title="Code">💻</a></td>
    </tr>
    <tr>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/sberyozkin"><img src="https://avatars.githubusercontent.com/u/467639?v=4?s=100" width="100px;" alt="Sergey Beryozkin"/><br /><sub><b>Sergey Beryozkin</b></sub></a><br /><a href="#example-sberyozkin" title="Examples">💡</a></td>
      <td align="center" valign="top" width="14.28%"><a href="http://kpavlov.me"><img src="https://avatars.githubusercontent.com/u/1517853?v=4?s=100" width="100px;" alt="Konstantin Pavlov"/><br /><sub><b>Konstantin Pavlov</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=kpavlov" title="Tests">⚠️</a> <a href="#infra-kpavlov" title="Infrastructure (Hosting, Build-Tools, etc)">🚇</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/mvduijnhoven"><img src="https://avatars.githubusercontent.com/u/16058040?v=4?s=100" width="100px;" alt="Martijn van Duijnhoven"/><br /><sub><b>Martijn van Duijnhoven</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=mvduijnhoven" title="Code">💻</a> <a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=mvduijnhoven" title="Tests">⚠️</a> <a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=mvduijnhoven" title="Documentation">📖</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/PierreBtz"><img src="https://avatars.githubusercontent.com/u/9881659?v=4?s=100" width="100px;" alt="Pierre Beitz"/><br /><sub><b>Pierre Beitz</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=PierreBtz" title="Code">💻</a> <a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=PierreBtz" title="Tests">⚠️</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://github.com/mrecouly"><img src="https://avatars.githubusercontent.com/u/583648?v=4?s=100" width="100px;" alt="Matthieu Recouly"/><br /><sub><b>Matthieu Recouly</b></sub></a><br /><a href="https://github.com/quarkiverse/quarkus-mcp-server/commits?author=mrecouly" title="Documentation">📖</a></td>
      <td align="center" valign="top" width="14.28%"><a href="https://wjglerum.nl"><img src="https://avatars.githubusercontent.com/u/7404187?v=4?s=100" width="100px;" alt="Willem Jan Glerum"/><br /><sub><b>Willem Jan Glerum</b></sub></a><br /><a href="#infra-wjglerum" title="Infrastructure (Hosting, Build-Tools, etc)">🚇</a></td>
    </tr>
  </tbody>
</table>

<!-- markdownlint-restore -->
<!-- prettier-ignore-end -->

<!-- ALL-CONTRIBUTORS-LIST:END -->

This project follows the [all-contributors](https://github.com/all-contributors/all-contributors) specification. Contributions of any kind welcome!
