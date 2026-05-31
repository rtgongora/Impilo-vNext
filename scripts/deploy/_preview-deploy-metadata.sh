#!/usr/bin/env bash
# Resolve branch/commit/buildDate for preview Helm deploy (safe in detached HEAD).
# Source from preview-deploy.sh; sets PREVIEW_DEPLOY_BRANCH, PREVIEW_DEPLOY_COMMIT, PREVIEW_DEPLOY_BUILD_DATE.

resolve_preview_deploy_metadata() {
  local repo="${REPO_PATH:-/opt/impilo/repos/Impilo-vNext}"
  local branch="${DEPLOY_BRANCH:-}"
  local commit="${DEPLOY_COMMIT_SHA:-}"

  if [[ -z "$branch" ]]; then
    branch="$(git -C "$repo" branch --show-current 2>/dev/null || true)"
  fi

  if [[ -z "$branch" || "$branch" == "HEAD" ]]; then
    local ref
    ref="$(git -C "$repo" symbolic-ref -q HEAD 2>/dev/null || true)"
    if [[ -n "$ref" ]]; then
      branch="${ref#refs/heads/}"
    fi
  fi

  # Detached HEAD (e.g. after git checkout $DEPLOY_COMMIT_SHA): use origin branch containing HEAD.
  if [[ -z "$branch" || "$branch" == "HEAD" ]]; then
    branch="$(
      git -C "$repo" for-each-ref --format='%(refname:short)' 'refs/remotes/origin/*' --points-at=HEAD 2>/dev/null \
        | grep -vE '/HEAD$' | head -1 | sed 's|^origin/||' || true
    )"
  fi

  if [[ -z "$commit" ]]; then
    commit="$(git -C "$repo" rev-parse HEAD 2>/dev/null || echo unknown)"
  fi

  if [[ -z "$branch" ]]; then
    branch="unknown"
  fi

  PREVIEW_DEPLOY_BRANCH="$branch"
  PREVIEW_DEPLOY_COMMIT="$commit"
  PREVIEW_DEPLOY_BUILD_DATE="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  export PREVIEW_DEPLOY_BRANCH PREVIEW_DEPLOY_COMMIT PREVIEW_DEPLOY_BUILD_DATE
}
