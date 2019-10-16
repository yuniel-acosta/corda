# Running Tests in Parallel on K8s

This is a design document for a plugin that allows running all the tests on a k8s cluster.

## Overview

The process in the POC solution is as follows:

1. Prepare Docker container with codebase for running in parallel
    1. compiled code + tests
    1. possibly other computation results for tests
    (all tests found, the grouping of them, any supporting data, like test runtimes)
1. Upload image to internal docker image repo
1. Launch worker pods running the docker image on k8s
1. Launch the build on worker nodes
    1. Needs to know which tests to run
        1. Logic required for listing all tests,
        1. Grouping them in similar sized buckets,
        1. And allocating buckets to workers
    1. Able to resume a previously aborted run to enable
    usage of cheaper preemptable nodes on k8s
    1. Collect test results from workers
1. Produce a global view of all test results fetched from workers

## Implementation

A few gradle plugins that are written in an idiomatic way are:
- [Java plugin][java]
- [Corda Api Scanner][apiscan]

[java]: https://github.com/gradle/gradle/blob/master/subprojects/plugins/src/main/java/org/gradle/api/plugins/JavaPlugin.java
[apiscan]: https://github.com/corda/corda-gradle-plugins/tree/master/api-scanner

Based on these, some general rules we should follow

1. Define extension objects with default values, so that defaults can be overridden.
1. Use extension object content for task configuration when creating them.
1. Make sure to register lazily initialised tasks instead of actual instantiated ones
1. Make sure not to force task evaluation of other tasks unless absolutely necessary
(when listing other tasks and changing them)
1. Try to extract logic from tasks into standalone objects, this lends better testability.

### Main Gradle Tasks

Tasks should be defined for the following things:

- Preparation for parallel building
- Docker image building
- Running build in parallel (coordinator side, uses k8s)
- Running a chunk of work (worker side)

With these four tasks it becomes simple to run the parallel build locally
- Either run all the workers via k8s
- Or just a specific instance of a worker

### Docker Image Creation

1. The build agent can perform the preparation work
    1. Compilation
    1. Grouping tests (can be written in files `test-group-1`, `test-group-2`, ..., `test-group-N`)
1. Creating docker image
    1. Packages prep work artifacts with code
    1. Currently one single docker image is created
    1. Each worker however could have its own dedicated image.
    This can be still cheap to upload/download if the docker images are appropriately layered.
    This could maybe simplify the pod running logic that we have in the POC solution.

#### Artifact Caching

It is beneficial for the build agent to have a local cache for non-snapshot artifacts.
These artifacts will always be the same so there is no point downloading them each time we build.
This speeds up the preparation phase.
This artifact cache should be packaged in the docker image so that builds on the worker also benefit from it.
As far as I know this is implemented.

An artifact cache cleanup policy needs to be configured to avoid excessive usage of storage.
If the build agent is not dedicated for running this specific build, its artifact cache
can contain artifacts that are not needed for running the parallel builds, increasing the size
of the docker image unnecessarily.

### 