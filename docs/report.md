# Reading List Manager

## Report for Assignment 1 — Individual Major Assignment

**Module:** Software Development Tools (2026–2027)  
**Programme:** NUIST–SETU Joint Programme  
**Author:** Zhang Yixin  
**Student ID:** 202583930036  
**Repository:** https://github.com/zhangyixin-rgb/reading-list  
**Version assessed:** `v1.1.0`  
**Date:** 28 November 2026

---

## Contents

1. Summary
2. Scope and traceability
3. Design
4. Toolchain
5. Results
6. Problems and resolutions
7. Limitations and future work
8. Reflection
9. References

Appendix A — Example session

Appendix B — Demonstration checklist

Statement on the use of tools

---

## 1. Summary

The assignment asked for a small but non-trivial command-line application in
Kotlin, delivered through a repository that shows a professional workflow rather
than a single upload of finished code. This report describes what was built, the
toolchain around it, the evidence that the toolchain works, and what the toolchain
changed about the way the work was done.

The result is a reading-list manager: books move through three states
(`to-read`, `reading`, `finished`), a rating belongs to a finished book, and two
commands answer the questions a reader actually has — what to read next, and how
the reading is going. The project builds reproducibly from a clean clone, runs 66
automated tests with 92.9% line coverage, enforces coverage and static analysis as
build gates, and runs the same build on every push through continuous
integration.

## 2. Scope and traceability

The brief names seven deliverables for the individual assignment. The table maps
each one to where it is implemented and how it can be checked, so that the report
can be read alongside the repository rather than instead of it.

| Brief requirement | Where it is met | Evidence |
| --- | --- | --- |
| Repository with a written branching model, four feature branches merged through pull requests, readable history | `docs/branching-model.md`; pull requests 1–4 | `git log --graph`; merge commits `#1`–`#4` |
| Gradle build with the wrapper committed and dependency versions pinned, so a clean clone builds | `gradlew`, `gradlew.bat`, `gradle/wrapper/*`, `build.gradle.kts` | `./gradlew build`; distribution checksum pinned |
| Sub-commands, argument parsing, validation, explicit error handling, persistence, two non-trivial data structures with a real purpose | `cli/ArgParser.kt`, `cli/App.kt`, `domain/ReadingList.kt`, `storage/JsonFileRepository.kt` | Sections 3.2–3.5; 66 tests |
| Test suite covering domain logic and command-line parsing, run by the build, with a coverage report as a build task | `src/test/kotlin`; `jacocoTestReport` | Section 5 |
| Static analysis configured in the build with no unresolved findings | `detekt` in `check`, `maxIssues: 0` | Section 4.5 |
| README in Markdown: purpose, build and run, architecture, results, reflection | `README.md` | Repository front page |
| Ten-minute demonstration running build, tests and analysis live | Appendix B | Demonstrated in the laboratory |

## 3. Design

### 3.1 Layering

```
        +--------------------------------------------------+
        |  cli                                             |
        |  ArgParser -> Command -> App -> Appendable out   |
        +-------------------------+------------------------+
                                  |
                                  v
        +--------------------------------------------------+
        |  domain                                          |
        |  ReadingList (rules + three indexes), Book,      |
        |  ReadingStatus, SortOrder, DomainError           |
        +-------------------------+------------------------+
                                  ^
                                  |
        +--------------------------------------------------+
        |  storage                                         |
        |  ReadingListRepository <- JsonFileRepository     |
        +-------------------------+------------------------+
                                  |
                                  v
                         library.json (atomic replace)
```

| Package | Responsibility | Depends on |
| --- | --- | --- |
| `domain` | Rules, state transitions, indexes | Kotlin standard library only |
| `storage` | Reading and writing the library file | `domain` |
| `cli` | Parsing, command dispatch, formatting | `domain`, `storage` |

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
`BlankAuthor`, `InvalidRating`, `RatingNeedsFinished`, `InvalidTransition`) rather
than string messages, so the command layer maps each kind to a distinct exit code
instead of printing a stack trace.

### 3.3 Invalid input, explicitly

The brief asks for robustness under invalid input, so each rejection is specified
rather than merely handled. Every row below is covered by a test whose name states
the rule.

