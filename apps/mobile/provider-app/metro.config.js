const { getDefaultConfig } = require("expo/metro-config");
const path = require("path");

const projectRoot = __dirname;
// The pnpm workspace root (apps/mobile), where the shared node_modules lives.
const workspaceRoot = path.resolve(projectRoot, "..");
const repoRoot = path.resolve(projectRoot, "../../..");
// Canonical cross-platform contracts. Watched so mobile can import the same
// source file the web shell does, instead of hand-mirroring it (G2).
const contractsRoot = path.resolve(repoRoot, "contracts");

const config = getDefaultConfig(projectRoot);

config.watchFolders = [
  ...(config.watchFolders ?? []),
  workspaceRoot,
  contractsRoot,
];

config.resolver.nodeModulesPaths = [
  ...(config.resolver.nodeModulesPaths ?? []),
  path.resolve(projectRoot, "node_modules"),
  path.resolve(workspaceRoot, "node_modules"),
];

config.resolver.disableHierarchicalLookup = false;

module.exports = config;
