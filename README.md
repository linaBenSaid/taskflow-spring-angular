# TaskFlow

A mini Jira: teams create projects, assign tasks on a Kanban board, and comment on them.
Built with Spring Boot 4 (Java 21) and Angular.

> **Live demo:** _TODO_ · **Demo login:** _TODO_ · **API docs (Swagger UI):** _TODO_

<!-- TODO: CI and coverage badges -->
<!-- TODO: screenshot or GIF -->

## Tech stack

| Layer | Choice |
| --- | --- |
| Backend | Java 21, Spring Boot 4 (Web, Data JPA, Security, Validation), Maven |
| Database | PostgreSQL + Flyway |
| Auth | JWT, roles ADMIN and MEMBER |
| Frontend | Angular (standalone components, signals), Angular Material |
| Tests | JUnit 5, Mockito, Testcontainers · Vitest, Playwright |
| DevOps | Docker, docker-compose, GitHub Actions |

## Project structure

```
taskflow/
├── backend/    # Spring Boot REST API
└── frontend/   # Angular SPA
```

## Architecture

_TODO: diagram_

## Run locally

_TODO: `docker compose up`_

1. Copy `.env.example` to `.env` and fill in the values.

## What I learned

_TODO_

## Next steps

_TODO_
