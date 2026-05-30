import os, sys
import paramiko
PASSWORD = os.environ.get("SSH_PASS") or sys.exit("Set the SSH_PASS environment variable before running this script.")
HOST = os.environ.get("SSH_HOST", "41.57.127.235")
c=paramiko.SSHClient();c.set_missing_host_key_policy(paramiko.AutoAddPolicy())
c.connect(HOST,2276,'robert',PASSWORD,timeout=30,look_for_keys=False,allow_agent=False)
cmds={
 'git':'cd /opt/impilo/repos/Impilo-vNext && git branch --show-current && git rev-parse --short HEAD',
 'jar':'ls -lh /opt/impilo/repos/Impilo-vNext/services/experience-bff/target/*.jar 2>&1',
 'ui_deps':'test -d /opt/impilo/repos/Impilo-vNext/ui/node_modules && echo yes || echo no',
 'docker_imgs':'docker images | grep impilo || echo none',
 'k3s_imgs':'sudo k3s ctr images ls | grep impilo || echo none',
 'pods':'kubectl get pods -n impilo-preview',
 'helm':'helm list -n impilo-preview',
 'procs':'pgrep -af preview-build|preview-deploy|build-all|docker || true',
 'curl':'curl -s -o /dev/null -w http_code=%{http_code} http://127.0.0.1/ ; curl -s http://127.0.0.1/health/version 2>/dev/null | head -c 200',
}
for k,cmd in cmds.items():
    _,o,_=c.exec_command(cmd,timeout=60,get_pty=True)
    print('===',k,'==='); print(o.read().decode('utf-8',errors='replace')[:1500])
c.close()
