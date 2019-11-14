def prepareWorkspace() {
    cleanWs()
    env.WORKSPACE_LOCAL = sh(returnStdout: true, script: 'pwd').trim()
    echo "Workspace set to:" + env.WORKSPACE_LOCAL
}

def checkout(def repoURL, def branch = 'master', def credentialsId = '') {
    git branch: branch,
            credentialsId: "${credentialsId}",
            url: repoURL
}

return this