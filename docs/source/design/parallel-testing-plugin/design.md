# Running Tests in Parallel on K8s

This is a design document for a plugin that allows running all the tests on a k8s cluster.

## Overview

The process is as follows:

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