| Input | Response | Exit code | Test |
| --- | --- | --- | --- |
| Unknown command (`borrow b1`) | Message plus a pointer to `help` | 2 | `ArgParserTest.unknown commands are reported` |
| Option without a value (`add --author`) | Names the empty option | 2 | `ArgParserTest.an option without a value is reported` |
| Unknown option (`list --colour blue`) | Rejected, not ignored | 2 | `ArgParserTest.unknown options are rejected instead of ignored` |
| Blank title or author | `Title must not be blank` | 4 | `ReadingListTest.add rejects blank title and blank author` |
| Rating outside 1–5 | `Rating must be between 1 and 5` | 4 | `ReadingListTest.add rejects ratings outside the allowed range` |
| Rating on an unread book | `A rating can only be recorded for a finished book` | 4 | `ReadingListTest.add rejects a rating for a book that is not finished` |
| Duplicate title and author | `... is already in the list` | 4 | `ReadingListTest.add rejects a duplicate title and author ignoring case` |
| `start` on a finished book | Illegal-transition message | 4 | `ReadingListTest.start rejects a book that is not in the to-read state` |
| Unknown id | `No book with id 'b9'` | 3 | `AppTest.an unknown id returns the not found exit code` |
| Corrupt library file | `library.json is not a valid reading-list file` | 5 | `JsonFileRepositoryTest.a corrupt file is reported instead of silently discarded` |

### 3.4 Three indexes, three jobs

| Structure | Purpose | Why not a plain list |
| --- | --- | --- |
| `LinkedHashMap<String, Book>` | Primary store | O(1) lookup by id, and insertion order gives `list` a stable default order |
| `TreeMap<String, Set<String>>`, case-insensitive | Author to book ids | `list --author` and `authors()` are already sorted; no sort per call |
| `TreeSet<Book>`, ordered by date added | Unread queue | `next` takes the head of the queue instead of scanning and sorting the library |

The rejected alternative was a single list filtered and sorted in each command.
That is shorter to write, but it makes every listing O(n log n) and duplicates the
ordering rules in three places.

The cost of each operation follows from those choices:

| Operation | Structure used | Cost |
| --- | --- | --- |
| `show`, `start`, `finish`, `rate`, `remove` by id | `byId` | O(1) average |
| `list` with no filter | `byId.values` | O(n) |
| `list --author x` | `byAuthor` | O(log a + k), a authors, k matches |
| `next n` | `readingQueue` | O(k log n) for k removed elements |
| `add` duplicate check | scan of `byId` | O(n) — the one place where an extra index would be justified at a larger scale |
| `stats` | scan of `byId` | O(n) |

Index maintenance is localised in two private methods, so an update cannot leave
an index stale:

```kotlin
private fun replace(previous: Book, updated: Book): Book {
    byId[updated.id] = updated
    if (!previous.author.equals(updated.author, ignoreCase = true)) {
        detachAuthor(previous)
        attachAuthor(updated)
    }
    readingQueue.remove(previous)
    if (updated.status == ReadingStatus.TO_READ) readingQueue.add(updated)
    return updated
}
```

### 3.5 Persistence

The library is a single JSON document with a `version` field and a `books` array;
dates are ISO-8601 strings so the file stays readable outside the program.

Saving writes to a temporary file in the same directory and moves it over the
target, so an interrupted save cannot leave a truncated library:

```kotlin
private fun moveIntoPlace(temporary: Path) {
    try {
        Files.move(temporary, file, REPLACE_EXISTING, ATOMIC_MOVE)
    } catch (exception: AtomicMoveNotSupportedException) {
        try {
            Files.move(temporary, file, REPLACE_EXISTING)
        } catch (failure: IOException) {
            failure.addSuppressed(exception)
            throw failure
        }
    }
}
```

A missing file is treated as an empty library; a file that cannot be parsed raises
an error and is left untouched. Silently replacing a corrupt file would destroy
the user's data, which is a worse outcome than refusing to start. Unknown fields
are ignored on read, so a later version can add a field without breaking an older
file.

### 3.6 Command-line contract

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

Parsing is strict: an unknown option is an error rather than something to ignore,
because a typo in `--ratng` should not silently drop the rating. Exit codes are
part of the contract: 0 success, 2 usage, 3 not found, 4 rejected by a rule,
5 storage failure. Errors go to standard error and begin with `error:`, so the
tool composes with scripts.

## 4. Toolchain

### 4.1 Reproducible build

| Decision | Alternative considered | Reason |
| --- | --- | --- |
| Gradle 8.13 through the committed wrapper, with the distribution's SHA-256 pinned | A global Gradle installation | The wrapper is the only entry point that behaves the same on a laboratory machine; the checksum turns a corrupted download into a failure instead of a subtly different compiler |
| Kotlin 2.1.20 with a JDK 21 toolchain | Whatever `JAVA_HOME` happens to be | The toolchain makes the compiler version a property of the project, not of the machine |
| Exact dependency versions | Ranges or `latest.release` | A range means the build can change without a commit, which makes "it built yesterday" meaningless |
| `application` plugin | A hand-written start script | `installDist` produces a runnable distribution, exercised by the tests |

