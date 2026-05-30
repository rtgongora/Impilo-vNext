# AI Agent Workflow

1. **Source of truth:** GitHub repository — not the VM disk alone.
2. **Primary environment:** VM Remote SSH workspace (`/opt/impilo/repos/Impilo-vNext`).
3. **Do not** use local laptop repo for normal development.
4. Work on branches; pull before starting; push when done.
5. Read contracts, docs, and tests before changes.
6. Run `scripts/dev/run-tests.sh` after substantive changes.
7. Build before preview deploy.
8. Deploy preview: build images → Helm deploy → smoke tests.
9. Report: preview URL, branch, commit SHA, tests passed/failed, limitations.
10. Surface backend functionality in frontend — do not duplicate backend in UI.
11. Never commit secrets.
12. Formal Test/Staging is separate — see `docs/environment/FUTURE_FORMAL_TEST_STAGING_REQUIREMENTS.md`.
