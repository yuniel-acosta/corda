package net.corda.testing

import io.github.classgraph.ClassGraph
import io.github.classgraph.ClassInfo
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test

import java.util.stream.Collectors

class ListShufflerAndAllocator {

    private final List<String> tests

    public ListShufflerAndAllocator(List<String> tests) {
        this.tests = new ArrayList<>(tests)
    }

    List<String> getTestsForFork(int fork, int forks, Integer seed) {
        Random shuffler = new Random(seed);
        List<String> copy = new ArrayList<>(tests);
        while (copy.size() < forks) {
            //pad the list
            copy.add(null);
        }
        Collections.shuffle(copy, shuffler);
        int numberOfTestsPerFork = Math.max((copy.size() / forks).intValue(), 1);
        int consumedTests = numberOfTestsPerFork * forks;
        int ourStartIdx = numberOfTestsPerFork * fork;
        int ourEndIdx = ourStartIdx + numberOfTestsPerFork;
        int ourSupplementaryIdx = consumedTests + fork;
        ArrayList<String> toReturn = new ArrayList<>(copy.subList(ourStartIdx, ourEndIdx));
        if (ourSupplementaryIdx < copy.size()) {
            toReturn.add(copy.get(ourSupplementaryIdx));
        }
        return toReturn.stream().filter { it -> it != null }.collect(Collectors.toList());
    }
}

interface TestLister {
    List<String> getAllTestsDiscovered()
}

class ListTests extends DefaultTask {

    Map<Test, Set<String>> tests

    Set<String> testsIn(Test task) {
        if (tests == null) throw new IllegalStateException("task should have been ran: $this")
        tests.get(task)
    }

    Set<String> allTests() {
        if (tests == null) throw new IllegalStateException("task should have been ran: $this")
        tests.collect { it.value }.flatten().toSet()
    }

    Set<String> findTestsIn(Test task) {
        def cp = task.project.getExtensions().getByType(SourceSetContainer)
                .toList()
                .findAll { it.name.contains("test") }
                .collect { it.output.classesDirs.toList() }
                .flatten()
        if (cp.isEmpty()) return Collections.emptySet()

        logger.lifecycle("Listing tests for $task, scanning ${cp.toList()}")
        def tests = new ClassGraph()
                .enableClassInfo()
                .enableMethodInfo()
                .ignoreClassVisibility()
                .ignoreMethodVisibility()
                .enableAnnotationInfo()
                .overrideClasspath(cp)
                .scan()
                .getClassesWithMethodAnnotation("org.junit.Test")
                .collect { it.getSubclasses() + Collections.singletonList(it) }
                .flatten()
                .toSet()
                .collect { ClassInfo testClass -> findTestsIn(testClass) }
                .flatten()
                .toSet()
        logger.lifecycle("Found ${tests.size()} tests")
        return tests
    }

    Set<String> findTestsIn(ClassInfo testClass) {
        def hasSetup = testClass.getMethodInfo().any { it.hasAnnotation("org.junit.BeforeClass") }

        if (hasSetup) {
            return [testClass.name + ".*"].toSet()
        }
        return testClass.getMethodInfo()
                .filter { m -> m.hasAnnotation("org.junit.Test") }
                .collect { m -> testClass.name + "." + m.name }
                .toSet()
    }

    @TaskAction
    def discoverTests() {
        tests = new HashMap<>()
        project.tasks.withType(Test)
                .findAll { DistributedTestingDynamicParameters.shouldRunTest(project, it) }
                .forEach { tests.put(it, findTestsIn(it)) }
    }
}