### 4.2 Continuous integration

Every push to `main` and every pull request runs the same build on a clean Ubuntu
runner with JDK 21 (`.github/workflows/build.yml`). The workflow does not simply
run the tests: it runs `./gradlew build`, which means the coverage gate and the
static-analysis gate are part of what CI checks. Test, coverage and analysis
reports are uploaded as artefacts even when the build fails, so a red run can be
diagnosed without reproducing it locally.

```yaml
- uses: actions/setup-java@v4
  with:
    distribution: temurin
    java-version: '21'
- run: |
    chmod +x gradlew
    ./gradlew build --no-daemon
```

### 4.3 Tests and testing strategy

Tests are JUnit 5 and run as part of `check`.

| Test class | Tests | What it covers |
| --- | --- | --- |
| `ReadingListTest` | 23 | Rules, state transitions, indexes, filtering, ordering, statistics |
| `ArgParserTest` | 19 | Every command, defaults, missing values, unknown options |
| `JsonFileRepositoryTest` | 7 | Round-trip, atomic save, corrupt file, unknown fields |
| `AppTest` | 17 | End-to-end flow, every exit code, a session persisted to a real file |

The suite is layered deliberately. Domain tests assert rules, not implementation:
each rejection has a test whose name states the rule, and index behaviour is
observed through the public API (starting a book removes it from the `next`
queue). `AppTest` goes through the same entry point as the user — an array of
arguments — so an exit code cannot drift from what the CLI actually returns.

What is deliberately not tested, and why:

- the eight lines in `main` that wire `System.out`, `System.err` and the data file
  path; testing them would test the JVM, not the application;
- the `IOException` catch around reading the file, which cannot be provoked
  without replacing the file system;
- comparator ties in `SortOrder.RATING`, where the secondary ordering key is
  already covered by the title and author orderings.

### 4.4 Coverage and the quality gate

JaCoCo runs after the tests and writes HTML and XML reports. Coverage is not a
target to be maximised; it was used to find shallow areas, which is how the
corrupt-file and unknown-field tests came about.

The report is also a gate: `jacocoTestCoverageVerification` fails the build below
80% line coverage or 70% branch coverage, and `check` depends on it. A tool that
is installed reports; a tool that is configured refuses. If a future change drops
coverage, the build says so before a reviewer has to.

### 4.5 Static analysis

Detekt runs in `check` with `maxIssues: 0`, so a finding fails the build. The rule
set is detekt's default with five thresholds relaxed for a small command-line
tool: method length 80, functions per class 25, return count 6, throw count 6 (the
validation path produces one throw per rule) and the usual magic-number
exceptions. No `@Suppress` annotation is used in the source; both findings raised
during development were fixed rather than silenced — a magic number in the
statistics output, and an exception swallowed in the atomic-move fallback.

### 4.6 Version control

`main` is the only long-lived branch and is always buildable. Work is done on
short-lived feature branches merged through pull requests after a review
checklist: four pull requests were merged for this submission, each with a
coherent commit series. The model is written down in `docs/branching-model.md`
rather than left implicit, and `docs/project-record.md` records decisions and
environment problems as they happened.

## 5. Results

Measured on a clean build (`./gradlew clean build`, no build cache):

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

The build output that produces those figures:

```
$ ./gradlew clean build
> Task :compileKotlin
> Task :detekt
> Task :compileTestKotlin
> Task :test
> Task :jacocoTestReport
> Task :jacocoTestCoverageVerification
> Task :check
> Task :build

BUILD SUCCESSFUL in 28s
9 actionable tasks: 9 executed
```

## 6. Problems and resolutions

1. **JDK version.** `JAVA_HOME` pointed at JDK 8. Gradle 8 requires at least JDK
   17 and the Kotlin compiler targets 21, so the build failed before compiling
   anything. JDK 21 was made the system-wide `JAVA_HOME` and selected as the
   Gradle JVM in the IDE.

2. **Test worker could not start.** With a non-ASCII path in the user profile,
   Gradle passes the test worker's classpath in an argument file under the Gradle
   user home. The JVM could not read that file and reported
   `ClassNotFoundException: GradleWorkerMain`. Pointing `GRADLE_USER_HOME` at an
   ASCII path fixed it; nothing in the project had to change. It is recorded
   because it is the kind of environment fault that looks like a code fault.

