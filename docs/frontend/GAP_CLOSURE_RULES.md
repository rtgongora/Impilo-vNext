# Gap closure rules (mandatory)

> **Policy:** We ship **full functionality only**. No stubs, no mocks, no “route exists” theater.
> Violating these rules creates rework loops: parity passes, product regresses, gaps reopen.

Applies to all agents and humans closing items in `REMAINING_FRONTEND_GAPS.md`, parity sweeps, and BFF/UI waves.

---

## 1. Definition of done

A gap is **closed** only when **all** of the following are true:

| # | Requirement |
|---|-------------|
| 1 | **Canonical client** — UI uses the domain hook/client for that bounded context (`useNhume`, `CoreTransactionCompositionService`, registry hooks, etc.), not a generic substitute from another domain. |
| 2 | **Real chain** — `route/screen → hook → BFF → sovereign service → contract` is wired and demonstrable. |
| 3 | **Product UI** — Lists, detail, and mutations render typed/domain fields; users can complete the intended workflow. |
| 4 | **Tests** — At least one test or e2e path proves the happy path (or documented denial path with authz). |
| 5 | **Honest maturity** — Surface labelled **Live** or **Partial** with explicit partial scope; never **Live** on fixture/stub data. |

**Not sufficient to close a gap:**

- A `page.tsx` file exists (route parity alone).
- BFF returns hard-coded / fallback JSON pretending to be upstream data.
- A page that dumps `JSON.stringify(apiResponse)` instead of product UI.
- Rewriting a mature page to fewer lines without an approved deprecation ADR.

---

## 2. Forbidden patterns (zero tolerance for new work)

### 2.1 UI — do not add

| Pattern | Why forbidden |
|---------|----------------|
| `{JSON.stringify(data)}` (or `<pre>` dumps) as the **primary** page body | Debug stub, not product |
| Empty handlers `onClick={() => {}}` on primary actions | Fake affordance |
| `useDispatch` (or other generic ops hooks) inside **`/nhume/**`** | Wrong bounded context; use `useNhume` / `lib/nhume` |
| Removing imports of existing domain hooks without replacing with equivalent UI | Silent downgrade |
| Marking gap **Closed** in docs while maturity is Fixture / Not wired | Checklist fraud |

### 2.2 BFF — do not add

| Pattern | Why forbidden |
|---------|----------------|
| Static “dev fallback” payloads masquerading as successful upstream responses | Mocks in production path |
| Controllers that bypass existing `*CompositionService` / domain clients to inline 20-line mappers | Loses PCT/Costa/trust composition |
| `200 OK` with empty fake lists when upstream failed, without `error` / `failureModes` | Hides outages |

**Allowed:** Structured empty results (`items: []`) with explicit `failureModes` or error envelope when upstream is unavailable.

### 2.3 Docs — do not add

| Pattern | Why forbidden |
|---------|----------------|
| “Closed (UI)” when only a shell/stub was added | Misleading status |
| Follow-up sections that duplicate open gaps without tracking owners/dates | Hides debt |

---

## 3. Prefer extend — replace only when strictly better

The anti-pattern is **downgrade replacement**: a smaller diff that removes capability and calls it “done.” That is what this policy blocks.

**Default (preferred):** extend the existing stack in place.

1. **Find existing code first** (`useNhume.ts`, `CoreTransactionCompositionService`, prior `page.tsx` on `main`).
2. **Wire or extend** it (new BFF field, tab, mutation, composition branch).
3. Keep canonical domain clients; do not swap them for a generic hook from another bounded context.

**Replacement is allowed** when the new implementation is **demonstrably more comprehensive** than what it replaces — not merely different or newer.

| Criterion | Required for replacement |
|-----------|---------------------------|
| Capability | Same user journeys **plus** additional ones (or same journeys with strictly better depth) |
| Data path | Same or stronger chain (hook → BFF → sovereign service); no new stubs/mocks |
| API surface | Same or more mutations/read models; no dropped actions “for later” |
| Proof in PR | Short **replacement rationale** listing what the old code did, what the new code does, and why nothing was lost |

