class JobConfig implements Serializable {
    def URL
    //non-existing branches
    def orphanedOldItemsNumKeep = '3'
    def orphanedOldItemsDaysKeep = '1'
    //e.g. for develop regression
    def oldItemsNumKeep = '10'
    def oldItemsDaysKeep = '10'
    def oldArtifactsNumKeep = '10'
    def oldArtifactsDaysKeep = '10'

    protected String jobName
    protected String scriptPath
    protected String credentialsId
    def performanceJobs = []

}

class PerformanceJobConfig extends JobConfig implements Serializable {

}

//performance feature & tests put inside because number of jobs per view grew too large
public enum JOB_TYPES {


    SELENIUM("selenium"), PERFORMANCE("performance"), SELENIUM_FEATURE("selenium/feature"), SELENIUM_REGRESSION("selenium/regression"), SELENIUM_STANDALONE("selenium/standalone"), PIPELINE("pipeline"),
    PERFORMANCE_REGRESSION("performance/regression"), PERFORMANCE_FEATURE("performance/feature")

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

def dslScripts = []
def credentialsId = 'CORP-TU'
String serviceRoot = 'https://zensus-pl.corp.capgemini.com'



node() {
    def repoURL = "https://github.com/gabrielstar/seed.git"
    final String mainFolder = ""
    def yamlModule, viewsModule, utilsModule = null
    def multibranchPipelineTemplate, viewTemplate, pipelineTemplate, folderStructureTemplate = ''
    def jobConfigs = []
    def testPipelineConfigs = []
    def browsers = []
    def config = ""

    stage("Clean Workspace") {
        cleanWs()
    }
    stage('Checkout Self') {
        git branch: 'master', credentialsId: '', url: repoURL
    }
    stage('Init Modules') {
        yamlModule = load "modules/moduleYAML.groovy"
        viewsModule = load "modules/moduleViews.groovy"
        utilsModule = load "modules/moduleUtils.groovy"
    }
    stage("Prepare WS") {
        utilsModule.prepareWorkspace()
    }
    stage('Read templates & General Configs') {
        multibranchPipelineTemplate = yamlModule.readTemplate('templates/multibranchPipeline.groovy')
        folderStructureTemplate = yamlModule.readTemplate('templates/folderStructureTemplate.groovy')
        pipelineTemplate = yamlModule.readTemplate('templates/pipeline.groovy')
        dslTestPipelineTemplate = yamlModule.readTemplate('templates/testPipeline.groovy')
        viewTemplate = yamlModule.readTemplate('templates/view.groovy')

        browsers = readYaml(file: "${env.WORKSPACE_LOCAL}/config/selenium.yaml")?.browsers
        config = readYaml(file: "${env.WORKSPACE_LOCAL}/config/conf.yaml")
        mainFolder = config?.mainFolder
    }
    stage('Read Job Config YAML files') {
        def jobConfigFiles = yamlModule.getProjectConfigPaths()
        for (def jobConfigFile : jobConfigFiles) {
            echo " %% READING CONFIG FILE: ${jobConfigFile} %%"
            def jobConfig = readYaml(file: "${jobConfigFile}")
            jobConfigs << jobConfig
            yamlModule.printYAML(jobConfig)
        }
    }
    stage('Read Pipeline Config YAML files') {
        def testPipelineConfigFiles = yamlModule.getPipelineConfigPaths()
        for (def testPipelineConfigFile : testPipelineConfigFiles) {
            echo " %% READING PIPELINE CONFIG FILE: ${testPipelineConfigFile} %%"
            def testPipelineConfig = readYaml(file: "${testPipelineConfigFile}")
            testPipelineConfigs << testPipelineConfig
            yamlModule.printYAML(testPipelineConfig)
        }
    }
    stage("Create Folder Structure") {
        String folderDsl
        List folders = []
        folders.add(folderStructureTemplate.replaceAll(':folder:', mainFolder))
        JOB_TYPES.each {
            folders.add(folderStructureTemplate.replaceAll(':folder:', it.folder))
        }
        folderDsl = folders.join("\n")
        writeFile(file: 'folderStructure.groovy', text: folderDsl)
        jobDsl failOnMissingPlugin: true, unstableOnDeprecation: true, targets: 'folderStructure.groovy'
    }
    stage('Prepare Performance Job Configurations') {
        for (jobConfig in jobConfigs) {
            if (jobConfig.job.type == JOB_TYPES.PERFORMANCE.toString()) {
                echo "Building ${JOB_TYPES.PERFORMANCE} job config for ${jobConfig.job.jobName}"
                if (jobConfig.job.regression.enabled as boolean) {
                    dslScripts << generatePerformanceJobConfigs(multibranchPipelineTemplate, jobConfig, JOB_TYPES.PERFORMANCE_REGRESSION)
                }
                if (jobConfig.job.feature.enabled as boolean) {
                    dslScripts << generatePerformanceJobConfigs(multibranchPipelineTemplate, jobConfig, JOB_TYPES.PERFORMANCE_FEATURE)
                }
                echo "Excluded branches: " + jobConfig.job.feature.excludedBranches
            } else
                echo "Not a performance job: ${jobConfig.job.jobName}, ${JOB_TYPES.PERFORMANCE}"
        }
    }
    stage('Prepare Selenium Job Configurations') {
        for (jobConfig in jobConfigs) {
            if (jobConfig.job.type == JOB_TYPES.SELENIUM.toString()) {
                echo "Building ${JOB_TYPES.SELENIUM} job config for ${jobConfig.job.jobName}"
                if (jobConfig.job.regression.enabled as boolean) {
                    dslScripts << generateSeleniumJobConfigs(multibranchPipelineTemplate, jobConfig, JOB_TYPES.SELENIUM_REGRESSION)
                }
                if (jobConfig.job.feature.enabled as boolean) {
                    dslScripts << generateSeleniumJobConfigs(multibranchPipelineTemplate, jobConfig, JOB_TYPES.SELENIUM_FEATURE)
                }
                if (jobConfig.job.standalone.enabled as boolean) {
                    dslScripts << generateSeleniumJobConfigs(pipelineTemplate, jobConfig, JOB_TYPES.SELENIUM_STANDALONE)
                }
            } else
                echo "Not a selenium job: ${jobConfig.job.jobName}, ${JOB_TYPES.SELENIUM}"
        }
        stage('Prepare Test Pipelines Job Configurations') {
            for (def testPipelineConfig : testPipelineConfigs) {
                for (def job : testPipelineConfig?.chain) {
                    echo "JOB " + job
                }
            }
        }
    }
/*
stage('Prepare Job Configurations') {
    repoJobConfigs.each { String repoName, JobConfig repoConfig ->

        echo "Generating integrated test pipeline jobs configs"
        dslScripts += (generateTestPipelinesJobConfigs(
                repoName,
                repoConfig,
                dslTestPipelineTemplate,
                multibranchPipeline.groovy,
                pipelineBrowsers,
                ENVIRONMENTS,
                excludedEnvironmentsForRegression
        ))

    }

}*/
    stage('Prepare custom Views') {
        dslScripts.addAll(viewsModule.createViewsDSL(viewTemplate, jobConfigs, mainFolder, browsers))
    }
    stage('Create Jobs & Views') {
        echo "Creating jobs and views"
        if (dslScripts.size() > 0) {
            String dslOutput = dslScripts.join("\n")
            writeFile(file: 'dslOutput.groovy', text: dslOutput)
            jobDsl failOnMissingPlugin: true, unstableOnDeprecation: true, targets: 'dslOutput.groovy'
        }
    }


}

//###################### END ############################
@NonCPS
def getRegressionJobFor(String projectName, String browser, String env, String branch, boolean isPerformance) {
    def jobRelativePath
    def jobRoot
    def downstreamJob
    def jobDescription
    if (isPerformance) {
        jobRoot = "tests/performance/regression/"
        jobDescription = "Performance tests for $projectName"
        jobRelativePath = "${projectName.toLowerCase()}.${replaceVariablesForEnvironments(env, projectName)}/$branch"

    } else {
        jobRoot = "tests/regression/"
        jobDescription = "Functional Tests Regression with $browser for $projectName"
        jobRelativePath = "${projectName.toLowerCase()}.$browser.${replaceVariablesForEnvironments(env, projectName)}/$branch"
    }

    downstreamJob = "buildJob = build job: '$jobRoot$jobRelativePath',propagate:false"

    return """
        stage('$jobDescription') {
            try{
                $downstreamJob
                currentBuild.result = buildJob.getResult()
            }catch(Exception e){
                currentBuild.result = failureStatus
            }
            if(currentBuild.result.contains(failureStatus)){
                print "Stopping pipeline as test have failed"
                error("Tests failed")
            }
        }
    """
}

@NonCPS
def getJobForConfig(String jobTemplate, def jobConfig, JOB_TYPES jobType) {

    def includes = "", excludes = "", trigger = "", browser = ""

    switch (jobType) {
        case JOB_TYPES.SELENIUM:
        case JOB_TYPES.SELENIUM_STANDALONE:
            includes = jobConfig.job.standalone.branches.includes
            break;
        case JOB_TYPES.SELENIUM_REGRESSION:
            browser = jobConfig?.job?.browser
            includes = jobConfig.job.regression.branches.includes
            excludes = jobConfig.job.regression.branches.excludes
            trigger = jobConfig.job.regression.trigger
            break;
        case JOB_TYPES.SELENIUM_FEATURE:
            browser = jobConfig?.job?.browser
            includes = jobConfig.job.feature.branches.includes
            excludes = jobConfig.job.feature.branches.excludes
            trigger = jobConfig.job.feature.trigger
            break;
        case JOB_TYPES.PERFORMANCE_FEATURE:
            includes = jobConfig.job.feature.branches.includes
            excludes = jobConfig.job.feature.branches.excludes
            trigger = jobConfig.job.feature.trigger
            break;
        case JOB_TYPES.PERFORMANCE_REGRESSION:
            includes = jobConfig.job.regression.branches.includes
            excludes = jobConfig.job.regression.branches.excludes
            trigger = jobConfig.job.regression.trigger
            break;
        case JOB_TYPES.PIPELINE:

            break

    }


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
            replaceAll(':env:', jobConfig.job.environment).
            replace("..", ".") //empty replacements clean up
}

@NonCPS
String replaceVariablesForEnvironments(String env, String projectKey) {
    return env.replace("_ENV_", projectKey)
}


@NonCPS
def generateTestPipelinesJobConfigs(String repoName, JobConfig repoConfig, def dslTestPipelineTemplate, def dslPerformanceTemplate, def browsers, def ENVIRONMENTS, def excludedEnvironmentsForRegression) {
    def configs = []
    ENVIRONMENTS.each {
        description = "Test Pipelines for integration with Production Line"
        def env = replaceVariablesForEnvironments(it.env, repoConfig["jobName"])
        if (!(env in excludedEnvironmentsForRegression)) {
            configs.add(
                    getJobForConfig(dslTestPipelineTemplate, repoConfig, JOB_TYPES.PIPELINE, description, "", env)
                            .replaceAll(":jobList:",
                            browsers.collect { browser ->
                                getRegressionJobFor(repoConfig['jobName'], browser, it.env, "develop", false)
                            }
                            .join("\n ")
                                    .plus("\n")
                                    .plus(
                                    repoConfig.performanceJobs.collect { def performanceRepoConfig ->
                                        getRegressionJobFor(performanceRepoConfig['jobName'], "", env, "develop", true)
                                    }.join("\n")
                            )
                    )
            )
        }
    }
    configs
}


@NonCPS
def generatePerformanceJobConfigs(def dslPerformanceTemplate, def jobConfig, JOB_TYPES jobType) {
    return getJobForConfig(dslPerformanceTemplate, jobConfig, jobType)
}

@NonCPS
def generateSeleniumJobConfigs(def dslSeleniumTemplate, def jobConfig, JOB_TYPES jobType) {
    return getJobForConfig(dslSeleniumTemplate, jobConfig, jobType)
}


return this