3. **Wrapper download blocked.** `services.gradle.org` timed out from this
   network while the same file downloaded from a mirror in seconds. The wrapper
   keeps the canonical URL and the SHA-256, and the distribution was seeded into
   the local cache, so the project stays portable but builds here. On CI, where
   the canonical URL is reachable, the wrapper downloads normally.

4. **Two detekt findings.** Both were real: a bare `9` used as a column width, and
   an exception discarded when an atomic move is unsupported. Both were fixed in
   the code rather than suppressed.

## 7. Limitations and future work

The application is a single-user command-line tool, and the design reflects that.
Naming the limits is more useful than implying there are none:

- **Single writer.** Saves are atomic per file, but nothing prevents two
  processes from writing the same library at once; a lock file would be the next
  step.
- **Duplicate detection is O(n).** A case-insensitive index on `(title, author)`
  would remove the scan, at the cost of a fourth index to keep consistent.
- **Schema version is read but not migrated.** `version` exists in the file
  format, but only version 1 is defined; a migration step would be needed before
  changing the shape of `Book`.
- **No search by title.** `list --author` matches a substring, but titles can only
  be listed and read, not searched.
- **English-only output.** Messages and labels are not externalised.

## 8. Reflection

Before this assignment I treated the build as something that happened in the IDE.
Writing the build file by hand changed three things.

First, the wrapper made the build reproducible. Knowing that `./gradlew build`
behaves the same on my machine, on a laboratory machine and on a CI runner removed
a class of "it works here" doubt, and pinning the distribution's checksum meant a
bad download would fail loudly rather than silently change the toolchain.

Second, writing tests changed the design. Because `ReadingList` has no console
dependency, transitions and indexes could be tested directly, and the validation
in `add` grew out of tests that asserted each rejection rather than out of
guesswork. The coverage report then showed where the tests were shallow, which is
where the storage tests came from.

Third, turning analysis and coverage into gates changed how I read a red build. A
warning that is reported can be ignored for a week; a warning that fails `check`
has to be answered in the same commit. That is why the two detekt findings were
fixed rather than suppressed, and why the coverage threshold was added after the
suite was already passing — a threshold only means something once it can fail.

The toolchain did not make the application bigger. It made the same application
defensible, and it made the cost of a shortcut visible before submission rather
than during the demonstration.

## 9. References

1. Kotlin documentation — https://kotlinlang.org/docs/ (accessed 28 November 2026)
2. kotlinx.serialization — https://github.com/Kotlin/kotlinx.serialization (accessed 28 November 2026)
3. Gradle user manual, 8.13 — https://docs.gradle.org/8.13/userguide/userguide.html (accessed 28 November 2026)
4. JUnit 5 user guide — https://junit.org/junit5/docs/current/user-guide/ (accessed 28 November 2026)
5. Detekt documentation — https://detekt.dev/ (accessed 28 November 2026)
6. JaCoCo documentation — https://www.jacoco.org/jacoco/trunk/doc/ (accessed 28 November 2026)
7. GitHub Actions documentation — https://docs.github.com/actions (accessed 28 November 2026)

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

## Appendix B — Demonstration checklist

Ten minutes, run live from a terminal in the repository root.

| Time | Action | Point being made |
| --- | --- | --- |
| 0:00 | `git log --oneline --graph` | Four pull requests, each merge visible in the history |
| 1:00 | `./gradlew clean build` | Tests, coverage gate and analysis all run by one command |
| 4:00 | Open `build/reports/tests/test/index.html` | 66 tests, none skipped |
| 5:00 | Open `build/reports/jacoco/test/html/index.html` | 92.9% lines; show the uncovered wiring and explain why it is not tested |
| 6:30 | Open `build/reports/detekt/detekt.html` | Zero findings; mention the two that were fixed rather than suppressed |
| 7:00 | `READING_LIST_DATA=/tmp/demo.json ./gradlew run --args="add ..."` | A change travelling from command line to file and back |
| 8:30 | Show `data` file and `list` | Persistence and the three indexed views |

Questions to expect: why three indexes rather than one list; why a rating is tied
to a finished book; why a corrupt file is an error rather than a reset; what the
coverage gate actually blocks.

## Statement on the use of tools

An AI coding assistant was used in a supporting role, for drafting code snippets
and build configuration text. All code was reviewed, executed and verified by the
author, who is responsible for everything submitted.
