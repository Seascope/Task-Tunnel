# Task Tunnel — Agent Operating Policy

## Primary objective

Build Task Tunnel correctly while minimizing included Work/Codex allowance consumption.

Do not equate "important task" with "use the most expensive model."

Use the cheapest model capable of completing each piece of work reliably.

The root agent should intelligently decompose work, delegate bounded tasks when useful, integrate results, and escalate only when evidence shows a more capable model is needed.

Optimize for:

- correctness
- total token/credit cost
- minimal duplicated work
- minimal repeated repository inspection
- minimal repeated builds/tests
- clear ownership of subtasks

Do not optimize for minimizing the number of agents if multiple cheaper agents can complete independent work more efficiently than one expensive agent.

Do not optimize for maximum parallelism either.

Use delegation only when it reduces expected total work, cost, or context usage.

---

# Usage-budget policy

GPT-6 Astra MUST NOT be used as the default root implementation agent.

Default routing:

- Luna for mechanical work
- Terra for routine implementation
- Sol for genuinely non-trivial implementation, integration, or debugging
- Astra only for unresolved architectural, ambiguous, or high-risk problems

Do not use Sol High merely because the milestone is important.

Reasoning effort should also be proportional to the task.

Prefer lower reasoning levels when the problem is straightforward.

For Astra:

- use Low or Medium reasoning unless higher reasoning is demonstrably necessary
- do not run builds
- do not inspect large portions of the repository
- do not write boilerplate
- do not perform repetitive edits
- do not update routine documentation
- do not perform ordinary debugging
- do not reimplement work already completed by cheaper agents
- return to a cheaper model immediately after the difficult decision is resolved

Astra escalation requires a concise evidence package from the cheaper worker unless the problem is clearly architectural or policy-critical from the start.

---

# Model routing

## GPT-5.6 Luna

Use Luna for:

- repository/file inspection
- locating symbols and references
- codebase mapping
- reading logs
- running commands
- build/test execution
- summarizing compiler/test output
- checking git state
- checking diffs
- simple boilerplate
- repetitive edits
- formatting
- documentation updates
- simple renames
- mechanical refactors
- manifest inspection
- Gradle inspection
- gathering evidence for another agent
- confirming whether an implementation matches acceptance criteria

Luna should often be used as an evidence-gathering worker before a more capable model is asked to reason about a problem.

Do not make Sol inspect twenty files if Luna can identify the three relevant ones first.

---

## GPT-5.6 Terra

Use Terra for:

- routine implementation
- bounded feature work
- small or medium multi-file changes
- straightforward Android work
- straightforward Compose work
- UI component implementation
- ViewModel plumbing
- Room/DAO/repository plumbing
- simple state handling
- focused unit tests
- instrumentation-test scaffolding
- ordinary bug fixes
- Gradle or manifest changes with clear requirements
- changes where architecture and expected behavior are already decided

Terra should be the default implementation worker when requirements are clear.

---

## GPT-5.6 Sol

Use Sol for:

- genuinely non-trivial implementation
- cross-component integration
- difficult debugging
- subtle state-machine bugs
- concurrency issues
- lifecycle issues
- AccessibilityService behavior
- overlay/event-ordering problems
- detector implementation or maintenance
- important refactors
- architectural integration within an already-established design
- resolving failures from Luna/Terra
- reviewing consequential changes
- release-critical code review

Sol should not be used just because a task spans multiple files.

Sol should not redo routine implementation that Terra can perform reliably.

Prefer Sol as an integrator/reviewer when several cheaper workers have completed independent pieces.

---

## GPT-6 Astra

Use Astra only when the task genuinely benefits from maximum reasoning:

- architecture and system decomposition
- decisions with significant long-term consequences
- ambiguous technical problems
- difficult bugs after cheaper agents gathered evidence
- cross-component reasoning where causal ownership is unclear
- Play Store / accessibility-policy-sensitive design decisions
- reviewing critical architecture before implementation
- resolving disagreements between lower-cost agents
- resolving repeated failures after proper escalation
- problems where choosing the wrong approach would cause expensive rework

Do not use Astra for work that Luna, Terra, or Sol can reliably perform.

Astra should primarily make decisions, review evidence, resolve ambiguity, and produce bounded implementation guidance.

Astra should rarely be the bulk code author.

---

# Root-agent policy

The root agent does NOT need to be the most capable model.

Choose the root model based on the overall task.

Examples:

MECHANICAL TASK:
Luna may be both root and worker.

