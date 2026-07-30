package com.org.orchestrator.agent;

import com.org.orchestrator.model.StageId;
import com.org.orchestrator.model.StageResult;
import com.org.orchestrator.model.WorkflowContext;

/**
 * The whole agent contract: report your stage, do the work.
 * Kept this small so adding a stage never forces an interface change.
 */
public interface Agent {

    StageId stageId();

    StageResult execute(WorkflowContext context);
}
