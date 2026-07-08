#!/usr/bin/env python3
"""Run remote bootstrap on Impilo VM and optionally sync/upload files."""
import os
import sys
from pathlib import Path, PurePosixPath

import paramiko

HOST = os.environ.get("SSH_HOST", "impilo.mohcc.gov.zw")
PORT = int(os.environ.get("SSH_PORT", "2276"))
USER = os.environ.get("SSH_USER", "robert")
PASSWORD = os.environ.get("SSH_PASS", "")
LOCAL_ROOT = Path(os.environ.get("LOCAL_REPO", "/opt/impilo/repos/Impilo-vNext"))
REMOTE_ROOT = "/opt/impilo/repos/Impilo-vNext"


def connect():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=60,
              banner_timeout=60, look_for_keys=False, allow_agent=False)
    return c


def upload_bytes(sftp, remote, data: bytes):
    remote = str(PurePosixPath(remote))
    parent = str(PurePosixPath(remote).parent)
    if parent not in ("/", ""):
        parts = parent.strip("/").split("/")
        cur = ""
        for p in parts:
            cur += "/" + p
            try:
                sftp.stat(cur)
            except (OSError, IOError):
                sftp.mkdir(cur)
    with sftp.file(remote, "w") as f:
        f.write(data)


def upload_tree(sftp, local_dir: Path, remote_dir: str):
    for root, _, files in os.walk(local_dir):
        rel = Path(root).relative_to(local_dir)
        rdir = str(PurePosixPath(remote_dir) / rel).replace("\\", "/")
        for name in files:
            lp = Path(root) / name
            rp = f"{rdir}/{name}"
            upload_bytes(sftp, rp, lp.read_bytes().replace(b"\r\n", b"\n"))


def run_stream(client, cmd, timeout=7200):
    print(">>>", cmd[:160])
    _, stdout, _ = client.exec_command(cmd, timeout=timeout, get_pty=True)
    while True:
        line = stdout.readline()
        if line:
            sys.stdout.write(line)
            sys.stdout.flush()
        elif stdout.channel.exit_status_ready():
            break
    code = stdout.channel.recv_exit_status()
    print("exit=", code)
    return code


def main():
    if not PASSWORD:
        print("Set SSH_PASS"); return 1

    client = connect()
    sftp = client.open_sftp()

    bootstrap = LOCAL_ROOT / "scripts/dev/vm-remote-bootstrap.sh"
    upload_bytes(sftp, "/tmp/vm-remote-bootstrap.sh",
                 bootstrap.read_bytes().replace(b"\r\n", b"\n"))

    env = f"export SUDO_PASS={PASSWORD!r}"
    if os.environ.get("SKIP_BOOTSTRAP") != "1":
        code = run_stream(client, f"{env} && bash /tmp/vm-remote-bootstrap.sh")
        if code != 0:
            print("Bootstrap failed")
            return code
    else:
        print("Skipping bootstrap (SKIP_BOOTSTRAP=1)")

    upload_paths = [
        "docs/environment", "scripts/dev", "scripts/deploy", "deploy",
        "tests/smoke", ".cursor/rules", ".github/workflows/deploy-preview.yml",
        ".env.preview.example", "deploy/env/preview.example.env",
        "docs/AI_AGENT_WORKFLOW.md",
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PreviewVersionController.java",
        "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/SecurityConfig.java",
    ]
    for rel in upload_paths:
        lp = LOCAL_ROOT / rel
        if not lp.exists():
            print("skip", rel)
            continue
        rp = str(PurePosixPath(REMOTE_ROOT) / rel)
        if lp.is_dir():
            upload_tree(sftp, lp, rp)
        else:
            upload_bytes(sftp, rp, lp.read_bytes().replace(b"\r\n", b"\n"))

    run_stream(client, f"chmod +x {REMOTE_ROOT}/scripts/dev/*.sh {REMOTE_ROOT}/scripts/deploy/*.sh 2>/dev/null || true", 60)
    sftp.close()

    if os.environ.get("RUN_DEPS") == "1":
        run_stream(client, f"cd {REMOTE_ROOT} && bash scripts/dev/install-dependencies.sh")
    if os.environ.get("RUN_BUILD") == "1":
        run_stream(client, f"cd {REMOTE_ROOT} && bash scripts/dev/build-all.sh")
    if os.environ.get("RUN_IMAGES") == "1":
        run_stream(client, f"cd {REMOTE_ROOT} && bash scripts/deploy/preview-build-images.sh")
    if os.environ.get("RUN_DEPLOY") == "1":
        run_stream(client, f"cd {REMOTE_ROOT} && bash scripts/deploy/preview-deploy.sh")

    run_stream(client, f"cd {REMOTE_ROOT} && bash scripts/dev/check-tools.sh || true", 120)
    client.close()
    return 0


if __name__ == "__main__":
    sys.exit(main() or 0)
