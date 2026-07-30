---
name: orchestrator-builder
description: Build an agentic SDLC orchestration engine in Java from a written spec. Use this whenever the user wants to generate, scaffold, or extend the URL-shortener orchestration agent, the workflow engine, the stage agents, the governance layer, or the confidence scoring described in the specs. Trigger this for any request to "build the orchestrator", "generate the engine skeleton", "add a stage", "wire the LLM client", or "add rollback/re-planning/metrics" to this system, even if the user does not name the spec files directly.
---

# Orchestrator Builder

This skill builds an agentic SDLC orchestration engine in Java. The engine takes
a software requirement, runs it through a fixed set of lifecycle stages
(requirement, design, implement, test, docs, release), and produces code, tests,
docs, and a full audit trail. A human approves the high-impact stages. The engine
operates on a separate, pre-built codebase; it does not
generate that app from scratch.

Build the code from the spec files in the `specs/` folder. Do not invent
structure that the specs do not describe. If something is missing from a spec,
stop and ask rather than guessing.

## How to use this skill

Read the specs in this order before writing any code:

1. `specs/00-overview.md` — what the system is, what it is not, and the scope
   boundaries. Read this first so you do not build the wrong thing.
2. `specs/01-architecture.md` — the packages, the classes, and how they fit.
3. `specs/02-engine.md` — how the workflow engine runs the graph.
4. `specs/03-agents-and-llm.md` — the agent contract and the LLM-with-fallback rule.
5. `specs/04-governance.md` — approval gates, retries, rollback, metrics, confidence.
6. `specs/05-build-and-run.md` — the Maven layout, the build script, and how to run it.

Then build the files in this order, because each layer depends on the one before it:

1. The model types (plain data: ids, states, results, context).
2. The LLM client interface and its two implementations (real and stub).
3. The agent base class and the six concrete agents.
4. The graph types (stage, dag) and the workflow factory.
5. The governance pieces (metrics, approval gate, confidence score).
6. The engine.
7. The command-line entry point.
8. The Maven file, the build script, and the README.

## Rules that matter

Keep the engine free of any knowledge about URLs. The engine schedules stages and
enforces gates. It must work for any workflow, not just this one. If you find
yourself writing the word "url" inside the engine package, you have made a mistake.

Every agent tries the LLM first and falls back to a fixed, always-valid result
when the LLM fails, times out, or returns something invalid. The fallback is real
output, not a stub that throws. This is what keeps the system safe when the model
is down. Never skip it.

The confidence score comes from things that actually happened during the run —
did the tests pass, did the code compile, how many retries and fallbacks were
used. It is never a number the LLM makes up about itself. Spec `04-governance.md`
gives the exact formula.

A human is asked to approve only the high-impact stages (implement and release).
Everywhere else the engine runs on its own. This split — automatic in the middle,
human at the edges — is the whole point. Do not add approval prompts to the other
stages.

Write plain code with short comments that say why, not what. Do not pad the code
with filler. Do not write marketing sentences in comments.

## When the user asks to extend the system

Adding a stage: write the agent as a subclass of the base agent, give it a stage
id, then wire it into the workflow factory with its dependencies. Spec
`03-agents-and-llm.md` shows the pattern.

Adding rollback or re-planning: these are named seams in the engine. Spec
`04-governance.md` describes where they hook in. Do not rebuild the engine to add
them; extend the marked points.

Adding a REST layer: the engine core does not change. Put a thin web layer on top
that calls the same engine. Spec `01-architecture.md` explains the seam.

## Checking your work

After building, confirm:

- The engine package imports nothing about URLs or the shortener.
- Every agent has a working fallback that returns valid output.
- The two parallel stages (test and docs) both depend only on implement, so the
  engine can run them at the same time.
- The confidence score reads from run outcomes, not from the model.
- The build script compiles the whole thing with one command.
