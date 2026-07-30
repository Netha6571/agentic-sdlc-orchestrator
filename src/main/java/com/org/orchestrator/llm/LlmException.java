package com.org.orchestrator.llm;

/**
 * Thrown on any model failure — network, timeout, bad response, auth.
 * Its presence is the signal that triggers the fallback path in the base agent.
 * Wraps the original cause so diagnostics are not lost.
 */
public class LlmException extends Exception {

    public LlmException(String message) {
        super(message);
    }

    public LlmException(String message, Throwable cause) {
        super(message, cause);
    }
}
