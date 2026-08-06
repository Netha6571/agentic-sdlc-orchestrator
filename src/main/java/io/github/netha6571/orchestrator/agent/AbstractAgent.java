package io.github.netha6571.orchestrator.agent;

import io.github.netha6571.orchestrator.llm.LlmClient;
import io.github.netha6571.orchestrator.llm.LlmException;
import io.github.netha6571.orchestrator.model.ArtifactSource;
import io.github.netha6571.orchestrator.model.StageId;
import io.github.netha6571.orchestrator.model.StageResult;
import io.github.netha6571.orchestrator.model.WorkflowContext;

import java.util.Map;

/**
 * Holds the try-LLM-then-fallback pattern once so each real agent
 * only fills in three small pieces: buildPrompt, parseResponse, fallback.
 *
 * The pattern is always the same:
 *   1. Build a prompt from the context.
 *   2. Send it to the model.
 *   3. Parse the answer into outputs.
 *   4. If anything throws, run the fallback instead.
 * Recording which path was taken is done here, not in the subclass.
 */
public abstract class AbstractAgent implements Agent {

    private final StageId stageId;
    private final LlmClient llmClient;

    protected AbstractAgent(StageId stageId, LlmClient llmClient) {
        this.stageId = stageId;
        this.llmClient = llmClient;
    }

    @Override
    public final StageId stageId() {
        return stageId;
    }

    @Override
    public final StageResult execute(WorkflowContext context) {
        try {
            String prompt = buildPrompt(context);
            String response = llmClient.call(prompt);
            Map<String, String> outputs = parseResponse(response, context);

            // Record model outputs in the shared context.
            outputs.forEach((key, value) ->
                    context.append(stageId, key, value, ArtifactSource.MODEL, "via " + llmClient.name()));

            return new StageResult(stageId, true, ArtifactSource.MODEL,
                    outputs, "Completed via " + llmClient.name(), requiresApproval());

        } catch (LlmException | RuntimeException e) {
            // Any failure — network, timeout, bad parse — triggers fallback.
            Map<String, String> outputs = fallback(context);

            outputs.forEach((key, value) ->
                    context.append(stageId, key, value, ArtifactSource.FALLBACK,
                            "fallback after: " + e.getMessage()));

            return new StageResult(stageId, true, ArtifactSource.FALLBACK,
                    outputs, "Fallback used: " + e.getMessage(), requiresApproval());
        }
    }

    // --- The three pieces each subclass fills in ---

    /** Build a prompt from what earlier stages left in the context. */
    protected abstract String buildPrompt(WorkflowContext context);

    /** Parse the model's response into named outputs. Throw if unusable. */
    protected abstract Map<String, String> parseResponse(String response, WorkflowContext context);

    /** Produce real, valid output when the model path fails. Never throws. */
    protected abstract Map<String, String> fallback(WorkflowContext context);

    /** Override to return true for high-impact stages (implement, release). */
    protected boolean requiresApproval() {
        return false;
    }

    // --- Helpers for subclasses ---

    /** Find the most recent value for a key written by a given stage. */
    protected String lastEntry(WorkflowContext context, String stageName, String key) {
        var entries = context.entries();
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            if (entry.stage().name().equals(stageName) && entry.key().equals(key)) {
                return entry.value();
            }
        }
        return "";
    }
}
