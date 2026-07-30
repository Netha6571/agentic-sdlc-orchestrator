# Spec 03 — Agents and the language model

## The agent contract

An agent does the work for one stage. It reports its stage id, and it does its work
against the run context and hands back a result. That is the whole interface. Keep
it that small.

## The base class holds the shared pattern

Every agent works the same way underneath, so put that pattern in a base class and
let each real agent fill in three small pieces. The pattern is:

1. Build a prompt from what earlier stages left in the context.
2. Send the prompt to the model.
3. Check the answer is usable. If it is, wrap it as a result marked as coming from
   the model.
4. If anything above throws — the call failed, timed out, or the answer was no
   good — run the fallback instead and mark the result as coming from the fallback.

The three pieces each agent fills in are: build the prompt, check and parse the
answer, and produce the fallback.

## The fallback rule

This is the most important rule in the whole system, so do not cut it.

Every agent has a fallback that returns real, valid output when the model path
fails. The fallback is not a stub that throws and it is not an empty result. For
the implement agent it is a known-good piece of code. For the test agent it is a
working test. For the design agent it is a plain design note. The point is that the
whole run keeps going and stays safe even when the model is down or wrong. A
reviewer's first question about any use of a model in a serious system is "what
happens when it fails", and the fallback is the answer.

Record which path was taken on every result, because the audit trail and the
confidence score both need to know how often the fallback was used.

## The language model client

The agents call an interface, never a specific model library. That is what lets
you swap the stub for the real client without touching a single agent.

The real client calls the model over plain HTTP with the built-in Java HTTP
client. It reads the key from an environment variable and never puts the key in
code or logs. It sets a timeout, so a slow model becomes a failure the fallback can
catch rather than a run that hangs.

The stub client returns a fixed answer with no network and no key, so the engine
runs anywhere and the tests are repeatable. It has a switch that makes it fail on
purpose, so you can show the fallback working on demand.

## The six agents

Each is a short subclass of the base class.

- Requirement. Turns the plain request into a clear spec and flags anything
  unclear. First stage, so it reads the raw requirement. Fallback: a plain
  restatement of the request as a spec.

- Design. Reads the spec, works out which parts of the existing code it touches,
  and sketches a design. Fallback: a plain design note naming the parts to change.

- Implement. Writes the code change from the design. This is high impact, so its
  result is marked as needing approval no matter which path produced it. Fallback:
  a known-good code skeleton.

- Test. Writes tests for the change and records whether they pass. Runs at the same
  time as docs. Fallback: a simple working test.

- Docs. Updates the documentation for the change. Runs at the same time as test.
  Fallback: a short doc stub.

- Release. Checks the change is ready and prepares a short summary for the pull
  request. High impact, so its result is marked as needing approval. Fallback: a
  ready result with a plain summary.

## Adding a new agent later

Write a new subclass with its three pieces, give it a stage id, and wire it into
the workflow factory with the stages it depends on. Do not touch the engine or the
base class to add a stage. If adding a stage needs an engine change, something is
wrong with the design.
