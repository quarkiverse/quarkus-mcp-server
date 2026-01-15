package io.quarkiverse.mcp.server;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Associate icons with a feature.
 *
 * @see Tool
 * @see Prompt
 * @see Resource
 * @see ResourceTemplate
 */
@Retention(RUNTIME)
@Target({ METHOD, TYPE })
public @interface Icons {

    /**
     * The provider class.
     */
    Class<? extends IconsProvider> value();

}
