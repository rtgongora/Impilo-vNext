# Knowledge admin console

Minimal **Next.js 14** operator UI for the national clinical knowledge **curation queue** (approve/reject proposed PDF excerpts into `clinical_knowledge_items`).

## Run

```powershell
cd ui/knowledge-admin
npm install
npm run dev
```

Open `http://localhost:3021`. Paste a JWT with **SYSTEM_ADMIN**, **FACILITY_ADMIN**, or **DEVELOPER** (same rule gate as BFF `/internal/v1/clinical/curation/**`). Requests proxy via `next.config.mjs` rewrites to `NEXT_PUBLIC_BFF_URL` (default `http://localhost:8160`).

Companion headers are sent explicitly from the page (tenant/pod/request/correlation).

## Related APIs

- `GET /internal/v1/clinical/curation/review-items?status=PROPOSED`
- `POST /internal/v1/clinical/curation/review-items/{id}/decision` — body `{ "decision": "APPROVED"|"REJECTED", "reviewer": "...", "notes": "..." }`

Upstream: `clinical-knowledge-platform-service` (see `docs/runbooks/clinical-knowledge-platform-dev.md`).
