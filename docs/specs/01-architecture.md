# Spec 01 — Architecture

## Package layout

All code sits under `com.org.orchestrator`. Six packages:

- `model` — plain data types. No logic beyond validation. Everything else depends
  on these, and they depend on nothing.
- `llm` — the language model client interface and its two implementations.
- `agent` — the agent contract, the shared base class, and the six agents.
- `engine` — the graph types and the engine that runs them. This is the core.
- `governance` — approval gates, metrics, and the confidence score.
- `cli` — the command-line entry point that wires everything together and runs it.

## What each class is for

### model

- `StageId` — a small wrapper around a stage name, so everything refers to stages
  by a stable name. Rejects a blank name.
- `StageState` — the states a stage moves through: pending, ready, running,
  awaiting approval, passed, failed, rolled back, skipped. Four of these are final.
- `ArtifactSource` — marks whether an output came from the model or the fallback.
- `StageResult` — what an agent hands back: the stage id, whether it succeeded,
  the source, the named outputs, a short reason, and whether it needs approval.
- `WorkflowContext` — the shared memory for one run. Append only. Every write is a
  timestamped entry recording who wrote it, what, why, and from which source.
  Because it only ever grows, it is also the audit trail. It must be safe for two
  threads to write at once, because test and docs run together.

### llm

- `LlmClient` — the interface the agents call. One method to send a prompt and get
  text back, and one to report its name for the logs.
- `LlmException` — thrown on any model failure. Its presence is what triggers a
  fallback.
- `StubLlmClient` — an offline implementation that returns a fixed answer, so the
  whole engine runs with no key and no network. It has a failure mode you can turn
  on to show the fallback working.
- `AnthropicLlmClient` — the real one. Calls the model over plain HTTP using the
  built-in Java HTTP client. Reads the key from an environment variable and never
  logs it. Has a timeout, so a slow model surfaces as a failure and the fallback
  takes over instead of the run hanging.

### agent

- `Agent` — the contract: report your stage id, and do your work against the run
  context.
- `AbstractAgent` — the base class that holds the try-the-model-then-fall-back
  logic once, so each real agent stays small. Details in spec 03.
- The six agents — requirement, design, implement, test, docs, release. Each is a
  short subclass. Details in spec 03.

### engine

- `Stage` — one node in the graph: its agent, the stages it depends on, its entry
  check, its retry budget, and whether it needs approval.
- `Dag` — the whole graph. Built once, never changed while running. Checks on
  construction that every dependency exists and that there are no cycles, because a
  graph with a cycle can never finish.
- `Workflows` — a factory that builds the standard six-stage graph and wires the
  dependencies so test and docs both hang off implement.
- `WorkflowEngine` — the core. Runs the graph. Details in spec 02.

### governance

- `RunMetrics` — counts the things the assignment asks for: success rate, retries,
  rollbacks, fallbacks, mean time to recover, and total run time.
- `ApprovalGate` — the human sign-off point. One version approves everything
  automatically for a hands-off demo; another asks on the console.
- `ConfidenceScore` — turns run outcomes into a number between zero and one.

### cli

- `Main` — reads the command-line flags, picks the real or stub model, picks the
  automatic or console gate, builds the graph, runs it, and prints the states, the
  decision trail, the confidence, and the metrics.

## The seam that keeps the options open

The engine core takes a requirement and runs it. It does not care how it was
started. Today a command-line class starts it. Later a web layer could start it
with no change to the engine, and later still each agent could run as its own
service. Keep this boundary clean: the entry point calls the engine; the engine
never reaches back out to the entry point.

## The dependency shape, drawn out

```
requirement -> design -> [approval] implement -> test  -> [approval] release
                                              \-> docs -/
```

Test and docs both depend only on implement, and nothing depends on just one of
them, so the engine runs them at the same time and waits for both before release.
