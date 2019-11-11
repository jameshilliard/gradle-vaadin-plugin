/*
 * Copyright 2018 John Ahlroos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.VersionCheckTask
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Base class for tests tests
 */
class IntegrationTest {

    private static final List<String> DEFAULT_ARGS = [
            '--stacktrace',
            '--warning-mode', 'all'
    ]

    private static final String VERSION_ARG =
            "-DGradleVaadinPlugin.version=${VersionCheckTask.getLatestReleaseVersion()}"

    @TempDir
    public Path projectDir

    protected GradleRunner runnerCache = null

    protected String gradleVersion = null

    protected Path buildFile

    protected Path settingsFile

    protected long startTime

    @BeforeEach
    void setup() {
        startTime = System.currentTimeMillis()
        println "Running test in $projectDir"
        buildFile = makeBuildFile(projectDir)
        settingsFile = projectDir.resolve("settings.gradle")
        Files.createFile(settingsFile)
    }

    @AfterEach
    void tearDown() {
        println "Test took ${(System.currentTimeMillis() - startTime)/1000L} seconds."
    }

    protected static String getPluginDir() {
        Path libsDir = Paths.get('.', 'build', 'libs')
        String escapedDir = libsDir.toAbsolutePath().toString().replace("\\","\\\\")
        escapedDir
    }

    protected Path makeBuildFile(Path projectDir, boolean applyPluginToFile=true) {
        Path buildFile = projectDir.resolve('build.gradle')
        Files.deleteIfExists(buildFile)
        Files.createFile(buildFile)

        // Apply plugin to project
        buildFile << "buildscript {\n"
            buildFile << "repositories {\n"
                applyBuildScriptRepositories(buildFile)
            buildFile << "}\n"
        buildFile << "dependencies {\n"
            applyBuildScriptClasspathDependencies(buildFile)
            buildFile << "}\n"
        buildFile << "}\n"

        // Apply custom plugins{} block
        applyThirdPartyPlugins(buildFile)

        if ( applyPluginToFile ) {
            applyRepositories(buildFile)
            applyPlugin(buildFile)
            buildFile << "vaadin.logToConsole = true\n"
        }

        buildFile
    }

    protected void applyBuildScriptClasspathDependencies(Path buildFile) {
        def projectVersion = System.getProperty('integrationTestProjectVersion')
        buildFile << "classpath group: 'org.codehaus.groovy.modules.http-builder', " +
                "name: 'http-builder', version: '0.7.1'\n"
        buildFile << "classpath group: 'com.devsoap.plugin', " +
                "name: 'gradle-vaadin-plugin', version: '$projectVersion'\n"
    }

    protected void applyBuildScriptRepositories(Path buildFile) {
        String escapedDir = getPluginDir()
        buildFile << "mavenLocal()\n"
        buildFile << "mavenCentral()\n"
        buildFile << "flatDir dirs:file('$escapedDir')\n"
    }

    protected void applyThirdPartyPlugins(Path buildFile) {
        if(!buildFile || Files.notExists(buildFile)){
            throw new IllegalArgumentException("$buildFile does not exist or is null")
        }
    }

    protected void applyRepositories(Path buildFile) {
        String escapedDir = getPluginDir()
        buildFile << """
            repositories {
                flatDir dirs:file('$escapedDir')
            }
        """.stripIndent()
    }

    protected void applyPlugin(Path buildFile) {
        buildFile << "apply plugin:com.devsoap.plugin.GradleVaadinPlugin\n"
    }

    protected String runWithArguments(String... args) {
        GradleRunner runner = setupRunner(projectDir)
                .withArguments(DEFAULT_ARGS + (args as List) + [VERSION_ARG])
        ArrayList<String> printArgs = new ArrayList<String>(runner.arguments)
        printArgs.remove(VERSION_ARG)
        println "Running gradle ${printArgs.join(' ')}"
        runner.build().output
    }

    protected String runFailureExpected() {
        GradleRunner runner = setupRunner().withArguments(DEFAULT_ARGS)
        println "Running gradle ${runner.arguments.join(' ')}"
        runner.buildAndFail().output
    }

    protected String runFailureExpected(String... args) {
        setupRunner()
                .withArguments(DEFAULT_ARGS + (args as List))
                .buildAndFail()
                .output
    }

    protected GradleRunner setupRunner(Path projectDir = this.projectDir) {
        GradleRunner runner
        if (runnerCache == null) {
            runner = GradleRunner.create().withPluginClasspath().withDebug(false)
        } else {
            runner = runnerCache
        }
        if (projectDir.toFile() != runner.getProjectDir()) {
            runner.withProjectDir(projectDir.toFile())
        }
        if (gradleVersion != null) {
            runner.withGradleVersion(gradleVersion)
        }
        runnerCache = runner
        runner
    }

    protected static int getPort() {
        final ServerSocket socket = new ServerSocket(0)
        socket.setReuseAddress(false)
        int port = socket.getLocalPort()
        socket.close()
        port
    }
}