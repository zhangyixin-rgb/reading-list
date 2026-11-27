# reading-list

[![build](https://github.com/zhangyixin-rgb/reading-list/actions/workflows/build.yml/badge.svg)](https://github.com/zhangyixin-rgb/reading-list/actions/workflows/build.yml)

A command-line reading list manager written in Kotlin. It keeps a small library of
books on disk, tracks whether each one is still to read, in progress or finished,
and answers questions such as "what should I read next?" and "how am I doing?".

The project is the individual assignment for *Software Development Tools*. The
application is deliberately modest; the toolchain around it — reproducible build,
tests, coverage, static analysis and a readable history — is the point.

## Requirements

- JDK 21 (the build uses a toolchain, so Gradle will select 21 if it is installed)
- No local Gradle installation: use the committed wrapper

## Build, test and run

```bash
./gradlew build          # compile, test, coverage report, static analysis
./gradlew test           # tests only
./gradlew jacocoTestReport
./gradlew detekt
./gradlew run --args="add --title Dune --author \"Frank Herbert\""
```

A distribution of the application is produced by `./gradlew installDist` and
lands in `build/install/reading-list/bin/`.

On Windows use `gradlew.bat` in place of `./gradlew`.

## Usage

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

Example session:

```
$ reading-list add --title "The Left Hand of Darkness" --author "Ursula K. Le Guin" --tag sci-fi
Added b1: The Left Hand of Darkness by Ursula K. Le Guin (to-read)

$ reading-list start b1
Started b1: The Left Hand of Darkness

$ reading-list finish b1 --rating 5
Finished b1: The Left Hand of Darkness with a rating of 5/5

$ reading-list list --status finished --sort rating
ID   STATUS    RATING  TITLE                              AUTHOR
b1   finished  5/5     The Left Hand of Darkness          Ursula K. Le Guin

$ reading-list stats
Total books: 1
  to-read   0
  reading   0
  finished  1
Average rating: 5.00
```

### Exit codes

| Code | Meaning |
| --- | --- |
| 0 | success |
| 2 | the command line could not be understood |
| 3 | the requested book does not exist |
| 4 | the request was rejected by a domain rule (blank field, bad rating, wrong state) |
| 5 | the library file could not be read or written |

Errors go to standard error and always start with `error:`, so the tool composes
with scripts.

### Data file

The library is a single JSON document. Its location is
`~/.reading-list/library.json` and can be overridden:

```bash
READING_LIST_DATA=/tmp/demo.json ./gradlew run --args="stats"
```

Writes are atomic: the file is written to a temporary file and moved into place,
so an interrupted save cannot truncate the library. A corrupt file is reported
and left untouched rather than being silently replaced.

## Architecture

```
cli/        ArgParser -> Command -> App -> output
domain/     ReadingList (indexes + rules), Book, ReadingStatus, DomainError
storage/    ReadingListRepository <- JsonFileRepository
```

- **cli** — `ArgParser` turns tokens into a `Command` sealed hierarchy and reports
  usage errors without throwing at the caller. `App` owns the flow (parse, load,
  execute, save) and maps failures to exit codes.
- **domain** — `ReadingList` enforces the rules (non-blank fields, rating only on
  finished books, legal state transitions, no duplicates) and maintains three
  indexes, each with a purpose:
  - `LinkedHashMap<String, Book>` — primary store, O(1) lookup by id, insertion
    order preserved for the default listing;
  - `TreeMap<String, LinkedHashSet<String>>` with a case-insensitive comparator —
    author to book ids, always sorted, so `list --author` and `authors()` never
    sort;
  - `TreeSet<Book>` ordered by the date a book was added — the unread queue, so
    `next` returns its head without scanning the library.
- **storage** — a `ReadingListRepository` interface with a JSON implementation,
  which keeps the domain independent of the file format and lets the tests use
  an in-memory double.

Dependencies are deliberately few: kotlinx-serialization for JSON and JUnit 5 for
tests. Everything else is the standard library.

## Tests and coverage

The suite has three layers:

| Test class | What it covers |
| --- | --- |
| `ReadingListTest` | domain rules, indexes, ordering, statistics |
| `ArgParserTest` | every command, defaults, unknown options, missing values |
| `JsonFileRepositoryTest` | round-trip, atomic save, corrupt file, unknown fields |
| `AppTest` | end-to-end command flow, exit codes, output |

Run `./gradlew test` for the results and `./gradlew jacocoTestReport` for the
HTML report in `build/reports/jacoco/test/html/index.html`. The build runs the
coverage task automatically after the tests, and `jacocoTestCoverageVerification`
fails the build below 80% line coverage or 70% branch coverage. The gate is part
of `check`, so `./gradlew build` refuses a change that lowers coverage.

<!-- COVERAGE-RESULTS -->

Last recorded run (Gradle 8.13, JDK 21): **66 tests, 0 failures**, with line
coverage **92.9% (403 of 434 lines)** and branch coverage **76.9% (183 of 238
branches)**. The lines that remain uncovered are the `main` wiring function and
the defensive catch blocks for filesystem failures that cannot be triggered from
a test.

## Continuous integration

`.github/workflows/build.yml` runs `./gradlew build` on JDK 21 for every push to
`main` and every pull request. Because `build` depends on `check`, the same
coverage and static-analysis gates that run locally also run in CI, and the test,
coverage and analysis reports are uploaded as artefacts even when the build fails.

## Static analysis

Detekt runs as part of `./gradlew check` with the configuration in
`config/detekt/detekt.yml`. The configuration builds on detekt's defaults and
only relaxes thresholds that do not fit a small CLI (method length, number of
functions per class, return count). No findings are suppressed in the source; if
one becomes necessary, the reason is recorded here.

<!-- ANALYSIS-RESULTS -->

`./gradlew detekt` reports no findings on `src/main/kotlin`, and the build fails
on any finding (`maxIssues: 0`). The rule set is detekt's default with five
thresholds relaxed for a small command-line tool: method length 80, functions per
class 25, return count 6, throw count 6 (the validation path in
`ReadingList.add` produces one throw per rule) and the usual magic-number
exceptions. No `@Suppress` annotations are used in the source.

## Branching model

See [docs/branching-model.md](docs/branching-model.md). Work is done on
short-lived `feature/*` branches and merged into `main` through pull requests.

## What the toolchain changed

<!-- REFLECTION -->

Before this assignment I treated the build as something that happened in the IDE.
Writing the build file by hand changed three things. First, the wrapper made the
build reproducible: the same `./gradlew build` runs on my machine and on a
laboratory machine, and pinning the wrapper's checksum means a corrupted download
fails loudly instead of producing a subtly different compiler. Second, the tests
changed how I wrote the domain code — because `ReadingList` has no console
dependency, I could test transitions and indexes directly, and the `add` method's
validation grew out of tests that asserted each rejection rather than out of
guesswork. Third, static analysis made me notice things I would otherwise have
left in: a magic number in the statistics output, and an exception that was
silently discarded in the atomic-move fallback. The coverage report also told me
where the tests were shallow. The toolchain did not make the application bigger;
it made the same application defensible.

## Attribution

Libraries used: Kotlin standard library, kotlinx-serialization-json (JSON
persistence), JUnit 5 (tests), Detekt (static analysis), JaCoCo (coverage), Gradle
(build). API details were taken from their official documentation.

<!-- AI-ASSISTANCE -->

An AI coding assistant was used in a supporting role, for drafting code snippets
and build configuration text. All code was reviewed, run and verified by the
author, who is responsible for everything in this repository.
