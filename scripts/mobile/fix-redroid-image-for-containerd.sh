#!/usr/bin/env bash
# fix-redroid-image-for-containerd.sh — produce the k3s-safe redroid image.
#
# Why: the upstream redroid image's Android rootfs uses ABSOLUTE symlinks at /
# (etc -> /system/etc, bin -> /system/bin, …). containerd's safe path
# resolution (openat2 RESOLVE_BENEATH) rejects them during container create:
#   "openat etc/passwd: path escapes from parent"
# Docker tolerates this; k3s/containerd does not. Fix: rewrite every absolute
# symlink in the (single) layer to an equivalent relative one — identical
# runtime semantics, containerd-safe. See
# docs/runbooks/redroid-android-sandbox-runbook.md.
#
# usage: fix-redroid-image-for-containerd.sh [workdir]
set -euo pipefail

SRC_IMAGE="${SRC_IMAGE:-redroid/redroid:13.0.0-latest}"
DST_IMAGE="${DST_IMAGE:-127.0.0.1:5000/redroid/redroid:13.0.0-k3s}"
WORK="${1:-$(mktemp -d /tmp/redroid-fix.XXXXXX)}"

mkdir -p "$WORK"; cd "$WORK"
echo "==> workdir: $WORK"

if [ ! -f img.tar ]; then
  echo "==> docker save $SRC_IMAGE"
  docker save "$SRC_IMAGE" -o img.tar
fi
tar -xf img.tar manifest.json index.json 2>/dev/null || tar -xf img.tar manifest.json

DST_IMAGE="$DST_IMAGE" python3 - <<'EOF'
import tarfile, hashlib, json, os

mani=json.load(open('manifest.json'))
layer=mani[0]['Layers'][0]
cfg_name=mani[0]['Config']
t=tarfile.open('img.tar')
for member in (layer, cfg_name):
    if not os.path.exists(member):
        t.extract(member)

src=tarfile.open(layer)
out=tarfile.open('newlayer.tar','w', format=tarfile.GNU_FORMAT)
fixed=0
for m in src:
    if m.issym() and m.linkname.startswith('/'):
        m.linkname=('../'*m.name.count('/'))+m.linkname.lstrip('/')
        fixed+=1
    out.addfile(m, src.extractfile(m) if m.isreg() else None)
out.close(); src.close()
print(f"rewrote {fixed} absolute symlinks")

h=hashlib.sha256()
with open('newlayer.tar','rb') as f:
    for chunk in iter(lambda: f.read(1<<20), b''):
        h.update(chunk)
new_digest=h.hexdigest()
os.makedirs('blobs/sha256', exist_ok=True)
os.replace('newlayer.tar', f'blobs/sha256/{new_digest}')

cfg=json.load(open(cfg_name))
cfg['rootfs']['diff_ids']=[f'sha256:{new_digest}']
cfg_bytes=json.dumps(cfg,separators=(',',':')).encode()
cfg_digest=hashlib.sha256(cfg_bytes).hexdigest()
open(f'blobs/sha256/{cfg_digest}','wb').write(cfg_bytes)

mani[0]['Config']=f'blobs/sha256/{cfg_digest}'
mani[0]['Layers']=[f'blobs/sha256/{new_digest}']
mani[0]['RepoTags']=[os.environ['DST_IMAGE']]
json.dump(mani,open('manifest.json','w'))

with tarfile.open('redroid-k3s.tar','w') as t2:
    t2.add('manifest.json')
    t2.add(f'blobs/sha256/{cfg_digest}')
    t2.add(f'blobs/sha256/{new_digest}')
print('==> redroid-k3s.tar ready')
EOF

echo "==> docker load"
docker load -i redroid-k3s.tar
echo "==> docker push $DST_IMAGE"
docker push "$DST_IMAGE"
echo "==> done: $DST_IMAGE"
