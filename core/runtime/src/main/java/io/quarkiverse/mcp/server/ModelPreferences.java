package io.quarkiverse.mcp.server;

import java.math.BigDecimal;
import java.util.List;

/**
 * The preferences for model selection.
 *
 * @param costPriority
 * @param modelHints
 * @param intelligencePriority
 * @param speedPriority
 * @see SamplingRequest
 */
public record ModelPreferences(BigDecimal costPriority, List<ModelHint> modelHints, BigDecimal intelligencePriority,
        BigDecimal speedPriority) {

    public ModelPreferences {
        if (costPriority != null
                && (costPriority.compareTo(BigDecimal.ZERO) < 0 || costPriority.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("costPriority must be between 0 and 1");
        }
        if (intelligencePriority != null
                && (intelligencePriority.compareTo(BigDecimal.ZERO) < 0
                        || intelligencePriority.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("intelligencePriority must be between 0 and 1");
        }
        if (speedPriority != null
                && (speedPriority.compareTo(BigDecimal.ZERO) < 0 || speedPriority.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("speedPriority must be between 0 and 1");
        }
    }

}
