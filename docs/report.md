# Reading List Manager

## Report for Assignment 1 — Individual Major Assignment

**Module:** Software Development Tools (2026–2027)  
**Programme:** NUIST–SETU Joint Programme  
**Author:** Zhang Yixin  
**Student ID:** 202583930036  
**Repository:** reading-list  
**Date:** 27 November 2026

---

## 1. Summary

The assignment asked for a small but non-trivial command-line application in
Kotlin, delivered through a repository that shows a professional workflow rather
than a single upload of finished code. This report describes what was built, the
toolchain that surrounds it, the evidence that the toolchain works, and what the
toolchain changed about the way the work was done.

The result is a reading-list manager: books move through three states
(`to-read`, `reading`, `finished`), a rating belongs to a finished book, and two
commands answer the questions a reader actually has — what to read next, and how
the year is going. The project builds reproducibly from a clean clone, has 66
automated tests, 92.9% line coverage, and a static-analysis gate that fails the
build on any finding.

## 2. Scope

The application is operated entirely from the command line and persists its data
to a single JSON file. Its scope was kept deliberately small so that the build,
the tests, the analysis and the history remain the centre of the assessment. The
functional requirements taken from the brief are:

- sub-commands with argument parsing;
- input validation and explicit error handling;
- a persistence layer;
- at least two non-trivial data structures used for a real purpose;
- a test suite run by the build, with a coverage report;
- static analysis configured in the build, with no unresolved findings;
- a README and a written branching model;
- a ten-minute live demonstration.

Each of these is addressed in the sections below.

## 3. Design

### 3.1 Layering

| Package | Responsibility | Depends on |
| --- | --- | --- |
| `domain` | Rules, state transitions, indexes | Kotlin standard library only |
| `storage` | Reading and writing the library file | `domain` |
| `cli` | Parsing, command dispatch, formatting | `domain`, `storage` |

The code is split into three packages with a single direction of dependency.
`cli` is the only package that writes to the console, and `domain` has no
knowledge of the console, the file system or the file format. That boundary is
what made the domain testable without a temporary directory or a fake terminal,
and it is the reason the storage layer can be replaced by an in-memory double in
the `App` tests.

### 3.2 Domain model and rules

A `Book` is immutable; every change produces a new instance that replaces the old
one in the aggregate. `ReadingList` is the only place where the rules live:

- title and author are trimmed and must not be blank;
- the same title and author cannot be added twice (case-insensitive), because the
  same edition recorded twice is a mistake;
- a rating is an integer from 1 to 5 and can only exist on a finished book;
- `start` only accepts a book in `to-read`, and `finish` only accepts a book that
  is not already finished;
- finishing records the date, and a later `rate` changes only the score.

Rejections are typed (`DomainError.NotFound`, `Duplicate`, `BlankTitle`,
`BlankAuthor`, `InvalidRating`, `RatingNeedsFinished`, `InvalidTransition`)
rather than string messages, so the command layer maps each kind to a distinct
exit code instead of printing a stack trace.

### 3.3 Three indexes, three jobs

The aggregate keeps three collections beside the primary store. Each one exists
because a command needs it, not for symmetry:

| Structure | Purpose | Why not a plain list |
| --- | --- | --- |
| `LinkedHashMap<String, Book>` | Primary store | O(1) lookup by id, and insertion order gives `list` a stable default order |
| `TreeMap<String, Set<String>>`, case-insensitive | Author to book ids | `list --author` and `authors()` are already sorted; no sort per call |
| `TreeSet<Book>`, ordered by date added | Unread queue | `next` takes the head of the queue instead of scanning and sorting the library |

The rejection alternative was a single list filtered and sorted in each command.
That is shorter to write, but it makes every listing O(n log n) and duplicates the
ordering rules in three places.

### 3.4 Persistence

The library is one JSON document:

```json
{
  "version": 1,
  "books": [
    {
      "id": "b1",
      "title": "Dune",
      "author": "Frank Herbert",
      "status": "FINISHED",
      "rating": 5,
      "addedOn": "2026-11-27",
      "finishedOn": "2026-11-27",
      "tags": ["sci-fi"]
    }
  ]
}
```

Saving writes to a temporary file in the same directory and moves it over the
target, so an interrupted save cannot leave a truncated library. A missing file
is treated as an empty library; a file that cannot be parsed raises an error and
is left untouched. Silently replacing a corrupt file would destroy the user's
data, which is a worse outcome than refusing to start.

