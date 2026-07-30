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


Cycle 8 - External Codebase as reference
- Prompt: Generate CodebaseService abstraction that agents call into to look at the external codebase via file system for now and the ImplementationAgent uses it read the codebase and look at the prompt add what code needs to be added/updated/removed.
Notes: Prompt did not update the README with the external codebase reference automatically.
- Outcome: FileSystemCodebaseService.java, CodebaseService.java


Cycle 9 - Update ReadME
- Prompt: Look at the codebase end to end and update the read me file with the details and also include a high level architecture diagram


Cycle 10 - Add Junit
- Prompt: Write a JUnit 5 test suite for the workflow engine in this project. The goal is to prove the engine's orchestration behavior is correct, not just that classes exist. Test the real behavior a reviewer would want to see verified.

Use the stub LLM client for all tests so they run offline with no API key and are fully repeatable. Do not call the real model. Where you need agents, use the existing stub-backed agents or small fake agents you write in the test, whichever keeps the test clear.

Cover these behaviors, one or more tests each:

The engine runs stages in dependency order. A stage does not start until the stages it depends on have passed.

Independent stages run in parallel. Set up two stages that both depend only on one earlier stage, and confirm the engine runs them together rather than one after the other. Also confirm a later stage that depends on both waits for both to finish before it starts.

A stage that fails after using up its retry budget causes the run to stop safely. Its dependent stages never run, and the run reports that it stopped rather than finishing.

The retry budget works. A stage that fails once but is allowed one retry, and succeeds on the retry, ends up passing. Confirm the retry was actually used.

The fallback path works. When the model call fails, the agent still returns valid output, the stage passes, and the result is recorded as coming from the fallback, not the model.

The approval gate halts a high-impact stage. With an approval gate that rejects, the implement stage does not pass and the branch stops. With an approval gate that approves, the stage passes and the run continues.

The graph rejects a cycle. Building a dag where stages depend on each other in a loop fails fast with a clear error, rather than running forever.

The run context records decisions in order. After a run, the context holds one entry per stage output, each with its stage, its source, and its timestamp, and they are in the order the stages ran.

The metrics are correct. After a run with a known set of outcomes, the success rate, retry count, fallback count, and stage count match what actually happened.

For each test, use a clear name that says what behavior it checks, arrange a small graph that isolates that one behavior, run the engine, and assert on the outcome. Keep each test focused on a single behavior so a failure points straight at the cause. Add short comments only where the setup is not obvious. Do not test getters or trivial data classes; test the engine's behavior.

Put the tests under src/test/java in the matching package, and make sure they compile and pass with the stub client before finishing.
Outcome: WorkflowEngineTest.java