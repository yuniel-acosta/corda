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
1. Gradle task listing (`./gradlew tasks`) ignores tasks with no task group specified.
The main tasks we want the user to invoke should therefore be put in a group.
1. Usage of gradle properties (`-P`) to pass in parameters to the build is considered
better practice than the usage of system properties (`-D`)

### Main Gradle Tasks

Tasks should be defined for the following things:

- Preparation for parallel building
- Docker image creation
- Running build in parallel (coordinator side, uses k8s)
- Running a chunk of work (worker side)

With these four tasks it becomes simple to run the parallel build locally
- Either run all the workers via k8s
- Or just a specific instance of a worker

### Preparation for Parallel Building

The build agent can perform the preparation work

1. Compilation
1. Grouping tests (can be written in files `test-group-1`, `test-group-2`, ..., `test-group-N`)
1. Or instead of grouping, fetching the necessary data for grouping (like test runtimes)

### Docker Image Creation

Depends on the preparation task.

1. Packages prep work artifacts with code
1. Currently one single docker image is created
1. Each worker however could have its own dedicated image.
This can be still cheap to upload/download if the docker images are appropriately layered.
This could maybe simplify the pod running logic that we have in the POC solution.

#### Artifact Caching

It is beneficial for the build agent to have a local cache for non-snapshot artifacts.
These artifacts will always be the same so there is no point downloading them each time we build.
This speeds up the preparation phase.
This artifact cache should be packaged in the docker image so that builds on the worker also
benefit from it.
As far as I know this is implemented.

An artifact cache cleanup policy needs to be configured to avoid excessive usage of storage.
If the build agent is not dedicated for running this specific build, its artifact cache
can contain artifacts that are not needed for running the parallel builds, increasing the size
of the docker image unnecessarily.

#### Implementation

The POC implementation has a slight difference, in that the preparation work
is done inside the docker image, then the state of the image is saved after the work is done.
The reason/advantage of this is unknown to me at the moment.
If this was not done, the docker image creation process could be composed out of fewer steps.

I think it would be better to define a separate task for preparation.
This task can make sure everything is in place for the docker image creation.

The docker image creator task would depend on the preparation task.

Docker image creation in the POC is currently implemented in the ImageBuilding plugin.
It defines a sequence of tasks chained together via dependencies, using gradle tasks written
in the [docker gradle plugin][dgp].
This plugin is meant to simplify the process of docker image creation from java projects
that are typically long lived services that need to run on a server somewhere in docker.

What we are doing here on the contrary is we are running the build itself in a docker container.
The benefits of simplified docker image creation therefore is not leveraged as we are
building an atypical docker image.
We are using low level building blocks of the plugin directly (the gradle tasks it ships with).
I think it is worth evaluating the usage of [docker api client][dapi] directly.
The gradle plugin uses this library in the tasks it provides, the tasks are merely wrappers
around direct gradle api calls.

[dgp]: https://bmuschko.github.io/gradle-docker-plugin/
[dapi]: https://github.com/docker-java/docker-java

It could be the case that the docker plugin has some advantage over the api client.
For example it might set sensible defaults that the client might not by itself do.
This needs to be looked at still.

### Test Grouping

_TLDR: POC approach is conceptually a good one, but in case it becomes an obstacle of
doing something else we might like to do, there are alternatives._

In the POC the test grouping (or bucketing) is performed dynamically on the worker nodes
in such a way that any single test is guaranteed to be assigned to a single worker.

We want to be able to run any type of tests in parallel (tests, integration tests,
slow integration tests, etc) in any combination, on any number of worker nodes
without having to generate a new expensive docker image for the test run.

#### As Preparation Artifacts
 
Performing grouping in the preparation phase would mean that the docker image generated
might not contain the most optimal distribution of tests to workers.
This is because the test distribution would be static, but the types of tests to run
and the number of workers to use is dynamically provided by the user.
If the user decided to only run tests for the `test` source set, the test grouping logic
can either

- only consider the tests in the `test` source set (and ignore integration tests, etc)
- or always consider all tests and perform grouping for them.

In the former case, the resulting docker image cannot be used to run tests in other source
sets, as there is no grouping defined for them.
In the latter case, the resulting test grouping might be suboptimal, as tests need to be 
dynamically excluded from running, that could result in unbalanced distribution of work.

Similarly, if the number of workers that the grouping was performed for is different
than the number of workers the user decides to run the tests on has the obvious consequence
of uneven workload or missing some tests.

One possible workaround is to layer the docker image in such a way that test distribution
is a thin layer on its own, applied on top of the other, more expensive layers.
This allows the user to dynamically supply the test source sets to run
along with the number of workers to use and the docker image creation to be still efficient
(as only the test distribution layer) would need to be uploaded.

#### By Master Node

When the master performs it, it somehow needs to communicate to the worker
which tests it needs to run.
This cannot be a command line parameter, as it has a length limitation that an
explicit list of tests can easily exceed.
A feasible way would be a file dynamically created by the master after worker launch.
This approach has an advantage of the test distribution logic has more freedom,
does not have to be deterministic.
In case this becomes a requirement, this approach is a viable one.

#### By Workers

When the workers perform test distribution logic, it needs to be deterministic to
guarantee that each test is only executed by a single worker.
Usage of pseudo randomness with a shared seed is deterministic.
This possibly makes running a worker on a local developer machine simpler.
