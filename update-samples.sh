#!/bin/bash
./samples/weather/mvnw versions:update-properties -Dincludes=io.quarkiverse.mcp:* -DgenerateBackupPoms=false -f samples/weather/pom.xml 
./samples/secure-mcp-sse-server/mvnw versions:update-properties -Dincludes=io.quarkiverse.mcp:* -DgenerateBackupPoms=false -f samples/secure-mcp-sse-server/pom.xml 
./samples/multiple-secure-mcp-http-servers/mvnw versions:update-properties -Dincludes=io.quarkiverse.mcp:* -DgenerateBackupPoms=false -f samples/multiple-secure-mcp-http-servers/pom.xml 
