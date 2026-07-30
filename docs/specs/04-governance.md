# Spec 04 — Governance

Governance is the set of controls around the run: who approves what, what happens
on failure, and how the run is measured. It is half the score, so build it
properly.

## Approval gates

Some stages are high impact — implement and release. These pause and ask a human
before the run goes on. The rest run on their own. This split is the heart of
"controlled autonomy": the engine works by itself in the middle and stops for a
person at the edges.

Put the gate behind an interface, so the engine does not care how the human is
asked. Provide two versions:

- One that approves everything automatically, for a hands-off demo or a test.
- One that prints the pending change on the console and reads yes or no.

When the human says no, the stage is marked failed and that branch stops. A
rejected change never slips through. That is the safe-stop for high-impact work.

## Bounded retries

Each stage has a retry budget, a small number. If the agent fails, the engine
tries again up to that budget, counting each retry. When the budget runs out, the
stage fails for good and its dependents never run. The budget stops the engine
retrying forever on something that will never work.

## Rollback — a marked seam

Leave this as a clearly commented hook, not a full build. The idea: a stage may
register an undo action. When a later stage fails, the engine walks back over the
stages that already passed and runs their undo actions in reverse, marks them
rolled back, and counts a rollback. A simple undo is enough to show the idea —
delete the files a stage created, or put the context back to its last good point.

## Metrics

The engine reports outcomes to a metrics object that counts:

- How many stages ran, and how many passed. Success rate is passed over run.
- How many retries were used.
- How many rollbacks happened.
- How many times the fallback was used instead of the model.
- Mean time to recover — the average time from a failure to the recovery that
  followed it.
- Total run time from start to finish.

These are collected as the run goes, not worked out afterwards, so they reflect
what actually happened.

## The confidence score

This is the number handed to the human at the approval gate. It must come from
things that happened during the run, never from the model saying how sure it is
about itself. A number the model makes up about its own work means nothing; a
number built from real outcomes can be defended.

Build it from these signals:

- The tests passed. Strong positive.
- The code compiled. Positive.
- The static checks were clean. Positive.
- The fallback was used. Negative — the fallback is safe but less tailored than a
  good model answer, so it lowers confidence.
- Retries were used. Small negative — instability.

Use this formula, and keep the result between zero and one:

- Start at zero.
- Add 0.40 if the tests passed.
- Add 0.25 if the code compiled.
- Add 0.15 if the static checks were clean.
- Add 0.20 as a base for finishing the run at all.
- Take away 0.10 for each fallback used.
- Take away 0.05 for each retry used.
- Clamp the final number to the range zero to one.

Print the signals next to the score, so the human sees why the number is what it
is, not just the number.

## Why the score is built this way

Anyone can print a confidence number. The reason this one holds up under
questioning is that every part of it points to something observable. If someone
asks "why 0.85", the answer is a list of facts about the run, not a shrug. That is
the difference between engineering and decoration.
