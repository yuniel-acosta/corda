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

    static class GroupTests extends DefaultTask {
        Map<String, List<String>> groups = new HashMap<>()

        @TaskAction
        def group() {
            println "Grouping tests"
            groups.put("1", Arrays.asList("test1", "test2"))
            groups.put("2", Arrays.asList("test3", "test4"))
        }

        List<String> includesForTest(Test t) {
            return groups.get(t.path, Arrays.asList("test1"))
        }
    }

    static class RunWorkerTests extends DefaultTask {
        RunWorkerTests() {
            group = "parallel builds"
        }

        @TaskAction
        def run() {
            println "Configuring test tasks"
            def grouper = project.tasks.withType(GroupTests).first()
            project.subprojects { Project p ->
                p.tasks.withType(Test) { Test t ->
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

    @Override
    void apply(Project project) {
        def g = project.tasks.create("groupParallelTests", GroupTests)
        def wt = project.tasks.create("runWorkerTests", RunWorkerTests) {
            group = "parallel builds"
            dependsOn(g)
        }
        project.subprojects { Project p ->
            def list = p.tasks.create("listTests", DefaultTask) {
                doFirst {
                    println "Listing tests for project $p"
                }
            }
            g.dependsOn(list)
            p.tasks.withType(Test) { Test t ->
                wt.finalizedBy(t)
            }
        }
    }

}
