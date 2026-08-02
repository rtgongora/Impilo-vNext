# Checkpoint 5 — consent evaluate wire

**Captured:** 2026-08-02 · **Branch:** `claude/tshepo-trust-completion-Yypyl`

## The defect

`tshepo-authz`'s `ConsentClient` POSTed a JSON body to an endpoint that only accepts
`GET /v1/consent/evaluate` with query parameters. **Five simultaneous mismatches:**

| # | Sent | Producer requires |
|---|---|---|
| 1 | `POST` | `GET` only ⇒ 405 |
| 2 | `resourceId` (+ a `resourceType` the producer does not know) | `subjectRef` |
| 3 | `purposeOfUse` | `purpose` |
| 4 | *(nothing)* | `scope` — required, no default |
| 5 | expected a bare `ConsentDecision` | an `ApiResponse` envelope |

Even a correct method would have failed on the missing `scope`; even a correct request would have
deserialised the envelope to all-nulls, i.e. `permitted=false`.

## Why it was invisible

The client's catch-all converts every failure into `deny("CONSENT_SERVICE_UNAVAILABLE")`.
**Fail-closed hid a call that could never have succeeded.** Nothing failed a test, nothing logged
an error anyone chased, and the estate looked like it had working consent enforcement.

It stayed latent only because Envoy/ext_authz is off the live path. The moment the PDP goes
on-path, **every consent-gated request would have been denied** — and the cause would have read as
a consent problem rather than a wire problem.

This is why the contract test asserts the **request**, not just the returned decision. A test that
only checked this side would have passed against the broken version too; one of the assertions
parses the producer's controller source directly, so drift on *either* side fails.

## A wrong "improvement", caught by an existing test

Fixing the wire, I also changed the call site to prefer `request.subjectId()` over
`request.resourceId()` as `subjectRef` — reasoning that consent governs the subject, not the
record.

That was wrong, and `PolicyEngineTest.evaluate_delegated_activeInScope_allows` caught it
immediately. **`X-Subject-ID` in this estate is the *delegation* subject** — the person an actor
declares they are acting for — not the subject of the record being read. In that test the two
differ, and the change would have evaluated the **guardian's** consent instead of the patient's.

For a `Patient` resource the CPID *is* the `resourceId`, so the original mapping was correct.

## Retained gap: non-Patient clinical resources

For an `Observation` or `MedicationRequest` the `resourceId` is the record's own id, and
`AuthzInternalRequest` carries **no field naming the patient it concerns**. Consent for those
resources therefore cannot be evaluated against the right subject.

This is recorded rather than approximated. A wrong subject fails closed, so guessing would produce
unexplained denials on exactly the resources consent exists to govern. Closing it means carrying
the record's subject into the authorization request — a contract change, not a client fix.

## Status

| Facet | State |
|---|---|
| Consent evaluate wire (authz → consent) | **FIXED**, 6 contract tests |
| Consent for `Patient` resources | correct subject |
| Consent for non-`Patient` clinical resources | **GAP** — subject not resolvable from the request |
| Consent enforcement on the live path | still **DISCONNECTED** — the PDP is not on the ingress path (ext_authz off) |

The last row is the honest boundary: this fix means consent evaluation will *work* when the PDP is
switched on. It is not itself enforcement.
