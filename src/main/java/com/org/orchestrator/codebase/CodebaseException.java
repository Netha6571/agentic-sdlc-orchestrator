package com.org.orchestrator.codebase;

/**
 * Thrown when a codebase operation fails — file not found,
 * unreadable, path outside root, etc.
 */
public class CodebaseException extends RuntimeException {

    public CodebaseException(String message) {
        super(message);
    }

    public CodebaseException(String message, Throwable cause) {
        super(message, cause);
    }
}
