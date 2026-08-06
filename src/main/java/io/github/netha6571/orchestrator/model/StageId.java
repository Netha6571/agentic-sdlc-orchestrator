package io.github.netha6571.orchestrator.model;

import java.util.Objects;

/**
 * Wraps a stage name so references stay stable even if display names change.
 * Reject blanks at the boundary; everything downstream can trust the value.
 */
public final class StageId {

    private final String name;

    public StageId(String name) {
        // Fail fast — a blank id would silently break lookups later.
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Stage id must not be blank");
        }
        this.name = name.strip();
    }

    public String name() {
        return name;
    }

    // Identity is by name alone, so sets and maps work as expected.

    @Override
    public boolean equals(Object o) {
        return o instanceof StageId other && name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
