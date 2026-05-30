#!/usr/bin/env python3
"""Orchestrate the complete preview BFF fix on the VM.

Phases:
  diag    - report current VM file/pod/sudo state
  upload  - SFTP the patched Java + Helm chart + scripts (LF-normalised)
  build   - rebuild jar + images + import into k3s (nohup + log poll)
  deploy  - clean Helm uninstall + install with values-preview.yaml
  verify  - pods Ready + endpoints + smoke test

Run with no args to do everything in order.
"""
import os
import sys
import time
from pathlib import Path

import paramiko

try:
    sys.stdout.reconfigure(line_buffering=True)
except Exception:  # noqa: BLE001
    pass

HOST, PORT, USER = "41.57.127.235", 2276, "robert"
PASSWORD = os.environ.get("SSH_PASS") or sys.exit("Set the SSH_PASS environment variable before running this script.")
REMOTE = "/opt/impilo/repos/Impilo-vNext"
LOCAL = Path(os.environ.get("LOCAL_REPO", str(Path(__file__).resolve().parents[2])))


def connect(retries=6):
    last = None
    for attempt in range(retries):
        try:
            c = paramiko.SSHClient()
            c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
            c.connect(HOST, PORT, USER, PASSWORD, timeout=60,
                      banner_timeout=60, auth_timeout=60,
                      look_for_keys=False, allow_agent=False)
            return c
        except Exception as e:  # noqa: BLE001
            last = e
            print(f"  connect attempt {attempt + 1} failed: {e}")
            time.sleep(5)
    raise RuntimeError(f"Could not connect: {last}")


def run(cmd, timeout=300, quiet=False):
    c = connect()
    try:
        _, stdout, stderr = c.exec_command(cmd, timeout=timeout)
        out = stdout.read().decode("utf-8", errors="replace")
        err = stderr.read().decode("utf-8", errors="replace")
        code = stdout.channel.recv_exit_status()
    finally:
        c.close()
    if not quiet:
        if out.strip():
            print(out.rstrip())
        if err.strip() and code != 0:
            print("STDERR:", err.rstrip()[-1500:])
    return code, out, err


def upload(local_rel, remote_abs=None):
    lp = LOCAL / local_rel
    if not lp.exists():
        print(f"  SKIP (missing locally): {local_rel}")
        return False
    remote_abs = remote_abs or f"{REMOTE}/{local_rel.replace(os.sep, '/')}"
    data = lp.read_bytes().replace(b"\r\n", b"\n")
    remote_dir = remote_abs.rsplit("/", 1)[0]
    for attempt in range(5):
        try:
            c = connect()
            c.exec_command(f"mkdir -p {remote_dir}")
            time.sleep(0.3)
            sftp = c.open_sftp()
            with sftp.file(remote_abs, "w") as f:
                f.write(data)
            sftp.close()
            c.close()
            print(f"  uploaded {local_rel} ({len(data)} bytes)")
            return True
        except Exception as e:  # noqa: BLE001
            print(f"  upload {local_rel} attempt {attempt + 1}: {e}")
            time.sleep(3)
    raise RuntimeError(f"upload failed: {local_rel}")


FILES = [
    "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/SecurityConfig.java",
    "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PreviewVersionController.java",
    "deploy/helm/impilo-vnext/Chart.yaml",
    "deploy/helm/impilo-vnext/values.yaml",
    "deploy/helm/impilo-vnext/values-preview.yaml",
    "deploy/helm/impilo-vnext/templates/redis.yaml",
    "deploy/helm/impilo-vnext/templates/postgres.yaml",
    "deploy/helm/impilo-vnext/templates/experience-bff.yaml",
    "deploy/helm/impilo-vnext/templates/one-ui-shell.yaml",
    "deploy/helm/impilo-vnext/templates/ingress.yaml",
    "scripts/dev/build-images.sh",
    "scripts/deploy/preview-deploy.sh",
    "scripts/deploy/preview-smoke-test.sh",
    "scripts/deploy/preview-fix-complete.sh",
]


