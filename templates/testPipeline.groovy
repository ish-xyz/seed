 pipelineJob(':folder:/:jobName:.:env:'){
      description(":description:")
      definition{
        cpsFlowDefinition{
          script("""
                    def buildJob
                    def failureStatus = 'FAILURE'
                    node(){
                        :jobList:
                    }
                 """)
          sandbox(true)
        }
      }
}