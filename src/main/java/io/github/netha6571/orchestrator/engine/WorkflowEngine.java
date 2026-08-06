package io.github.netha6571.orchestrator.engine;

import io.github.netha6571.orchestrator.governance.ApprovalGate;
import io.github.netha6571.orchestrator.governance.RunMetrics;
import io.github.netha6571.orchestrator.model.ArtifactSource;
import io.github.netha6571.orchestrator.model.StageId;
import io.github.netha6571.orchestrator.model.StageResult;
import io.github.netha6571.orchestrator.model.StageState;
import io.github.netha6571.orchestrator.model.WorkflowContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The core. Runs the DAG by promoting stages to ready when their
 * dependencies pass and their entry checks open, executing the ready
 * frontier in parallel, joining before continuing, and applying results
 * with approval gates, bounded retries, and safe-stop.
 *
 * The engine knows nothing about URLs, the shortener, or any agent by
 * name. It works with stages, states, and gates only.
 */
public final class WorkflowEngine {

    private static final int POOL_SIZE = 4;

    private final Dag dag;
    private final ApprovalGate gate;
    private final RunMetrics metrics;
    private final Map<StageId, StageState> states;

    public WorkflowEngine(Dag dag, ApprovalGate gate, RunMetrics metrics) {
        this.dag = dag;
        this.gate = gate;
        this.metrics = metrics;
        // Mutable state map — tracks each stage's lifecycle.
        this.states = new LinkedHashMap<>();
        for (StageId id : dag.stages().keySet()) {
            states.put(id, StageState.PENDING);
        }
    }

    /**
     * Run the entire workflow for the given requirement.
     * Returns true if the run completed (all stages terminal),
     * false if it stopped safely (stuck frontier).
     */
    public boolean run(WorkflowContext context) {
        metrics.markStart();
        ExecutorService pool = Executors.newFixedThreadPool(POOL_SIZE);

        try {
            while (true) {
                // --- RE-PLANNING SEAM ---
                // Hook: if an upstream output changed on a re-run, mark dependent
                // stages stale and reset them to PENDING so they run again.
                // Not built yet — left as a named seam next to the readiness check.

                promoteReady();
                List<Stage> frontier = collectFrontier();

                if (frontier.isEmpty()) {
                    if (allTerminal()) {
                        // Every stage reached a final state — run is done.
                        break;
                    }
                    // Stuck: nothing can move forward. Safe-stop.
                    System.out.println("[engine] Stuck — no stages are ready but "
                            + "not all stages are terminal. Stopping safely.");
                    break;
                }

                executeFrontier(frontier, context, pool);
            }
        } finally {
            pool.shutdownNow();
            metrics.markEnd();
        }

        return allTerminal();
    }

    /**
     * Look at every PENDING stage. If all its dependencies have passed
     * or been skipped, and its entry check passes, promote it to READY.
     */
    private void promoteReady() {
        for (Map.Entry<StageId, Stage> entry : dag.stages().entrySet()) {
            StageId id = entry.getKey();
            if (states.get(id) != StageState.PENDING) continue;

            Stage stage = entry.getValue();
            boolean depsOk = stage.dependencies().stream().allMatch(dep -> {
                StageState depState = states.get(dep);
                return depState == StageState.PASSED || depState == StageState.SKIPPED;
            });

            if (depsOk && stage.entryCheckPasses(Collections.unmodifiableMap(states))) {
                states.put(id, StageState.READY);
            }
        }
    }

    /**
     * Collect all READY stages — this is the frontier.
     */
    private List<Stage> collectFrontier() {
        List<Stage> frontier = new ArrayList<>();
        for (Map.Entry<StageId, Stage> entry : dag.stages().entrySet()) {
            if (states.get(entry.getKey()) == StageState.READY) {
                frontier.add(entry.getValue());
            }
        }
        return frontier;
    }

