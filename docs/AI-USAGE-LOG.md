Cycle 1 — Model types
- Spec(s): 00, 01
- Prompt: Read specs/00-overview.md and specs/01-architecture.md. Generate only the model package under com.org.orchestrator.model: StageId, StageState, ArtifactSource, StageResult, and WorkflowContext. These are plain data types with validation only — no engine logic, no LLM, no agents. WorkflowContext must be append-only and safe for concurrent writes. Do not create any other package yet. Plain code, short comments saying why not what
- Outcome: ArtifactSource.java, StageId.java, StageResult.java, StageState.java, WorkflowContext.java

Cycle 2 - LLM Client
- Spec: 03
- Prompt: Read specs/03-agents-and-llm.md, the language model section. Generate only the llm package: an LlmClient interface, an LlmException, a StubLlmClient (offline, deterministic, with a switch to fail on purpose), and an GoogleAdkLlmClient. Do not touch the agents yet. Do not add an SDK dependency
- Outcome: GoogleAdkLlmClient.java, LlmClient.java, LlmException.java, StubLlmClient.java
