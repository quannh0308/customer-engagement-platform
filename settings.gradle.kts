rootProject.name = "ceap-platform"

// Include all subprojects
include(
    "ceap-common",
    "ceap-models",
    "ceap-storage",
    "ceap-connectors",
    "ceap-scoring",
    "ceap-filters",
    "ceap-serving",
    "ceap-channels",
    "ceap-workflow-etl",
    "ceap-workflow-filter",
    "ceap-workflow-score",
    "ceap-workflow-store",
    "ceap-workflow-reactive"
)
