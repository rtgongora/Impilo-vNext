# Impilo vNext — Build, Fix, and Push

## Objective

Build every component of the Impilo vNext platform on branch `claude/staging-ux-orchestration-remediation-Yypyl`, fix every error you encounter, push all fixes, and document what you fixed.

## Rules

1. **Never skip a failing build.** If something fails, read the error, diagnose the root cause, fix it, and re-run.
2. **Atomic commits.** One commit per logical fix. Use Conventional Commits: `fix:`, `chore:`, `refactor:`.
3. **Push after every successful fix.** `git push -u origin claude/staging-ux-orchestration-remediation-Yypyl` — retry up to 4 times with exponential backoff on network failure.
4. **Do not delete tests.** If a test fails, fix the code or the test — never remove it.
5. **Do not downgrade dependencies** unless the current version has a known CVE or is genuinely incompatible.
6. **Do not introduce new features.** This is a stabilisation pass — fix what's broken, nothing more.
7. **Log every fix** in the build log file at the end.

## Phase 1 — Infrastructure

```bash
# Verify Docker is running
docker info

# Start infrastructure (PostgreSQL, Redis, Kafka, Keycloak)
docker compose -f docker-compose.runtime.yml up -d

# Wait for healthy containers (poll every 5s, timeout 120s)
timeout 120 bash -c 'until docker compose -f docker-compose.runtime.yml ps | grep -q "healthy"; do sleep 5; done'

# Seed databases (if script exists and pg is reachable)
psql -h localhost -U postgres < scripts/seed/init-databases.sql 2>/dev/null || echo "SKIP: DB seed (psql not available or already seeded)"

# Bootstrap Kafka topics
bash scripts/bootstrap/bootstrap-topics.sh 2>/dev/null || echo "SKIP: Kafka topics (broker not reachable)"
```

If Docker is not available, skip this phase and proceed to compilation — the builds do not require running infrastructure.

## Phase 2 — Java (Maven)

### 2a. Full compile from root

```bash
cd /home/user/Impilo-vNext
mvn clean compile -DskipTests -T1C 2>&1 | tail -100
```

If this fails:
- Read the **first** error (not the cascade). Maven errors cascade — fix the root cause.
- Common issues:
  - **Missing dependency**: check `pom.xml` for typos or missing `<module>` entries.
  - **Dead import**: a class was deleted but something still imports it. Remove the import.
  - **Incompatible type**: a method signature changed upstream. Align the caller.
  - **Duplicate bean**: two `@Component`/`@Bean` definitions for the same type. Remove one or qualify with `@Primary`.
- After fixing, re-run `mvn clean compile -DskipTests -T1C`.
- Commit each fix: `git add <files> && git commit -m "fix(service-name): describe what you fixed"`.

### 2b. Focus on the Experience BFF

If the full build is too large, focus on the critical path:

```bash
mvn clean compile -pl services/experience-bff -am -DskipTests
```

`-am` builds parent + shared libs (`tech-companion`, `shared-kernel-java`, `tshepo-contracts`).

### 2c. Run BFF tests (if compile passes)

```bash
mvn test -pl services/experience-bff
```

Fix any test failures. Do not skip tests with `@Disabled` unless the test is genuinely obsolete.

## Phase 3 — Experience UI (Next.js / TypeScript)

### 3a. Install and type-check

```bash
cd /home/user/Impilo-vNext/ui/experience
pnpm install
pnpm type-check
```

If `pnpm` is not available, use `npm install && npx tsc --noEmit`.

Common TypeScript errors to fix:
- **TS2307 (Cannot find module)**: a file was moved or deleted. Update the import path.
- **TS2345 (Argument not assignable)**: a prop type changed. Align the component call.
- **TS2304 (Cannot find name)**: a type/function was removed. Add the import or define it.
- **TS1005 (expected comma/bracket)**: syntax error. Check for missing commas, quotes, or brackets. Look for control characters (`\x01`) masquerading as missing commas.

After each fix, re-run `pnpm type-check` to confirm.

### 3b. Build

```bash
pnpm build
```

If build fails but `type-check` passed, the issue is likely:
- A runtime import that only fails during SSR (dynamic `window` access).
- A missing environment variable. Set `NEXT_PUBLIC_BFF_URL=http://localhost:8160`.