`version` and `ignoreUnknownKeys` are deliberate: they let a later version add a
field without breaking an older file, and the field is ignored when reading.

### 3.5 Command-line contract

```
reading-list add --title <text> --author <text> [--status to-read|reading|finished]
                 [--rating 1-5] [--tag <text>]
reading-list list [--status <status>] [--author <text>] [--sort added|title|author|rating]
reading-list show <id>
reading-list start <id>
reading-list finish <id> [--rating 1-5]
reading-list rate <id> --rating 1-5
reading-list remove <id>
reading-list next [--limit <n>]
reading-list stats
reading-list help
```

Parsing is strict: an unknown option is an error rather than something to
ignore, because a typo in `--ratng` should not silently drop the rating.
Exit codes are part of the contract:

| Code | Meaning |
| --- | --- |
| 0 | success |
| 2 | the command line could not be understood |
| 3 | the requested book does not exist |
| 4 | the request was rejected by a domain rule |
| 5 | the library file could not be read or written |

Errors are written to standard error and begin with `error:`, so the tool can be
used from a script.

## 4. Toolchain

### 4.1 Reproducible build

| Decision | Alternative considered | Reason |
| --- | --- | --- |
| Gradle 8.13 through the committed wrapper, with the distribution's SHA-256 pinned | A global Gradle installation | The wrapper is the only entry point that behaves the same on a laboratory machine; the checksum turns a corrupted download into a failure instead of a subtly different compiler |
| Kotlin 2.1.20 with a JDK 21 toolchain | Whatever `JAVA_HOME` happens to be | The toolchain makes the compiler version a property of the project, not of the machine |
| Exact dependency versions | Ranges or `latest.release` | A range means the build can change without a commit, which makes "it built yesterday" meaningless |
| `application` plugin | A hand-written start script | `installDist` produces a runnable distribution and is exercised by the tests |

### 4.2 Tests

Tests are JUnit 5 and run as part of `check`. The suite has four layers:

| Test class | Tests | What it covers |
| --- | --- | --- |
| `ReadingListTest` | 23 | Rules, state transitions, indexes, filtering, ordering, statistics |
| `ArgParserTest` | 19 | Every command, defaults, missing values, unknown options |
| `JsonFileRepositoryTest` | 7 | Round-trip, atomic save, corrupt file, unknown fields |
| `AppTest` | 17 | End-to-end flow, every exit code, a session persisted to a real file |

The domain tests assert rules, not implementation: each rejection has a test whose
name states the rule, and the index behaviour is observed through the public API
(for example, starting a book removes it from the `next` queue).

### 4.3 Coverage

JaCoCo runs automatically after the tests (`finalizedBy`) and writes an HTML and
an XML report. Coverage is not treated as a target to be maximised; it was used
to find shallow areas, which is how the corrupt-file and unknown-field tests
came about.

### 4.4 Static analysis

Detekt is wired into `check` with `maxIssues: 0`, so a finding fails the build.
The rule set is detekt's default with five thresholds relaxed for a small
command-line tool: method length 80, functions per class 25, return count 6,
throw count 6 (the validation path produces one throw per rule) and the usual
magic-number exceptions. No `@Suppress` annotation is used in the source; the
two findings raised during development were fixed rather than silenced — a magic
number in the statistics output, and an exception swallowed in the atomic-move
fallback.

### 4.5 Version control

`main` is the only long-lived branch, and it is always buildable. Work is done on
short-lived `feature/*` branches, each with a coherent commit series, merged
through pull requests after a review checklist. The model is written down in
`docs/branching-model.md` rather than left implicit.

## 5. Results

Measured on the last clean build (`gradle clean build`, no build cache):

| Measure | Result |
| --- | --- |
| Tests | 66 run, 0 failed |
| Line coverage | 403 of 434 lines (92.9%) |
| Branch coverage | 183 of 238 branches (76.9%) |
| Detekt findings | 0 |
| Build | `BUILD SUCCESSFUL` in 28 s |

Coverage by package:

