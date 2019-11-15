node() {

    def dslScripts = []
    def repoURL = 'https://github.com/gabrielstar/seed.git'
    def mainFolder = '', yamlModule = '', viewsModule = '', utilsModule = ''
    def multibranchPipelineTemplate = '', viewTemplate = '', pipelineTemplate = '', pipelineStageTemplate = '', folderStructureTemplate = ''
    def jobConfigs = [], testPipelineConfigs = []
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
    stage("Prepare Workspace") {
        utilsModule.prepareWorkspace()
    }
    stage('Read Templates & General Config') {
        multibranchPipelineTemplate = yamlModule.readTemplate('templates/multibranchPipeline.groovy')
        folderStructureTemplate = yamlModule.readTemplate('templates/folderStructureTemplate.groovy')
        pipelineTemplate = yamlModule.readTemplate('templates/pipeline.groovy')
        testPipelineTemplate = yamlModule.readTemplate('templates/testPipeline.groovy')
        testPipelineStageTemplate = yamlModule.readTemplate('templates/pipelineStage.groovy')
        viewTemplate = yamlModule.readTemplate('templates/view.groovy')
        browsers = readYaml(file: "${env.WORKSPACE_LOCAL}/config/selenium.yaml")?.browsers
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
                def stages = []
                for (def pipelineStage : testPipelineConfig?.chain) {
                    echo "Parsing JOB " + pipeline

                    def confFile = yamlModule.getYAMLConfig(pipelineStage?.downstreamJob)
                    def content = readYaml(file: "${confFile}")

                    if (content.job."${pipelineStage?.type}".enabled) {
                        echo "Adding ${pipelineStage?.downstreamJob} ${pipelineStage?.type} branch " + pipelineStage?.branch
                        def name
                        if (content.job.type == 'selenium') {
                            name = "${content.job.type}/${pipelineStage.type}/${content.job.jobName}.${content.job.browser}.${content.job.environment}/${pipelineStage?.branch}"
                        } else if (content.job.type == 'performance') {
                            name = "${content.job.type}/${pipelineStage.type}/${content.job.jobName}.${content.job.environment}/${pipelineStage?.branch}"
                        }
                        echo "$mainFolder/$name"
                        def downstreamJob = "buildJob = build job: '$mainFolder/$name',propagate:false"
                        def stage = testPipelineStageTemplate.
                                replaceAll(':description:', name).
                                replaceAll(':downstreamJob:', downstreamJob)
                        stages << stage
                    }

                }
                dslScripts << getJobForConfig(testPipelineTemplate, testPipelineConfig, JOB_TYPES.PIPELINE).replace(":jobList:", stages.join("\n"))

            }
        }
    }
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
            replaceAll(':description:', jobConfig?.job?.description ?: "").
            replaceAll(':URL:', jobConfig?.job?.url ?: "").
            replaceAll(':orphanedOldItemsNumKeep:', jobConfig?.job?.orphanedOldItemsNumKeep as String ?: "").
            replaceAll(':orphanedOldItemsDaysKeep:', jobConfig?.job?.orphanedOldItemsDaysKeep as String ?: "").
            replaceAll(':oldItemsNumKeep:', jobConfig?.job?.oldItemsNumKeep as String ?: "").
            replaceAll(':oldItemsDaysKeep:', jobConfig?.job?.oldItemsDaysKeep as String ?: "").
            replaceAll(':oldArtifactsNumKeep:', jobConfig?.job?.oldArtifactsNumKeep as String ?: "").
            replaceAll(':oldArtifactsDaysKeep:', jobConfig?.job?.oldArtifactsDaysKeep as String ?: "").
            replaceAll(':jobName:', jobConfig?.job?.jobName?.toLowerCase() ?: "").
            replaceAll(':folder:', jobType.folder ?: "").
            replaceAll(':scriptPath:', jobConfig?.job?.scriptPath ?: "").
            replaceAll(':credentialsId:', jobConfig?.job?.credentialsId ?: "").
            replaceAll(':includes:', includes ?: "").
            replaceAll(':excludes:', excludes ?: "").
            replaceAll(':trigger:', trigger ?: "").
            replaceAll(':browser:', browser ?: "").
            replaceAll(':env:', jobConfig?.job?.environment ?: "").
            replace("..", ".") //empty replacements clean up
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