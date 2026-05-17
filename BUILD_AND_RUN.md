# Build and Run

## Prerequisites

- Java 21
- Maven 3.9+
- Node 20+
- npm 10+ (web)
- pnpm 9+ (mobile)
- Docker Desktop (optional but recommended for infra dependencies)

## Repository Build Entry Points

- Backend: `services/`
- Web: `ui/`
- Mobile: `apps/mobile/`

## Backend Build

From `services`:

```bash
mvn -DskipTests package
```

Targeted resume after a failure:

```bash
mvn -DskipTests package -rf :<module-artifact-id>
```

## Web Build (Turbo)

From `ui`:

```bash
npm run type-check
npm run lint
npm run build
```

## Mobile Build/Test

From `apps/mobile`:

```bash
pnpm install
pnpm -r type-check
pnpm -r test
```

## Run (Developer Mode)

### Backend services

From `services`:

```bash
mvn -pl experience-bff -am spring-boot:run
```

Run other services similarly by module (`-pl tshepo-service`, `-pl nhume-service`, etc).

### Web app examples

From `ui`:

```bash
npm -w one-ui-shell run dev
npm -w experience run dev
```

### Mobile apps

From `apps/mobile`:

```bash
pnpm --filter @impilo/citizen-app start
pnpm --filter @impilo/provider-app start
```

## Environment and Infra Notes

- Backend defaults many service base URLs to localhost in `experience-bff` config.
- For coherent multi-service runtime, use compose/runtime scripts referenced in:
  - `compose/experience/README.md`
  - `ops/runtime/environments/README.md`
- For local-only build verification, compile/package can be done without bringing all infra up.
