package io.github.netha6571.orchestrator.llm;

/**
 * The single surface agents call to get text from a language model.
 * Kept to two methods so swapping implementations never ripples into agent code.
 */
public interface LlmClient {

    /**
     * Send a prompt and get text back.
     * Throws LlmException on any failure — timeout, bad response, network error.
     */
    String call(String prompt) throws LlmException;

    /**
     * A human-readable name for the logs and audit trail.
     */
    String name();
}
