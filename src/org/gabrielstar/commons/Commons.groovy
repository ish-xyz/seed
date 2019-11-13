package org.gabrielstar.commons

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
