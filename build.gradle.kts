// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}

tasks.register("verifyModuleBoundaries") {
    group = "verification"
    description = "Checks the P0A module dependency allowlist and verifies it is acyclic."
    notCompatibleWithConfigurationCache("Inspects declared project dependencies across modules")

    doLast {
        val allowed = mapOf(
            ":app" to setOf(":core", ":data", ":ocr", ":parser", ":feature-task", ":feature-capture", ":feature-review", ":feature-result", ":feature-settings"),
            ":core" to emptySet(),
            ":data" to setOf(":core"),
            ":ocr" to setOf(":core"),
            ":parser" to setOf(":core"),
            ":feature-task" to setOf(":data", ":core"),
            ":feature-capture" to setOf(":data", ":ocr", ":parser", ":core"),
            ":feature-review" to setOf(":data", ":core"),
            ":feature-result" to setOf(":data", ":parser", ":core"),
            ":feature-settings" to setOf(":data", ":core"),
        )
        val directConfigurations = setOf("api", "implementation", "runtimeOnly")
        val actual: Map<String, Set<String>> = subprojects.associate { module ->
            val projectDependencies: Set<String> = module.configurations
                .filter { configuration -> configuration.name in directConfigurations }
                .flatMap { configuration -> configuration.dependencies }
                .filterIsInstance<org.gradle.api.artifacts.ProjectDependency>()
                .map { dependency -> dependency.path }
                .toSet()
            module.path to projectDependencies
        }

        require(actual.keys == allowed.keys) {
            "Module allowlist mismatch: expected ${allowed.keys}, found ${actual.keys}"
        }
        actual.forEach { (module, dependencies) ->
            require(dependencies.all { it in allowed.getValue(module) }) {
                "Forbidden project dependency from $module: ${dependencies - allowed.getValue(module)}"
            }
            require(dependencies == allowed.getValue(module)) {
                "Missing or unexpected project dependency for $module: expected ${allowed.getValue(module)}, found $dependencies"
            }
        }

        val remaining = actual.mapValues { (_, dependencies) -> dependencies.toMutableSet() }.toMutableMap()
        while (remaining.isNotEmpty()) {
            val dependencyLeaves = remaining.filterValues { dependencies ->
                dependencies.none { dependency -> dependency in remaining.keys }
            }.keys
            require(dependencyLeaves.isNotEmpty()) {
                "Circular module dependency detected among ${remaining.keys}"
            }
            dependencyLeaves.forEach(remaining::remove)
        }
    }
}
