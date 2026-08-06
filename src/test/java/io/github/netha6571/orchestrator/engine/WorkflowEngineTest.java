package io.github.netha6571.orchestrator.engine;

import io.github.netha6571.orchestrator.agent.Agent;
import io.github.netha6571.orchestrator.agent.RequirementAgent;
import io.github.netha6571.orchestrator.governance.ApprovalGate;
import io.github.netha6571.orchestrator.governance.RunMetrics;
import io.github.netha6571.orchestrator.llm.StubLlmClient;
import io.github.netha6571.orchestrator.model.ArtifactSource;
import io.github.netha6571.orchestrator.model.StageId;
import io.github.netha6571.orchestrator.model.StageResult;
import io.github.netha6571.orchestrator.model.StageState;
import io.github.netha6571.orchestrator.model.WorkflowContext;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior tests for the WorkflowEngine.
 *
 * Every test uses the stub LLM client or small fake agents, so the suite
 * runs offline with no API key and is fully repeatable. Each test builds
 * a minimal graph that isolates one behavior so a failure points straight
 * at the cause.
 */
class WorkflowEngineTest {

    // ---------------------------------------------------------------
    // Fake agents — small, purpose-built helpers for the tests below.
    // ---------------------------------------------------------------

    /** An agent that always succeeds and records its id in the given list. */
    static Agent trackingAgent(StageId id, List<StageId> executionOrder) {
        return new Agent() {
            @Override public StageId stageId() { return id; }
            @Override public StageResult execute(WorkflowContext context) {
                executionOrder.add(id);
                context.append(id, "output", "done", ArtifactSource.MODEL, "test");
                return new StageResult(id, true, ArtifactSource.MODEL,
                        Map.of("output", "done"), "passed", false);
            }
        };
    }

    /** An agent that always succeeds (no tracking). */
    static Agent passingAgent(StageId id) {
        return new Agent() {
            @Override public StageId stageId() { return id; }
            @Override public StageResult execute(WorkflowContext context) {
                context.append(id, "output", "done", ArtifactSource.MODEL, "test");
                return new StageResult(id, true, ArtifactSource.MODEL,
                        Map.of("output", "done"), "passed", false);
            }
        };
    }

    /** An agent that always fails. */
    static Agent failingAgent(StageId id) {
        return new Agent() {
            @Override public StageId stageId() { return id; }
            @Override public StageResult execute(WorkflowContext context) {
                return new StageResult(id, false, ArtifactSource.MODEL,
                        Map.of(), "deliberate failure", false);
            }
        };
    }

    /** An agent that fails the first N calls, then succeeds. Thread-safe. */
    static Agent failThenSucceedAgent(StageId id, int failCount) {
        AtomicInteger calls = new AtomicInteger();
        return new Agent() {
            @Override public StageId stageId() { return id; }
            @Override public StageResult execute(WorkflowContext context) {
                if (calls.getAndIncrement() < failCount) {
                    return new StageResult(id, false, ArtifactSource.MODEL,
                            Map.of(), "transient failure", false);
                }
                context.append(id, "output", "recovered", ArtifactSource.MODEL, "retry");
                return new StageResult(id, true, ArtifactSource.MODEL,
                        Map.of("output", "recovered"), "passed after retry", false);
            }
        };
    }

    /** An agent that succeeds and reports FALLBACK as its source. */
    static Agent fallbackSourceAgent(StageId id) {
        return new Agent() {
            @Override public StageId stageId() { return id; }
            @Override public StageResult execute(WorkflowContext context) {
                context.append(id, "output", "fallback-value", ArtifactSource.FALLBACK, "forced");
                return new StageResult(id, true, ArtifactSource.FALLBACK,
                        Map.of("output", "fallback-value"), "used fallback", false);
            }
        };
    }

    /** An agent whose result demands approval. */
    static Agent approvalRequiredAgent(StageId id) {
        return new Agent() {
            @Override public StageId stageId() { return id; }
            @Override public StageResult execute(WorkflowContext context) {
                context.append(id, "output", "done", ArtifactSource.MODEL, "test");
                return new StageResult(id, true, ArtifactSource.MODEL,
                        Map.of("output", "done"), "needs sign-off", true);
            }
        };
    }

