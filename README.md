# seed
@Author - Gabriel Starczewski
This is Jenkins seed job for performance and selenium tests

Based on YAML file configurations it can generate multibranch pipelines for selenium and performance tests (jmeter), that are based of a template containing
Jenkinsfile. It generates:

- regression jobs (jobs that are run regularly based on cron expression and should contains current stable tests)
- feature jobs (jobs that are usually triggered manuall during development and fixes of new tests)
- test pipelines (any kind of job chain that we can configure based of existing jobs)

Features:

- removed branches cause jobs to disappear from jenkins
- we can easily regenerate all jobs for multiple teams
- we have the same development flow using standard git branchign model for selenium and performance tests
- we can use existing jobs to create any kind of test pipeline (performance and selenium jobs) that can be exposed to be part of larger build, deploy, test pipeline


How to Use?

Install Jenkins with necessary plugins
Configure YAML files as per examples (downstream repositories need to contain Jenkinsfile in repo root)
In Jenkins
    New Item -> Pipeline -> Pipeline Script from SCM -> Enter Your Repo URL -> Script Path : seed.groovy