| Package | Lines | Branches |
| --- | --- | --- |
| `domain` | 162/167 (97.0%) | 67/84 (79.8%) |
| `cli` | 206/213 (96.7%) | 112/144 (77.8%) |
| `storage` | 35/46 (76.1%) | 4/4 (100%) |
| `Main.kt` (wiring) | 0/8 (0%) | 0/6 (0%) |

The uncovered lines are the wiring in `main`, the defensive catch blocks for
filesystem failures that cannot be triggered from a test, and a few comparator
branches in `SortOrder`.

## 6. Problems encountered and how they were resolved

1. **JDK version.** `JAVA_HOME` pointed at JDK 8. Gradle 8 requires at least
   JDK 17, and the Kotlin compiler targets 21, so the build failed before
   compiling anything. The JDK 21 installation on the machine was made the
   system-wide `JAVA_HOME`, and the same JDK was selected for the Gradle JVM in
   the IDE.

2. **Test worker could not start.** With a non-ASCII path in the user profile,
   Gradle passes the test worker's classpath in an argument file under the Gradle
   user home. The JVM could not read that file and reported
   `ClassNotFoundException: GradleWorkerMain`. The fix was to point
   `GRADLE_USER_HOME` at an ASCII path; nothing in the project had to change. This
   is recorded because it is the kind of environment fault that looks like a code
   fault.

3. **Wrapper download blocked.** `services.gradle.org` timed out from this
   network while the same file downloaded from a mirror in seconds. The wrapper
   keeps the canonical URL and the SHA-256, and the distribution was seeded into
   the local cache, so the project stays portable but builds here.

4. **Two detekt findings.** Both were real: a bare `9` used as a column width,
   and an exception discarded when an atomic move is unsupported. Both were fixed
   in the code rather than suppressed.

## 7. Reflection

Before this assignment I treated the build as something that happened in the IDE.
Writing the build file by hand changed three things.

First, the wrapper made the build reproducible. Knowing that `./gradlew build`
behaves the same on my machine and on a laboratory machine removed a class of
"it works here" doubt, and pinning the distribution's checksum meant a bad
download would fail loudly.

Second, writing tests first changed the design. Because `ReadingList` has no
console dependency, transitions and indexes could be tested directly, and the
validation in `add` grew out of tests that asserted each rejection rather than out
of guesswork. The coverage report then showed where the tests were shallow, which
is where the storage tests came from.

Third, static analysis made me fix things I would otherwise have left in: the
magic number in the statistics output and the swallowed exception are small, but
both were invisible until a tool pointed at them. The same is true of the commit
history: a branch-per-change model forced each change to be a decision that could
be described in one sentence.

The toolchain did not make the application bigger. It made the same application
defensible, and it made the cost of a shortcut visible before submission rather
than during the demonstration.

## 8. References

1. Kotlin documentation — https://kotlinlang.org/docs/
2. kotlinx.serialization — https://github.com/Kotlin/kotlinx.serialization
3. Gradle user manual — https://docs.gradle.org/8.13/userguide/userguide.html
4. JUnit 5 user guide — https://junit.org/junit5/docs/current/user-guide/
5. Detekt documentation — https://detekt.dev/
6. JaCoCo documentation — https://www.jacoco.org/jacoco/trunk/doc/

## Appendix A — Example session

```
$ reading-list add --title "The Left Hand of Darkness" --author "Ursula K. Le Guin" --tag sci-fi
Added b1: The Left Hand of Darkness by Ursula K. Le Guin (to-read)

$ reading-list add --title "Dune" --author "Frank Herbert"
Added b2: Dune by Frank Herbert (to-read)

$ reading-list start b1
Started b1: The Left Hand of Darkness

$ reading-list finish b1 --rating 5
Finished b1: The Left Hand of Darkness with a rating of 5/5

$ reading-list list
ID   STATUS    RATING  TITLE                             AUTHOR
b1   finished  5/5     The Left Hand of Darkness         Ursula K. Le Guin
b2   to-read   -       Dune                              Frank Herbert

$ reading-list stats
Total books: 2
  to-read   1
  reading   0
  finished  1
Average rating: 5.00

$ reading-list next
ID   STATUS    RATING  TITLE                             AUTHOR
b2   to-read   -       Dune                              Frank Herbert
```

## Statement on the use of tools

An AI coding assistant was used in a supporting role, for drafting code snippets
and build configuration text. All code was reviewed, executed and verified by the
author, and the author is responsible for everything submitted.