```
✅  Extend: CoreTransactionCompositionService.listCoreTransactions() + dispatch merge
✅  Replace: Rewrite delivery detail page — adds custody tab + 8 mutations that were missing in old page
❌  Replace: Nhume delivery detail → JSON.stringify + two buttons (fewer lines ≠ better)
❌  Replace: CoreTransactionController bypassing composition service (fewer lines, less composition)
```

**When in doubt:** extend the existing module. If you believe replacement is justified, prove parity in the PR — line count alone is not proof.

---

## 4. Domain canonical clients (reference)

| Zone | Canonical UI | Canonical backend |
|------|----------------|-------------------|
| Nhume / logistics | `@/hooks/useNhume`, `@/lib/nhume` | `nhume-service` via BFF nhume proxies |
| Core transaction | `useCoreTransactionExperience` + doctrine types | `CoreTransactionCompositionService` |
| Citizen personal health | `useCitizenHealthSummary` / mobile citizen summary | `CitizenHealthSummaryService` |
| Dispatch (facility ops) | `useDispatch` | `/internal/v1/dispatch/*` |
| Registry / VARAPI | `useRegistry` | `RegistryController`, `VarapiServiceClient` |

Using a “nearby” API to avoid wiring the canonical stack is **not** consolidation — it is a stub.

---

## 5. Maturity labels vs gap closure

From [`MATURITY_TAXONOMY.md`](./MATURITY_TAXONOMY.md):

| Label | May close a gap? |
|-------|------------------|
| **Live** | Yes, if §1 satisfied |
| **Partial** | Only if gap text explicitly allows partial scope and missing pieces are listed |
| **Fixture** | **No** — prototype isolation only |
| **Not wired** | **No** |
| **Blocked** | **No** — document blocker instead |

---

## 6. PR / agent checklist (copy into every gap PR)

```markdown
- [ ] I did not replace a comprehensive page/hook/service with a thinner substitute
- [ ] No JSON.stringify / pre-dump primary UI on touched pages
- [ ] Nhume paths use useNhume (not useDispatch)
- [ ] Core-transaction paths use CoreTransactionCompositionService (not bypass)
- [ ] BFF has no new static mock success payloads
- [ ] Gap doc status matches real maturity (not "Closed" for stubs)
- [ ] test:routes + test:no-stubs + type-check pass
```

---

## 7. CI enforcement

| Check | Command |
|-------|---------|
| Route files exist | `npm run test:routes` |
| No stub patterns / domain rules | `npm run test:no-stubs` |

Implemented in `ui/one-ui-shell/scripts/no-stub-guard.mjs`.

**Blocking (CI fails):**

- Any violation under `src/app/nhume/**` (full zone strict).
- Any violation in a `page.tsx` file **changed in the PR**.
- All violations when `NO_STUB_STRICT=1` (local debt burn-down).

**Legacy debt:** Unchanged non-nhume pages with existing `JSON.stringify` dumps are reported as **warnings** until remediated — **do not add new pages to this debt.**

---

## 8. Remediating existing stub debt

Legacy pages that predate this policy may still contain debug dumps. **Do not add them to an allowlist.** Remediation = replace with real UI in the same PR that touches the file, or a dedicated stub-removal PR.

---

## Related docs

- [`AGENTS.md`](../../AGENTS.md) — repository-wide integrity rules
- [`REMAINING_FRONTEND_GAPS.md`](./REMAINING_FRONTEND_GAPS.md) — backlog (status must follow §1)
- [`MATURITY_TAXONOMY.md`](./MATURITY_TAXONOMY.md) — honest labelling
- [`FRONTEND_IMPLEMENTATION_STATUS.md`](./FRONTEND_IMPLEMENTATION_STATUS.md) — surface-by-surface truth
