pipelineJob(':folder:/:jobName:') {
    description(":description:")
    definition{
      cpsScm{
          scm{
              git{
                  branches("develop")
                  remote{
                      url(':URL:')
                      credentials(':credentialsId:')
                  }
              }
          }
          scriptPath(':scriptPath:')
      }
    }
}