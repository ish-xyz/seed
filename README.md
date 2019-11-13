# seed
jenkins seed job for performance and selenium tests

Based on YAML file configurations it can generate multibranch pipelines for selenium and performance tests (jmeter), that are based of a template containing
Jenkinsfile. It generates:

- regression jobs
- feature jobs
- test pipelines (selenium regression+jmeter regression)
