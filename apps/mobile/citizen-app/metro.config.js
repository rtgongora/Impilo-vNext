const { getDefaultConfig } = require("expo/metro-config");
const path = require("path");

const projectRoot = __dirname;
const workspaceRoot = path.resolve(projectRoot, "..");

const config = getDefaultConfig(projectRoot);

config.watchFolders = [workspaceRoot];

config.resolver.nodeModulesPaths = [
  path.resolve(projectRoot, "node_modules"),
  path.resolve(workspaceRoot, "node_modules"),
];

config.resolver.disableHierarchicalLookup = false;
config.resolver.unstable_enableSymlinks = true;

config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (
    moduleName === "../../App" &&
    context.originModulePath.includes("node_modules/expo/AppEntry")
  ) {
    return {
      filePath: path.resolve(projectRoot, "App.tsx"),
      type: "sourceFile",
    };
  }
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
