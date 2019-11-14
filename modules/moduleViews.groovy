def createViewsDSL(def view, def jobConfigs, def mainFolder, def browsers) {
    def dslScripts = []
    echo "Preparing custom views"
    jobConfigs.each {
        jobConfig ->
            dslScripts.add(view.
                    replaceAll(':name:', "2. ${jobConfig?.job?.jobName}").
                    replaceAll(':regex:', jobConfig?.job?.jobName).
                    replaceAll(':folder:', mainFolder)
            )
    }
    //regressions
    dslScripts.add(view.
            replaceAll(':name:', '0. regressions').
            replaceAll(':regex:', 'regression').
            replaceAll(':folder:', mainFolder)
    )
    //unstable
    dslScripts.add(view.
            replaceAll(':name:', '1. unstable').
            replaceAll(':regex:', '.*').
            replaceAll(':folder:', mainFolder)
    )

    browsers.each {
        browser ->
            dslScripts.add(view.
                    replaceAll(':name:', "3. ${browser}").
                    replaceAll(':regex:', browser).
                    replaceAll(':folder:', mainFolder)
            )
    }
    return dslScripts
}

return this