def phase_diag():
    print("=== DIAG ===")
    run("echo '%s' | sudo -S -v 2>&1 && echo SUDO_OK" % PASSWORD)
    run("cd %s && git branch --show-current && git rev-parse --short HEAD" % REMOTE)
    run("test -f %s/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PreviewVersionController.java "
        "&& echo HAS_VERSION_CONTROLLER || echo NO_VERSION_CONTROLLER" % REMOTE)
    run("grep -c 'actuator/health/' %s/services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/SecurityConfig.java "
        "2>/dev/null || echo 0" % REMOTE)
    run("kubectl get pods -n impilo-preview 2>&1; helm list -n impilo-preview 2>&1")
    run("test -f /tmp/preview-fix-complete.log && wc -l /tmp/preview-fix-complete.log || echo NO_LOG")


def phase_upload():
    print("=== UPLOAD ===")
    for rel in FILES:
        upload(rel)
    run("chmod +x %s/scripts/deploy/*.sh %s/scripts/dev/*.sh 2>&1; echo chmod_done" % (REMOTE, REMOTE))


def _poll_log(logfile, marker_done, max_polls=80, interval=30, proc_match="preview-fix"):
    last_len = 0
    for i in range(max_polls):
        time.sleep(interval)
        code, out, _ = run(
            "tail -c 4000 %s 2>/dev/null; echo '<<<SEP>>>'; pgrep -af '%s' | grep -v pgrep || echo NOPROC"
            % (logfile, proc_match),
            timeout=30, quiet=True)
        body, _, proc = out.partition("<<<SEP>>>")
        # print only new tail
        tail = body.strip()
        print(f"--- poll {i} (proc: {'running' if 'NOPROC' not in proc else 'done'}) ---")
        print(tail[-1200:])
        if marker_done in body:
            print(">>> marker found")
            return True
        if "NOPROC" in proc and i >= 1:
            print(">>> process ended")
            return True
    return False


def phase_build():
    """Rebuild ONLY the experience-bff jar + image and import into k3s.

    The one-ui-shell image is already built and running, so we skip it to
    save time and avoid an unnecessary Next.js rebuild.
    """
    print("=== BUILD (BFF jar + image + k3s import) ===")
    build_script = (
        "set -x\n"
        "echo BUILD_START\n"
        "cd %s/services\n"
        "mvn -q package -pl experience-bff -am -DskipTests && echo JAR_OK || { echo JAR_FAIL; echo BUILD_DONE_1; exit 1; }\n"
        "cd %s\n"
        "docker build -f services/experience-bff/Dockerfile -t impilo/experience-bff:preview "
        "-t impilo/experience-bff:preview-$(git rev-parse --short HEAD) services/experience-bff "
        "&& echo IMAGE_OK || { echo IMAGE_FAIL; echo BUILD_DONE_2; exit 2; }\n"
        "echo \"$SUDO_PASS\" | sudo -S -v\n"
        "docker save impilo/experience-bff:preview | sudo k3s ctr images import - "
        "&& echo IMPORT_OK || { echo IMPORT_FAIL; echo BUILD_DONE_3; exit 3; }\n"
        "echo BUILD_DONE_0\n"
        % (REMOTE, REMOTE)
    )
    # write the build script to the VM then run it under nohup
    c = connect()
    sftp = c.open_sftp()
    with sftp.file("/tmp/preview-build.sh", "w") as f:
        f.write(build_script)
    sftp.close()
    c.close()
    cmd = (
        "export SUDO_PASS='%s'; echo \"$SUDO_PASS\" | sudo -S -v; "
        "nohup bash /tmp/preview-build.sh > /tmp/preview-build.log 2>&1 & echo launched_pid=$!"
        % PASSWORD
    )
    run(cmd, timeout=40)
    ok = _poll_log("/tmp/preview-build.log", "BUILD_DONE_", max_polls=80, interval=30,
                   proc_match="mvn|docker|preview-build")
    run("tail -30 /tmp/preview-build.log")
    run("docker images | grep impilo || echo no_images")
    return ok


