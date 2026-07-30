package com.org.orchestrator.agent;

import com.org.orchestrator.llm.LlmClient;
import com.org.orchestrator.model.StageId;
import com.org.orchestrator.model.WorkflowContext;

import java.util.Map;

/**
 * Writes tests for the change. Runs in parallel with docs —
 * both depend only on implement. Fallback: a simple working test.
 */
public final class TestAgent extends AbstractAgent {

    public static final StageId ID = new StageId("test");

    public TestAgent(LlmClient llmClient) {
        super(ID, llmClient);
    }

    @Override
    protected String buildPrompt(WorkflowContext context) {
        String code = lastEntry(context, "implement", "code");
        String spec = lastEntry(context, "requirement", "spec");
        return "You are a test engineer. "
                + "Write tests that verify this code change meets the specification.\n\n"
                + "Specification:\n" + spec + "\n\n"
                + "Code change:\n" + code;
    }

    @Override
    protected Map<String, String> parseResponse(String response, WorkflowContext context) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Empty test response");
        }
        return Map.of("tests", response.strip());
    }

    @Override
    protected Map<String, String> fallback(WorkflowContext context) {
        // A simple working test — not a stub that throws.
        return Map.of("tests",
                "import org.junit.jupiter.api.Test;\n"
                + "import static org.junit.jupiter.api.Assertions.*;\n\n"
                + "class ChangeImplTest {\n"
                + "    @Test\n"
                + "    void changeAppliesWithoutError() {\n"
                + "        var impl = new ChangeImpl();\n"
                + "        assertDoesNotThrow(impl::apply);\n"
                + "    }\n"
                + "}\n");
    }
}
