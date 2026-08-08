# GameDev Mentor API

Java and Spring Boot backend for contextual and pedagogical mentoring in Game Development.

The project combines deterministic project inspection with generative AI to help students investigate bugs, understand programming logic, and develop autonomy instead of simply receiving ready-made solutions.

> **Status:** Functional MVP — currently in stabilization and engineering-quality improvement.

---

## Overview

GameDev Mentor started as a simple text integration with Gemini and evolved into a backend capable of receiving a student's question together with an optional Scratch `.sb3` project.

When a project is attached, the API:

1. validates the uploaded file;
2. opens the `.sb3` package as a ZIP archive;
3. locates and extracts `project.json`;
4. combines the project context with the student's question;
5. sends the controlled context to Gemini;
6. returns a pedagogically guided response.

The current goal is not to let AI solve the student's project.

The mentor should prioritize:

- hypotheses;
- debugging directions;
- investigative questions;
- conceptual explanations;
- progressive hints.

---

## Current MVP

The current version already includes:

- Spring Boot REST API;
- Gemini integration;
- synchronous HTTP integration using `RestClient`;
- provider abstraction through `TextGenerationClient`;
- DTOs implemented with Java `record`;
- JSON serialization and deserialization with Jackson;
- API key configured through environment variables;
- configurable Gemini model;
- `multipart/form-data` support;
- optional Scratch `.sb3` upload;
- basic Scratch package inspection;
- `project.json` extraction;
- upload size validation;
- pedagogical base prompt;
- text-only mentoring when no project is attached;
- simple HTML interface for manual testing;
- development through branches and Pull Requests.

---

## Current Flow

```text
Student question
      +
optional .sb3 file
      |
      v
MentorController
      |
      v
MentorService
      |
      +-------------------------+
      |                         |
      v                         v
ScratchInterpreterService   TextGenerationClient
      |                         |
      v                         v
 project.json              GeminiTextClient
      |                         |
      +------ context ----------+
                                |
                                v
                           Gemini API
                                |
                                v
                         MentorResponse
```

---

## Current Architecture

The MVP uses a simple layered architecture while keeping external AI integration isolated from the main application flow.

### `MentorController`

Responsible for HTTP concerns:

- receives the prompt;
- receives the optional project file;
- delegates processing to the service;
- returns the HTTP response.

The controller should not contain parsing rules or AI-provider integration details.

### `MentorService`

Coordinates the mentoring flow.

It decides whether a project is attached, requests project inspection when necessary, builds the pedagogical context, and calls the text generation contract.

### `ScratchInterpreterService`

Responsible for the current Scratch project inspection.

A Scratch `.sb3` file is a ZIP package containing a `project.json` file that describes the project's logical structure.

The current implementation extracts this JSON as text.

> A full semantic Scratch parser is intentionally not part of the current stabilization cycle.

### `TextGenerationClient`

Represents the abstract capability of generating a text response.

```java
public interface TextGenerationClient {
    String generateResponse(String _prompt);
}
```

The application depends on this contract instead of depending directly on Gemini.

### `GeminiTextClient`

Current implementation of `TextGenerationClient`.

Responsibilities:

- build Gemini requests;
- execute HTTP requests;
- deserialize provider responses;
- validate the returned structure;
- extract generated text;
- translate provider-specific failures.

This separation allows other providers to be introduced later without changing the main mentoring flow.

---

## Pedagogical Principle

> **AI should amplify student autonomy, not replace the learning process.**

The mentor should avoid immediately providing complete solutions.

Responses should encourage students to:

1. inspect the problem;
2. formulate hypotheses;
3. test project behavior;
4. understand the underlying concept;
5. reach the solution with progressively less assistance.

---

## Current Endpoint

The main endpoint accepts `multipart/form-data`.

```http
POST /api/chat
Content-Type: multipart/form-data
```

Request parts:

```text
prompt = "My character does not jump. What should I investigate?"
file   = project.sb3   # optional
```

Without a project file, the same endpoint behaves as a text mentor.

---

## Scratch Flow

```text
project.sb3
    |
    v
ZIP inspection
    |
    v
project.json
    |
    + student question
    |
    v
pedagogical prompt
    |
    v
Gemini
    |
    v
guided response
```

The raw `.sb3` archive is not sent directly to the AI provider.

The backend controls which project information reaches the model.

---

## Gemini Configuration

Gemini is currently the only real AI provider enabled by the application.

Example configuration:

```properties
gemini.api.base-url=https://generativelanguage.googleapis.com
gemini.api.key=${GEMINI_API_KEY}
gemini.api.model=gemini-3.6-flash
```

The API key must remain server-side.

Example:

```bash
export GEMINI_API_KEY="your-api-key"
```

Gemini-specific code remains isolated behind `TextGenerationClient`.

Gemini is therefore an implementation detail of the current MVP, not a dependency of the main application logic.

---

## Security

Uploaded project files must always be treated as untrusted input.

### Current protections

- maximum upload size;
- `.sb3` extension validation;
- provider credentials stored only on the backend;
- API key not exposed to the browser.

### Stabilization improvements

