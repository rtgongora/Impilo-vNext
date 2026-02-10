# Impilo vNext — Offline Maven Build Harness

This directory contains tooling for building and testing the Impilo vNext Java services without requiring Maven Central or DNS access at build time.

## Approach 1: Vendored Local Repository (Recommended)

The vendored approach uses a local Maven repository at `vendor/m2/repository/` that ships with the repo (or is populated once from a connected machine).

### Populating the vendor repo (one-time, on a connected machine)

```bash
# On a machine with internet access, run a full build to populate the vendor cache:
./tools/maven/mvn-local.sh -pl services/vito-service -am test-compile

# The vendor/m2/repository/ directory now contains all required artifacts.
# Copy it to the offline machine:
rsync -a vendor/m2/repository/ <offline-machine>:<repo>/vendor/m2/repository/
```

Or copy your existing `~/.m2/repository` into the vendor directory:

```bash
cp -r ~/.m2/repository/* vendor/m2/repository/
```

### Running an offline build

```bash
# Verify offline readiness:
./tools/maven/verify-offline.sh

# Compile VITO service offline:
./tools/maven/mvn-local.sh -pl services/vito-service -am test-compile -o

# Run tests offline:
./tools/maven/mvn-local.sh -pl services/vito-service -am test -o
```

## Approach 2: Local Nexus Mirror (LAN-Connected)

For teams with a LAN but no internet, run a local Nexus instance as a caching proxy.

### Start Nexus

```bash
docker compose -f tools/maven/nexus/docker-compose.yml up -d

# Wait ~60 seconds for Nexus to start.
# UI: http://localhost:8081
# Default admin password: cat tools/maven/nexus/data/admin.password
```

### Configure Nexus (first time)

1. Log in at http://localhost:8081 with admin credentials.
2. The default `maven-central` proxy repository should already be configured.
3. Optionally create a hosted repo named `impilo-internal` for team-built artifacts.

### Build via Nexus

```bash
# This uses Nexus as the Maven mirror AND caches into vendor/m2/repository:
./tools/maven/mvn-nexus.sh -pl services/vito-service -am test-compile
```

After the first build, `vendor/m2/repository/` will be populated and subsequent builds can use `mvn-local.sh -o` for fully offline operation.

## Offline Compile Commands (Quick Reference)

```bash
# 1. Check readiness
./tools/maven/verify-offline.sh

# 2. Offline test-compile for VITO
./tools/maven/mvn-local.sh -pl services/vito-service -am test-compile -o

# 3. Offline test run for VITO (H2 profile)
./tools/maven/mvn-local.sh -pl services/vito-service -am test -o -Dspring.profiles.active=test

# 4. Full offline build (all services)
./tools/maven/mvn-local.sh -DskipTests package -o
```

## Troubleshooting

### "Non-resolvable parent POM" error

This means the Spring Boot parent POM is missing from the vendor repo. The minimum required paths are:

```
vendor/m2/repository/org/springframework/boot/spring-boot-starter-parent/3.3.6/
vendor/m2/repository/org/springframework/boot/spring-boot-dependencies/3.3.6/
```

**Fix:** Copy these from a connected machine's `~/.m2/repository/`:

```bash
# On connected machine:
mkdir -p vendor/m2/repository/org/springframework/boot/
cp -r ~/.m2/repository/org/springframework/boot/spring-boot-starter-parent \
      vendor/m2/repository/org/springframework/boot/
cp -r ~/.m2/repository/org/springframework/boot/spring-boot-dependencies \
      vendor/m2/repository/org/springframework/boot/
```

Or run `verify-offline.sh` which will print the exact missing paths and remediation steps.

### "Could not resolve dependencies" in offline mode

The vendor repo is missing transitive dependencies. On a connected machine, run a full resolve:

```bash
./tools/maven/mvn-local.sh dependency:go-offline -pl services/vito-service -am
```

Then copy the updated `vendor/m2/repository/` to the offline machine.

### H2 test errors (VITO)

VITO tests use H2 in PostgreSQL-compatibility mode with `application-test.yml`. Key points:
- Flyway is disabled (Postgres-specific migrations don't parse in H2).
- Hibernate `ddl-auto: create-drop` creates JPA-managed tables.
- `testdata/seed.sql` creates non-JPA tables (e.g., `idempotency_keys`) and inserts baseline data.
- Kafka and Redis auto-configuration is excluded to prevent network calls.

## File Inventory

| File | Purpose |
|------|---------|
| `tools/maven/mvn-local.sh` | Maven wrapper using vendored local repo |
| `tools/maven/mvn-nexus.sh` | Maven wrapper using local Nexus + vendored repo |
| `tools/maven/verify-offline.sh` | Offline readiness verifier + VITO compile check |
| `tools/maven/settings-nexus.xml` | Maven settings pointing to local Nexus |
| `tools/maven/nexus/docker-compose.yml` | Nexus OSS container definition |
| `tools/maven/README.md` | This file |
| `vendor/m2/repository/` | Vendored Maven artifact cache (gitignored) |
