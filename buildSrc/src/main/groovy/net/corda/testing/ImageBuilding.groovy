package net.corda.testing

import com.bmuschko.gradle.docker.DockerRegistryCredentials
import com.bmuschko.gradle.docker.tasks.container.DockerCreateContainer
import com.bmuschko.gradle.docker.tasks.container.DockerLogsContainer
import com.bmuschko.gradle.docker.tasks.container.DockerRemoveContainer
import com.bmuschko.gradle.docker.tasks.container.DockerStartContainer
import com.bmuschko.gradle.docker.tasks.container.DockerWaitContainer
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.DockerCommitImage
import com.bmuschko.gradle.docker.tasks.image.DockerPullImage
import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.bmuschko.gradle.docker.tasks.image.DockerRemoveImage
import com.bmuschko.gradle.docker.tasks.image.DockerTagImage
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.core.command.BuildImageResultCallback
import com.github.dockerjava.core.command.PushImageResultCallback
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

/**
 this plugin is responsible for setting up all the required docker image building tasks required for producing and pushing an
 image of the current build output to a remote container registry
 */
class ImageBuilding implements Plugin<Project> {

    public static final String registryName = "stefanotestingcr.azurecr.io/testing"
    DockerPushImage pushTask

    @Override
    void apply(Project project) {

        project.tasks.create("buildWorkerImage", BuildWorkerImage) {
            group = "Parallel Builds"
        }

        def registryCredentialsForPush = new DockerRegistryCredentials(project.getObjects())
        registryCredentialsForPush.username.set("stefanotestingcr")
        registryCredentialsForPush.password.set(System.getProperty("docker.push.password") ? System.getProperty("docker.push.password") : "")

        DockerPullImage pullTask = project.tasks.create("pullBaseImage", DockerPullImage) {
            repository = "stefanotestingcr.azurecr.io/buildbase"
            tag = "latest"
            doFirst {
                registryCredentials = registryCredentialsForPush
            }
        }

        DockerBuildImage buildDockerImageForSource = project.tasks.create('buildDockerImageForSource', DockerBuildImage) {
            dependsOn Arrays.asList(project.rootProject.getTasksByName("clean", true), pullTask)
            inputDir.set(new File("."))
            dockerFile.set(new File(new File("testing"), "Dockerfile"))
        }

        DockerCreateContainer createBuildContainer = project.tasks.create('createBuildContainer', DockerCreateContainer) {
            File baseWorkingDir = new File(System.getProperty("docker.work.dir") ? System.getProperty("docker.work.dir") : System.getProperty("java.io.tmpdir"))
            File gradleDir = new File(baseWorkingDir, "gradle")
            File mavenDir = new File(baseWorkingDir, "maven")
            doFirst {
                if (!gradleDir.exists()) {
                    gradleDir.mkdirs()
                }
                if (!mavenDir.exists()) {
                    mavenDir.mkdirs()
                }

                logger.info("Will use: ${gradleDir.absolutePath} for caching gradle artifacts")
            }

            dependsOn buildDockerImageForSource
            targetImageId buildDockerImageForSource.getImageId()
            binds = [(gradleDir.absolutePath): "/tmp/gradle", (mavenDir.absolutePath): "/home/root/.m2"]
        }

        DockerStartContainer startBuildContainer = project.tasks.create('startBuildContainer', DockerStartContainer) {
            dependsOn createBuildContainer
            targetContainerId createBuildContainer.getContainerId()
        }

        DockerLogsContainer logBuildContainer = project.tasks.create('logBuildContainer', DockerLogsContainer) {
            dependsOn startBuildContainer
            targetContainerId createBuildContainer.getContainerId()
            follow = true
        }

        DockerWaitContainer waitForBuildContainer = project.tasks.create('waitForBuildContainer', DockerWaitContainer) {
            dependsOn logBuildContainer
            targetContainerId createBuildContainer.getContainerId()
            doLast {
                if (getExitCode() != 0) {
                    throw new GradleException("Failed to build docker image, aborting build")
                }
            }
        }

        DockerCommitImage commitBuildImageResult = project.tasks.create('commitBuildImageResult', DockerCommitImage) {
            dependsOn waitForBuildContainer
            targetContainerId createBuildContainer.getContainerId()
        }


        DockerTagImage tagBuildImageResult = project.tasks.create('tagBuildImageResult', DockerTagImage) {
            dependsOn commitBuildImageResult
            imageId = commitBuildImageResult.getImageId()
            tag = System.getProperty("docker.provided.tag") ? System.getProperty("docker.provided.tag") : "${UUID.randomUUID().toString().toLowerCase().subSequence(0, 12)}"
            repository = registryName
        }

        DockerPushImage pushBuildImage = project.tasks.create('pushBuildImage', DockerPushImage) {
            dependsOn tagBuildImageResult
            doFirst {
                registryCredentials = registryCredentialsForPush
            }
            imageName = registryName
            tag = tagBuildImageResult.tag
        }
        this.pushTask = pushBuildImage


        DockerRemoveContainer deleteContainer = project.tasks.create('deleteBuildContainer', DockerRemoveContainer) {
            dependsOn pushBuildImage
            targetContainerId createBuildContainer.getContainerId()
        }
        DockerRemoveImage deleteTaggedImage = project.tasks.create('deleteTaggedImage', DockerRemoveImage) {
            dependsOn pushBuildImage
            force = true
            targetImageId commitBuildImageResult.getImageId()
        }
        DockerRemoveImage deleteBuildImage = project.tasks.create('deleteBuildImage', DockerRemoveImage) {
            dependsOn deleteContainer, deleteTaggedImage
            force = true
            targetImageId buildDockerImageForSource.getImageId()
        }
        if (System.getProperty("docker.keep.image") == null) {
            pushBuildImage.finalizedBy(deleteContainer, deleteBuildImage, deleteTaggedImage)
        }
    }
}


class BuildWorkerImage extends DefaultTask {
    @TaskAction
    void buildImage() {
        DockerClient client = DockerClientBuilder.getInstance(
                DefaultDockerClientConfig.createDefaultConfigBuilder()
                        .withRegistryUsername("stefanotestingcr")
                        .withRegistryPassword(System.getProperty("docker.push.password"))
                        .build()
        ).build()

        // TODO somehow also add gradle and maven cache to img:
        // /tmp/gradle
        // /home/root/.m2
        // BuildImageResultCallback would need to be implemented
        String imageId = client.buildImageCmd()
                .withBaseDirectory(project.rootDir)
                .withDockerfile(project.file("testing/Dockerfile"))
                .withTag(System.getProperty("docker.provided.tag", "githash"))
                .exec(new BuildImageResultCallback()).awaitImageId()

        client.pushImageCmd(imageId)
                .withTag(System.getProperty("docker.provided.tag", "githash"))
                .exec(new PushImageResultCallback()).awaitCompletion()
    }
}