multibranchPipelineJob(':folder:/:jobName:.:browser:.:env:') {
    description(":description:")
    branchSources{
           branchSource {
            source {
                git {
                  remote(':URL:')
                  credentialsId(':credentialsId:')
                  traits{
                      headWildcardFilter {
                            includes(':includes:')
                            excludes(':excludes:')
                        }
                  }
                }
            }
          strategy {
                defaultBranchPropertyStrategy {
                  props {
                    noTriggerBranchProperty() //to prevent starting builds on branch discovery
                    buildRetentionBranchProperty {
                        buildDiscarder {
                            logRotator {
                                    daysToKeepStr(':oldItemsNumKeep:')
                                    numToKeepStr(':oldItemsDaysKeep:')
                                    artifactDaysToKeepStr(':oldArtifactsNumKeep:')
                                    artifactNumToKeepStr(':oldArtifactsDaysKeep:')
                                }
                            }
                        }
                  }
                }
              }
          }
        orphanedItemStrategy {
            discardOldItems {
                numToKeep(:orphanedOldItemsNumKeep:)
                daysToKeep(:orphanedOldItemsNumKeep:)
            }
       }
    }
    triggers{
        :trigger:
    }
    configure { node ->
        def traits = node / sources / data / 'jenkins.branch.BranchSource' / source / traits
        traits <<  'jenkins.plugins.git.traits.BranchDiscoveryTrait'()  //enable discovery of branches
      }
}