ROUTINE FEATURE:
Terra may be root and implement directly, delegating inspection/build work to Luna if useful.

NON-TRIVIAL FEATURE:
Sol may coordinate the task while delegating inspection, boilerplate, tests, or isolated components to Luna/Terra.

CRITICAL / AMBIGUOUS TASK:
Astra may reason about the architecture, then hand implementation back to Sol/Terra.

Do not automatically make Sol High or Astra the root for every milestone.

---

# Delegation rules

Before substantial work, classify each subtask:

MECHANICAL -> Luna

ROUTINE -> Terra

COMPLEX -> Sol

CRITICAL / AMBIGUOUS -> Astra

When uncertain between two levels, start with the cheaper model unless failure would create substantial rework or risk.

Delegate by task shape, not by prestige.

Good delegation examples:

- Luna maps existing Compose screens and relevant files.
- Terra implements reusable UI primitives.
- Terra converts isolated screens.
- Luna runs focused tests and reports failures.
- Sol integrates cross-screen navigation/state behavior.
- Astra is consulted only if a deeper product/architecture decision appears.

Bad delegation examples:

- Sol High reads the whole repository, edits every file, runs every build, writes all docs, and fixes trivial errors.
- Astra writes boilerplate Compose components.
- Three expensive agents independently solve the same straightforward bug.
- A root agent delegates work and then rewrites all delegated work itself.

---

# Parallelism policy

Parallelize only genuinely independent tasks.

Good parallel work:

- repository inspection
- independent screen implementation
- independent unit-test creation
- documentation preparation
- separate static audits
- separate research/evidence gathering

Avoid parallel work when multiple agents would modify the same core files or make conflicting architectural decisions.

If work touches shared central state, prefer sequential ownership or clearly separated file boundaries.

The root agent should avoid merge-conflict-heavy delegation.

---

# Integration policy

Delegation is not complete until results are integrated.

The lead/root agent should:

1. define bounded subtasks
2. assign each task to the cheapest suitable model
3. give each worker only relevant context
4. receive concise results
5. inspect the actual diff/result rather than trusting summaries blindly
6. integrate compatible work
7. resolve conflicts without redoing everything
8. run targeted verification
9. escalate only unresolved issues

The integrating agent should review delegated work, not duplicate it.

---

# Subagent context policy

Subagents should receive only:

- objective
- relevant files
- important constraints
- acceptance criteria
- known failure evidence when applicable

Do not dump the full conversation or entire repository history into every worker.

Do not make every agent reread:

- full feasibility dossier
- full UX dossier
- all milestone logs
- all previous implementation reports

unless the task genuinely requires them.

Prefer references to:

- AGENTS.md
- CURRENT_STATE.md
- specific source files
- specific design documents
- specific failing tests/logs

---

# Escalation protocol

A cheaper agent should not endlessly retry.

If a worker encounters a problem:

1. Make at most 2 meaningful repair attempts.
2. Gather exact evidence:
   - error output
   - relevant files
   - relevant code locations
   - commands executed
   - current hypothesis
   - what was already tried
3. Return a concise escalation report.

Escalation path:

Luna -> Terra -> Sol -> Astra

Do not jump directly to Astra unless the problem is clearly architectural, ambiguous, policy-sensitive, or high-risk.

When escalating, pass the evidence package rather than the entire worker transcript.

If Sol resolves the issue, implementation should return to Terra/Luna where appropriate instead of keeping Sol active for the remaining mechanical work.

---

# Evidence-first debugging

For runtime bugs, especially AccessibilityService and third-party-app detection issues:

Do not speculate first.

Prefer:

instrument -> reproduce -> collect sanitized evidence -> isolate cause -> patch -> verify

Use cheaper agents for:

- collecting logs
- locating relevant code
- comparing fingerprints
- running tests
- summarizing differences

Use Sol/Astra only for the reasoning step if the evidence is genuinely difficult to interpret.

Do not burn expensive-model tokens rediscovering facts that runtime evidence can provide.

---

# Context efficiency

Avoid repeatedly rereading the entire repository.

Maintain concise project state/documentation.

Before opening large files:

- search for symbols
- locate call sites
- identify ownership
- inspect only relevant sections

Prefer narrow diffs.

Prefer concise worker outputs.

Do not paste large build logs to expensive agents when Luna can summarize the relevant failure.

---

# Coding discipline

Implement only the requested task.

Do not generate speculative architecture for future work.

Prefer the simplest correct implementation.

Do not add dependencies without a concrete reason.

Do not refactor unrelated code while implementing a feature.

