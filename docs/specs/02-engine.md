# Spec 02 — The engine

The engine runs the graph. This is the part that matters most, so build it
carefully and be able to explain every line.

## The core idea

The engine does not run the stages in a hard-coded order. It keeps a state for
each stage and runs whichever stages are ready. A stage is ready when every stage
it depends on has passed and its own entry check says go. Because readiness drives
the order, the order comes out of the dependencies on its own. This is what makes
it an engine and not a script with six method calls in a row.

## The run loop

Set every stage to pending. Then repeat these steps until every stage has reached
a final state or nothing can move forward:

1. Look at every pending stage. If all its dependencies have passed or were
   skipped, and its entry check passes, move it to ready.
2. Collect all the ready stages. This is the frontier.
3. If the frontier is empty but not everything is finished, the run is stuck —
   most likely a dependency failed. Stop safely and report where it stopped.
4. Run the whole frontier at the same time on a small thread pool. Wait for all of
   them to finish before looping again. This wait is the synchronisation point.

## Running one stage

Each stage runs through a retry wrapper:

- Try the agent. If it succeeds, take that result.
- If it fails, count a retry and try again, up to the stage's retry budget.
- If it still fails after the budget is spent, hand back the failed result and let
  the caller decide what happens.

## Applying a result

When a stage finishes, do this in order:

1. Write its outputs into the run context, so later stages can read them and the
   audit trail grows.
2. If the output came from the fallback, count a fallback.
3. If the stage failed, mark it failed and count it. Do not run its dependents.
4. If the stage needs approval, set it to awaiting approval and ask the gate. If
   the human says no, mark the stage failed and stop that branch. This is the
   safe-stop: a rejected high-impact change does not slip through.
5. Otherwise mark it passed and count it.

## Parallel work

Run the ready frontier on a thread pool with a few threads. Submit each ready
stage, then collect the results. Do not move on until all of them are back. The
run context is written by more than one thread at this point, which is why it must
be safe for concurrent writes.

## Safe-stop

Two things stop the run cleanly rather than letting it thrash:

- A stage that fails after using up its retries. Its dependents never become ready,
  so the run winds down.
- A frontier that comes back empty while stages remain unfinished. That means the
  run is blocked, so stop and report.

## The seams to leave in, clearly marked

Do not build these fully. Leave a named, commented hook for each so it is obvious
where they go:

- Rollback. Each stage may register an undo action. When a later stage fails, walk
  back over the passed stages and run their undo actions in reverse. Mark those
  stages rolled back and count a rollback. Leave the hook where a result is
  applied.
- Re-planning. If an upstream output changes on a re-run, mark the stages that
  depend on it stale and set them back to pending so they run again. Leave the hook
  next to the readiness check.

## What the engine must not do

It must not mention URLs, the shortener, or any agent by name. It works with
stages, states, and gates only. If it needs to know something about a stage, that
something comes from the stage's own data or the run context, never from a special
case written into the engine.
