package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.BuildJavadocJarTask
import com.devsoap.plugin.tasks.CreateDirectoryZipTask
import com.devsoap.plugin.tasks.CreateProjectTask
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assertions.fail

/**
 * Created by john on 1/7/17.
 */
class JavadocTest extends IntegrationTest {

    @Test void 'Build Javadoc jar'() {
        runWithArguments(CreateProjectTask.NAME)

        String result = runWithArguments(BuildJavadocJarTask.NAME)
        assertFalse result.contains('warnings'), result

        Path libsDir = projectDir.resolve('build').resolve('libs')
        Path javadocJar = Files.walk(libsDir).find { it -> it.getFileName().toString().endsWith('-javadoc.jar')} as Path
        assertTrue Files.exists(javadocJar), 'Javadoc jar was missing'
        assertTrue javadocJar.getFileName().toString().endsWith('-javadoc.jar'), "$javadocJar was not a javadoc jar"
    }

    @Test void 'Build Javadoc jar with client dependencies'() {
        buildFile << 'vaadinCompile.widgetset = "com.example.MyWidgetset"\n'
        buildFile << 'vaadinCompile.widgetsetGenerator = "com.example.MyWidgetsetGenerator"\n'

        runWithArguments(CreateProjectTask.NAME)

        String result = runWithArguments(BuildJavadocJarTask.NAME)
        assertFalse result.contains('warnings'), result

        Path libsDir = projectDir.resolve('build').resolve('libs')
        Path javadocJar = Files.walk(libsDir).find { it -> it.getFileName().toString().endsWith('-javadoc.jar')} as Path
        assertTrue Files.exists(javadocJar), 'Javadoc jar was missing'
        assertTrue javadocJar.getFileName().toString().endsWith('-javadoc.jar'), "$javadocJar was not a javadoc jar"
    }

    @Test void 'Build directory zip with javadoc and sources'() {
        runWithArguments(CreateProjectTask.NAME)

        runWithArguments(CreateDirectoryZipTask.NAME)

        Path libsDir = projectDir.resolve('build').resolve('libs')

        Path javadocJar = Files.walk(libsDir).find { it -> it.getFileName().toString().endsWith('-javadoc.jar')} as Path
        assertTrue Files.exists(javadocJar), 'Javadoc was not built'

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
                    case 3: assertEquals "libs/${projectDir.getFileName().toString()}-javadoc.jar".toString(), entry.name; break
                    case 4: assertEquals "libs/${projectDir.getFileName().toString()}-sources.jar".toString(), entry.name; break
                    case 5: assertEquals "libs/${projectDir.getFileName().toString()}.jar".toString(), entry.name; break
                    case 6: assertEquals 'javadoc/', entry.name; break
                    default:
                        if (!entry.name.startsWith('javadoc/')) {
                            fail("Unexpected file $entry.name")
                        }
                }
            }
        }
    }
}
