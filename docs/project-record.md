# Project record

A running log of decisions and their reasons. It is updated as the work happens,
not rewritten afterwards.

## 2026-11-27

**Scope.** Reading-list manager: books move `to-read -> reading -> finished`, a
rating belongs to a finished book, and `stats`/`next` answer the two questions a
reader actually has.

**Domain rules.** Ids are short (`b1`, `b2`) rather than UUIDs so that a
demonstration from the command line stays readable. Duplicate detection is on
title plus author, case-insensitive, because the same edition added twice is a
mistake while a new edition is not (it would carry a different title or author).

**Indexes.** Three structures with a job each: `LinkedHashMap` for id lookup and
insertion order, a case-insensitive `TreeMap` for author lookups that are already
sorted, and a `TreeSet` for the unread queue ordered by the date added. The
alternative — one list filtered and sorted per command — was rejected because
`list`, `next` and `stats` would each re-sort the whole library.

**Persistence.** JSON through kotlinx-serialization, written to a temporary file
and moved into place. A corrupt file raises an error instead of being replaced,
because silently discarding a user's list is the worst possible failure mode.

**Toolchain.** Gradle 8.13 with the wrapper committed, Kotlin 2.1.20, JUnit 5,
JaCoCo and Detekt. Dependencies are pinned to exact versions so that a build on a
laboratory machine resolves the same artefacts.

**Problems hit.**
- `JAVA_HOME` pointed at JDK 8; Gradle 8 needs 17+. Changed to the installed
  Temurin 21, both as a system variable and inside the IDE.
- Gradle's test worker passes its classpath in an `@argfile` under the Gradle user
  home. With a non-ASCII user profile path, the JVM could not read that file and
  failed with `ClassNotFoundException: GradleWorkerMain`. Fixed by pointing
  `GRADLE_USER_HOME` at an ASCII path.
- The wrapper's download from `services.gradle.org` timed out on this network, so
  the distribution was seeded into the wrapper cache. The canonical URL and its
  SHA-256 are kept in `gradle-wrapper.properties` so the build stays portable.

**Next.** Push the four feature branches, merge them through pull requests in
order, then write the demonstration script and the PDF report.