    /**
     * An agent that coordinates with a CountDownLatch to prove it runs
     * in parallel with another agent. Both must arrive before either
     * proceeds; if they were sequential the test would time out.
     */
    static Agent latchAgent(StageId id, CountDownLatch bothArrived, List<StageId> order) {
        return new Agent() {
            @Override public StageId stageId() { return id; }
            @Override public StageResult execute(WorkflowContext context) {
                bothArrived.countDown();
                try {
                    if (!bothArrived.await(3, TimeUnit.SECONDS)) {
                        return new StageResult(id, false, ArtifactSource.MODEL,
                                Map.of(), "parallel peer never arrived", false);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return new StageResult(id, false, ArtifactSource.MODEL,
                            Map.of(), "interrupted", false);
                }
                order.add(id);
                context.append(id, "output", "done", ArtifactSource.MODEL, "parallel");
                return new StageResult(id, true, ArtifactSource.MODEL,
                        Map.of("output", "done"), "passed", false);
            }
        };
    }

    // ---------------------------------------------------------------
    // 1. Stages run in dependency order
    // ---------------------------------------------------------------

    @Test
    void stagesRunInDependencyOrder() {
        StageId a = new StageId("a");
        StageId b = new StageId("b");
        StageId c = new StageId("c");

        List<StageId> order = Collections.synchronizedList(new ArrayList<>());

        Dag dag = new Dag(List.of(
                new Stage(trackingAgent(a, order), Set.of(),  null, 0, false),
                new Stage(trackingAgent(b, order), Set.of(a), null, 0, false),
                new Stage(trackingAgent(c, order), Set.of(b), null, 0, false)));

        RunMetrics metrics = new RunMetrics();
        WorkflowEngine engine = new WorkflowEngine(dag, ApprovalGate.autoApprove(), metrics);

        boolean completed = engine.run(new WorkflowContext("test"));

        assertTrue(completed);
        assertEquals(List.of(a, b, c), order,
                "stages should execute in the order a → b → c");
    }

    // ---------------------------------------------------------------
    // 2. Independent stages run in parallel; join waits for both
    // ---------------------------------------------------------------

    @Test
    void independentStagesRunInParallelAndJoinWaitsForBoth() {
        StageId root  = new StageId("root");
        StageId left  = new StageId("left");
        StageId right = new StageId("right");
        StageId join  = new StageId("join");

        List<StageId> order = Collections.synchronizedList(new ArrayList<>());

        // The latch requires both left and right to arrive before either
        // proceeds. If the engine ran them sequentially, the first would
        // block on await and the test would time out.
        CountDownLatch bothArrived = new CountDownLatch(2);

        Dag dag = new Dag(List.of(
                new Stage(trackingAgent(root, order),  Set.of(),           null, 0, false),
                new Stage(latchAgent(left, bothArrived, order),  Set.of(root), null, 0, false),
                new Stage(latchAgent(right, bothArrived, order), Set.of(root), null, 0, false),
                new Stage(trackingAgent(join, order),   Set.of(left, right), null, 0, false)));

        RunMetrics metrics = new RunMetrics();
        WorkflowEngine engine = new WorkflowEngine(dag, ApprovalGate.autoApprove(), metrics);

        boolean completed = engine.run(new WorkflowContext("test"));

        assertTrue(completed);
        assertEquals(4, order.size());
        assertEquals(root, order.get(0), "root runs first");
        assertEquals(join, order.get(3), "join runs last, after both parallel stages");
        assertTrue(order.subList(1, 3).containsAll(List.of(left, right)),
                "left and right both ran between root and join");
    }

    // ---------------------------------------------------------------
    // 3. Failed stage after retry exhaustion stops the run safely
    // ---------------------------------------------------------------

    @Test
    void failedStageStopsRunSafely() {
        StageId a = new StageId("a");
        StageId b = new StageId("b"); // always fails
        StageId c = new StageId("c"); // depends on b — should never run

        List<StageId> order = Collections.synchronizedList(new ArrayList<>());

        // b gets 1 retry (2 attempts total), both will fail.
        Dag dag = new Dag(List.of(
                new Stage(trackingAgent(a, order), Set.of(),  null, 0, false),
                new Stage(failingAgent(b),         Set.of(a), null, 1, false),
                new Stage(trackingAgent(c, order), Set.of(b), null, 0, false)));

        RunMetrics metrics = new RunMetrics();
        WorkflowEngine engine = new WorkflowEngine(dag, ApprovalGate.autoApprove(), metrics);

        boolean completed = engine.run(new WorkflowContext("test"));

        assertFalse(completed, "run should not complete when a stage fails");
        assertEquals(StageState.FAILED, engine.stageStates().get(b));
        assertEquals(StageState.PENDING, engine.stageStates().get(c),
                "dependent stage should stay PENDING, never executed");
        assertFalse(order.contains(c), "c should never have run");
    }

    // ---------------------------------------------------------------
    // 4. Retry budget works — fail once, succeed on retry
    // ---------------------------------------------------------------

    @Test
    void retryBudgetAllowsRecovery() {
        StageId a = new StageId("a");

        // Fails the first call, succeeds the second. Budget of 1 retry.
        Dag dag = new Dag(List.of(
                new Stage(failThenSucceedAgent(a, 1), Set.of(), null, 1, false)));

        RunMetrics metrics = new RunMetrics();
        WorkflowEngine engine = new WorkflowEngine(dag, ApprovalGate.autoApprove(), metrics);

        boolean completed = engine.run(new WorkflowContext("test"));

        assertTrue(completed);
        assertEquals(StageState.PASSED, engine.stageStates().get(a));
        assertEquals(1, metrics.retries(), "exactly one retry should have been used");
        // Two attempts total: the initial failure + the successful retry.
        assertEquals(2, metrics.stagesRun());
    }

    // ---------------------------------------------------------------
    // 5. Fallback path works (real agent with force-fail stub client)
    // ---------------------------------------------------------------

    @Test
    void fallbackPathProducesValidOutputAndRecordsFallbackSource() {
        // RequirementAgent backed by a stub client that always throws.
        // The agent's fallback method returns a real spec, not an error.
        RequirementAgent agent = new RequirementAgent(new StubLlmClient(true));

        Dag dag = new Dag(List.of(
                new Stage(agent, Set.of(), null, 0, false)));

        RunMetrics metrics = new RunMetrics();
        WorkflowEngine engine = new WorkflowEngine(dag, ApprovalGate.autoApprove(), metrics);
        WorkflowContext context = new WorkflowContext("Build a URL shortener");

        boolean completed = engine.run(context);

        assertTrue(completed);
        assertEquals(StageState.PASSED, engine.stageStates().get(RequirementAgent.ID));
        assertEquals(1, metrics.fallbacks(), "one fallback should be counted");

        // The context should record the output as FALLBACK, not MODEL.
        var entries = context.entries();
        assertFalse(entries.isEmpty(), "fallback should still produce entries");
        assertEquals(ArtifactSource.FALLBACK, entries.get(0).source());

        // The fallback value should be non-blank real content, not an error.
        String value = entries.get(0).value();
        assertFalse(value.isBlank(), "fallback output should not be blank");
        assertTrue(value.contains("URL shortener"),
                "fallback should reference the original requirement");
    }

    // ---------------------------------------------------------------
    // 6a. Approval gate rejects — branch stops
    // ---------------------------------------------------------------

    @Test
    void rejectedApprovalStopsBranch() {
        StageId a = new StageId("a");
        StageId b = new StageId("b");

        ApprovalGate rejectAll = (stageId, description) -> false;

        // Stage a's result requires approval; the gate rejects.
        Dag dag = new Dag(List.of(
                new Stage(approvalRequiredAgent(a), Set.of(),  null, 0, true),
                new Stage(passingAgent(b),          Set.of(a), null, 0, false)));

        RunMetrics metrics = new RunMetrics();
        WorkflowEngine engine = new WorkflowEngine(dag, rejectAll, metrics);

        boolean completed = engine.run(new WorkflowContext("test"));

        assertFalse(completed, "run should not complete when approval is rejected");
        assertEquals(StageState.FAILED, engine.stageStates().get(a),
                "rejected stage should be marked FAILED");
        assertEquals(StageState.PENDING, engine.stageStates().get(b),
                "downstream stage should never have started");
    }

    // ---------------------------------------------------------------
    // 6b. Approval gate approves — run continues
    // ---------------------------------------------------------------

    @Test
    void approvedGateAllowsContinuation() {
        StageId a = new StageId("a");
        StageId b = new StageId("b");

        ApprovalGate approveAll = (stageId, description) -> true;

        Dag dag = new Dag(List.of(
                new Stage(approvalRequiredAgent(a), Set.of(),  null, 0, true),
                new Stage(passingAgent(b),          Set.of(a), null, 0, false)));

        RunMetrics metrics = new RunMetrics();
        WorkflowEngine engine = new WorkflowEngine(dag, approveAll, metrics);

        boolean completed = engine.run(new WorkflowContext("test"));

        assertTrue(completed);
        assertEquals(StageState.PASSED, engine.stageStates().get(a));
        assertEquals(StageState.PASSED, engine.stageStates().get(b));
    }

    // ---------------------------------------------------------------
    // 7. Cyclic graph is rejected at construction time
    // ---------------------------------------------------------------

    @Test
    void cyclicGraphFailsFastWithClearError() {
        StageId a = new StageId("a");
        StageId b = new StageId("b");

        // a depends on b, b depends on a — a cycle.
        Stage stageA = new Stage(passingAgent(a), Set.of(b), null, 0, false);
        Stage stageB = new Stage(passingAgent(b), Set.of(a), null, 0, false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> new Dag(List.of(stageA, stageB)));

        assertTrue(ex.getMessage().toLowerCase().contains("cycle"),
                "error message should mention the cycle");
    }

    // ---------------------------------------------------------------
    // 8. Context records decisions in stage order
    // ---------------------------------------------------------------

    @Test
    void contextRecordsDecisionsInStageOrder() {
        StageId a = new StageId("a");
        StageId b = new StageId("b");
        StageId c = new StageId("c");

        Dag dag = new Dag(List.of(
                new Stage(passingAgent(a), Set.of(),  null, 0, false),
                new Stage(passingAgent(b), Set.of(a), null, 0, false),
                new Stage(passingAgent(c), Set.of(b), null, 0, false)));

        RunMetrics metrics = new RunMetrics();
        WorkflowEngine engine = new WorkflowEngine(dag, ApprovalGate.autoApprove(), metrics);
        WorkflowContext context = new WorkflowContext("test");

        engine.run(context);

        var entries = context.entries();
        assertEquals(3, entries.size(), "one entry per stage");

        // Stages should appear in dependency order.
        assertEquals(a, entries.get(0).stage());
        assertEquals(b, entries.get(1).stage());
        assertEquals(c, entries.get(2).stage());

        // Every entry has a non-null source tag.
        entries.forEach(e -> assertNotNull(e.source(), "source must not be null"));

        // Timestamps are non-decreasing.
        for (int i = 1; i < entries.size(); i++) {
            assertFalse(entries.get(i).timestamp().isBefore(entries.get(i - 1).timestamp()),
                    "timestamps should be non-decreasing");
        }
    }

    // ---------------------------------------------------------------
    // 9. Metrics match actual outcomes
    // ---------------------------------------------------------------

    @Test
    void metricsMatchActualOutcomes() {
        StageId a = new StageId("a"); // passes normally
        StageId b = new StageId("b"); // fails once, retries, passes
        StageId c = new StageId("c"); // succeeds via fallback source

        Dag dag = new Dag(List.of(
                new Stage(passingAgent(a),              Set.of(),  null, 0, false),
                new Stage(failThenSucceedAgent(b, 1),   Set.of(a), null, 1, false),
                new Stage(fallbackSourceAgent(c),       Set.of(b), null, 0, false)));

        RunMetrics metrics = new RunMetrics();
        WorkflowEngine engine = new WorkflowEngine(dag, ApprovalGate.autoApprove(), metrics);

        boolean completed = engine.run(new WorkflowContext("test"));

        assertTrue(completed);

        // a: 1 attempt. b: 2 attempts (fail + retry). c: 1 attempt. Total = 4.
        assertEquals(4, metrics.stagesRun(), "total attempts");
        assertEquals(3, metrics.stagesPassed(), "all three stages passed");
        assertEquals(1, metrics.retries(), "one retry on b");
        assertEquals(1, metrics.fallbacks(), "one fallback on c");
        assertEquals(0, metrics.rollbacks(), "no rollbacks");

        // Success rate = passed / run = 3/4.
        assertEquals(0.75, metrics.successRate(), 0.001);

        // Total time should be positive.
        assertTrue(metrics.totalTime().toMillis() >= 0, "total time should be non-negative");
    }
}