Do not redesign Task Tunnel's product concept unless a serious technical flaw is discovered.

Avoid opportunistic cleanup.

Avoid "while I'm here" changes.

Preserve established architecture unless the current task actually requires changing it.

---

# UI implementation delegation

UI work should NOT default to Sol High.

For visual/product UI work:

Use Luna for:

- locating current Compose screens
- mapping navigation
- finding themes/colors/typography
- identifying duplicated components
- screenshots/build verification
- routine inspection

Use Terra for:

- design-system primitives
- buttons
- cards
- typography components
- navigation components
- reusable layout components
- isolated screen implementation
- straightforward animation
- previews
- UI-focused tests

Use Sol for:

- cross-screen architecture
- state/navigation integration
- complicated animation/state coordination
- difficult Compose performance bugs
- interaction conflicts with Accessibility overlays
- final integration/review of consequential UI changes

Use Astra only if:

- the visual redesign requires architectural changes
- the UI conflicts with Task Tunnel's runtime model
- there is a genuinely difficult cross-system design decision

Do not use Sol High to hand-author every screen.

---

# Verification discipline

Testing should be proportional to the change.

Do not repeatedly run expensive broad checks when targeted checks are sufficient.

During implementation:

- use focused tests
- use narrow builds where possible
- verify individual components cheaply

At milestone/task completion:

- run the required full relevant test suite once
- run the required build once
- run git diff/checks once

Repeat a broad test only when:

- relevant code changed afterward
- the previous run failed
- a remaining uncertainty specifically requires it

Use Luna for routine test/build execution and result summarization whenever delegation is available.

Do not use Sol/Astra merely to execute commands.

Do not create tests that only duplicate trivial implementation behavior.

---

# Review policy

Not every change needs an expensive review.

Mechanical work:
- Luna self-check or targeted tests are sufficient.

Routine implementation:
- Terra plus tests is normally sufficient.

Consequential cross-component work:
- Sol review may be appropriate.

Architecture/security/policy-sensitive work:
- Astra review may be appropriate.

Do not request Sol/Astra review for every successful change.

---

# Stop conditions

Once acceptance criteria are satisfied:

- stop changing code
- summarize what changed
- report tests performed
- report unresolved risks
- report anything requiring human validation
- do not continue polishing unless requested

Do not invent another milestone.

Do not add post-MVP functionality automatically.

---

# Task Tunnel product constraints

This is a solo-developer Android project.

Preserve:

- Android-only MVP
- Instagram and YouTube as the only Task Tunnel-supported apps for the MVP
- Reddit only as an optional Drift-pool app where already implemented
- Kotlin + Jetpack Compose
- AccessibilityService as the primary runtime mechanism
- local-first architecture
- no backend for MVP
- deterministic rules
- fail-open behavior for uncertain surface detection
- user-visible override
- narrow detector scope
- privacy-preserving event storage
- Purpose Gate -> Task Tunnel -> Drift Detection -> Attention Debugger workflow
- Attention + Protection primary information architecture
- Settings as secondary
- normal users must not see detector internals

Distinguish proposed work as:

- MVP
- post-MVP
- unnecessary scope

Do not expand supported Task Tunnel apps, introduce ML/LLMs, cloud accounts,
hard anti-uninstall behavior, generic app support, or unrelated feature scope
unless explicitly instructed.

---

# Current project-state discipline

Do not assume an old milestone is still current.

Read CURRENT_STATE.md before substantial work.

The project has progressed beyond the original M0-M6 implementation sequence.

Do not reimplement completed milestones unless a regression or explicit redesign requires it.

When changing existing behavior:

- preserve proven detector rules unless evidence requires modification
- preserve fail-open behavior
- preserve local-first privacy constraints
- preserve existing runtime semantics unless explicitly instructed otherwise

Treat completed physical validation as evidence, not something to casually overwrite.

---

# Expensive-model anti-patterns

Avoid all of the following:

- Sol High as the automatic root for every task
- Astra as the automatic root for every milestone
- expensive agents running routine builds
- expensive agents reading entire repositories unnecessarily
- expensive agents writing routine documentation
- expensive agents doing repetitive Compose conversion
- multiple expensive agents solving the same simple problem
- escalating without concrete evidence
- keeping Sol/Astra active after the difficult part is resolved
- redoing delegated work instead of integrating it
- spawning subagents just because delegation exists
- broad parallelism that creates merge conflicts

The goal is not "use the smartest model."

The goal is:

**use the right amount of intelligence at the right point in the workflow.**