- stronger ZIP validation;
- explicit rejection of malformed archives;
- failure when `project.json` is missing;
- archive entry limits;
- decompressed-size limits;
- `project.json` size limit;
- ZIP bomb protection;
- `try-with-resources`;
- global exception handling;
- removal of `printStackTrace()` from application flow.

Real student projects must never be committed as public fixtures.

Tests should use synthetic and sanitized projects.

---

## Privacy

This public repository represents only the generic and sanitized project core.

Do not commit:

- student names;
- real student conversations;
- private student projects;
- school credentials;
- internal PROFIA Portal URLs;
- API keys;
- access tokens;
- private institutional documents.

Institution-specific integrations should remain separate from the public core.

---

# Immediate Roadmap — MVP Stabilization

Before adding new product features, the existing MVP will be corrected and hardened.

## 1. Documentation

- [ ] keep the README aligned with the real implementation;
- [ ] document local execution;
- [ ] document `multipart/form-data` requests;
- [ ] document current limitations.

## 2. Build Consistency

- [ ] review the Java version used by the project;
- [ ] align documentation and build configuration;
- [ ] remove unused dependencies;
- [ ] keep `pom.xml` aligned with the actual implementation.

## 3. Code Cleanup

- [ ] remove Gemini-specific naming from the service layer;
- [ ] review dependency visibility;
- [ ] remove unused imports;
- [ ] simplify controller and method names when useful;
- [ ] preserve the project's `_parameter` naming convention.

Example:

```java
public String generateResponse(String _prompt) {
    // ...
}
```

The `_` prefix is intentionally kept to distinguish method parameters from local variables.

## 4. Error Handling

- [ ] create application-specific exceptions;
- [ ] implement `@RestControllerAdvice`;
- [ ] map invalid files to appropriate HTTP responses;
- [ ] translate provider failures consistently;
- [ ] remove `printStackTrace()`.

Possible error codes:

```text
INVALID_PROJECT_FILE
INVALID_SCRATCH_PROJECT
AI_PROVIDER_RATE_LIMITED
AI_PROVIDER_UNAVAILABLE
AI_PROVIDER_TIMEOUT
INTERNAL_ERROR
```

## 5. Scratch Interpreter Hardening

- [ ] use `try-with-resources`;
- [ ] fail explicitly when `project.json` is missing;
- [ ] reject corrupted ZIP archives;
- [ ] limit archive entry count;
- [ ] limit decompressed data;
- [ ] limit `project.json` size;
- [ ] add ZIP bomb protection.

> Semantic Scratch parsing is intentionally postponed.

## 6. Automated Tests

- [ ] `ScratchInterpreterService` tests;
- [ ] `MentorService` tests;
- [ ] mocked `TextGenerationClient`;
- [ ] multipart endpoint tests;
- [ ] invalid file tests;
- [ ] malformed archive tests;
- [ ] missing `project.json` tests;
- [ ] CI with `./mvnw verify`.

---

# Future Product Roadmap

## 1. Construct `.c3p` Interpreter

Add support for Construct projects.

Initial goals:

- layouts;
- objects;
- behaviors;
- variables;
- Event Sheets;
- conditions;
- actions;
- includes;
- assets;
- basic relationships between elements.

Support should evolve incrementally, starting with simple educational projects.

---

## 2. GameMaker `.yyp` Interpreter

Evaluate and implement support for GameMaker projects.

Possible stages:

### Level 1 — Inventory

- rooms;
- objects;
- sprites;
- scripts;
- assets.

### Level 2 — Relationships

- objects using sprites;
- instances inside rooms;
- resource references.

### Level 3 — Structural GML Analysis

- source parsing;
- AST;
- calls;
- variables;
- symbols.

Full GML analysis should be studied before being treated as a committed requirement.

---

## 3. Image Generation

Add image generation through Gemini for:

- visual concepts;
- sprites;
- character references;
- educational assets;
- visual prototyping.

This capability should use separate endpoints and quotas from text mentoring.

---

## 4. Teacher and Student Profiles

Introduce different mentoring behaviors for:

- **Student**
- **Teacher**

### Student

More guided answers with:

- investigative questions;
- progressive hints;
- conceptual explanations.

### Teacher

More technical answers with:

- diagnostics;
- likely causes;
- project structure analysis;
- intervention suggestions.

---

## 5. Local Database

Introduce local persistence for:

- users;
- profiles;
- settings;
- summarized interactions;
- personalization data;
- project metadata.

The storage technology will be selected when this requirement becomes active.

---

## 6. Pedagogical Personalization

Each student may eventually have an evolving learning profile based on observable usage signals.

Possible signals:

- concepts already understood;
- recurring mistakes;
- number of hints required;
- concepts currently being developed;
- preferred explanation style;
- recent progress;
- increasing autonomy.

This system should record observable evidence rather than psychological labels.

Example:

```json
{
  "strengths": [
    "understands simple sequences"
  ],
  "developingSkills": [
    "nested loops"
  ],
  "preferredSupport": [
    "step-by-step questions"
  ]
}
```

This feature may later integrate with ContextSyncAI.

---

## 7. Scalability and Asynchronous Processing

Spring already supports concurrent HTTP requests.

