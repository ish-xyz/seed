def printYAML(def yaml) {
    echo "content: " + yaml
}

def readTemplate(def templatePath) {
    def template = readFile "$templatePath"
    return template
}

def getYAMLConfigs(def extension, def root = 'projects', def excludes = '*.defaults.yaml') {
    def configFilesPathList = []
    configFilesPathList = findFiles(glob: "**/${root}/**/*${extension}", excludes: "**/${excludes}") //everywhere
    return configFilesPathList
}

def getYAMLConfig(def extension) {
    return getYAMLConfigs(extension)?.first()
}

def getConfigsPaths(def extensionList = ['.yaml', '.yml'], def root = 'projects', def excludes = '*.defaults.yaml') {
    def configFilesPathList = []
    for (String extension : extensionList) {
        configFilesPathList.addAll(getYAMLConfigs(extension, root, excludes))
    }
    return configFilesPathList
}

def getProjectConfigPaths() {
    return getConfigsPaths()
}

def getPipelineConfigPaths() {
    return getConfigsPaths(['.yaml', '.yml'], "pipelines")
}

return this