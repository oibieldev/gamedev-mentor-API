# GameDev Mentor API

Java and Spring Boot backend for contextual and pedagogical mentoring in Game Development.

The project combines deterministic game-project inspection with generative AI to help students investigate bugs, understand programming logic, and develop autonomy instead of simply receiving ready-made solutions.

> **Status:** Functional multi-engine MVP — currently evolving toward stronger validation, automated tests, and structured project models.

---

## Overview

GameDev Mentor receives a student's question together with an optional game project.

The backend identifies the project format, selects the appropriate interpreter, extracts relevant project information, combines that context with the student's question, and sends the resulting prompt to the configured AI provider.

Currently supported engines:

| Engine | Project format | Current inspection |
|---|---|---|
| Scratch | `.sb3` | `project.json` |
| Construct 3 | `.c3p` | project, object types, layouts and event sheets |
| GameMaker | `.yyz` | project, objects, rooms, sprites and GML source code |

The raw project archive is not sent directly to the AI provider.

The backend controls which information is extracted and included in the mentoring context.

---

## Pedagogical Principle

> **AI should amplify student autonomy, not replace the learning process.**

The mentor should prioritize:

- hypotheses;
- debugging directions;
- investigative questions;
- conceptual explanations;
- progressive hints.

Instead of immediately solving the problem, the system should help the student understand what to inspect, why something may be happening, and how to reach the solution.

---

## Current MVP

The current version includes:

- Java;
- Spring Boot REST API;
- Gemini integration;
- provider abstraction through `TextGenerationClient`;
- synchronous HTTP integration;
- `multipart/form-data` requests;
- optional project uploads;
- centralized project interpreter resolution;
- Scratch `.sb3` support;
- Construct 3 `.c3p` support;
- GameMaker `.yyz` support;
- upload size validation;
- pedagogical base prompt;
- text-only mentoring when no project is attached;
- simple browser interface for manual testing.

---

## Current Flow

```text
Student question
      +
optional project
(.sb3 / .c3p / .yyz)
      |
      v
MentorController
      |
      v
MentorService
      |
      v
ProjectInterpreterService
      |
      v
List<ProjectInterpreter>
      |
      +----------------+----------------+
      |                |                |
      v                v                v
   Scratch         Construct 3       GameMaker
Interpreter        Interpreter       Interpreter
      |                |                |
      +----------------+----------------+
                       |
                       v
                Project context
                       |
                       +
                Student question
                       |
                       v
             TextGenerationClient
                       |
                       v
                GeminiTextClient
                       |
                       v
                   Gemini
                       |
                       v
              Mentor response
```

---

# Architecture

## `MentorController`

Responsible for HTTP concerns.

Main responsibilities:

- receive the student's prompt;
- receive the optional project file;
- delegate the request to the service layer;
- return the response.

Parsing rules and AI-provider details should remain outside the controller.

---

## `MentorService`

Coordinates the mentoring flow.

Responsibilities:

- build the pedagogical base prompt;
- detect whether a project was attached;
- request project interpretation;
- combine project context and student question;
- call the text generation abstraction.

The service does not need to know how Scratch, Construct, or GameMaker projects are internally structured.

---

## `ProjectInterpreter`

Common contract for all supported project interpreters.

```java
public interface ProjectInterpreter {

    boolean supports(String _fileExtension);

    String interpret(MultipartFile _file);
}
```

Each implementation decides which extension it supports and how that project format should be inspected.

---

## `ProjectInterpreterService`

Responsible for:

- validating uploaded files;
- identifying the project extension;
- selecting the correct interpreter.

Spring injects all implementations of:

```java
ProjectInterpreter
```

into:

```java
List<ProjectInterpreter>
```

The interpreter is selected dynamically through:

```java
interpreter.supports(fileExtension)
```

This allows new engines to be added without creating engine-specific conditions inside the main mentoring flow.

Current implementations:

```text
ProjectInterpreter
├── ScratchInterpreterService
├── ConstructInterpreterService
└── GameMakerInterpreterService
```

---

# Supported Project Formats

## Scratch `.sb3`

Scratch projects are ZIP packages.

The current interpreter locates:

```text
project.json
```

and extracts it as text.

```text
project.sb3
    |
    v
ZIP inspection
    |
    v
project.json
    |
    v
AI context
```

