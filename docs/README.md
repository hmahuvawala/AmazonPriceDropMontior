# Repo context for agents

This `docs/` directory is a token-efficient briefing for future agents working on the Amazon Price Monitor. Read **only** the files relevant to the change you're making — each is self-contained.

## When to read what

| You are about to… | Read |
|---|---|
| Get oriented / first time in this repo | [overview.md](overview.md) |
| Build, run, or write tests | [build-and-run.md](build-and-run.md) |
| Touch any `application.yml`, env var, or `*Properties` class | [configuration.md](configuration.md) |
| Add/alter a column, index, entity, or repository | [domain-and-db.md](domain-and-db.md) |
| Add/change a REST endpoint, DTO, or validation | [api.md](api.md) |
| Touch a fetcher, AlterLab integration, or money parsing | [price-fetching.md](price-fetching.md) |
| Touch the scheduler, monitoring orchestration, or Slack alerts | [monitoring-and-alerts.md](monitoring-and-alerts.md) |
| Touch the SPA (HTML/CSS/JS) | [frontend.md](frontend.md) |
| Plan a feature; want pointers to the right files | [extension-points.md](extension-points.md) |

## Conventions in these docs

- File and line links are clickable: `[Name](../path/to/File.java#L42)`. They point to the source-of-truth — verify with `Read` before editing if a change has happened since these docs were written.
- "Subtleties" sections call out non-obvious behavior (silent currency hard-coding, transaction scope of Slack call, EU money-format bug, `@ConditionalOnProperty` gating, etc.). These are the things you can't infer from the code at a glance.
- Defaults are duplicated between `application.yml` and the `@ConfigurationProperties` Java classes — when you change one, change the other (or pick one as the source of truth and remove the duplicate).
- Hibernate is `ddl-auto: validate` against Flyway. **Schema changes require a new `V{N}__*.sql` migration**, never an entity-only edit.

## What lives outside `docs/`

- [../README.md](../README.md) — user-facing quickstart, public API table, license. Keep in sync if you add/remove endpoints or env vars.
- [../context.md](../context.md) — short narrative ("current goals / architectural decisions / recent changes"). Update when goals shift.

## Maintaining these docs

When you change code that contradicts something here, update the relevant doc in the same change. Bias toward removing stale content over adding new sections — the value of this directory is that an agent can read three short files and have full context, not ten long ones.