def phase_build_ui():
    """Rebuild the one-ui-shell image for SAME-ORIGIN (empty NEXT_PUBLIC_BFF_URL).

    The browser bundle then calls the BFF via relative paths (routed by Traefik),
    so the preview works at any public IP and through an SSH tunnel.
    """
    print("=== BUILD UI (one-ui-shell same-origin + k3s import) ===")
    build_script = (
        "set -x\n"
        "echo UIBUILD_START\n"
        "cd %s\n"
        "docker build -f ui/one-ui-shell/Dockerfile "
        "--build-arg NEXT_PUBLIC_BFF_URL= "
        "--build-arg NEXT_PUBLIC_API_GATEWAY_URL= "
        "-t impilo/one-ui-shell:preview "
        "-t impilo/one-ui-shell:preview-$(git rev-parse --short HEAD) . "
        "&& echo UIIMAGE_OK || { echo UIIMAGE_FAIL; echo UIBUILD_DONE_2; exit 2; }\n"
        "echo \"$SUDO_PASS\" | sudo -S -v\n"
        "docker save impilo/one-ui-shell:preview | sudo k3s ctr images import - "
        "&& echo UIIMPORT_OK || { echo UIIMPORT_FAIL; echo UIBUILD_DONE_3; exit 3; }\n"
        "echo UIBUILD_DONE_0\n"
        % REMOTE
    )
    c = connect()
    sftp = c.open_sftp()
    with sftp.file("/tmp/preview-build-ui.sh", "w") as f:
        f.write(build_script)
    sftp.close()
    c.close()
    cmd = (
        "export SUDO_PASS='%s'; echo \"$SUDO_PASS\" | sudo -S -v; "
        "nohup bash /tmp/preview-build-ui.sh > /tmp/preview-build-ui.log 2>&1 & echo launched_pid=$!"
        % PASSWORD
    )
    run(cmd, timeout=40)
    ok = _poll_log("/tmp/preview-build-ui.log", "UIBUILD_DONE_", max_polls=100, interval=30,
                   proc_match="docker|npm|next|preview-build-ui")
    run("tail -30 /tmp/preview-build-ui.log")
    run("docker images | grep one-ui-shell || echo no_images")
    return ok


def phase_restart_ui():
    print("=== RESTART one-ui-shell (pick up new image) ===")
    run("kubectl rollout restart deployment/one-ui-shell -n impilo-preview; "
        "kubectl rollout status deployment/one-ui-shell -n impilo-preview --timeout=120s", timeout=140)


def phase_deploy():
    print("=== DEPLOY (clean Helm install) ===")
    cmd = (
        "export SUDO_PASS='%s'; cd %s; "
        "helm uninstall impilo-preview -n impilo-preview 2>/dev/null || true; "
        "kubectl delete secret -n impilo-preview -l owner=helm 2>/dev/null || true; "
        "kubectl delete deploy experience-bff -n impilo-preview 2>/dev/null || true; "
        "sleep 3; "
        "nohup bash %s/scripts/deploy/preview-deploy.sh > /tmp/preview-deploy.log 2>&1 & echo launched_pid=$!"
        % (PASSWORD, REMOTE, REMOTE)
    )
    run(cmd, timeout=60)
    _poll_log("/tmp/preview-deploy.log", "Deployed branch=", max_polls=20, interval=15,
              proc_match="helm|preview-deploy")
    run("tail -30 /tmp/preview-deploy.log")


def phase_verify():
    print("=== VERIFY ===")
    # wait for rollout
    for _ in range(12):
        code, out, _ = run("kubectl get pods -n impilo-preview --no-headers", timeout=30, quiet=True)
        print(out.rstrip())
        if "experience-bff" in out and "1/1" in out and "Running" in out:
            bff_line = [l for l in out.splitlines() if "experience-bff" in l]
            if bff_line and "1/1" in bff_line[0]:
                break
        time.sleep(15)
    print("--- endpoints ---")
    run("curl -s -o /dev/null -w 'root=%{http_code}\\n' http://127.0.0.1/ ; "
        "echo '--- /actuator/health ---'; curl -s http://127.0.0.1/actuator/health; echo; "
        "echo '--- /health/version ---'; curl -s http://127.0.0.1/health/version; echo")
    print("--- smoke test ---")
    run("bash %s/scripts/deploy/preview-smoke-test.sh" % REMOTE)
    print("--- final pods ---")
    run("kubectl get pods -n impilo-preview")


PHASES = {
    "diag": phase_diag,
    "upload": phase_upload,
    "build": phase_build,
    "build_ui": phase_build_ui,
    "restart_ui": phase_restart_ui,
    "deploy": phase_deploy,
    "verify": phase_verify,
}


def main():
    args = sys.argv[1:]
    order = args if args else ["diag", "upload", "build", "deploy", "verify"]
    for name in order:
        fn = PHASES.get(name)
        if not fn:
            print(f"unknown phase: {name}")
            continue
        fn()
    return 0


if __name__ == "__main__":
    sys.exit(main())
