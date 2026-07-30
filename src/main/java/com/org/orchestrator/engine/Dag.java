package com.org.orchestrator.engine;

import com.org.orchestrator.model.StageId;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The whole workflow graph. Built once, never changed while running.
 *
 * Validates on construction:
 *   1. Every dependency referenced by a stage must exist in the graph.
 *   2. No cycles — a graph with a cycle can never finish.
 * Both checks fail fast so a misconfigured workflow surfaces immediately,
 * not mid-run when a stage hangs forever.
 */
public final class Dag {

    private final Map<StageId, Stage> stages;

    public Dag(List<Stage> stageList) {
        Objects.requireNonNull(stageList, "stageList");
        if (stageList.isEmpty()) {
            throw new IllegalArgumentException("Dag must have at least one stage");
        }

        Map<StageId, Stage> map = new LinkedHashMap<>();
        for (Stage stage : stageList) {
            if (map.containsKey(stage.id())) {
                throw new IllegalArgumentException("Duplicate stage id: " + stage.id());
            }
            map.put(stage.id(), stage);
        }

        // Every dependency must resolve to a stage in this graph.
        for (Stage stage : stageList) {
            for (StageId dep : stage.dependencies()) {
                if (!map.containsKey(dep)) {
                    throw new IllegalArgumentException(
                            stage.id() + " depends on " + dep + ", which is not in the graph");
                }
            }
        }

        // A cycle means the run can never finish — reject it now.
        detectCycles(map);

        this.stages = Collections.unmodifiableMap(map);
    }

    public Stage get(StageId id) {
        return stages.get(id);
    }

    public Map<StageId, Stage> stages() {
        return stages;
    }

    public int size() {
        return stages.size();
    }

    /**
     * Topological-sort cycle check via DFS colouring.
     * White = unvisited, grey = in current path, black = fully explored.
     * A back-edge (hitting grey) means a cycle.
     */
    private static void detectCycles(Map<StageId, Stage> map) {
        Set<StageId> white = new HashSet<>(map.keySet());
        Set<StageId> grey  = new HashSet<>();
        Set<StageId> black = new HashSet<>();

        for (StageId id : map.keySet()) {
            if (white.contains(id)) {
                dfs(id, map, white, grey, black);
            }
        }
    }

    private static void dfs(StageId current,
                            Map<StageId, Stage> map,
                            Set<StageId> white,
                            Set<StageId> grey,
                            Set<StageId> black) {

        white.remove(current);
        grey.add(current);

        for (StageId dep : map.get(current).dependencies()) {
            if (grey.contains(dep)) {
                throw new IllegalArgumentException(
                        "Cycle detected: " + current + " -> " + dep);
            }
            if (white.contains(dep)) {
                dfs(dep, map, white, grey, black);
            }
        }

        grey.remove(current);
        black.add(current);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Dag[\n");
        stages.values().forEach(s -> sb.append("  ").append(s).append("\n"));
        sb.append("]");
        return sb.toString();
    }
}
