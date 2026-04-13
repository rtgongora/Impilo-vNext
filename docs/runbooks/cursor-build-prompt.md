# Developer Runbook — Build Stabilisation

> **Audience**: Developers running the first build of the `claude/staging-ux-orchestration-remediation-Yypyl` branch.
>
> **Goal**: Compile every component, verify tests pass, document results.
>
> See also: `docs/runbooks/first-build-guide.md` for prerequisites and full setup.

---

## 1. Java Services (Maven)

### Compile all services

```bash
cd /path/to/Impilo-vNext
mvn clean compile -DskipTests -T1C
```

If the full build is too large for a first pass, focus on the critical path:

```bash
mvn clean compile -pl services/experience-bff -am -DskipTests
```

### Run BFF tests

```bash
mvn test -pl services/experience-bff
```

### Known patterns to watch for

| Symptom | Fix |
|---------|-----|
| Import references `experience.domain.*` or `experience.repository.*` | Dead import — the BFF is a pure proxy with no JPA. Delete the import. |
| Duplicate bean definition | Remove one or annotate with `@Primary`. |
| `resilience4j` version mismatch | BFF uses `2.2.0`. Check parent POM `<resilience4j.version>`. |

---

## 2. Experience UI (Next.js)

### Install, type-check, build

```bash
cd ui/experience
pnpm install
pnpm type-check                                         # Must be zero errors
NEXT_PUBLIC_BFF_URL=http://localhost:8160 pnpm build     # Production build
```

### Run tests and lint

```bash
pnpm test
pnpm lint
```

### Known patterns to watch for

| Symptom | Fix |
|---------|-----|
| Unknown Tailwind class `bg-impilo-500` | Correct — defined in `tailwind.config.ts`. Do NOT replace with `bg-green-*`. |
| `TS1005` expected comma | Check for stray control characters (`\x01`). Run `grep -P '\x01' src/**/*.ts` to find them. Replace with `, `. |
| `TS2307` Cannot find module `@/components/brand/ImpiloLogo` | File should exist at `src/components/brand/ImpiloLogo.tsx`. If missing, the branch checkout was incomplete. |

---

## 3. Mobile Apps (Expo / React Native)

### Install workspace, then type-check each app

```bash
cd apps/mobile
pnpm install

cd citizen-app
pnpm type-check
pnpm test

cd ../provider-app
pnpm type-check
pnpm test
```

### Known patterns to watch for

| Symptom | Fix |
|---------|-----|
| `Button` has no prop `label` | Use `title` instead. The `@impilo/mobile-design-system` Button API uses `title`. |
| `size="small"` is not assignable | Use `size="sm"`. Valid values: `"sm"`, `"md"`, `"lg"`. |
| Cannot find `@impilo/mobile-design-system` | Run `pnpm install` from `apps/mobile/` (workspace root), not from the individual app. |

---

## 4. Build Log Template

After completing all phases, create `docs/runbooks/build-log.md`:

```markdown
# Build Log

## YYYY-MM-DD — Stabilisation Build

### Environment
- Java: (java -version)
- Node: (node -v)
- pnpm: (pnpm -v)
- Maven: (mvn -v | head -1)
- OS: (uname -a)

### Java Build
- mvn clean compile: PASS / FAIL
- mvn test (BFF): PASS / FAIL (X passed, Y failed)
- Fixes applied:
  1. description — commit SHA

### Experience UI Build
- pnpm type-check: PASS / FAIL
- pnpm build: PASS / FAIL
- pnpm test: PASS / FAIL (X passed, Y failed)
- pnpm lint: PASS / FAIL
- Fixes applied:
  1. description — commit SHA

### Mobile Apps Build
- Citizen type-check: PASS / FAIL
- Provider type-check: PASS / FAIL
- Citizen test: PASS / FAIL
- Provider test: PASS / FAIL
- Fixes applied:
  1. description — commit SHA

### Summary
- Total fixes: N
- All builds green: YES / NO
- Remaining blockers: (list or "none")
```

Commit the log:

```bash
git add docs/runbooks/build-log.md
git commit -m "docs: add build log from stabilisation pass"
git push origin claude/staging-ux-orchestration-remediation-Yypyl
```

---

## 5. Quick Reference

| Component | Command | Port |
|-----------|---------|------|
| BFF compile | `mvn clean compile -pl services/experience-bff -am -DskipTests` | 8160 |
| BFF run | `cd services/experience-bff && mvn spring-boot:run` | 8160 |
| BFF health | `curl http://localhost:8160/actuator/health` | — |
| UI dev | `cd ui/experience && NEXT_PUBLIC_BFF_URL=http://localhost:8160 pnpm dev` | 3020 |
| UI build | `cd ui/experience && pnpm build` | — |
| Mobile install | `cd apps/mobile && pnpm install` | — |
| Citizen dev | `cd apps/mobile/citizen-app && pnpm start` | Expo |
| Provider dev | `cd apps/mobile/provider-app && pnpm start` | Expo |
