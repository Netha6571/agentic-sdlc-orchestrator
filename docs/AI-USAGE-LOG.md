Cycle 1 — Model types
- Spec(s): 00, 01
- Prompt: Read specs/00-overview.md and specs/01-architecture.md. Generate only the model package under com.org.orchestrator.model: StageId, StageState, ArtifactSource, StageResult, and WorkflowContext. These are plain data types with validation only — no engine logic, no LLM, no agents. WorkflowContext must be append-only and safe for concurrent writes. Do not create any other package yet. Plain code, short comments saying why not what
- Outcome: ArtifactSource.java, StageId.java, StageResult.java, StageState.java, WorkflowContext.java