A semantic Scratch parser is intentionally postponed.

---

## Construct 3 `.c3p`

Construct 3 project files are ZIP packages containing several JSON resources.

The current interpreter extracts the essential project structure:

```text
project.c3proj

objectTypes/
    *.json

layouts/
    *.json

eventSheets/
    *.json
```

UI state files such as:

```text
*.uistate.json
```

are ignored.

The resulting context contains:

```json
{
  "project": {},
  "objectTypes": [],
  "layouts": [],
  "eventSheets": []
}
```

This provides the AI with information about:

- project configuration;
- objects;
- layouts;
- variables;
- conditions;
- actions;
- event logic.

---

## GameMaker `.yyz`

GameMaker `.yyz` files are compressed project packages.

A GameMaker project contains a main `.yyp` file together with several `.yy` resources and GML source files.

The current interpreter intentionally focuses on the essential resources.

### Project

```text
*.yyp
```

### Objects

```text
objects/**/*.yy
```

### Rooms

```text
rooms/**/*.yy
```

### Sprites

```text
sprites/**/*.yy
```

### GML code

```text
**/*.gml
```

Other resources such as images, build options, and IDE metadata are currently ignored.

The resulting context follows this structure:

```json
{
  "project": {},
  "objects": [],
  "rooms": [],
  "sprites": [],
  "code": []
}
```

GML files preserve both their project path and source code:

```json
{
  "file": "objects/obj_player/Step_0.gml",
  "content": "..."
}
```

This is important because the source location provides context about which object or resource owns the code.

GameMaker `.yy` and `.yyp` resources are normalized using Jackson before being included in the resulting context.

GML source code is wrapped into JSON objects so characters such as quotes and line breaks are escaped safely.

---

# Interpreter Extensibility

Adding another engine should require implementing only:

```java
ProjectInterpreter
```

Example:

```java
@Service
public class ExampleInterpreterService
        implements ProjectInterpreter {

    @Override
    public boolean supports(String _fileExtension) {
        return "example".equalsIgnoreCase(_fileExtension);
    }

    @Override
    public String interpret(MultipartFile _file) {
        // project inspection
    }
}
```

Spring automatically includes the new implementation in:

```java
List<ProjectInterpreter>
```

No engine-specific `switch` is required inside `ProjectInterpreterService`.

---

# AI Provider Abstraction

The main application depends on:

```java
public interface TextGenerationClient {

    String generateResponse(String _prompt);
}
```

The current implementation is:

```text
GeminiTextClient
```

This keeps Gemini-specific integration outside the mentoring domain.

Conceptually:

```text
MentorService
      |
      v
TextGenerationClient
      |
      v
GeminiTextClient
      |
      v
Gemini API
```

Other providers can be introduced later without changing the central mentoring flow.

---

# Endpoint

The main endpoint accepts:

```http
POST /api/chat
Content-Type: multipart/form-data
```

Request:

```text
prompt = "My character does not jump. What should I investigate?"

file = project.sb3
```

or:

```text
file = project.c3p
```

or:

```text
file = project.yyz
```

The file is optional.

Without an attached project, GameDev Mentor behaves as a text-only mentor.

---

# Upload Validation

The current application validates:

- empty files;
- maximum upload size;
- file extension;
- availability of an interpreter for the extension.

Current upload limit:

```text
10 MB
```

Supported extensions:

```text
.sb3
.c3p
.yyz
```

---

# Security

Uploaded project files must always be treated as untrusted input.

Current protections include:

- maximum archive upload size;
- controlled interpreter selection;
- project format validation;
- backend-only provider credentials;
- no direct exposure of API keys to the browser.

Archive hardening is still an active engineering task.

Future improvements include:

- archive entry count limits;
- decompressed-size limits;
- per-entry size limits;
- ZIP bomb protection;
- stronger corrupted archive validation;
- application-specific project exceptions;
- centralized HTTP error handling.

The current interpreters still use archive-entry reads that should eventually receive decompressed-size protection.

---

# Privacy

Real student information must never be committed to the public repository.

Do not commit:

- student names;
- student conversations;
- private student projects;
- school credentials;
- internal institutional URLs;
- API keys;
- access tokens;
- private institutional documents.

Tests should use synthetic or sanitized fixtures.

---

# Current Limitations

Project interpretation currently prioritizes useful context extraction rather than full semantic parsing.

