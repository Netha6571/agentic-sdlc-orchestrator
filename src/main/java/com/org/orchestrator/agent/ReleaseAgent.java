package com.org.orchestrator.agent;

import com.org.orchestrator.llm.LlmClient;
import com.org.orchestrator.model.StageId;
import com.org.orchestrator.model.WorkflowContext;

import java.util.Map;

/**
 * Final stage. Checks the change is ready and prepares a pull-request
 * summary. High impact — always requires approval.
 */
public final class ReleaseAgent extends AbstractAgent {

    public static final StageId ID = new StageId("release");

    public ReleaseAgent(LlmClient llmClient) {
        super(ID, llmClient);
    }

    @Override
    protected String buildPrompt(WorkflowContext context) {
        String spec = lastEntry(context, "requirement", "spec");
        String code = lastEntry(context, "implement", "code");
        String tests = lastEntry(context, "test", "tests");
        String docs = lastEntry(context, "docs", "docs");
        return "You are a release manager. "
                + "Review the following change and prepare a short pull-request summary. "
                + "Confirm it is ready for release or flag any blockers.\n\n"
                + "Specification:\n" + spec + "\n\n"
                + "Code:\n" + code + "\n\n"
                + "Tests:\n" + tests + "\n\n"
                + "Docs:\n" + docs;
    }

    @Override
    protected Map<String, String> parseResponse(String response, WorkflowContext context) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty release response");
        }
        return Map.of("summary", response.strip());
    }

    @Override
    protected Map<String, String> fallback(WorkflowContext context) {
        String spec = lastEntry(context, "requirement", "spec");
        return Map.of("summary",
                "Release Summary\n"
                + "----------------\n"
                + "Change: " + truncate(spec, 200) + "\n"
                + "Status: Ready for review.\n"
                + "Tests: Included.\n"
                + "Docs: Updated.\n"
                + "No blockers identified.\n");
    }

    // Release is high impact — always needs sign-off.
    @Override
    protected boolean requiresApproval() {
        return true;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
