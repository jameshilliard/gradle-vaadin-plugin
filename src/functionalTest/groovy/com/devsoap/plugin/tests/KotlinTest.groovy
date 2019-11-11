package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.CreateDirectoryZipTask
import com.devsoap.plugin.tasks.CreateProjectTask
import com.devsoap.plugin.tasks.RunTask
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.fail
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests Kotlin project creation and usage
 */
class KotlinTest extends KotlinIntegrationTest {

    KotlinTest() { }

    static Stream<String> getKotlinVersions() {
        return Stream.of('1.4.32')
    }

    void setup(String kotlinVersion) {
        super.setup(kotlinVersion)
        super.setup()
    }

    @MethodSource("getKotlinVersions")
    @ParameterizedTest void 'Create project'(String kotlinVersion) {
        setup(kotlinVersion)

        runWithArguments(CreateProjectTask.NAME, '--name=hello-world')

        Path pkg = projectDir.resolve('src').resolve('main').resolve('kotlin')
                .resolve('com').resolve('example').resolve('helloworld')
        assertTrue Files.exists(pkg),'Package name should have been converted'
        assertTrue Files.exists(pkg.resolve('HelloWorldServlet.kt')),'Servlet should exist'
        assertTrue Files.exists(pkg.resolve('HelloWorldUI.kt')), 'UI should exist'

        runWithArguments('classes')

        Path classes = projectDir.resolve('build').resolve('classes').resolve('kotlin')
                .resolve('main').resolve('com').resolve('example').resolve('helloworld')
        assertTrue Files.exists(classes), 'Classes should exist'
        assertTrue Files.exists(classes.resolve('HelloWorldServlet.class')), 'Servlet not compiled'
        assertTrue Files.exists(classes.resolve('HelloWorldUI.class')), 'UI not compiled'
    }

    @MethodSource("getKotlinVersions")
    @ParameterizedTest void 'Run with Jetty'(String kotlinVersion) {
        setup(kotlinVersion)
        buildFile << """
           val vaadinRun : com.devsoap.plugin.tasks.RunTask by tasks
           vaadinRun.apply {
                server = "jetty"
                debugPort = ${getPort()}
                serverPort = ${getPort()}
           }
        """.stripIndent()

        def output = runWithArguments('--info', CreateProjectTask.NAME, RunTask.NAME, '--stopAfterStart')
        assertTrue output.contains("Starting jetty"), output
        assertTrue output.contains('Application running on '), output
    }

    @MethodSource("getKotlinVersions")
    @ParameterizedTest void 'No javadoc for Kotlin projects'(String kotlinVersion) {
        setup(kotlinVersion)
        runWithArguments(CreateDirectoryZipTask.NAME)

        Path libsDir = projectDir.resolve('build').resolve('libs')

        Path javadocJar = Files.walk(libsDir).find { it -> it.getFileName().toString().endsWith('-javadoc.jar')} as Path
        assertNull javadocJar, 'Javadoc was built'

        Path sourcesJar = Files.walk(libsDir).find { it -> it.getFileName().toString().endsWith('-sources.jar')} as Path
        assertTrue Files.exists(sourcesJar), 'Sources was not built'

        Path distributionDir = projectDir.resolve('build').resolve('distributions')

        Path addonZip = Files.walk(distributionDir).find { it -> Files.isRegularFile(it) } as Path
        assertTrue Files.exists(addonZip), 'Distribution zip was not built'

        ZipFile zip = new ZipFile(addonZip.toFile())
        zip.withCloseable { ZipFile it ->
            it.entries().eachWithIndex { ZipEntry entry, int i ->
                switch (i) {
                    case 0: assertEquals 'META-INF/', entry.name; break
                    case 1: assertEquals 'META-INF/MANIFEST.MF', entry.name; break
                    case 2: assertEquals 'libs/', entry.name; break
                    case 3: assertEquals "libs/${projectDir.getFileName().toString()}-sources.jar".toString(), entry.name; break
                    case 4: assertEquals "libs/${projectDir.getFileName().toString()}.jar".toString(), entry.name; break
                    default: fail("Unexpected file $entry.name")
                }
            }
        }
    }
}

