Cycle 1 — Model types
- Spec(s): 00, 01
- Prompt: Read specs/00-overview.md and specs/01-architecture.md. Generate only the model package under com.org.orchestrator.model: StageId, StageState, ArtifactSource, StageResult, and WorkflowContext. These are plain data types with validation only — no engine logic, no LLM, no agents. WorkflowContext must be append-only and safe for concurrent writes. Do not create any other package yet. Plain code, short comments saying why not what
- Outcome: ArtifactSource.java, StageId.java, StageResult.java, StageState.java, WorkflowContext.java

Cycle 2 - LLM Client
- Spec: 03
- Prompt: Read specs/03-agents-and-llm.md, the language model section. Generate only the llm package: an LlmClient interface, an LlmException, a StubLlmClient (offline, deterministic, with a switch to fail on purpose), and an GoogleAdkLlmClient. Do not touch the agents yet. Do not add an SDK dependency
- Outcome: GoogleAdkLlmClient.java, LlmClient.java, LlmException.java, StubLlmClient.java

Cycle 3 - Agent
- Spec: 03
- Prompt: Read specs/03-agents-and-llm.md in full. Generate the agent package: the Agent interface, an AbstractAgent base class holding the try-LLM-then-fallback pattern once, and the six agents (requirement, design, implement, test, docs, release). Every agent must have a fallback that returns real valid output, not a stub that throws. Implement and release are marked as needing approval. Depend only on the model and llm packages already built
- Outcome: Agent.java, AbstractAgent.java, RequirementsAgent.java, DesignAgent.java, ImplementationAgent.java, TestAgent.java, DocsAgent.java, ReleaseAgent.java

Cycle 4 - Graph
- Spec: 01, 02
- Prompt: Read specs/01-architecture.md and specs/02-engine.md, the graph parts only. Generate the engine package's graph types: Stage (agent, dependencies, entry check, retry budget, approval flag) and Dag (validates that dependencies exist and rejects cycles on construction). Also generate the Workflows factory that wires the six stages so test and docs both depend only on implement. Do not write the WorkflowEngine yet.
- Outcome: Dag.java, Stage.java, Workflows.java

Cycle 5 - Governance
- Spec: 04
- Prompt: Read specs/04-governance.md. Generate the governance package: RunMetrics (success rate, retries, rollbacks, fallbacks, MTTR, total time), ApprovalGate (an interface with an auto-approve version and a console version), and ConfidenceScore computed from run outcomes using the exact formula in the spec — never a number the model reports. Depend only on the model package
- Outcome: ApprovalGate.java, ConfidenceScore.java, RunMetrics.java

Cycle 6 - Engine
- Spec: 02
- Prompt: Read specs/02-engine.md in full. Generate the WorkflowEngine in the engine package. It keeps a state per stage, promotes stages to ready when dependencies pass and the entry check opens, runs the ready frontier in parallel on a small thread pool, joins before continuing, applies results (record to context, run approval gate if needed, bounded retries, safe-stop), and leaves clearly-commented seams for rollback and re-planning. The engine must contain no mention of URLs, the shortener, or any agent by name
- Outcome: WorkflowEngine.java

Cycle 7 - CLI
- Spec: 05
- Prompt: Read specs/05-build-and-run.md. Generate the cli Main class: parse flags (real vs stub model, interactive vs auto approval, force-fail), take the requirement as the last non-flag argument, wire everything, run the engine, and print final stage states, the decision lineage, the confidence score with its signals, and the metrics summary
- Outcome: Main.java

