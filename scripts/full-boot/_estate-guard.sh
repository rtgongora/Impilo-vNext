#!/usr/bin/env bash
# _estate-guard.sh  (sourced helper)
#
# All of vNext is accountable. All deployable vNext services must run. One estate means all
# deployable vNext services. Supporting artifacts do not run as pods, but they remain part of
# vNext truth. Waves are sequencing, not optionality. Deployment truth is the running estate,
# not the deployment story. A deployment is not complete until the full estate is running,
# aligned, healthy, current, and testable.
#
# Provides the refuse-partial guard and the mandatory debug banner used by all standard
# deploy/update/status entrypoints. Source this file; do not execute it.

# Recognised explicit debug/partial flags (presence => operator accepts a non-full-estate run).
ESTATE_DEBUG_FLAGS="--debug-required-spine-only --debug-wave-zero-only --slice --allow-partial --no-full-estate"

# estate_debug_flag_present "$@" -> returns 0 if any recognised debug flag is in the args.
estate_debug_flag_present() {
  local arg flag
  for arg in "$@"; do
    for flag in $ESTATE_DEBUG_FLAGS; do
      [[ "$arg" == "$flag" ]] && return 0
    done
  done
  return 1
}

# estate_debug_banner -> print the mandated banner for any explicitly partial mode.
estate_debug_banner() {
  echo "This is not the full vNext estate and is not valid for full product testing. All of vNext is vNext." >&2
}

# estate_refuse_partial <effective_target> <debug_flag_set 0|1>
#   effective_target: "all" (full estate) or a wave number / subset descriptor.
#   debug_flag_set:    1 if an explicit debug/partial flag was passed, else 0.
# Refuses a partial deploy unless explicitly requested as a debug slice.
estate_refuse_partial() {
  local target="${1:-all}"
  local debug_set="${2:-0}"
  if [[ "$debug_set" == "1" ]]; then
    estate_debug_banner
    return 0
  fi
  if [[ "$target" != "all" && -n "$target" ]]; then
    echo "Refusing partial vNext deploy. All of vNext is vNext. Use the full-estate deploy path or explicitly request a debug slice." >&2
    exit 2
  fi
  return 0
}

# estate_normalize_max_wave <value> -> echoes "all" for full estate, or the numeric wave.
# 'all' / empty / 'full' all mean full estate (no --max-wave passed downstream).
estate_normalize_max_wave() {
  local v="${1:-all}"
  case "$v" in
    all|ALL|full|FULL|"") echo "all" ;;
    *) echo "$v" ;;
  esac
}
