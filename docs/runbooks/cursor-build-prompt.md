# Cursor Build Prompt

> Copy everything below the line into Cursor's agent chat window.

---

You are working on the Impilo vNext Health Operating System. The repo is already cloned at the current working directory. You are on branch `claude/staging-ux-orchestration-remediation-Yypyl`.

Read `CLAUDE.md` at the repo root first — it contains the project's architectural rules, commit conventions, and tech stack. Follow them exactly.

Your job is to build every component, fix every error, push every fix, and document what you did. Do not add features. Do not delete tests. Do not downgrade dependencies. Do not create a pull request.

## Context you need to know

- The Experience BFF (`services/experience-bff/`) is a **pure proxy** — no database, no JPA, no Spring Data. All domain entities and repositories were intentionally deleted. If you find any leftover imports referencing `zw.gov.mohcc.impilo.experience.domain.*`, `...repository.*`, `...events.OutboxPublisher`, or `...service.OutboxService`, those are dead imports — remove them.
- The UI (`ui/experience/`) uses a custom Tailwind colour palette called `impilo` (brand green #1F7A3A). Classes like `bg-impilo-500`, `text-impilo-600` are correct — they are defined in `tailwind.config.ts`.
- The mobile apps (`apps/mobile/citizen-app/` and `apps/mobile/provider-app/`) use a shared design system where `Button` takes a `title` prop (not `label`) and `size="sm"` (not `size="small"`).
- BFF runs on port 8160. UI runs on port 3020. Full port map is in `docs/runbooks/port-allocation.md`.

## Step 1 — Java build

Run from the repo root:

```
mvn clean compile -DskipTests -T1C 2>&1 | tail -200
```

If it fails, read the FIRST error (not the cascade), fix the root cause, then re-run. Common fixes:
- Dead import → remove it.
- Duplicate bean → remove one or add `@Primary`.
- Missing dependency → check `pom.xml`.
- Method signature changed → align the caller.

After each fix, commit and push:
```
git add <changed-files>
git commit -m "fix(service-name): describe what you fixed"
git push origin claude/staging-ux-orchestration-remediation-Yypyl
```

If the full build is too large, focus on the critical path first:
```
mvn clean compile -pl services/experience-bff -am -DskipTests
```

Once compile passes, run tests:
```
mvn test -pl services/experience-bff
```

Fix any test failures. Do not add `@Disabled` — fix the code or the test.

## Step 2 — Experience UI build

```
cd ui/experience
pnpm install
pnpm type-check
```

If `pnpm` is not available, use `npm install && npx tsc --noEmit`.

Common TypeScript fixes:
- `TS2307` (Cannot find module) → file moved or deleted, update the import path.
- `TS2345` (Argument not assignable) → prop type changed, align the call site.
- `TS1005` (expected comma) → syntax error, check for missing commas or stray control characters.

After type-check passes:
```
NEXT_PUBLIC_BFF_URL=http://localhost:8160 pnpm build
pnpm test
pnpm lint
```

Fix every error. Commit and push after each fix.

## Step 3 — Mobile apps build

```
cd apps/mobile
pnpm install

cd citizen-app
pnpm type-check
pnpm test

cd ../provider-app
pnpm type-check
pnpm test
```

Common mobile fixes:
- Missing design system export → check `packages/mobile-design-system/src/index.ts`.
- Wrong Button prop → use `title` not `label`, `size="sm"` not `size="small"`.
- Missing type → add it to the app's `types/index.ts`.

Commit and push after each fix.

## Step 4 — Write the build log

Create `docs/runbooks/build-log.md` with this structure:

```markdown
# Build Log

## YYYY-MM-DD — Stabilisation Build

### Environment
- Java: (output of java -version)
- Node: (output of node -v)
- pnpm: (output of pnpm -v)
- Maven: (output of mvn -v | head -1)
- OS: (output of uname -a)

### Java Build
- mvn clean compile: PASS / FAIL
- mvn test (BFF): PASS / FAIL (X passed, Y failed)
- Fixes:
  1. fix(service): description — commit SHA
  2. ...

### Experience UI Build
- pnpm type-check: PASS / FAIL
- pnpm build: PASS / FAIL
- pnpm test: PASS / FAIL (X passed, Y failed)
- pnpm lint: PASS / FAIL
- Fixes:
  1. fix(ui): description — commit SHA
  2. ...

### Mobile Apps Build
- Citizen type-check: PASS / FAIL
- Provider type-check: PASS / FAIL
- Citizen test: PASS / FAIL
- Provider test: PASS / FAIL
- Fixes:
  1. fix(mobile): description — commit SHA
  2. ...

### Summary
- Total fixes applied: N
- All builds green: YES / NO
- Remaining blockers: (list or "none")
```

Commit and push the build log:
```
git add docs/runbooks/build-log.md
git commit -m "docs: add build log from stabilisation pass"
git push origin claude/staging-ux-orchestration-remediation-Yypyl
```

## Step 5 — Update the build guide if needed

If you discovered missing prerequisites, wrong commands, incorrect ports, or additional required steps, update `docs/runbooks/first-build-guide.md` with corrections.

```
git add docs/runbooks/first-build-guide.md
git commit -m "docs: update build guide with corrections from stabilisation"
git push origin claude/staging-ux-orchestration-remediation-Yypyl
```

## Final check

Run `git status` — working tree must be clean. Run `git log --oneline -20` and confirm all your fix commits are pushed.
