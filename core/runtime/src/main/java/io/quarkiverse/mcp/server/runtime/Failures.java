package io.quarkiverse.mcp.server.runtime;

import io.quarkus.security.AuthenticationException;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;

final class Failures {

    static boolean isSecurityFailure(Throwable throwable) {
        return throwable instanceof UnauthorizedException
                || throwable instanceof AuthenticationException
                || throwable instanceof ForbiddenException;
    }

}
