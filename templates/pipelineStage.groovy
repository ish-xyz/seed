stage(':description:') {
    try {
        ":downstreamJob:"
        currentBuild.result = buildJob.getResult()
    } catch (Exception e) {
        currentBuild.result = failureStatus
    }
    if (currentBuild.result.contains(failureStatus)) {
        print "Stopping pipeline as test have failed"
        error("Tests failed")
    }
}