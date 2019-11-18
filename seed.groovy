node() {

    final String MODULES_DIR = "modules", TEMPLATES_DIR = "templates", CONFIG_DIR = "config"
    def dslScripts = []
    def repoURL = 'https://github.com/gabrielstar/seed.git'
    def mainFolder = '', yamlModule = '', viewsModule = '', utilsModule = ''
    def multibranchPipelineTemplate = '', viewTemplate = '', pipelineTemplate = '', pipelineStageTemplate = '', folderStructureTemplate = ''
    def jobConfigs = [], testPipelineConfigs = []
    def browsers = []
    def config = "", jobConfigDefaults = ""

    stage("Clean Workspace") {
        cleanWs()
    }
    stage('Checkout Self') {
        git branch: 'master', credentialsId: '', url: repoURL
    }
    stage('Init Modules') {
        yamlModule = load "${MODULES_DIR}/moduleYAML.groovy"
        viewsModule = load "${MODULES_DIR}/moduleViews.groovy"
        utilsModule = load "${MODULES_DIR}/moduleUtils.groovy"
    }
    stage("Prepare Workspace") {
        utilsModule.prepareWorkspace()
    }
    stage('Read Templates & General Config') {
        multibranchPipelineTemplate = yamlModule.readTemplate("${TEMPLATES_DIR}/multibranchPipeline.groovy")
        folderStructureTemplate = yamlModule.readTemplate("${TEMPLATES_DIR}/folderStructureTemplate.groovy")
        pipelineTemplate = yamlModule.readTemplate("${TEMPLATES_DIR}/pipeline.groovy")
        testPipelineTemplate = yamlModule.readTemplate("${TEMPLATES_DIR}/testPipeline.groovy")
        testPipelineStageTemplate = yamlModule.readTemplate("${TEMPLATES_DIR}/pipelineStage.groovy")
        viewTemplate = yamlModule.readTemplate("${TEMPLATES_DIR}/view.groovy")
        config = readYaml(file: "${env.WORKSPACE_LOCAL}/${CONFIG_DIR}/conf.yaml")
        mainFolder = config?.mainFolder
        browsers = config?.browsers
        assert mainFolder == JOB_TYPES.SELENIUM.rootFolder
        jobConfigDefaults = readYaml(file: "${env.WORKSPACE_LOCAL}/${CONFIG_DIR}/defaults.yaml")
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
        List folders = []
        folders.add(folderStructureTemplate.replaceAll(':folder:', mainFolder))
        JOB_TYPES.each {
            folders.add(folderStructureTemplate.replaceAll(':folder:', it.folder))
        }
        writeFile(file: 'folderStructure.groovy', text: folders.join("\n"))
        jobDsl failOnMissingPlugin: true, unstableOnDeprecation: true, targets: 'folderStructure.groovy'
    }
    stage('Prepare Performance Job Configurations') {
        for (jobConfig in jobConfigs) {
            if (jobConfig.job.type == JOB_TYPES.PERFORMANCE.toString()) {
                echo "Building ${JOB_TYPES.PERFORMANCE} job config for ${jobConfig.job.jobName}"
                if (jobConfig.job.regression.enabled as boolean) {
                    dslScripts << generatePerformanceJobConfigs(multibranchPipelineTemplate, jobConfig, jobConfigDefaults, JOB_TYPES.PERFORMANCE_REGRESSION)
                }
                if (jobConfig.job.feature.enabled as boolean) {
                    dslScripts << generatePerformanceJobConfigs(multibranchPipelineTemplate, jobConfig, jobConfigDefaults, JOB_TYPES.PERFORMANCE_FEATURE)
                }
            }
        }
    }
    stage('Prepare Selenium Job Configurations') {
        for (jobConfig in jobConfigs) {
            if (jobConfig.job.type == JOB_TYPES.SELENIUM.toString()) {
                echo "Building ${JOB_TYPES.SELENIUM} job config for ${jobConfig.job.jobName}"
                if (jobConfig.job.regression.enabled as boolean) {
                    dslScripts << generateSeleniumJobConfigs(multibranchPipelineTemplate, jobConfig, jobConfigDefaults, JOB_TYPES.SELENIUM_REGRESSION)
                }
                if (jobConfig.job.feature.enabled as boolean) {
                    dslScripts << generateSeleniumJobConfigs(multibranchPipelineTemplate, jobConfig, jobConfigDefaults, JOB_TYPES.SELENIUM_FEATURE)
                }
                if (jobConfig.job.standalone.enabled as boolean) {
                    dslScripts << generateSeleniumJobConfigs(pipelineTemplate, jobConfig, jobConfigDefaults, JOB_TYPES.SELENIUM_STANDALONE)
                }
            }
        }
        stage('Prepare Test Pipelines Job Configurations') {
            for (def testPipelineConfig : testPipelineConfigs) {
                def testPipelineStages = []
                for (def testPipelineStageConfig : testPipelineConfig?.chain) {
                    def confFile = yamlModule.getYAMLConfig(testPipelineStageConfig?.downstreamJob)
                    def jobConfig = readYaml(file: "${confFile}")
                    if (jobConfig.job."${testPipelineStageConfig?.type}".enabled) {
                        echo "Adding ${testPipelineStageConfig?.downstreamJob} ${testPipelineStageConfig?.type} branch " + testPipelineStageConfig?.branch
                        def name
                        if (jobConfig.job.type == 'selenium') {
                            name = "${jobConfig.job.type}/${testPipelineStageConfig.type}/${jobConfig.job.jobName}.${jobConfig.job.browser}.${jobConfig.job.environment}/${testPipelineStageConfig?.branch}"
                        } else if (jobConfig.job.type == 'performance') {
                            name = "${jobConfig.job.type}/${testPipelineStageConfig.type}/${jobConfig.job.jobName}.${jobConfig.job.environment}/${testPipelineStageConfig?.branch}"
                        }
                        testPipelineStages << getTestPipelineStageJobForConfig(testPipelineStageTemplate, name, mainFolder)
                    }
                }
                dslScripts << getJobForConfig(testPipelineTemplate, testPipelineConfig, jobConfigDefaults, JOB_TYPES.PIPELINE).replace(":jobList:", testPipelineStages.join("\n"))
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

enum JOB_TYPES {

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
def getJobForConfig(String jobTemplate, def jobConfig, def jobConfigDefaults, JOB_TYPES jobType) {

    def includes = "", excludes = "", trigger = "", browser = ""

    switch (jobType) {
        case JOB_TYPES.SELENIUM:
        case JOB_TYPES.SELENIUM_STANDALONE:
            includes = jobConfig?.job?.standalone?.branches?.includes ?: jobConfigDefaults.job.standalone.branches.includes
            break;
        case JOB_TYPES.SELENIUM_REGRESSION:
        case JOB_TYPES.SELENIUM_FEATURE:
            browser = jobConfig?.job?.browser ?: jobConfigDefaults?.job?.browser
            includes = jobConfig?.job?.regression?.branches?.includes ?: jobConfigDefaults.job.regression.branches.includes
            excludes = jobConfig?.job?.regression?.branches?.excludes ?: jobConfigDefaults.job.regression.branches.excludes
            trigger = jobConfig?.job?.regression?.trigger ?: jobConfigDefaults.job.regression.trigger
            break;
        case JOB_TYPES.PERFORMANCE_FEATURE:
        case JOB_TYPES.PERFORMANCE_REGRESSION:
            includes = jobConfig?.job?.regression?.branches?.includes ?: jobConfigDefaults.job.regression.branches.includes
            excludes = jobConfig?.job?.regression?.branches?.excludes ?: jobConfigDefaults.job.regression.branches.excludes
            trigger = jobConfig?.job?.regression?.trigger ?: jobConfigDefaults.job.regression.trigger
            break;
        case JOB_TYPES.PIPELINE:
            break

    }

    return jobTemplate.
            replaceAll(':description:', jobConfig?.job?.description ?: jobConfigDefaults.job.description).
            replaceAll(':URL:', jobConfig?.job?.url ?: "").
            replaceAll(':orphanedOldItemsNumKeep:', jobConfig?.job?.orphanedOldItemsNumKeep as String ?: jobConfigDefaults.job.orphanedOldItemsNumKeep as String).
            replaceAll(':orphanedOldItemsDaysKeep:', jobConfig?.job?.orphanedOldItemsDaysKeep as String ?: jobConfigDefaults.job.orphanedOldItemsDaysKeep as String).
            replaceAll(':oldItemsNumKeep:', jobConfig?.job?.oldItemsNumKeep as String ?: jobConfigDefaults.job.oldItemsNumKeep as String).
            replaceAll(':oldItemsDaysKeep:', jobConfig?.job?.oldItemsDaysKeep as String ?: jobConfigDefaults.job.oldItemsDaysKeep as String).
            replaceAll(':oldArtifactsNumKeep:', jobConfig?.job?.oldArtifactsNumKeep as String ?: jobConfigDefaults.job.oldArtifactsNumKeep as String).
            replaceAll(':oldArtifactsDaysKeep:', jobConfig?.job?.oldArtifactsDaysKeep as String ?: jobConfigDefaults.job.oldArtifactsDaysKeep as String).
            replaceAll(':jobName:', jobConfig?.job?.jobName?.toLowerCase() ?: "").
            replaceAll(':folder:', jobType.folder ?: "").
            replaceAll(':scriptPath:', jobConfig?.job?.scriptPath ?: jobConfigDefaults.job.scriptPath).
            replaceAll(':credentialsId:', jobConfig?.job?.credentialsId ?: jobConfigDefaults.job.credentialsId).
            replaceAll(':includes:', includes).
            replaceAll(':excludes:', excludes).
            replaceAll(':trigger:', trigger).
            replaceAll(':browser:', browser).
            replaceAll(':env:', jobConfig?.job?.environment ?: jobConfigDefaults.job.environment).
            replace("..", ".") //empty replacements clean up
}

@NonCPS
def generatePerformanceJobConfigs(def dslPerformanceTemplate, def jobConfig, def jobConfigDefaults, JOB_TYPES jobType) {
    return getJobForConfig(dslPerformanceTemplate, jobConfig, jobConfigDefaults, jobType)
}

@NonCPS
def generateSeleniumJobConfigs(def dslSeleniumTemplate, def jobConfig, def jobConfigDefaults, JOB_TYPES jobType) {
    return getJobForConfig(dslSeleniumTemplate, jobConfig, jobConfigDefaults, jobType)
}

@NonCPS
def getTestPipelineStageJobForConfig(String jobTemplate, def name, def mainFolder) {
    def downstreamJob = "buildJob = build job: '$mainFolder/$name',propagate:false"
    return jobTemplate.
            replaceAll(':description:', name).
            replaceAll(':downstreamJob:', downstreamJob)

}

return this