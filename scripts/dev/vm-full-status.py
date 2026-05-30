import os
import sys

import paramiko

PASSWORD = os.environ.get("SSH_PASS") or sys.exit("Set the SSH_PASS environment variable before running this script.")
HOST = os.environ.get("SSH_HOST", "41.57.127.235")
c = paramiko.SSHClient()
c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST, 2276, "robert", PASSWORD, timeout=30, look_for_keys=False, allow_agent=False)
sections = {
    "workspace": "cd /opt/impilo/repos/Impilo-vNext && git branch --show-current && git rev-parse --short HEAD",
    "tools": "java -version 2>&1 | head -1; docker --version; kubectl get nodes 2>&1 | tail -1; helm version --short 2>&1",
    "build": "ls -lh /opt/impilo/repos/Impilo-vNext/services/experience-bff/target/*.jar 2>&1 | tail -1; docker images | grep impilo | head -3",
    "pods": "kubectl get pods -n impilo-preview; helm list -n impilo-preview 2>&1",
    "http": "curl -s -o /dev/null -w ui=%{http_code} http://127.0.0.1/; echo; curl -s http://127.0.0.1/health/version 2>&1 | head -c 350; echo; curl -s http://127.0.0.1/actuator/health 2>&1 | head -c 200; echo",
    "bff": "kubectl logs -n impilo-preview -l app=experience-bff --tail=5 2>&1 | tail -12",
}
for name, cmd in sections.items():
    _, o, _ = c.exec_command(cmd, timeout=40)
    print(f"=== {name} ===")
    print(o.read().decode("utf-8", errors="replace"))
c.close()
