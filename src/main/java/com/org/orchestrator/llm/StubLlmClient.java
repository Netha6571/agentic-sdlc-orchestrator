package com.org.orchestrator.llm;

/**
 * Offline, deterministic stand-in. Returns a fixed answer with no network
 * and no API key, so the engine runs anywhere and tests are repeatable.
 *
 * The forceFailure flag exists specifically to demo the fallback path:
 * turn it on, and every call throws, showing the agents recover.
 */
public final class StubLlmClient implements LlmClient {

    private final boolean forceFailure;

    public StubLlmClient() {
        this(false);
    }

    public StubLlmClient(boolean forceFailure) {
        this.forceFailure = forceFailure;
    }

    @Override
    public String call(String prompt) throws LlmException {
        if (forceFailure) {
            throw new LlmException("Stub forced failure — demonstrating fallback path");
        }
        // Deterministic answer keyed off the prompt so different stages
        // get distinguishable output without needing a real model.
        return "[stub] Response to: " + summarise(prompt);
    }

    @Override
    public String name() {
        return "stub" + (forceFailure ? " (force-fail)" : "");
    }

    // Keep the echoed prompt short so logs stay readable.
    private static String summarise(String prompt) {
        if (prompt == null) return "(null)";
        String trimmed = prompt.strip();
        if (trimmed.length() <= 80) return trimmed;
        return trimmed.substring(0, 77) + "...";
    }
}
