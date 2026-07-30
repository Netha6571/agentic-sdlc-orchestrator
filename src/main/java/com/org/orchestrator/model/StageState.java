package com.org.orchestrator.model;

/**
 * The lifecycle states a stage moves through.
 * Four are terminal — once reached, the engine stops revisiting the stage.
 */
public enum StageState {

    PENDING,
    READY,
    RUNNING,
    AWAITING_APPROVAL,
    PASSED,
    FAILED,
    ROLLED_BACK,
    SKIPPED;

    // Terminal states are grouped here so the engine can ask once
    // instead of scattering the same switch in several places.

    public boolean isTerminal() {
        return this == PASSED || this == FAILED
            || this == ROLLED_BACK || this == SKIPPED;
    }
}
