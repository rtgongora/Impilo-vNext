# Clinical Plane Wave 1 — Cursor Agent Briefs

## Overview

5 agents to close critical Clinical Plane gaps and create two new services.

| Agent | Scope |
|-------|-------|
| 1 | Inpatient Revival — OAuth2, Kafka events, OpenAPI, PCT subordination |
| 2 | PACS Full Implementation — OAuth2, Kafka events, OpenAPI, OROS wiring |
| 3 | Clinical Knowledge + Document Wiring — OAuth2, event consumers |
| 4 | Community Service Creation — new care setting subordinate to PCT |
| 5 | SIMBA Service Creation — new wellness orchestrator |

All agents work on branch: claude/implement-impilo-vnext-issues-fr4iV
All agents must: git pull at start, git push at end.

---

## Agent 1: Inpatient Revival

Scope: Make inpatient-service a functional care setting subordinate to PCT.
Currently: 15 files, 1 test, no OAuth2, no Kafka, no OpenAPI. Completely isolated.

Tasks:
1. Add OAuth2 to application.yml
2. Add Kafka producer config + outbox publisher
3. Add Kafka consumers for PCT journey events
4. Create OpenAPI contract
5. Publish admission/discharge/transfer events for PCT consumption

---

## Agent 2: PACS Full Implementation

Scope: Make pacs-adapter-service a proper execution service.
Currently: 13 files, no OAuth2, no Kafka events, no OpenAPI.

Tasks:
1. Add OAuth2 to application.yml
2. Add Kafka producer — publish pacs.study.available when studies arrive
3. Create OpenAPI contract
4. Wire OROS integration (OROS already listens for pacs.study.available)

---

## Agent 3: Clinical Knowledge + Document Wiring

Scope: Fix security on clinical-knowledge-platform-service, wire document events.
Currently: Clinical Knowledge has no OAuth2 AND no SecurityConfig. Document events published but never consumed.

Tasks:
1. Add OAuth2 + SecurityConfig to clinical-knowledge-platform-service
2. Verify document-service events are consumable by PCT

---

## Agent 4: Community Service Creation

Scope: Create community-service as a new care setting subordinate to PCT.
Handles: outreach visits, home visits, CHW workflows, community health units.

Tasks:
1. Create service skeleton (Spring Boot 3.3, Java 21)
2. Database schema (community unit, outreach visit, CHW assignment)
3. REST controllers
4. Kafka outbox
5. OpenAPI contract
6. Wire as PCT care setting

---

## Agent 5: SIMBA Service Creation

Scope: Create simba-service as the wellness orchestrator.
Owns the 15+ BFF wellness tables. Parallels PCT for the wellness domain.

Tasks:
1. Create service skeleton (Spring Boot 3.3, Java 21)
2. Database schema (activities, vitals log, mood, challenges, clubs, sleep, exercise, diet, goals, Health Connect)
3. REST controllers
4. Kafka outbox
5. OpenAPI contract
6. Register in services-registry.yaml
