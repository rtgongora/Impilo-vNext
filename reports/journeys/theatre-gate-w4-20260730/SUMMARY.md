# Ten theatre rigs — W4 gate run (2026-07-30)

Run after surgery V010 and inpatient V305, on `worktree-surgery-completion`, serially
(`theatre-elective` and `theatre-persistence` both bind 28121 and cannot overlap).

| Rig | Exit | Result |
|---|---|---|
| elective | 0 | pass |
| elective-completeness | 1 | **14/16 — the two known amber board assertions** |
| emergency | 0 | 26/26 |
| persistence | 0 | 5/5 |
| authz | 0 | 11/11 |
| clinical-safety | 0 | pass |
| commodities | 0 | pass |
| alt | 0 | **45/45**, including the fourteen new J-AL-14 return-to-theatre assertions |
| queue-drainage | 0 | 14/14 |
| recovery-reporting | 0 | 16/16 |

## The one red is the baseline, not a regression

`elective-completeness` fails `board did not block` and `resolve-blocker did not route`. Both
were already failing on unmodified canonical at the W1 baseline run
(`reports/journeys/theatre-gate-20260730/`) and were recorded as amber at programme open. Nothing
in W3 or W4 touches the board or the blocker-resolution path.

## What W4 added to the gate

`theatre-alt-journeys.sh` J-AL-14 grew from two assertions to sixteen. The additions are mostly
negatives, because the value of V305 is in what it refuses:

- an uncategorised return (400) — free text cannot be counted;
- an invented complication category (400);
- a haemorrhage declared `planned` (400) — the dangerous direction, since it would quietly remove
  a real harm event from the unplanned-return indicator;
- a self-referencing predecessor link, refused by the CHECK;
- and the confirmation that a refused return wrote no row at all.

Plus the positives the boolean could never carry: cause, deciding actor, a second return recorded
as sequence 2, a genuine staged second-look recorded as planned and correctly excluded from the
unplanned count, and a predecessor link that reads back.

## Scope of this proof

These are service-and-database rigs: real Postgres, real Spring Boot over HTTP on a local port.
They are not a deployment. Nothing in this programme has been deployed or reached over the
preview ingress — see the close-out note in the lease.
