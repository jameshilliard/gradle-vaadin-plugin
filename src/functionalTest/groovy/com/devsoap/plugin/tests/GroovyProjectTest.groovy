package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.CreateProjectTask
import com.devsoap.plugin.tasks.RunTask
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assertions.fail

/**
 * Created by john on 7/13/16.
 */
class GroovyProjectTest extends IntegrationTest {

    @Override
    protected void applyThirdPartyPlugins(Path buildFile) {
        super.applyThirdPartyPlugins(buildFile)
        buildFile << """
           plugins {
                id 'groovy'
           }

           dependencies {
                implementation localGroovy()
           }

        """.stripIndent()
    }

    @Test void 'Run Groovy Project'() {
        def output = runWithArguments(CreateProjectTask.NAME, RunTask.NAME, '--stopAfterStart')
        assertServerRunning output
    }

    @Test void 'Create Groovy Project'() {
        runWithArguments(CreateProjectTask.NAME)

        Path sourceFolder = projectDir.resolve('src').resolve('main').resolve('groovy')
        assertTrue(Files.exists(sourceFolder), 'Source folder did not exist')

        Path packageFolder = sourceFolder.resolve('com').resolve('example').resolve(projectDir.getFileName().toString())
        assertTrue(Files.exists(packageFolder), 'Package did not exist')

        assertEquals(2, Files.list(packageFolder).withCloseable { it.count() }, "There should be 2 files, found ${Files.list(packageFolder).count()}")
        Files.list(packageFolder).withCloseable {
            it.each { Path file ->
                if (!file.getFileName().toString().endsWith('.groovy')) {
                    fail "Only groovy files should exist, found ${file.getFileName().toString()}"
                }
            }
        }
    }

    private void assertServerRunning(String output) {
        assertTrue output.contains('Application running on '), output
    }

}
