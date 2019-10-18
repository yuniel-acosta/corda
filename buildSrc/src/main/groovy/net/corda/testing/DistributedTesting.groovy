package net.corda.testing

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test

/**
 This plugin is responsible for wiring together the various components of test task modification
 */
class DistributedTesting implements Plugin<Project> {

    @Override
    void apply(Project project) {
        def groupTask = project.tasks.create("groupTests", GroupTests) {
            subprojects = true
        }
        def run = project.tasks.create("runTestWorker", RunWorkerTests) {
            group = "parallel builds"
            dependsOn(groupTask)
        }
        project.subprojects { Project p ->
            p.pluginManager.apply(DistributedTestModule)
            p.tasks.withType(ListTests) { groupTask.dependsOn(it) }
            p.tasks.withType(Test)
                    .findAll { DistributedTestingDynamicParameters.shouldRunTest(p, it.name) }
                    .forEach { run.finalizedBy(it) }
        }
    }

}

class DistributedTestModule implements Plugin<Project> {
    @Override
    void apply(Project target) {
        def list = target.create("listTests", ListTests)
        def group = target.create("groupTests", GroupTests) {
            dependsOn list
            subprojects = false
        }
        def run = target.create("runTestWorker", RunWorkerTests) {
            dependsOn group
        }
        target.tasks.withType(Test)
                .findAll { DistributedTestingDynamicParameters.shouldRunTest(target, it.name) }
                .forEach {
                    run.finalizedBy(it)
                }
    }
}

class GroupTests extends DefaultTask {
    boolean subprojects = false
    Map<Test, Set<String>> testsToRun = new HashMap<>()

    @TaskAction
    def group() {
        logger.lifecycle("Grouping tests for $project")
        Collection<ListTests> listers = project.tasks.withType(ListTests)
        if (subprojects) {
            logger.lifecycle("Including subprojects")
            project.subprojects { Project sub ->
                listers += sub.tasks.withType(ListTests)
            }
        }

        listers.forEach { ListTests it ->
            it.tests.forEach { Map.Entry<Test, Set<String>> tests ->
                testsToRun.put(tests.key, tests.value.take(1).toSet())
            }
        }
    }

    Set<String> includesForTest(Test t) {
        return testsToRun.get(t, new HashSet<>())
    }
}

class RunWorkerTests extends DefaultTask {
    RunWorkerTests() {
        group = "parallel builds"
    }

    @TaskAction
    def run() {
        println "Configuring test tasks"
        def grouper = project.tasks.withType(GroupTests).first()
        project.subprojects { Project p ->
            p.tasks.withType(Test)
                    .findAll { DistributedTestingDynamicParameters.shouldRunTest(project, it.name) }
                    .forEach { Test t ->
                        if (t.hasProperty("ignoreForDistribution")) {
                            return
                        }
                        def includes = grouper.includesForTest(t)
                        println "Configuring test for includes: $t: $includes"
                        t.configure {
                            doFirst {
                                println "Running modified test: $t"
                                filter {
                                    failOnNoMatchingTests false
                                    includes.forEach {
                                        includeTestsMatching it
                                    }
                                }
                            }
                        }
//                    t.testNameIncludePatterns = tests
                    }
        }
        println "Running worker tests"
    }
}

class DistributedTestingDynamicParameters {

    static boolean shouldRunTest(Project project, String testTaskName) {
        def targets = testTaskNames(project)
        if (targets == ["all"]) return true
        return targets.contains(testTaskName)
    }

    static Set<String> testTaskNames(Project project) {
        return ((project.property("parallelTestTasks") as String) ?: "all")
                .split(",")
                .toList()
                .toSet()
    }

    static int workerNumber(Project project) {
        return (project.property("parallelTestWorkerId") as Integer) ?: 0
    }

    static int numberOfWorkers(Project project) {
        return (project.property("parallelTestWorkers") as Integer) ?: 1
    }
}