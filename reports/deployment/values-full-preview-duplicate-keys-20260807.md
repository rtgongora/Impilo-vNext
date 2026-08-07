# `values-full-preview.yaml` — two pre-existing duplicate keys are silently discarded

**Date:** 2026-08-07 · Found while charting `TSHEPO_AUTHZ_CONTEXT_HEADER_MODE` (Phase 0 · E, Part 2).

A duplicate YAML key at the same level is silently discarded — last one wins. The file already
documents this trap twice in prose (the `experienceBff` `env:` note and the `rtc-gateway-service`
note). Rather than trust the prose, the file was parsed with a duplicate-detecting loader before
adding anything. `tshepo-authz-service` was clean — exactly one key, one `env:` block — but the
scan found **two other duplicates that are live today**.

## 1. `butano-service` — a trust-plane setting reaches nothing 🟠

| | |
|---|---|
| First block | line 609 — `env.IMPILO_SUBJECT_CONTEXT_MODE: "log"` |
| Second block | line 777 — `env.SPRING_KAFKA_LISTENER_AUTO_STARTUP: "true"` |
| Winner | line 777. The first block is **discarded entirely.** |

`IMPILO_SUBJECT_CONTEXT_MODE` never reaches the pod. Confirmed both ways:

- `helm template` renders `butano-service` with no such variable.
- The live Deployment has no such variable.

The property is `impilo.subject-context.mode` (`TechCompanionAutoConfiguration`,
`SubjectContextFilter`), **default `off`**. So `butano-service` runs with `SubjectContextFilter`
disabled, not in `log` mode as the chart intends. Sibling services (`pct-service`, `butano-fhir`,
`daidzai-service`) set the same value in blocks that are *not* shadowed and do get it.

**Not fixed here.** Chart and live estate currently agree (both effectively `off`); merging the
blocks would change behaviour on the next upgrade, which is a decision for the trust plane, not a
side effect of an unrelated chart edit.

## 2. `ingress` — benign, but it is the same defect 🟢

| | |
|---|---|
| First block | line 52 — `enabled: true`, `className: traefik` |
| Second block | line 150 — `bffService: envoy`, `bffPort: 10000` |
| Winner | line 150. |

Harmless **only by luck**: the two discarded keys restate values that `values.yaml` already supplies
as chart defaults, so the deep merge restores them. Had the first block carried anything the chart
default did not, it would have vanished silently.

## Recipe

```
python3 - <<'PY'
import yaml
dups=[]
class L(yaml.SafeLoader): pass
def cm(loader,node,deep=False):
    seen={}
    for k,_ in node.value:
        key=loader.construct_object(k,deep=deep)
        if key in seen: dups.append((key,seen[key],k.start_mark.line+1))
        seen[key]=k.start_mark.line+1
    return yaml.SafeLoader.construct_mapping(loader,node,deep)
L.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, cm)
yaml.load(open('deploy/helm/impilo-vnext/values-full-preview.yaml'),Loader=L)
for k,a,b in dups: print(f'{k!r}: line {a} shadowed by line {b}')
PY
```

Prose warnings in the file did not prevent either of these. A parser would have.
