#!/bin/bash
./samples/weather/mvnw versions:use-dep-version -Dincludes=io.quarkiverse.mcp:quarkus-mcp-server-stdio -DdepVersion=${CURRENT_VERSION} -DgenerateBackupPoms=false -f samples/weather/pom.xml 
./samples/secure-mcp-sse-server/mvnw versions:use-dep-version -Dincludes=io.quarkiverse.mcp:quarkus-mcp-server-sse -DdepVersion=${CURRENT_VERSION} -DgenerateBackupPoms=false -f samples/secure-mcp-sse-server/pom.xml 
./samples/multiple-secure-mcp-http-servers/mvnw versions:use-dep-version -Dincludes=io.quarkiverse.mcp:quarkus-mcp-server-sse -DdepVersion=${CURRENT_VERSION} -DgenerateBackupPoms=false -f samples/multiple-secure-mcp-http-servers/pom.xml 
