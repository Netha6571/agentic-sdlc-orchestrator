# Spec 00 — Overview and scope

## What this system is

An orchestration engine. It takes one software requirement written in plain
language, such as "add custom alias support", and runs it through a fixed set of
software lifecycle stages. Each stage is handled by an agent. The stages are:

1. Requirement — read the request, write a clear spec, flag anything unclear.
2. Design — look at the existing code, work out what it touches, sketch a design.
3. Implement — write the code change. A human approves this before it counts.
4. Test — write and run tests against the change.
5. Docs — update the documentation.
6. Release — check the change is ready. A human approves this too.

The engine produces the code, the tests, the docs, and a record of every decision
it made along the way, including which parts came from the language model and
which came from the fallback.

## What this system is not

It is not the URL shortener. The URL shortener is a separate Spring Boot project
that already exists. The orchestrator reads and changes that project; it does not
create it from nothing on every run. Building the whole app from scratch each time
was considered and rejected, because model-generated apps do not compile cleanly
on the first try and the demo would spend all its time fixing broken code instead
of showing the orchestration working.

It is not a code-completion tool. A completion tool helps a person write the line
they are typing. This engine runs a whole sequence of stages on its own and stops
only at the points where a human needs to sign off. The difference is not that the
model is smarter here — it is the same model. The difference is the graph, the
gates, the record, and the safe-stop around it.

## The scope boundaries to keep

State these plainly wherever the system is described, because naming them is part
of the point:

- The shortener is a separate, pre-built codebase. The engine operates on it.
- Rollback and re-planning exist as named seams in the engine, not as fully built
  features. The hooks are there; the full behaviour is left for later.
- Creating a pull request is the right place for the final human sign-off. Running
  a real build-and-deploy pipeline should be stubbed behind an interface. Wiring a
  live pipeline adds fragile outside dependencies and shows nothing new about the
  orchestration.
- Everything is held in memory. A real build would swap the run context for a
  store that survives a restart.

## The one rule that shapes everything

The engine knows nothing about URLs. It schedules stages and enforces gates for
any workflow. All knowledge about the shortener lives in the agents and the
codebase they touch, never in the engine. If the engine package mentions URLs, the
design has leaked and needs fixing.
