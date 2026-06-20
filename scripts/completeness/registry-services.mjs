import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import yaml from 'js-yaml';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REGISTRY_PATH = path.resolve(__dirname, '../../docs/registry/services-registry.yaml');

export function loadRegistryServices() {
  const raw = yaml.load(fs.readFileSync(REGISTRY_PATH, 'utf8'));
  return raw.services ?? [];
}
