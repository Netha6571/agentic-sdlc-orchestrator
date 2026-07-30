package com.org.orchestrator.cli;

import com.org.orchestrator.codebase.CodebaseService;
import com.org.orchestrator.codebase.FileSystemCodebaseService;
import com.org.orchestrator.engine.Dag;
import com.org.orchestrator.engine.WorkflowEngine;
import com.org.orchestrator.engine.Workflows;
import com.org.orchestrator.governance.ApprovalGate;
import com.org.orchestrator.governance.ConfidenceScore;
import com.org.orchestrator.governance.RunMetrics;
import com.org.orchestrator.llm.GoogleAdkLlmClient;
import com.org.orchestrator.llm.LlmClient;
import com.org.orchestrator.llm.StubLlmClient;
import com.org.orchestrator.model.ArtifactSource;
import com.org.orchestrator.model.StageId;
import com.org.orchestrator.model.StageState;
import com.org.orchestrator.model.WorkflowContext;

import java.nio.file.Path;
import java.util.Map;

/**
 * Command-line entry point. Reads flags, picks the model and gate,
 * builds the graph, runs the engine, and prints the four output sections.
 *
 * Flags:
 *   --repo <path>    Point the orchestrator at an external codebase.
 *   --real-model     Use the Google ADK client (needs GOOGLE_API_KEY).
 *   --interactive    Ask for approval on the console at high-impact stages.
 *   --force-fail     Make the stub model fail on purpose (shows fallback).
 *
 * The last non-flag argument is the requirement. If none is given,
 * a sensible default is used.
 */
public final class Main {

    private static final String DEFAULT_REQUIREMENT = "Add custom alias support";

    public static void main(String[] args) {
        boolean useRealModel = false;
        boolean interactive  = false;
        boolean forceFail    = false;
        String requirement   = null;
        String repoPath      = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--real-model"  -> useRealModel = true;
                case "--interactive" -> interactive  = true;
                case "--force-fail"  -> forceFail    = true;
                case "--repo"       -> {
                    if (i + 1 < args.length) {
                        repoPath = args[++i];
                    } else {
                        System.err.println("--repo requires a path argument");
                        System.exit(1);
                    }
                }
                default             -> requirement   = args[i];
            }
        }

        if (requirement == null || requirement.isBlank()) {
            requirement = DEFAULT_REQUIREMENT;
        }

        // --- Wire everything ---

        LlmClient llmClient;
        if (useRealModel) {
            llmClient = new GoogleAdkLlmClient();
        } else {
            llmClient = new StubLlmClient(forceFail);
        }

        CodebaseService codebaseService = null;
        if (repoPath != null) {
            codebaseService = new FileSystemCodebaseService(Path.of(repoPath));
        }

        ApprovalGate gate = interactive
                ? ApprovalGate.console()
                : ApprovalGate.autoApprove();

        RunMetrics metrics = new RunMetrics();
        Dag dag = Workflows.standard(llmClient, codebaseService);
        WorkflowEngine engine = new WorkflowEngine(dag, gate, metrics);
        WorkflowContext context = new WorkflowContext(requirement);

        System.out.println("=== Agentic SDLC Orchestrator ===");
        System.out.println("Model:       " + llmClient.name());
        System.out.println("Approval:    " + (interactive ? "console (interactive)" : "auto-approve"));
        if (codebaseService != null) {
            System.out.println("Target repo: " + codebaseService.rootPath());
        } else {
            System.out.println("Target repo: (none — prompt-only mode)");
        }
        System.out.println("Requirement: " + requirement);
        System.out.println();

        // --- Run ---

        boolean completed = engine.run(context);

        // --- Print output sections ---

        System.out.println();
        printStageStates(engine.stageStates());
        printDecisionTrail(context);
        printConfidence(engine.stageStates(), metrics);
        printMetrics(metrics);
        printOutcome(completed);
    }

    // --- Section 1: Final state of every stage ---

    private static void printStageStates(Map<StageId, StageState> states) {
        System.out.println("=== Stage States ===");
        states.forEach((id, state) ->
                System.out.printf("  %-15s %s%n", id, state));
        System.out.println();
    }

    // --- Section 2: Decision trail ---

    private static void printDecisionTrail(WorkflowContext context) {
        System.out.println("=== Decision Trail ===");
        if (context.entries().isEmpty()) {
            System.out.println("  (no entries)");
        } else {
            for (WorkflowContext.Entry entry : context.entries()) {
                System.out.printf("  [%s] %-15s %-10s %s = %s%n",
                        entry.timestamp(),
                        entry.stage(),
                        entry.source(),
                        entry.key(),
                        truncate(entry.value(), 120));
                if (entry.reason() != null) {
                    System.out.println("    reason: " + entry.reason());
                }
            }
        }
        System.out.println();
    }

    // --- Section 3: Confidence score with signals ---

    private static void printConfidence(Map<StageId, StageState> states, RunMetrics metrics) {
        // Determine signals from observable outcomes.
        boolean testsPassed = states.getOrDefault(new StageId("test"), StageState.PENDING)
                == StageState.PASSED;
        boolean codeCompiled = states.getOrDefault(new StageId("implement"), StageState.PENDING)
                == StageState.PASSED;
        // Static checks are considered clean if all stages that ran passed.
        boolean staticChecksClean = metrics.stagesRun() > 0
                && metrics.stagesRun() == metrics.stagesPassed();
        boolean runFinished = states.values().stream().allMatch(StageState::isTerminal);

        ConfidenceScore score = new ConfidenceScore()
                .testsPassed(testsPassed)
                .codeCompiled(codeCompiled)
                .staticChecksClean(staticChecksClean)
                .runFinished(runFinished)
                .fallbackCount(metrics.fallbacks())
                .retryCount(metrics.retries());

        System.out.println("=== Confidence Score ===");
        System.out.printf("  Score: %.2f%n", score.compute());
        System.out.println("  Signals:");
        for (String signal : score.signals()) {
            System.out.println("    " + signal);
        }
        System.out.println();
    }

    // --- Section 4: Metrics summary ---

    private static void printMetrics(RunMetrics metrics) {
        System.out.println("=== Metrics ===");
        System.out.println("  " + metrics);
        System.out.println();
    }

    // --- Section 5: Outcome ---

    private static void printOutcome(boolean completed) {
        System.out.println("=== Outcome ===");
        if (completed) {
            System.out.println("  Run FINISHED — all stages reached a terminal state.");
        } else {
            System.out.println("  Run STOPPED SAFELY — not all stages could complete.");
        }
        System.out.println();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "(null)";
        // Replace newlines with spaces for single-line display.
        String flat = s.replace('\n', ' ').replace('\r', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max - 3) + "...";
    }
}
