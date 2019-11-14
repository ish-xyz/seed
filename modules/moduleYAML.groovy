def printYAML(def project) {
    echo "Project name: " + project.job
}

def readTemplate(def templatePath) {
    def template = readFile "$templatePath"
    return template
}

def getYAMLConfigs(def extension, def excludes, def root = 'projects') {
    def configFilesPathList = []
    configFilesPathList = findFiles(glob: "**/${root}/**/*${extension}", excludes: "**/${excludes}") //everywhere
    return configFilesPathList
}

def getConfigsPaths(def extensionList = ['.yaml', '.yml'], def excludes = 'defaults.yaml') {
    def configFilesPathList = []
    for (String extension : extensionList) {
        configFilesPathList.addAll(getYAMLConfigs(extension, excludes))
    }
    return configFilesPathList
}

return this