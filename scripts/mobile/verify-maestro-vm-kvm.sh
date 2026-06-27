#!/usr/bin/env bash
# Verify KVM readiness on the Maestro Android sandbox VM (41.57.127.218).
set -euo pipefail

fail=0
warn() { echo "WARN: $*" >&2; }
ok() { echo "OK: $*"; }
die() { echo "FAIL: $*" >&2; fail=1; }

vcpu_flags="$(egrep -c '(vmx|svm)' /proc/cpuinfo 2>/dev/null || echo 0)"
if [[ "${vcpu_flags}" -ge 1 ]]; then
  ok "CPU virtualisation flags count: ${vcpu_flags}"
else
  die "No vmx/svm flags in /proc/cpuinfo"
fi

if [[ -e /dev/kvm ]]; then
  ok "/dev/kvm exists"
else
  die "/dev/kvm missing"
fi

if [[ -r /dev/kvm && -w /dev/kvm ]]; then
  ok "KVM readable and writable for current user"
else
  die "KVM not readable/writable — ensure user is in kvm group"
fi

if groups 2>/dev/null | grep -q '\bkvm\b'; then
  ok "Current user in kvm group"
else
  warn "Current user not in kvm group (may still work as root)"
fi

if [[ "${fail}" -ne 0 ]]; then
  echo "KVM verification FAILED" >&2
  exit 1
fi

echo "KVM verification PASSED"
