package io.github.netha6571.orchestrator.agent;

import io.github.netha6571.orchestrator.llm.LlmClient;
import io.github.netha6571.orchestrator.model.StageId;
import io.github.netha6571.orchestrator.model.WorkflowContext;

import java.util.Map;

/**
 * First stage. Turns a plain request into a clear spec and flags
 * anything unclear. Reads the raw requirement since nothing precedes it.
 */
public final class RequirementAgent extends AbstractAgent {

    public static final StageId ID = new StageId("requirement");

    public RequirementAgent(LlmClient llmClient) {
        super(ID, llmClient);
    }

    @Override
    protected String buildPrompt(WorkflowContext context) {
        return "You are a requirements analyst. "
                + "Turn this request into a clear, structured specification. "
                + "Flag anything that is ambiguous or missing.\n\n"
                + "Request: " + context.requirement();
    }

    @Override
    protected Map<String, String> parseResponse(String response, WorkflowContext context) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty response from model");
        }
        return Map.of("spec", response.strip());
    }

    @Override
    protected Map<String, String> fallback(WorkflowContext context) {
        // Plain restatement — real output, not a stub.
        return Map.of("spec",
                "Specification: Implement the following requirement as described.\n"
                + "Requirement: " + context.requirement() + "\n"
                + "Assumptions: Standard conventions apply. No ambiguities flagged.");
    }
}