Asynchronous processing should be introduced when workloads actually require operations outside the request-response flow.

Possible cases:

- image generation;
- audio generation;
- 3D generation;
- expensive project analysis;
- queues;
- batch processing;
- long-running background tasks.

---

## 8. Multi-LLM Orchestration

Expand the provider abstraction to support multiple models.

Possible providers:

- Gemini;
- OpenAI;
- Anthropic;
- Mistral;
- open-weight models;
- other providers.

A future routing layer may select providers according to:

- task type;
- cost;
- latency;
- availability;
- reasoning requirements;
- privacy policy.

Conceptual flow:

```text
Student request
      |
      v
ModelRouter
      |
      +------ Gemini
      |
      +------ OpenAI
      |
      +------ Anthropic
      |
      +------ alternative model
```

---

## 9. Audio Generation

Research APIs for:

- narration;
- spoken explanations;
- sound effects;
- accessibility support;
- educational audio.

---

## 10. 3D Model Generation

Evaluate AI-generated assets in formats compatible with Game Development workflows.

Possible outputs:

- `.glb`;
- `.gltf`;
- other engine-compatible formats.

The priority should be practical compatibility rather than technology demonstration alone.

---

## 11. PROFIA-Owned AI Model

Long-term research topic.

It should only be considered when there is:

- sufficient data;
- proper governance;
- a sanitized dataset;
- a clear methodology;
- benchmarks;
- infrastructure;
- demonstrated need;
- economic justification.

Possible approaches:

- fine-tuning;
- specialized open-weight models;
- proprietary embeddings;
- auxiliary classifiers;
- routing models.

This is not an MVP goal.

---

## 12. VS Code Extension / IDE Integration

A future extension may allow GameDev Mentor to understand larger projects such as Unity applications without requiring manual upload of a single file.

Conceptual flow:

```text
VS Code Extension
      |
      v
controlled project inspection
      |
      v
GameDev Mentor API
      |
      v
analysis + AI
```

Possible information:

- file tree;
- C# scripts;
- scenes;
- prefabs;
- packages;
- compiler errors;
- logs;
- references between scripts.

The extension should never upload an entire project indiscriminately.

---

# Not Being Implemented Now

To preserve focus, the following are intentionally postponed:

- semantic Scratch parser;
- Construct support;
- GameMaker support;
- database;
- user profiles;
- pedagogical personalization;
- multi-LLM routing;
- image generation;
- audio generation;
- 3D generation;
- proprietary AI model;
- VS Code extension.

The current priority is **engineering quality of the existing MVP**.

---

# Development Workflow

Use short-lived and focused branches.

Examples:

```text
docs/update-readme
build/clean-unused-dependencies
refactor/mentor-service
fix/scratch-error-handling
fix/scratch-archive-security
test/scratch-interpreter
feat/api-exception-handler
```

---

## Conventional Commits

Commits should be concise and preferably written in English.

Examples:

```text
docs: align README with current MVP

build: remove unused AI dependencies

refactor(mentor): remove provider-specific naming

fix(scratch): close archive stream safely

fix(scratch): fail when project json is missing

feat(api): add global exception handling

test(scratch): cover malformed archives
```

Avoid messages such as:

```text
updates
changes
final
fix stuff
finish project
```

---

## Before Each Commit

```bash
git status
git diff
git add -p
git diff --staged
./mvnw verify
git commit
```

Each commit should represent one small, logical, understandable change.

---

# Development Philosophy

The project follows a few simple rules:

- keep external integrations at system boundaries;
- inspect and validate projects before sending context to AI;
- prefer deterministic logic when AI is not required;
- create abstractions only when a real boundary exists;
- treat external files as hostile input;
- never expose credentials to clients;
- preserve student autonomy as a product requirement;
- evolve through small and testable Pull Requests;
- keep the code simple enough to understand and maintain.

---

# Current Status

```text
Gemini text MVP                  ✅
Provider abstraction             ✅
Scratch .sb3 upload              ✅
project.json extraction          ✅
Pedagogical base prompt          ✅
Browser demo                     ✅
Pull Requests                    ✅

MVP stabilization               🚧
Global error handling            🚧
Automated tests                  🚧
Robust ZIP security              🚧
Dependency cleanup               🚧

Construct interpreter            ⏳
GameMaker interpreter            ⏳
Image generation                 ⏳
Teacher/Student profiles         ⏳
Local database                   ⏳
Pedagogical personalization      ⏳
Multi-LLM orchestration          ⏳
Audio and 3D generation          ⏳
PROFIA-owned AI model            ⏳
VS Code extension                ⏳
```

---

# Why This Project Exists

GameDev Mentor is both an educational product experiment and a backend engineering project.

It explores the integration of:

- Java;
- Spring Boot;
- external APIs;
- file parsing;
- defensive programming;
- software architecture;
- generative AI;
- Game Development;
- educational methodology.

The long-term goal is not to build just another chatbot.

The goal is to build a system that understands enough about the student's real project to provide useful, explainable, contextual, and pedagogically appropriate guidance.

---

## License

The license will be defined after project ownership, public scope, and future contribution rules are finalized.