    /**
     * Run the whole frontier in parallel on the thread pool.
     * Wait for all of them to finish before returning — this is
     * the synchronisation point.
     */
    private void executeFrontier(List<Stage> frontier, WorkflowContext context,
                                 ExecutorService pool) {
        // Mark all frontier stages as RUNNING.
        for (Stage stage : frontier) {
            states.put(stage.id(), StageState.RUNNING);
        }

        // Submit each stage and collect futures.
        List<Future<StageRunOutcome>> futures = new ArrayList<>();
        for (Stage stage : frontier) {
            futures.add(pool.submit(() -> runWithRetries(stage, context)));
        }

        // Join — wait for every result before moving on.
        List<StageRunOutcome> outcomes = new ArrayList<>();
        for (Future<StageRunOutcome> future : futures) {
            try {
                outcomes.add(future.get());
            } catch (Exception e) {
                // Should not happen — runWithRetries catches everything.
                // But defend against it anyway.
                System.err.println("[engine] Unexpected error collecting result: " + e.getMessage());
            }
        }

        // Apply each result in order.
        for (StageRunOutcome outcome : outcomes) {
            applyResult(outcome.stage, outcome.result, context);
        }
    }

    /**
     * Run a stage through the retry wrapper.
     * Try the agent. If it fails, retry up to the stage's budget.
     * Track retries and recovery time for metrics.
     */
    private StageRunOutcome runWithRetries(Stage stage, WorkflowContext context) {
        StageResult result = null;
        Instant failureTime = null;
        int attemptsLeft = stage.retryBudget() + 1; // first try + retries

        for (int attempt = 0; attempt < attemptsLeft; attempt++) {
            metrics.recordRun();
            result = stage.agent().execute(context);

            if (result.succeeded()) {
                // If this was a recovery (retry after failure), record MTTR.
                if (failureTime != null) {
                    metrics.recordRecovery(Duration.between(failureTime, Instant.now()));
                }
                return new StageRunOutcome(stage, result);
            }

            // Failed — count a retry if we have budget left.
            if (attempt < attemptsLeft - 1) {
                metrics.recordRetry();
                if (failureTime == null) failureTime = Instant.now();
                System.out.println("[engine] " + stage.id()
                        + " failed (attempt " + (attempt + 1) + "), retrying...");
            }
        }

        // Budget exhausted — hand back the failed result.
        return new StageRunOutcome(stage, result);
    }

    /**
     * Apply a result in order (spec 02, "Applying a result"):
     *   1. Outputs already written to context by the agent.
     *   2. Count fallback if used.
     *   3. If failed, mark failed.
     *   4. If needs approval, run the gate.
     *   5. Otherwise mark passed.
     */
    private void applyResult(Stage stage, StageResult result, WorkflowContext context) {
        // 1. Outputs are already written to the context by the agent
        //    during execute() — the base agent does this.

        // 2. Count fallback usage.
        if (result.source() == ArtifactSource.FALLBACK) {
            metrics.recordFallback();
        }

        // --- ROLLBACK SEAM ---
        // Hook: if a later stage fails, walk back over the passed stages
        // and run their registered undo actions in reverse. Mark those
        // stages ROLLED_BACK and count a rollback.
        // Not built yet — left as a named seam where a result is applied.

        // 3. If the stage failed, mark it failed and stop.
        if (!result.succeeded()) {
            states.put(stage.id(), StageState.FAILED);
            System.out.println("[engine] " + stage.id() + " FAILED: " + result.reason());
            return;
        }

        // 4. If the stage needs approval, ask the gate.
        if (stage.approvalRequired() || result.approvalRequired()) {
            states.put(stage.id(), StageState.AWAITING_APPROVAL);

            String description = result.reason()
                    + "\nOutputs: " + result.outputs().keySet();

            boolean approved = gate.requestApproval(stage.id(), description);
            if (!approved) {
                // Safe-stop: a rejected high-impact change does not slip through.
                states.put(stage.id(), StageState.FAILED);
                System.out.println("[engine] " + stage.id()
                        + " REJECTED at approval gate — branch stopped.");
                return;
            }
        }

        // 5. Mark passed.
        states.put(stage.id(), StageState.PASSED);
        metrics.recordPass();
        System.out.println("[engine] " + stage.id() + " PASSED");
    }

    /**
     * Check whether every stage has reached a terminal state.
     */
    private boolean allTerminal() {
        return states.values().stream().allMatch(StageState::isTerminal);
    }

    /**
     * Returns an unmodifiable snapshot of the current stage states.
     */
    public Map<StageId, StageState> stageStates() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(states));
    }

    // Internal record to pair a stage with its result from the thread pool.
    private record StageRunOutcome(Stage stage, StageResult result) {}
}
