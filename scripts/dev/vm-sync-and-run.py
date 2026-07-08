#!/usr/bin/env python3
"""Upload Impilo workspace files to VM and run remote commands."""
import os, sys, stat, paramiko
from pathlib import Path, PurePosixPath

HOST = os.environ.get("SSH_HOST", "impilo.mohcc.gov.zw")
PORT = int(os.environ.get("SSH_PORT", "2276"))
USER = os.environ.get("SSH_USER", "robert")
PASSWORD = os.environ.get("SSH_PASS", "")
LOCAL_ROOT = Path(os.environ.get("LOCAL_REPO", r"c:\Users\rgong\OneDrive\Documents\EHR\vNext\Repo\Impilo-vNext"))
REMOTE_ROOT = "/opt/impilo/repos/Impilo-vNext"

UPLOAD_PATHS = [
    "docs/environment",
    "scripts/dev",
    "scripts/deploy",
    "deploy",
    "tests/smoke",
    ".cursor/rules",
    ".github/workflows/deploy-preview.yml",
    ".env.preview.example",
    "deploy/env/preview.example.env",
    "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/controller/PreviewVersionController.java",
    "services/experience-bff/src/main/java/zw/gov/mohcc/impilo/experience/config/SecurityConfig.java",
]

def connect():
    c = paramiko.SSHClient()
    c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    c.connect(HOST, port=PORT, username=USER, password=PASSWORD, timeout=60,
              banner_timeout=60, look_for_keys=False, allow_agent=False)
    return c

def run(client, cmd, timeout=3600):
    print(f"\n>>> {cmd[:200]}")
    stdin, stdout, stderr = client.exec_command(cmd, timeout=timeout, get_pty=True)
    out = stdout.read().decode("utf-8", errors="replace")
    err = stderr.read().decode("utf-8", errors="replace")
    code = stdout.channel.recv_exit_status()
    if out.strip():
        safe = out[-8000:] if len(out) > 8000 else out
        print(safe.encode("utf-8", errors="replace").decode("utf-8", errors="replace"))
    if err.strip() and code != 0:
        print("STDERR:", err[-4000:])
    print(f"exit={code}")
    return code, out, err

def sftp_mkdir_p(sftp, remote_dir):
    remote_dir = str(PurePosixPath(remote_dir))
    if remote_dir in ("/", ""):
        return
    parts = remote_dir.strip("/").split("/")
    cur = ""
    for p in parts:
        cur += "/" + p
        try:
            sftp.stat(cur)
        except (FileNotFoundError, IOError, OSError):
            sftp.mkdir(cur)

def upload_file(sftp, local, remote):
    remote = str(PurePosixPath(remote))
    sftp_mkdir_p(sftp, str(PurePosixPath(remote).parent))
    data = Path(local).read_bytes().replace(b"\r\n", b"\n")
    with sftp.file(remote, "w") as f:
        f.write(data)

def upload_tree(sftp, local_dir, remote_dir):
    local_dir = Path(local_dir)
    for root, dirs, files in os.walk(local_dir):
        rel = Path(root).relative_to(local_dir)
        rdir = str(Path(remote_dir) / rel).replace("\\", "/")
        sftp_mkdir_p(sftp, rdir)
        for f in files:
            lp = Path(root) / f
            rp = f"{rdir}/{f}"
            upload_file(sftp, lp, rp)

def main():
    if not PASSWORD:
        print("Set SSH_PASS"); sys.exit(1)
    client = connect()
    sftp = client.open_sftp()

    bootstrap = LOCAL_ROOT / "scripts/dev/vm-remote-bootstrap.sh"
    upload_file(sftp, bootstrap, "/tmp/vm-remote-bootstrap.sh")
    sudo_pass = PASSWORD.replace("'", "'\\''")
    run(client, f"export SUDO_PASS='{sudo_pass}' && chmod +x /tmp/vm-remote-bootstrap.sh && bash /tmp/vm-remote-bootstrap.sh", timeout=7200)
    code, _, _ = run(client, "test -d /opt/impilo/repos/Impilo-vNext/.git", timeout=30)
    if code != 0:
        print("Bootstrap did not create repo — aborting upload")
        client.close()
        sys.exit(1)

    for rel in UPLOAD_PATHS:
        lp = LOCAL_ROOT / rel
        if not lp.exists():
            print("skip missing", rel)
            continue
        rp = str(PurePosixPath(REMOTE_ROOT) / rel).replace("\\", "/")
        if lp.is_dir():
            upload_tree(sftp, lp, rp)
        else:
            upload_file(sftp, lp, rp)

    run(client, f"chmod +x {REMOTE_ROOT}/scripts/dev/*.sh {REMOTE_ROOT}/scripts/deploy/*.sh 2>/dev/null || true")
    sftp.close()

    if os.environ.get("RUN_DEPS") == "1":
        run(client, f"cd {REMOTE_ROOT} && bash scripts/dev/install-dependencies.sh", timeout=7200)
    if os.environ.get("RUN_BUILD") == "1":
        run(client, f"cd {REMOTE_ROOT} && bash scripts/dev/build-all.sh", timeout=7200)
    if os.environ.get("RUN_IMAGES") == "1":
        run(client, f"cd {REMOTE_ROOT} && bash scripts/deploy/preview-build-images.sh", timeout=7200)
    if os.environ.get("RUN_DEPLOY") == "1":
        run(client, f"cd {REMOTE_ROOT} && bash scripts/deploy/preview-deploy.sh", timeout=1800)

    run(client, f"cd {REMOTE_ROOT} && bash scripts/dev/check-tools.sh || true")
    run(client, "kubectl get nodes; kubectl get pods -A | head -30; helm version --short")
    client.close()

if __name__ == "__main__":
    main()