### Scratch

Currently extracts:

```text
project.json
```

No semantic block model yet.

### Construct 3

Currently extracts relevant project JSON resources.

There is no dedicated Java domain model yet.

### GameMaker

Currently extracts:

- project metadata;
- objects;
- rooms;
- sprites;
- GML source code.

The interpreter does not currently build:

- GML ASTs;
- symbol tables;
- variable graphs;
- call graphs;
- object relationships;
- semantic code models.

These capabilities may be introduced only when they provide clear value to mentoring.

---

# Immediate Engineering Roadmap

## Project Models

Replace manually assembled JSON strings incrementally with structured Java models using records where useful.

Possible direction:

```text
ScratchProject
ConstructProject
GameMakerProject
```

---

## Error Handling

Introduce application-specific exceptions and:

```java
@RestControllerAdvice
```

Possible errors:

```text
INVALID_PROJECT_FILE
UNSUPPORTED_PROJECT_FORMAT
INVALID_SCRATCH_PROJECT
INVALID_CONSTRUCT_PROJECT
INVALID_GAMEMAKER_PROJECT
AI_PROVIDER_RATE_LIMITED
AI_PROVIDER_UNAVAILABLE
AI_PROVIDER_TIMEOUT
INTERNAL_ERROR
```

---

## Archive Security

Add:

- maximum entry count;
- decompressed-size limits;
- resource size limits;
- corrupted archive validation;
- ZIP bomb protection.

---

## Automated Tests

Add tests for:

```text
ScratchInterpreterService
ConstructInterpreterService
GameMakerInterpreterService
ProjectInterpreterService
MentorService
MentorController
```

Important scenarios:

- supported project;
- unsupported extension;
- empty file;
- oversized file;
- malformed archive;
- missing root project file;
- malformed JSON resource;
- valid GML extraction.

CI should eventually execute:

```bash
./mvnw verify
```

for every Pull Request.

---

# Future Product Roadmap

Possible future capabilities include:

- structured project Records;
- richer GameMaker GML analysis;
- deeper Construct project relationships;
- teacher and student mentoring profiles;
- persistent user data;
- pedagogical personalization;
- image generation;
- audio generation;
- multi-provider AI routing;
- IDE integrations;
- Unity project inspection;
- larger asynchronous analysis workflows.

These should be introduced incrementally according to demonstrated product needs.

---

# Development Workflow

Prefer short-lived and focused branches.

Examples:

```text
feat/gamemaker-interpreter
feat/project-records
fix/archive-validation
test/project-interpreters
feat/api-exception-handler
docs/update-readme
```

---

## Conventional Commits

Commit messages should be concise and preferably written in English.

Examples:

```text
feat(interpreter): add GameMaker project support

feat(interpreter): add Construct project support

refactor(interpreter): resolve project interpreters dynamically

fix(scratch): correct project reading error

test(gamemaker): cover YYZ project interpretation

docs: update supported project formats
```

Avoid vague messages such as:

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

Each commit should represent one small, understandable change.

---

# Development Philosophy

The project follows a few core principles:

- keep external integrations at system boundaries;
- inspect projects deterministically before sending context to AI;
- keep engine-specific logic inside dedicated interpreters;
- prefer simple abstractions with clear responsibilities;
- treat uploaded files as hostile input;
- never expose credentials to clients;
- preserve student autonomy as a product requirement;
- evolve through small and testable Pull Requests;
- avoid premature semantic parsing;
- keep the code understandable before making it sophisticated.

---

# Current Status

```text
Gemini text mentoring              ✅
Provider abstraction               ✅
Text-only mentoring                ✅

ProjectInterpreter abstraction     ✅
Dynamic interpreter resolution     ✅

Scratch .sb3 support               ✅
Construct 3 .c3p support           ✅
GameMaker .yyz support             ✅

Browser testing interface          ✅
10 MB upload validation            ✅

Structured project Records         ⏳
Global exception handling          ⏳
Automated interpreter tests        ⏳
Archive hardening                  ⏳
CI verification                    ⏳
```

---

## Tech Stack

```text
Java 25
Spring Boot 4
Spring MVC
Jackson
Lombok
Gemini API
Maven
```

---

## Author

Developed as an educational backend experiment focused on applying AI to Game Development mentoring without replacing the student's learning process.
