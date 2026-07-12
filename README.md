# Game Dev Mentor API

Java/Spring backend prototype for guided Game Dev mentoring through deterministic project parsing, explainable diagnostics and replaceable AI providers.

> **Status:** active pre-MVP. This public repository is the generic, sanitized core. Institution-specific integration and real student data do not belong here.

## Product goal

Help students investigate Game Dev projects without turning AI into a shortcut for copying complete solutions.

```text
.c3p upload
  → safe ZIP inspection
  → normalized ProjectSummary
  → deterministic diagnostics
  → pedagogical prompt policy
  → mock or real LLM adapter
  → guided response with evidence
```

## MVP scope

- Java 21 and Spring Boot
- safe `.c3p` upload
- read-only Construct parser
- versioned `ProjectSummary`
- explainable issue detection
- guided, diagnostic and teacher modes
- PostgreSQL metadata and audit history
- provider-independent LLM and image ports
- mocks enabled by default
- Docker, CI and automated tests

## Non-goals

- modifying student project files;
- automatically inserting generated assets;
- sending raw archives directly to an LLM;
- exposing provider credentials to a browser;
- storing real student projects as public fixtures.

## Architecture

```text
web adapters
    ↓
application use cases
    ↓
domain and diagnostic rules
    ↑
parser · storage · persistence · LLM · image adapters
```

The parser and deterministic diagnostics must remain useful when every AI provider is disabled.

## Upload security

Treat every project file as untrusted input: validate size and signature, limit archive expansion, prevent Zip Slip and ZIP bombs, never execute uploaded content and use sanitized fixtures.

## Local target

```bash
./mvnw verify
docker compose up -d
./mvnw spring-boot:run
```

## Roadmap

1. bootstrap, health and CI;
2. secure archive index;
3. minimal Construct summary;
4. objects, variables and event sheets;
5. deterministic issue detection;
6. mock mentor flow;
7. real providers behind feature flags;
8. authentication, retention and pilot hardening.

## Privacy

Never commit student names, conversations, private `.c3p` files, Portal URLs, internal contracts or credentials.

## License

To be selected after confirming ownership and contribution terms.
