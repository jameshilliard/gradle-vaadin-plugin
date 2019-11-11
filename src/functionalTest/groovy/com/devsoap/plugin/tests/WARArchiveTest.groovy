package com.devsoap.plugin.tests

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests the creation the WAR archive
 */
class WARArchiveTest extends IntegrationTest {

    @Override
    @BeforeEach
    void setup() {
        super.setup()
        buildFile << "vaadin.version = '8.12.2'\n"
    }

    @Test void 'WAR task action is run'() {
        String output = runWithArguments('--info', 'build')
        assertTrue output.contains('Applying JavaPluginAction')
        assertTrue output.contains('Applying WarPluginAction')
    }

    @Tag("WidgetsetAndThemeCompile")
    @Test void 'Project with no dependencies'() {

        runWithArguments('war')

        // Files in WEB-INF/lib
        def final FILES_IN_WEBINF_LIB = [
                'vaadin-server-8.12.2.jar',
                'vaadin-themes-8.12.2.jar',
                'vaadin-sass-compiler-0.9.13.jar',
                'vaadin-shared-8.12.2.jar',
                'jsoup-1.11.2.jar',
                'sac-1.3.jar',
                'flute-1.3.0.gg2.jar',
                'vaadin-client-compiled-8.12.2.jar',
                'gentyref-1.2.0.vaadin1.jar'
        ]

        warFile.withCloseable { ZipFile it ->
            assertFilesInFolder(it, FILES_IN_WEBINF_LIB, 'WEB-INF/lib')
        }
    }

    @Tag("WidgetsetAndThemeCompile")
    @Test void 'Project with widgetset'() {
        buildFile << "vaadinCompile.widgetset = 'com.example.TestWidgetset'\n"

        runWithArguments('war')

        // Files in WEB-INF/lib
        def final FILES_IN_WEBINF_LIB = [
                'vaadin-server-8.12.2.jar',
                'vaadin-themes-8.12.2.jar',
                'vaadin-sass-compiler-0.9.13.jar',
                'vaadin-shared-8.12.2.jar',
                'jsoup-1.11.2.jar',
                'sac-1.3.jar',
                'flute-1.3.0.gg2.jar',
                'gentyref-1.2.0.vaadin1.jar'
        ]

        warFile.withCloseable { ZipFile it ->
            assertFilesInFolder(it, FILES_IN_WEBINF_LIB, 'WEB-INF/lib')
        }
    }

    @Tag("WidgetsetAndThemeCompile")
    @Test void 'Project theme is included in archive'() {

        runWithArguments('vaadinCreateProject')
        runWithArguments('war')

        def final THEME_FILES = [
            'styles.scss',
            'styles.css',
            'styles.css.gz',
            "${projectDir.fileName.toString()}.scss".toString(),
            'addons.scss',
            'favicon.ico'
        ]

        warFile.withCloseable { ZipFile it ->
            assertFilesInFolder(it, THEME_FILES, "VAADIN/themes/${projectDir.fileName.toString().capitalize()}".toString())
        }
    }

    @Tag("WidgetsetAndThemeCompile")
    @Test void 'Project widgetset is included in archive'() {

        runWithArguments('vaadinCreateProject', '--widgetset=com.example.Widgetset')
        runWithArguments('war')

        def final WIDGETSET_FILES = [
                'com.example.Widgetset.nocache.js.gz',
                'com.example.Widgetset.nocache.js'
        ]

        warFile.withCloseable { ZipFile it ->
            assertFilesInFolder(it, WIDGETSET_FILES, 'VAADIN/widgetsets/com.example.Widgetset', true)
        }
    }

    @Test void 'Provided and runtime dependencies not included'() {
        buildFile << """
        dependencies {
            runtimeOnly 'commons-lang:commons-lang:2.6'
            compileOnly 'commons-lang:commons-lang:2.6'
            providedCompile 'commons-lang:commons-lang:2.6'
        }
        """.stripIndent()

        // Adding provided and runtime dependencies should result in the same WAR as when
        // none of those dependencies are added
        'Project with no dependencies'()
    }

    @Tag("WidgetsetAndThemeCompile")
    @Test void 'Vaadin addons in vaadinCompile are added to war'() {
        buildFile << """
        dependencies {
            vaadinCompile 'commons-lang:commons-lang:2.6'
        }
        """.stripIndent()

        runWithArguments('war')

        warFile.withCloseable { ZipFile it ->
            assertFilesInFolder(it, ["commons-lang-2.6.jar"], 'WEB-INF/lib', true)
        }
    }

    private static List<ZipEntry> getFilesInFolder(ZipFile archive, String folder) {
        archive.entries().findAll { ZipEntry entry ->
            !entry.directory && entry.name.startsWith(folder)
        }
    }

    private ZipFile getWarFile() {
        Path libsDir = projectDir.resolve('build').resolve('libs')
        new ZipFile(libsDir.resolve(projectDir.getFileName().toString() + '.war').toFile())
    }

    private static void assertFilesInFolder(ZipFile archive, List<String> files, String folder,
                                            boolean ignoreExtraFiles = false) {
        List<ZipEntry> webInfLib = getFilesInFolder(archive, folder)

        // Check for extra files
        if ( !ignoreExtraFiles ) {
            webInfLib.each { ZipEntry entry ->
                assertTrue(
                        files.contains(entry.name - (folder + '/')),
                        "Archive contained extra file $entry.name")
            }
        }

        // Check for missing files
        files.each { String fileName ->
            ZipEntry file = webInfLib.find { ZipEntry entry ->
                entry.name == "$folder/$fileName".toString()
            }
            Assertions.assertNotNull(file, "File $folder/$fileName was missing from archive")
        }
    }
}
