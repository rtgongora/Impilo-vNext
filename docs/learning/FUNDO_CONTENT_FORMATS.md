# Fundo content formats (authoring and upload)

Impilo Fundo accepts learning assets in governed formats. Authors upload **metadata + reference URL** via the library; lesson players render native content types.

## Supported upload resource types

| Library `resourceType` | Lesson `contentType` | Player behaviour |
|------------------------|----------------------|------------------|
| `PDF`, `SOP`, `JOB_AID` | `DOCUMENT` | Linked document opens in new tab |
| `VIDEO` | `VIDEO` | YouTube/Vimeo embed or MP4 inline player |
| `LINK` | `LINK` | External resource link |
| `IMAGE`, `AUDIO`, `WORD`, `POWERPOINT` | `DOCUMENT` or `LINK` | Reference URL until rich viewer ships |
| Structured JSON checklist | `PRACTICAL_TASK` | In-lesson competency checklist |
| Markdown/plain text | `TEXT` | Inline lesson body |
| `STRUCTURED_BLOCKS` JSON | `TEXT` + `contentFormat` | Heading + paragraph blocks |

## Authoring workflow

1. **Upload** — `/learning/library/uploads` — title, resource type, `contentRef` URL, governance tags (`reviewStatus: DRAFT`).
2. **Link to course** — `/learning/admin/courses/{id}/edit` — create module + lesson; set `contentType` and `contentRef` from library asset.
3. **Publish** — course status `PUBLISHED`; pathway optional for cadre rollouts.
4. **Assess** — attach quiz; free-text items route to `/learning/admin/moderation`.

## CPD and certificates

- Course `cpdEligible` + `cpdPoints` are **evidence hints** only.
- Certificate issuance produces metadata + **SHA-256 `verificationDigest`** (tamper-evident, not PKI).
- Council acceptance and CPD ledger credit remain in **Varapi** (`/registry/provider-council/self-service`).

## Compose / preview runtime

Experience compose includes `learning-service:8235`. Run:

```bash
./tools/dev/up.sh
curl -sf http://localhost:8235/actuator/health
curl -sf -H 'X-Tenant-ID: 00000000-0000-0000-0000-000000000001' -H 'X-Pod-ID: national-spine' \
  'http://localhost:8160/internal/v1/learning/v11/catalog?status=PUBLISHED&limit=5'
```
