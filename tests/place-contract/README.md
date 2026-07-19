# Place Contract — acceptance pack

`place-journeys.sh` is the SOFTWARE_CONTRACT_GREEN bar for the **place identity**
journeys (Place Journey Doctrine, FJ/SJ). Run it against a deployed estate
(post full-boot); each check proves one contract invariant end-to-end and
reports SKIP — never a false PASS — when a service or the DB is unreachable.

```
bash tests/place-contract/place-journeys.sh
```

Exit 0 = GREEN (all pass), 2 = AMBER (no failures, some skips — estate not fully
up), 1 = RED (a contract invariant failed). Evidence in
`reports/place-contract/<ts>/`. Companion: `tests/identity-contract/provider-journeys.sh`
(provider journeys) and `tests/identity-contract/identity-contract-journeys.sh`
(client identity). Verdict tiers: SOFTWARE_CONTRACT_GREEN (this pack) →
EXTERNAL_INTEGRATION_GREEN (real HPA/council/DHIS2 links) → NATIONAL_PRODUCTION_GREEN.
