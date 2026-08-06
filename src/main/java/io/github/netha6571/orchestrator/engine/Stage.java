package io.github.netha6571.orchestrator.engine;

import io.github.netha6571.orchestrator.agent.Agent;
import io.github.netha6571.orchestrator.model.StageId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * One node in the workflow graph.
 *
 * Immutable after construction — the engine reads it, never mutates it.
 * The entry check is a predicate over current stage states so the engine
 * can evaluate readiness without knowing what the check actually tests.
 */
public final class Stage {

    private final Agent agent;
    private final Set<StageId> dependencies;
    private final Predicate<Map<StageId, ?>> entryCheck;
    private final int retryBudget;
    private final boolean approvalRequired;

    public Stage(Agent agent,
                 Set<StageId> dependencies,
                 Predicate<Map<StageId, ?>> entryCheck,
                 int retryBudget,
                 boolean approvalRequired) {

        Objects.requireNonNull(agent, "agent");
        if (retryBudget < 0) {
            throw new IllegalArgumentException("Retry budget must not be negative");
        }

        this.agent = agent;
        // Defensive copy — callers cannot change deps after construction.
        this.dependencies = dependencies != null
                ? Collections.unmodifiableSet(new LinkedHashSet<>(dependencies))
                : Set.of();
        // Default entry check: always pass. Lets simple stages skip the predicate.
        this.entryCheck = entryCheck != null ? entryCheck : states -> true;
        this.retryBudget = retryBudget;
        this.approvalRequired = approvalRequired;
    }

    public StageId id()                  { return agent.stageId(); }
    public Agent agent()                 { return agent; }
    public Set<StageId> dependencies()   { return dependencies; }
    public int retryBudget()             { return retryBudget; }
    public boolean approvalRequired()    { return approvalRequired; }

    /**
     * Evaluate the entry check against the current state map.
     * The engine calls this to decide whether a stage is ready to run.
     */
    public boolean entryCheckPasses(Map<StageId, ?> currentStates) {
        return entryCheck.test(currentStates);
    }

    @Override
    public String toString() {
        return "Stage[" + id() + ", deps=" + dependencies
                + ", retries=" + retryBudget
                + (approvalRequired ? ", approval" : "") + "]";
    }
}
