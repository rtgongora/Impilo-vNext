# Ndila street stack data

Place **`zimbabwe.pmtiles`** here for self-hosted street basemaps.

Generate or download a Zimbabwe (or Southern Africa) PMTiles build — never commit large
binaries to Git. Run:

```bash
bash scripts/operator/setup-ndila-street-stack.sh
```

Martin serves the file at `http://localhost:3410/zimbabwe/{z}/{x}/{y}` when present.

Optional OSRM routing (`docker compose --profile routing up`) needs
`data/osrm/zimbabwe.osrm` built from Geofabrik `zimbabwe-latest.osm.pbf` — see the setup script.
