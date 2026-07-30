package com.org.orchestrator.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Shared memory for one workflow run.
 *
 * Append-only: entries are never removed or replaced, so the list doubles
 * as the audit trail. CopyOnWriteArrayList handles concurrent writes
 * (test and docs stages run in parallel) without external locking.
 */
public final class WorkflowContext {

    private final String requirement;
    private final CopyOnWriteArrayList<Entry> entries;

    public WorkflowContext(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            throw new IllegalArgumentException("Requirement must not be blank");
        }
        this.requirement = requirement.strip();
        this.entries = new CopyOnWriteArrayList<>();
    }

    public String requirement() {
        return requirement;
    }

    /**
     * Append a new entry. Safe to call from multiple threads.
     */
    public void append(StageId stage, String key, String value,
                       ArtifactSource source, String reason) {

        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(source, "source");

        entries.add(new Entry(Instant.now(), stage, key, value, source, reason));
    }

    /**
     * Returns an unmodifiable snapshot — readers never see a half-written state.
     */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public int size() {
        return entries.size();
    }

    /**
     * One timestamped record in the trail.
     * Records are created internally; nothing outside this class constructs them.
     */
    public record Entry(
            Instant timestamp,
            StageId stage,
            String key,
            String value,
            ArtifactSource source,
            String reason
    ) {
        public Entry {
            Objects.requireNonNull(timestamp, "timestamp");
            Objects.requireNonNull(stage, "stage");
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(source, "source");
        }

        @Override
        public String toString() {
            return "[" + timestamp + "] " + stage + " | " + key + " = "
                    + value + " (" + source
                    + (reason != null ? ", " + reason : "") + ")";
        }
    }
}
