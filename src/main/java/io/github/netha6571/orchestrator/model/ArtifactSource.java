package io.github.netha6571.orchestrator.model;

/**
 * Tags where an output came from, so the audit trail
 * and confidence score can distinguish model work from fallback work.
 */
public enum ArtifactSource {

    MODEL,
    FALLBACK
}
