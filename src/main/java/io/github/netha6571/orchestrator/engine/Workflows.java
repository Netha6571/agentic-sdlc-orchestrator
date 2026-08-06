package io.github.netha6571.orchestrator.engine;

import io.github.netha6571.orchestrator.agent.DesignAgent;
import io.github.netha6571.orchestrator.agent.DocsAgent;
import io.github.netha6571.orchestrator.agent.ImplementAgent;
import io.github.netha6571.orchestrator.agent.ReleaseAgent;
import io.github.netha6571.orchestrator.agent.RequirementAgent;
import io.github.netha6571.orchestrator.agent.TestAgent;
import io.github.netha6571.orchestrator.codebase.CodebaseService;
import io.github.netha6571.orchestrator.llm.LlmClient;

import java.util.List;
import java.util.Set;

/**
 * Factory that builds the standard six-stage graph.
 *
 * The dependency shape:
 *   requirement -> design -> [approval] implement -> test  -> [approval] release
 *                                                \-> docs -/
 *
 * Test and docs both depend only on implement, so the engine runs them
 * at the same time. Release depends on both, so it waits for the pair.
 */
public final class Workflows {

    // Default retry budget — agents get one retry before giving up.
    private static final int DEFAULT_RETRIES = 1;

    private Workflows() {
        // Factory class — no instances.
    }

    /**
     * Build the standard SDLC workflow with all six stages wired.
     * No codebase context — agents work from the prompt only.
     */
    public static Dag standard(LlmClient llmClient) {
        return standard(llmClient, null);
    }

    /**
     * Build the standard SDLC workflow with codebase access.
     * When codebaseService is non-null, agents that support it
     * (currently ImplementAgent) will read the target project.
     */
    public static Dag standard(LlmClient llmClient, CodebaseService codebaseService) {
        Stage requirement = new Stage(
                new RequirementAgent(llmClient),
                Set.of(),                       // first stage — no dependencies
                null,                           // default entry check (always pass)
                DEFAULT_RETRIES,
                false);

        Stage design = new Stage(
                new DesignAgent(llmClient),
                Set.of(RequirementAgent.ID),    // depends on requirement
                null,
                DEFAULT_RETRIES,
                false);

        Stage implement = new Stage(
                new ImplementAgent(llmClient, codebaseService),
                Set.of(DesignAgent.ID),         // depends on design
                null,
                DEFAULT_RETRIES,
                true);                          // high impact — needs approval

        Stage test = new Stage(
                new TestAgent(llmClient),
                Set.of(ImplementAgent.ID),      // depends only on implement
                null,
                DEFAULT_RETRIES,
                false);

        Stage docs = new Stage(
                new DocsAgent(llmClient),
                Set.of(ImplementAgent.ID),      // depends only on implement (runs parallel with test)
                null,
                DEFAULT_RETRIES,
                false);

        Stage release = new Stage(
                new ReleaseAgent(llmClient),
                Set.of(TestAgent.ID, DocsAgent.ID),  // waits for both test and docs
                null,
                DEFAULT_RETRIES,
                true);                          // high impact — needs approval

        return new Dag(List.of(requirement, design, implement, test, docs, release));
    }
}
