# Spec 05 — Build and run

## Layout

A standard Maven project. Sources under `src/main/java`. The package root is
`com.org.orchestrator`. Java 17 or newer.

## The Maven file

Keep it small. Java 17 as the release level. UTF-8 sources. The only dependency is
a test library, and only for tests. Set the main class so the project can be run
straight from Maven. Do not pull in a language-model library, a web framework, or a
workflow framework — none of them are needed. The engine is plain Java, the model
call is the built-in HTTP client, and the parallel work uses the built-in thread
pool.

## A build script as well as Maven

Provide a short shell script that compiles the whole thing with the plain Java
compiler and runs it, so the project works even without Maven. It should find all
the source files, compile them into a build folder, and run the command-line class,
passing along whatever arguments it was given.

## How to run it

The command-line class takes a few flags and a requirement:

- No flags: use the stub model and approve everything automatically. This is the
  quick path that runs anywhere.
- A real-model flag: use the real client, which needs the key in the environment.
- An interactive flag: ask for approval on the console at each high-impact stage.
- A force-fail flag on the model: make the stub fail on purpose, to show the
  fallback taking over.

The last argument that is not a flag is the requirement. If none is given, use a
sensible default like adding alias support.

## What it prints

At the end of a run, print:

- The final state of every stage.
- The decision trail: each entry with its time, its stage, its source, and what it
  wrote.
- The confidence score with the signals that produced it.
- The metrics summary.
- Whether the run finished or stopped safely.

## Definition of done

The build is done when:

- The whole project compiles with one command.
- A plain run with the stub finishes and prints all four sections above.
- A run with the force-fail flag shows the fallback being used and a lower
  confidence score as a result.
- A run with the interactive flag stops and waits for a yes or no at the implement
  and release stages.
- The engine package contains no mention of URLs or the shortener.

## A README

Write a short README that says what the system is, how to run it in each mode, and
which class maps to which requirement from the assignment. List the scope
boundaries from spec 00 plainly, because naming them is part of the work. Do not
write it in a marketing voice. Say what the thing does and how to use it.
