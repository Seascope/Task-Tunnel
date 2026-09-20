# Task Tunnel — Agent Operating Policy

## Usage-budget policy

Minimize included Work/Codex allowance consumption.

GPT-6 Astra MUST NOT be used as the default root implementation agent.

Default root model:
- Luna for mechanical work
- Terra for routine implementation
- Sol for non-trivial implementation
- Astra only for unresolved architectural/ambiguous problems

Do not spawn subagents merely because delegation is available.
Use the minimum number of agents necessary.

Prefer one focused worker over several parallel workers for small milestones.

For Astra:
- use Low or Medium reasoning unless higher reasoning is demonstrably necessary
- do not run builds, inspect large portions of the repository, write boilerplate,
  update routine documentation, or perform ordinary debugging
- return to a cheaper model immediately after the difficult decision is resolved

Astra escalation requires a concise evidence package from the cheaper worker.
## Primary objective

Build Task Tunnel correctly while minimizing expensive-model usage.

Treat GPT-6 Astra as the technical lead and escalation model, not as the default implementation worker.

Whenever delegation is available, delegate bounded work to the cheapest model capable of completing it reliably.

## Model routing

Use GPT-5.6 Luna for:
- repository/file inspection
- locating symbols and references
- reading logs
- running commands
- build/test execution
- summarizing compiler/test output
- simple boilerplate
- repetitive edits
- formatting
- documentation updates
- simple renames
- mechanical refactors
- gathering evidence for another agent

Use GPT-5.6 Terra for:
- routine implementation
- small or medium multi-file changes
- straightforward Android/Compose work
- simple test creation
- ordinary bug fixes
- changes where requirements are already clear

Use GPT-5.6 Sol for:
- non-trivial implementation
- difficult debugging
- concurrency/state issues
- AccessibilityService behavior
- detector implementation
- important refactors
- review of consequential changes

Use GPT-6 Astra only when the task genuinely benefits from maximum reasoning:
- architecture and system decomposition
- decisions with significant long-term consequences
- ambiguous technical problems
- difficult bugs after cheaper agents have gathered evidence
- cross-component reasoning
- Play Store / accessibility-policy-sensitive implementation decisions
- reviewing critical designs before implementation
- resolving disagreements or failures from lower-cost agents

Do not use Astra for work that Luna, Terra, or Sol can reliably perform.

## Astra's role

When Astra is the root agent:

1. Understand the goal and constraints.
2. Break the work into bounded tasks.
3. Decide which tasks actually require Astra-level reasoning.
4. Delegate everything else to cheaper agents.
5. Give delegated agents minimal relevant context rather than the entire conversation/repository history.
6. Review their results rather than redoing their work.
7. Integrate results and make only the decisions that require high-level judgment.

Astra should prefer being an orchestrator/reviewer over being the bulk code author.

## Delegation rules

Before doing substantial work directly, classify the task:

MECHANICAL -> Luna
ROUTINE -> Terra
COMPLEX -> Sol
CRITICAL / AMBIGUOUS -> Astra

When uncertain between two levels, start with the cheaper model unless failure would be costly.

Parallelize independent investigation tasks when doing so reduces total expensive-model context.

Do not spawn an Astra subagent for routine work.

## Escalation protocol

A cheaper agent should not endlessly retry.

If a worker encounters a problem:

1. Make at most 2 meaningful repair attempts.
2. Gather exact evidence:
   - error output
   - relevant files
   - commands executed
   - hypothesis
   - what was already tried
3. Return a concise escalation report.

Escalate:
Luna -> Terra -> Sol -> Astra

Do not jump directly to Astra unless the problem is clearly architectural, ambiguous, or high risk.

When escalating, pass only the relevant evidence instead of the full worker transcript.

## Context efficiency

Avoid repeatedly rereading the entire repository.

Maintain concise project state/documentation where useful.

Before opening large files, search for the relevant symbols or sections.

Subagents should receive:
- objective
- relevant files
- constraints
- acceptance criteria

Do not pass unrelated project history.

Prefer concise outputs from worker agents.

## Coding discipline

Do not generate speculative architecture for future milestones.

Implement only the current milestone unless a tiny supporting abstraction is clearly necessary.

Prefer the simplest correct implementation.

Do not add dependencies without a concrete reason.

Do not refactor unrelated code while implementing a task.

Do not redesign Task Tunnel's product concept unless a serious technical flaw is discovered.

## Verification discipline

Testing should be proportional to the change.

Do not repeatedly run expensive or broad checks when a targeted check is sufficient.

For small changes:
- run the narrowest relevant check

For milestone completion:
- run the required build/tests once after implementation

Repeat a test only when:
- code changed after the previous run
- the test failed
- a remaining uncertainty specifically requires it

Do not create tests that merely duplicate trivial implementation behavior.

## Stop conditions

Once acceptance criteria are satisfied:
- stop changing code
- summarize what changed
- report tests performed
- report unresolved risks
- do not continue polishing unless requested

Avoid opportunistic features and "while I'm here" work.

## Task Tunnel product constraints

This is a solo-developer Android project.

Preserve:
- Android-only MVP
- exactly Instagram and YouTube initially
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

Distinguish all proposed work as:
- MVP
- post-MVP
- unnecessary scope

Do not expand supported apps, introduce ML/LLMs, cloud accounts,
hard anti-uninstall behavior, or generic app support during the MVP
unless explicitly instructed.

## M0 spike constraints

- M0 is limited to the Compose developer UI, AccessibilityService foreground/event state, a sanitized bounded tree inspector, and a test overlay.
- Do not persist UI text, content descriptions, screenshots, raw accessibility trees, or other raw user content.
- Do not add detectors, drift logic, Purpose Gate workflow behavior, or later milestones.
- Physical device testing is not assumed; report emulator/device-dependent validation separately when unavailable.
- Route mechanical inspection, commands, build/test execution, and documentation to Luna; routine implementation to Terra; complex AccessibilityService or state work to Sol; critical or ambiguous architecture/policy decisions to Astra.
- A cheaper agent may make at most two meaningful repair attempts before escalating with exact evidence.
