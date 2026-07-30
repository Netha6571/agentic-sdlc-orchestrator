package com.org.orchestrator.agent;

import com.org.orchestrator.llm.LlmClient;
import com.org.orchestrator.model.StageId;
import com.org.orchestrator.model.WorkflowContext;

import java.util.Map;

/**
 * Reads the spec from the requirement stage, works out what the change
 * touches, and sketches a design. Fallback: a plain design note.
 */
public final class DesignAgent extends AbstractAgent {

    public static final StageId ID = new StageId("design");

    public DesignAgent(LlmClient llmClient) {
        super(ID, llmClient);
    }

    @Override
    protected String buildPrompt(WorkflowContext context) {
        String spec = lastEntry(context, "requirement", "spec");
        return "You are a software designer. "
                + "Given this specification, identify the components affected "
                + "and sketch a design for the change.\n\n"
                + "Specification:\n" + spec;
    }

    @Override
    protected Map<String, String> parseResponse(String response, WorkflowContext context) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty design response");
        }
        return Map.of("design", response.strip());
    }

    @Override
    protected Map<String, String> fallback(WorkflowContext context) {
        String spec = lastEntry(context, "requirement", "spec");
        return Map.of("design",
                "Design note: Modify existing components to satisfy the specification.\n"
                + "Affected area: Core module.\n"
                + "Approach: Minimal change to existing structure.\n"
                + "Based on: " + truncate(spec, 200));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
