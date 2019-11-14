listView('tests/:name:') {
    description('')
    filterBuildQueue()
    filterExecutors()
    jobs {
        regex(/.*:regex:.*/)
    }
    recurse(true)
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}