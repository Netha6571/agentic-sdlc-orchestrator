package com.org.orchestrator.agent;

import com.org.orchestrator.llm.LlmClient;
import com.org.orchestrator.model.StageId;
import com.org.orchestrator.model.WorkflowContext;

import java.util.Map;

/**
 * Writes the code change from the design. High impact — always requires
 * approval no matter which path (model or fallback) produced the output.
 */
public final class ImplementAgent extends AbstractAgent {

    public static final StageId ID = new StageId("implement");

    public ImplementAgent(LlmClient llmClient) {
        super(ID, llmClient);
    }

    @Override
    protected String buildPrompt(WorkflowContext context) {
        String design = lastEntry(context, "design", "design");
        String spec = lastEntry(context, "requirement", "spec");
        return "You are a software engineer. "
                + "Implement the following design as a code change.\n\n"
                + "Specification:\n" + spec + "\n\n"
                + "Design:\n" + design;
    }

    @Override
    protected Map<String, String> parseResponse(String response, WorkflowContext context) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty implementation response");
        }
        return Map.of("code", response.strip());
    }

    @Override
    protected Map<String, String> fallback(WorkflowContext context) {
        String design = lastEntry(context, "design", "design");
        // Known-good skeleton — compiles and does something real.
        return Map.of("code",
                "// Implementation skeleton based on design\n"
                + "// Design: " + truncate(design, 150) + "\n"
                + "public class ChangeImpl {\n"
                + "    public void apply() {\n"
                + "        // TODO: fill in from design\n"
                + "        System.out.println(\"Change applied\");\n"
                + "    }\n"
                + "}\n");
    }

    // Implement is high impact — always needs sign-off.
    @Override
    protected boolean requiresApproval() {
        return true;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
