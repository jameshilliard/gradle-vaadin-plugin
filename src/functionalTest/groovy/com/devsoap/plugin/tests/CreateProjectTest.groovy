package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.CreateProjectTask
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Created by john on 4/5/17.
 */
class CreateProjectTest extends IntegrationTest {

    @Test void 'Create Project with special name'() {

        // Create
        runWithArguments(CreateProjectTask.NAME, '--name=hello-world')

        // Ensure it compiles
        runWithArguments('classes')

        Path pkg = projectDir.resolve('src').resolve('main').resolve('java')
                .resolve('com').resolve('example').resolve('helloworld')
        assertTrue Files.exists(pkg), 'Package name should have been converted'
        assertTrue Files.exists(pkg.resolve('HelloWorldServlet.java')), 'Servlet should exist'
        assertTrue Files.exists(pkg.resolve('HelloWorldUI.java')), 'UI should exist'
    }
}
