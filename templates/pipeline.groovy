pipelineJob(':folder:/:jobName:') {
    description(":description:")
    definition {
        cpsScm {
            scm {
                git {
                    branches(":includes:")
                    remote {
                        url(':URL:')
                        credentials(':credentialsId:')
                    }
                }
            }
            scriptPath(':scriptPath:')
        }
    }
}