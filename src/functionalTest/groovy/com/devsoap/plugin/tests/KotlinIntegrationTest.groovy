package com.devsoap.plugin.tests

import com.devsoap.plugin.extensions.VaadinPluginExtension

import java.nio.file.Files
import java.nio.file.Path

/**
 * Base class for testing building projects with Kotlin and Kotlin DSL
 */
class KotlinIntegrationTest extends IntegrationTest {

    String kotlinVersion

    KotlinIntegrationTest() { }

    void setup(String kotlinVersion) {
        this.kotlinVersion = kotlinVersion
    }

    @Override
    void setup() {
        startTime = System.currentTimeMillis()
        println "Running test in $projectDir"
        buildFile = makeBuildFile(projectDir)
        settingsFile = projectDir.resolve("settings.gradle")
        Files.createFile(settingsFile)
    }

    @Override
    protected Path makeBuildFile(Path projectDir, boolean applyPluginToFile=true) {
        Path buildFile = projectDir.resolve('build.gradle.kts')
        Files.createFile(buildFile)

        // Imports
        applyImports(buildFile)

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
            buildFile << """
                configure<VaadinPluginExtension> {
                    logToConsole = true
                }
            """.stripIndent()
        }

        buildFile
    }

    @Override
    protected void applyRepositories(Path buildFile) {
        String escapedDir = getPluginDir()
        buildFile << """
            repositories {
                flatDir {
                    dirs(file("$escapedDir"))
                } 
            }
        """.stripIndent()
    }

    @Override
    protected void applyPlugin(Path buildFile) {
       buildFile << """
        apply {
            plugin("com.devsoap.plugin.vaadin")
        }
        """.stripIndent()
    }

    @Override
    protected void applyBuildScriptRepositories(Path buildFile) {
        String escapedDir = getPluginDir()
        buildFile << "mavenLocal()\n"
        buildFile << "mavenCentral()\n"
        buildFile << """
            flatDir {
                dirs(file("$escapedDir"))
            }
         """.stripIndent()
    }

    @Override
    protected void applyThirdPartyPlugins(Path buildFile) {
        if(!buildFile || Files.notExists(buildFile)){
            throw new IllegalArgumentException("$buildFile does not exist or is null")
        }

        buildFile << """
           plugins {
                id("org.jetbrains.kotlin.jvm").version("$kotlinVersion")
           }

           dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
           }
        """.stripIndent()
    }

    @Override
    protected void applyBuildScriptClasspathDependencies(Path buildFile) {
        def projectVersion = System.getProperty('integrationTestProjectVersion')
        buildFile << """
             classpath("org.codehaus.groovy.modules.http-builder:http-builder:0.7.1")
             classpath("com.devsoap.plugin:gradle-vaadin-plugin:$projectVersion")
             classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
        """.stripIndent()
    }

    protected void applyImports(Path buildFile) {
        buildFile << """
            import $VaadinPluginExtension.canonicalName
        """.stripIndent()
    }
}
