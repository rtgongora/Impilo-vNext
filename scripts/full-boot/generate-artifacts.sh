#!/usr/bin/env bash
# Entry point for full-boot artifact generation.
#
# Call this rather than `node generate-full-boot-artifacts.mjs` directly: it provisions the
# generator's js-yaml dependency first. Called bare, the generator exits ERR_MODULE_NOT_FOUND, and
# because every caller wrapped it in `|| true` the estate ran for weeks on stale registry artifacts
# while the phase reported advisory-clean.
set -euo pipefail
source "$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/_full-boot-common.sh"
full_boot_generate_artifacts