### 3c. Run tests

```bash
pnpm test
```

Fix any failing tests.

### 3d. Lint

```bash
pnpm lint
```

Fix lint errors. Do not add `eslint-disable` comments — fix the underlying issue.

## Phase 4 — Mobile Apps (Expo / React Native)

### 4a. Install workspace dependencies

```bash
cd /home/user/Impilo-vNext/apps/mobile
pnpm install
```

### 4b. Type-check Citizen App

```bash
cd citizen-app
pnpm type-check
```

Common issues:
- **Missing type exports from `@impilo/mobile-design-system`**: check the design system's `src/index.ts` exports.
- **Prop name mismatches**: the design system `Button` uses `title` not `label`, and `size="sm"` not `size="small"`.

### 4c. Type-check Provider App

```bash
cd ../provider-app
pnpm type-check
```

### 4d. Run tests for both

```bash
cd ../citizen-app && pnpm test
cd ../provider-app && pnpm test
```

Fix any failures.

## Phase 5 — Commit and Push All Fixes

After each fix throughout the process, you should have already committed and pushed. Do a final check:

```bash
cd /home/user/Impilo-vNext
git status
```

If there are uncommitted changes, stage and commit them:

```bash
git add -A
git commit -m "fix: final stabilisation pass — describe remaining fixes"
git push -u origin claude/staging-ux-orchestration-remediation-Yypyl
```

## Phase 6 — Write the Build Log

Create or update the file `docs/runbooks/build-log.md` with a timestamped entry:

```markdown
# Build Log

## YYYY-MM-DD — First Stabilisation Build

### Environment
- Java: (version from `java -version`)
- Node: (version from `node -v`)
- pnpm: (version from `pnpm -v`)
- Maven: (version from `mvn -v`)
- OS: (output of `uname -a`)

### Phase 2 — Java
- [ ] `mvn clean compile -DskipTests` — PASS / FAIL
- Fixes applied:
  1. `fix(service): description` — commit hash
  2. ...

### Phase 3 — Experience UI
- [ ] `pnpm type-check` — PASS / FAIL
- [ ] `pnpm build` — PASS / FAIL
- [ ] `pnpm test` — PASS / FAIL (X passed, Y failed)
- Fixes applied:
  1. `fix(ui): description` — commit hash
  2. ...

### Phase 4 — Mobile Apps
- [ ] Citizen `pnpm type-check` — PASS / FAIL
- [ ] Provider `pnpm type-check` — PASS / FAIL
- [ ] Citizen `pnpm test` — PASS / FAIL
- [ ] Provider `pnpm test` — PASS / FAIL
- Fixes applied:
  1. `fix(mobile): description` — commit hash
  2. ...

### Summary
- Total fixes: N
- All builds passing: YES / NO
- Remaining blockers: (list or "none")
```

Commit and push the build log:

```bash
git add docs/runbooks/build-log.md
git commit -m "docs: add build log from stabilisation pass"
git push -u origin claude/staging-ux-orchestration-remediation-Yypyl
```

## Phase 7 — Update the Build Guide (if needed)

If you discovered any missing prerequisites, incorrect commands, wrong ports, or additional steps during the build, update `docs/runbooks/first-build-guide.md`:

```bash
# Edit the file with corrections
git add docs/runbooks/first-build-guide.md
git commit -m "docs: update build guide with corrections from first build"
git push -u origin claude/staging-ux-orchestration-remediation-Yypyl
```

## Important Context

- **Branch**: `claude/staging-ux-orchestration-remediation-Yypyl`
- **BFF architecture**: Pure proxy — no database, no JPA, no Spring Data. All domain entities and repositories were deleted. If you see references to them, those are dead imports to remove.
- **BFF port**: 8160
- **UI port**: 3020
- **Tailwind**: Extended with `impilo` colour palette (brand green #1F7A3A). If you see `bg-impilo-500` in TSX, that's correct — it's defined in `tailwind.config.ts`.
- **CLAUDE.md**: Read this file at repo root for full project conventions before making changes.
- **Do not create a pull request.** Just fix, commit, and push to the branch.
