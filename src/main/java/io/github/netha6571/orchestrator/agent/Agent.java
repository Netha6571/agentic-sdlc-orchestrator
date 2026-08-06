package io.github.netha6571.orchestrator.agent;

import io.github.netha6571.orchestrator.model.StageId;
import io.github.netha6571.orchestrator.model.StageResult;
import io.github.netha6571.orchestrator.model.WorkflowContext;

/**
 * The whole agent contract: report your stage, do the work.
 * Kept this small so adding a stage never forces an interface change.
 */
public interface Agent {

    StageId stageId();

    StageResult execute(WorkflowContext context);
}
