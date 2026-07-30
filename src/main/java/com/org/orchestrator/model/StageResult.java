package com.org.orchestrator.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What an agent hands back after doing its work.
 * Immutable once built — the engine reads it, never mutates it.
 */
public final class StageResult {

    private final StageId stageId;
    private final boolean succeeded;
    private final ArtifactSource source;
    private final Map<String, String> outputs;
    private final String reason;
    private final boolean approvalRequired;

    public StageResult(StageId stageId,
                       boolean succeeded,
                       ArtifactSource source,
                       Map<String, String> outputs,
                       String reason,
                       boolean approvalRequired) {

        Objects.requireNonNull(stageId, "stageId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reason, "reason");

        this.stageId = stageId;
        this.succeeded = succeeded;
        this.source = source;
        // Defensive copy — callers cannot change outputs after construction.
        this.outputs = Collections.unmodifiableMap(
                new LinkedHashMap<>(outputs != null ? outputs : Map.of()));
        this.reason = reason;
        this.approvalRequired = approvalRequired;
    }

    public StageId stageId()          { return stageId; }
    public boolean succeeded()        { return succeeded; }
    public ArtifactSource source()    { return source; }
    public Map<String, String> outputs() { return outputs; }
    public String reason()            { return reason; }
    public boolean approvalRequired() { return approvalRequired; }

    @Override
    public String toString() {
        return stageId + " [" + (succeeded ? "OK" : "FAIL") + ", " + source + "]"
                + (approvalRequired ? " (needs approval)" : "");
    }
}
