#!/usr/bin/env bash
set -euo pipefail
source "$(dirname "$0")/_gate-common.sh"
cd "$REPO_PATH"
FAIL=0

gate_run "ui-install" bash -c 'cd ui && npm ci --ignore-scripts -w one-ui-shell' || FAIL=1
gate_run "frontend-lint" bash -c 'cd ui/one-ui-shell && npm run lint' || FAIL=1
gate_run "frontend-typecheck" bash -c 'cd ui/one-ui-shell && npm run type-check' || FAIL=1
# The Playwright specs are excluded from the app's tsconfig, so they were type-checked by nothing:
# a spec that could not compile reported zero failures instead of an error. Separate pass, because
# pulling test code into the app's config would let a test-only type error fail the production build.
gate_run "frontend-typecheck-e2e" bash -c 'cd ui/one-ui-shell && npm run test:typecheck-e2e' || FAIL=1
# The reverse of route-parity: a page that exists but is in no registry is reachable by nothing.
# route-parity-check only walks registry -> filesystem and is structurally blind to this.
gate_run "frontend-orphan-pages" bash -c 'cd ui/one-ui-shell && npm run test:orphan-pages' || FAIL=1
# no-stub-guard catches an EMPTY onClick; this catches a <button> with no handler at all, which is
# the commoner shape and looks like finished markup rather than a decision someone made.
gate_run "frontend-decorative-controls" bash -c 'cd ui/one-ui-shell && npm run test:decorative-controls' || FAIL=1
gate_run "frontend-unit-tests" bash -c 'cd ui/one-ui-shell && npm test' || FAIL=1
# next.config.mjs fails the build when the rewrite upstreams are absent under NODE_ENV=production,
# which `next build` always sets. That guard is right — rewrites bake in at build time, and a
# localhost default once shipped an image where every /api/v1/* route died on ECONNREFUSED. But this
# gate exported nothing, so it could only ever fail, and it was never proving the app compiles.
# Supply the same in-cluster upstreams the Dockerfile defaults to (ui/one-ui-shell/Dockerfile:24-25).
# This build is a compile check; its output is never shipped, and image builds pass their own args.
gate_run "frontend-build" bash -c 'cd ui/one-ui-shell \
  && API_GATEWAY_URL="${API_GATEWAY_URL:-http://experience-bff:8160}" \
     BFF_URL="${BFF_URL:-http://experience-bff:8160}" \
     npm run build' || FAIL=1

exit "$FAIL"
