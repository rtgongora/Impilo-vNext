# Core Transaction Feature Alignment Checklist

Use this checklist for every new feature, API, UI flow, or integration.

- [ ] 1. Which Core Transaction type does this support?
- [ ] 2. Which lifecycle stage does it support?
- [ ] 3. Which actor does it serve?
- [ ] 4. Which Person Journey stage applies, if any?
- [ ] 5. Which Provider Journey stage applies, if any?
- [ ] 6. Which Platform Journey stage applies?
- [ ] 7. Which plane owns the source of truth?
- [ ] 8. Which service owns the data?
- [ ] 8a. Which continuum does this touch — Care (PCT) or Wellness (Simba) — and what resolvable PCT anchor (journey_id / encounter_ref / admission handshake) does each new clinical record carry? (care-continuum-doctrine.md CC-5)
- [ ] 9. Which transaction state does it create/read/update/close?
- [ ] 10. Which event is emitted?
- [ ] 11. Which permission or consent decision applies?
- [ ] 12. Which audit event is generated?
- [ ] 13. Which UI surface exposes it?
- [ ] 14. Which BFF or contract wires it to the frontend?
- [ ] 15. Which tests prove it works?
- [ ] 16. Which analytics/reporting output can learn from it?
- [ ] 17. Which offline/federated behavior applies?
- [ ] 18. What happens when identity, consent, payment, connectivity, or sync fails?
- [ ] 19. How does this improve access, delivery, continuity, accountability, accessibility, or intelligence?
- [ ] 20. Does this create duplicate domain truth?
- [ ] 21. Does this preserve person-centered continuity?
- [ ] 22. Does this reduce or increase provider burden?
- [ ] 23. Does Nompilo need to explain, assist, nudge, guide, or capture feedback here?
- [ ] 24. What accessibility needs must be considered?
- [ ] 25. What omnichannel feedback opportunity exists?
- [ ] 26. Does this help the health system act better?

## Gateway addendum (citizen-facing features)

Apply when the feature has any citizen-facing surface
(see [`docs/doctrine/health-services-gateway-doctrine.md`](../doctrine/health-services-gateway-doctrine.md)).

- [ ] 27. Which intent pillar does this serve, and is the citizen-facing label free of internal service names?
- [ ] 28. What is the minimum trust rung (R0–R5) for this ACTION (not this user), and where is it enforced (PolicyEngine `min_loa` / assurance policy)?
- [ ] 29. If trust must step up, does it happen in place with journey context preserved (fields, documents, return route, language, accessibility)?
- [ ] 30. Is "Sign in / Create account" offered but never forced where the action is public or low-risk?
- [ ] 31. Is Emergency Help reachable from this surface, and never blocked by identity, coverage, or payment state?
- [ ] 32. Which coverage/payment safeguard applies (no emergency denial, no visible vulnerability flags, minimal payer disclosure, no silent rejection)?
- [ ] 33. Does mobile share the same workflow state, with honest offline labels (saved-on-phone / queued / submitted)?
- [ ] 34. Where does Nompilo explain the trust escalation, and is the explanation auditable without exposing security internals?
