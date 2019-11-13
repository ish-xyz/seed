//performance feature & tests put inside because number of jobs per view grew too large
public enum JOB_TYPES {
    FEATURE("feature"), REGRESSION("regression"), STANDALONE("standalone"), PIPELINE("pipeline"),
    PERFORMANCE("performance"), PERFORMANCE_REGRESSION("performance/regression"), PERFORMANCE_FEATURE("performance/feature"),
    SELENIUM("selenium")

    String folder
    String name
    String rootFolder = "tests_y"

    private JOB_TYPES(String folder) {
        this.folder = "$rootFolder/$folder"
        this.name = folder
    }

    @NonCPS
    public String toString() {
        return this.name
    }
}

@NonCPS
def getJobForConfig(String jobTemplate, def jobConfig, JOB_TYPES jobType) {

    def includes
    def excludes
    def trigger

    switch (jobType) {
        case JOB_TYPES.PERFORMANCE_FEATURE:
        case JOB_TYPES.FEATURE:
            includes = jobConfig.job.feature.branches.includes
            excludes = jobConfig.job.feature.branches.excludes
            trigger = jobConfig.job.feature.trigger
            break
        case JOB_TYPES.PERFORMANCE_REGRESSION:
        case JOB_TYPES.REGRESSION:
            includes = jobConfig.job.regression.branches.includes
            excludes = jobConfig.job.regression.branches.excludes //otherwise trait will throw error
            trigger = jobConfig.job.regression.trigger
            break
        case JOB_TYPES.PIPELINE:
            includes = jobConfig.job.regression.branches.includes
            excludes = jobConfig.job.regression.branches.excludes
            trigger = jobConfig.job.regression.trigger
            break

    }
    def browser = jobConfig.job?.selenium?.browser ?: ""

    return jobTemplate.
            replaceAll(':description:', jobConfig.job.description).
            replaceAll(':URL:', jobConfig.job.url).
            replaceAll(':orphanedOldItemsNumKeep:', jobConfig.job.orphanedOldItemsNumKeep as String).
            replaceAll(':orphanedOldItemsDaysKeep:', jobConfig.job.orphanedOldItemsDaysKeep as String).
            replaceAll(':oldItemsNumKeep:', jobConfig.job.oldItemsNumKeep as String).
            replaceAll(':oldItemsDaysKeep:', jobConfig.job.oldItemsDaysKeep as String).
            replaceAll(':oldArtifactsNumKeep:', jobConfig.job.oldArtifactsNumKeep as String).
            replaceAll(':oldArtifactsDaysKeep:', jobConfig.job.oldArtifactsDaysKeep as String).
            replaceAll(':jobName:', jobConfig.job.jobName.toLowerCase()).
            replaceAll(':folder:', jobType.folder).
            replaceAll(':scriptPath:', jobConfig.job.scriptPath).
            replaceAll(':credentialsId:', jobConfig.job.credentialsId).
            replaceAll(':includes:', includes).
            replaceAll(':excludes:', excludes).
            replaceAll(':trigger:', trigger).
            replaceAll(':browser:', browser).
            replaceAll(':env:', jobConfig.job.environment)
}

return this