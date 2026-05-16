# Core Transaction Failure Modes

## Canonical Failure Modes

- identity not found / duplicate suspected
- consent denied / access denied
- provider unavailable or unauthorized
- service unavailable
- payment failed / claim rejected
- result delayed
- referral rejected
- sync pending / sync failed
- emergency override required
- audit anomaly flagged

## Failure Handling Rules

1. Failures must map to explicit transaction state or failure mode code.
2. Failures must preserve user guidance (`nextActions`) and recovery cues.
3. Failures must preserve audit and event semantics.
4. Critical failures must not silently terminate transaction continuity.

## UX Rule

The user-facing transaction timeline and panels must make failures visible, not hidden in logs.
