# Agentic SDLC Orchestrator

An orchestration engine that takes a software requirement in plain language and
runs it through a fixed set of lifecycle stages, each handled by an agent. It
produces code, tests, documentation, and a record of every decision made along
the way — including which parts came from the language model and which came from
the fallback.

## How to build

### With Maven

```bash
mvn compile
```

### Without Maven

```bash
chmod +x build.sh
./build.sh
```

## How to run

### Default mode (stub model, auto-approve)

No flags needed. Uses the offline stub model and approves everything
automatically. Runs anywhere with no API key and no network.

```bash
# Maven
mvn exec:java

# Shell script
./build.sh

# Direct
java -cp build com.org.orchestrator.cli.Main
```

### With a custom requirement

Pass the requirement as the last argument:

```bash
./build.sh "Add rate limiting to the API"
```

If no requirement is given, the default is "Add custom alias support".

### Real model mode

Uses the Google ADK (Gemini) client. Requires the `GOOGLE_API_KEY` environment
variable to be set.

```bash
export GOOGLE_API_KEY=your-key-here
./build.sh --real-model "Add rate limiting"
```

### Interactive mode

Asks for human approval on the console at the implement and release stages.

```bash
./build.sh --interactive "Add rate limiting"
```

### Force-fail mode

Makes the stub model fail on purpose, so every agent uses its fallback. Shows
the fallback path working and produces a lower confidence score.

```bash
./build.sh --force-fail
```

Flags can be combined:

```bash
./build.sh --real-model --interactive "Add rate limiting"
```

## What it prints

At the end of a run, the output includes:

1. **Stage states** — the final state of every stage (passed, failed, etc.).
2. **Decision trail** — each entry with its timestamp, stage, source (model or
   fallback), key, value, and reason.
3. **Confidence score** — a number between 0 and 1 built from observable run
   outcomes, with the signals that produced it.
4. **Metrics** — success rate, retries, rollbacks, fallbacks, mean time to
   recover, and total run time.
5. **Outcome** — whether the run finished or stopped safely.

## Class-to-requirement mapping

| Requirement | Class(es) |
|---|---|
| Orchestration engine with stage-based workflow | `WorkflowEngine`, `Dag`, `Stage`, `Workflows` |
| Agent-based processing with LLM | `Agent`, `AbstractAgent`, `LlmClient` |
| Six lifecycle stages | `RequirementAgent`, `DesignAgent`, `ImplementAgent`, `TestAgent`, `DocsAgent`, `ReleaseAgent` |
| Try-model-then-fallback pattern | `AbstractAgent` (the base class holds this once) |
| Approval gates (controlled autonomy) | `ApprovalGate` (auto-approve and console versions) |
| Bounded retries | `Stage.retryBudget`, retry loop in `WorkflowEngine` |
| Rollback (seam) | Commented hook in `WorkflowEngine.applyResult()` |
| Re-planning (seam) | Commented hook in `WorkflowEngine.run()` |
| Confidence score from run outcomes | `ConfidenceScore` (exact formula from spec) |
| Metrics collection | `RunMetrics` |
| Parallel execution (test + docs) | Thread pool in `WorkflowEngine`, `CopyOnWriteArrayList` in `WorkflowContext` |
| Audit trail | `WorkflowContext` (append-only, timestamped entries) |
| Offline/stub mode | `StubLlmClient` |
| Real LLM integration | `GoogleAdkLlmClient` (Google ADK via built-in HTTP client) |
| Command-line interface | `Main` |

## Package layout

```
com.org.orchestrator
├── model       — plain data types (StageId, StageState, ArtifactSource, StageResult, WorkflowContext)
├── llm         — LLM client interface + stub + Google ADK implementation
├── agent       — agent interface, base class, and six agents
├── engine      — DAG, Stage, Workflows factory, WorkflowEngine
├── governance  — ApprovalGate, RunMetrics, ConfidenceScore
└── cli         — Main entry point
```

## Scope boundaries

These are stated plainly because naming them is part of the work:

- **The shortener is separate.** It is a pre-built codebase that the engine
  operates on. The orchestrator does not create it from nothing.
- **Rollback and re-planning are seams.** The hooks are there as named,
  commented places in the engine. The full behaviour is left for later.
- **The final sign-off is a gate, not a PR.** Creating a real pull request is
  where the final human sign-off belongs. The gate interface is the seam.
- **No live pipeline.** Running a real build-and-deploy pipeline is stubbed
  behind an interface. Wiring a live pipeline adds fragile outside dependencies
  and shows nothing new about the orchestration.
- **Everything is in memory.** A real build would swap the run context for a
  store that survives a restart.
- **The engine knows nothing about URLs.** It schedules stages and enforces
  gates for any workflow. All domain knowledge lives in the agents, never in the
  engine.
