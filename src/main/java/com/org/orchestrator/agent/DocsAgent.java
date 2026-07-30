package com.org.orchestrator.agent;

import com.org.orchestrator.llm.LlmClient;
import com.org.orchestrator.model.StageId;
import com.org.orchestrator.model.WorkflowContext;

import java.util.Map;

/**
 * Updates documentation for the change. Runs in parallel with test —
 * both depend only on implement. Fallback: a short doc stub.
 */
public final class DocsAgent extends AbstractAgent {

    public static final StageId ID = new StageId("docs");

    public DocsAgent(LlmClient llmClient) {
        super(ID, llmClient);
    }

    @Override
    protected String buildPrompt(WorkflowContext context) {
        String code = lastEntry(context, "implement", "code");
        String spec = lastEntry(context, "requirement", "spec");
        return "You are a technical writer. "
                + "Update the documentation to reflect this code change.\n\n"
                + "Specification:\n" + spec + "\n\n"
                + "Code change:\n" + code;
    }

    @Override
    protected Map<String, String> parseResponse(String response, WorkflowContext context) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty docs response");
        }
        return Map.of("docs", response.strip());
    }

    @Override
    protected Map<String, String> fallback(WorkflowContext context) {
        String spec = lastEntry(context, "requirement", "spec");
        return Map.of("docs",
                "# Change Documentation\n\n"
                + "## Summary\n"
                + "A change was applied to the codebase.\n\n"
                + "## Details\n"
                + truncate(spec, 300) + "\n\n"
                + "## Impact\n"
                + "See the implementation and test outputs for specifics.\n");
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
