package com.devsoap.plugin.tests


import com.devsoap.plugin.tasks.CompileWidgetsetTask
import com.devsoap.plugin.tasks.CreateComponentTask
import com.devsoap.plugin.tasks.CreateProjectTask
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Created by john on 3/17/16.
 */
class CompileWidgetsetTest extends IntegrationTest {

    @Test void 'No widgetset, no compile'() {
        def result = runWithArguments(CreateProjectTask.NAME, CompileWidgetsetTask.NAME, '--info')
        assertFalse result.contains('Detected widgetset'), result
        assertFalse result.contains('Compiling module'), result
    }

    @Tag("WidgetsetCompile")
    @Test void 'No widgetset defined, automatic widgetset detected and compiled'() {
        runWithArguments(CreateProjectTask.NAME, '--widgetset=com.example.MyWidgetset')
        def result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertTrue result.contains('Detected widgetset com.example.MyWidgetset'), result
        assertTrue result.contains('Compiling module com.example.MyWidgetset'), result
        assertTrue result.contains('Linking succeeded'), result
    }

    @Tag("WidgetsetCompile")
    @Test void 'Widgetset defined, manual widgetset detected and compiled'() {
        buildFile << """
            vaadinCompile.widgetset = 'com.example.MyWidgetset'
        """
        runWithArguments(CreateProjectTask.NAME)

        String result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertCompilationSucceeded(result)
    }

    @Tag("WidgetsetCompile")
    @Test void 'No Widgetset defined, but addons exist in project'() {
        buildFile << """
            dependencies {
                implementation 'org.vaadin.addons:qrcode:+'
            }
        """

        runWithArguments(CreateProjectTask.NAME)

        def widgetsetName = 'AppWidgetset'
        def result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertTrue result.contains("Compiling module $widgetsetName"), result
        assertTrue result.contains('Linking succeeded'), result

        Path widgetsetFile = projectDir.resolve('src').resolve('main')
                .resolve('resources').resolve('AppWidgetset.gwt.xml')
        assertTrue Files.exists(widgetsetFile), "Widgetset file $widgetsetFile did not exist"
    }

    @Test void 'Compile with Vaadin CDN'() {
        buildFile << """
            dependencies {
                implementation 'org.vaadin.addons:qrcode:+'
            }

            vaadinCompile {
                widgetsetCDN true
            }
        """

        runWithArguments(CreateProjectTask.NAME)

        String result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertTrue result.contains('Querying widgetset for'), result
        assertTrue result.contains('Widgetset is available, downloading...'), result
        assertTrue result.contains('Extracting widgetset'), result
        assertTrue result.contains('Generating AppWidgetset'), result

        Path appWidgetset = projectDir.resolve('src').resolve('main')
                .resolve('java').resolve('AppWidgetset.java')
        assertTrue Files.exists(appWidgetset), 'AppWidgetset.java was not created'

        Path widgetsetFolder = projectDir.resolve('src').resolve('main')
                .resolve('webapp').resolve('VAADIN').resolve('widgetsets')
        assertTrue Files.exists(widgetsetFolder), 'Widgetsets folder did not exist'
        assertTrue Files.list(widgetsetFolder).withCloseable { it.count() } == 1,
                'Widgetsets folder did not contain widgetset'

    }

    @Tag("WidgetsetCompile")
    @Test void 'Compile with legacy dependencies'(){
        buildFile << """
            dependencies {
                implementation("com.vaadin:vaadin-compatibility-server:8.12.2")
                implementation("com.vaadin:vaadin-compatibility-client:8.12.2")
                implementation("com.vaadin:vaadin-compatibility-shared:8.12.2")
            }
            vaadinCompile.widgetset = 'com.example.MyWidgetset'
        """

        runWithArguments(CreateProjectTask.NAME)

        String result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertCompilationSucceeded(result)
    }

    @Tag("WidgetsetCompile")
    @Test void 'Compile with legacy dependencies and classpath jar'(){
        buildFile << """
            dependencies {
                implementation("com.vaadin:vaadin-compatibility-server:8.12.2")
                implementation("com.vaadin:vaadin-compatibility-client:8.12.2")
                implementation("com.vaadin:vaadin-compatibility-shared:8.12.2")
            }
            vaadinCompile.widgetset = 'com.example.MyWidgetset'
            vaadin.useClassPathJar = true

        """

        runWithArguments(CreateProjectTask.NAME)

        String result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertCompilationSucceeded(result)
    }

    @Tag("WidgetsetCompile")
    @Test void 'Compile with upgraded validation-jar'() {
        buildFile << """
            dependencies {
                implementation 'javax.validation:validation-api:1.0.0.GA'              
            }
            vaadinCompile.widgetset = 'com.example.MyWidgetset'
        """

        runWithArguments(CreateProjectTask.NAME)

        String result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertCompilationSucceeded(result)
    }

    @Tag("WidgetsetCompile")
    @Test void 'Compile with client sources'() {
        buildFile << """            
            vaadinCompile.widgetset = 'com.example.MyWidgetset'
        """

        runWithArguments(CreateProjectTask.NAME)

        runWithArguments(CreateComponentTask.NAME, '--name=MyLabel')

        String result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertCompilationSucceeded(result)
    }

    @Tag("WidgetsetCompile")
    @Test void 'Compile with client sources and classpath jar'() {
        buildFile << """            
            vaadinCompile.widgetset = 'com.example.MyWidgetset'
            vaadin.useClassPathJar = true
        """

        runWithArguments(CreateProjectTask.NAME)

        runWithArguments(CreateComponentTask.NAME, '--name=MyLabel')

        String result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertCompilationSucceeded(result)
    }

    @Tag("WidgetsetCompile")
    @Test void 'Compile with third-party non-vaadin addon dependency'() {
        buildFile << """
            vaadin.version = "8.12.2"
            dependencies {
                vaadinCompile "org.vaadin.addon:v-leaflet:3.0.0"
            }
            vaadinCompile.widgetset = 'com.example.MyWidgetset'
        """

        runWithArguments(CreateProjectTask.NAME)

        String result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertCompilationSucceeded(result)
    }

    private static void assertCompilationSucceeded(String result) {
        assertFalse result.contains('Detected widgetset com.example.MyWidgetset'), result
        assertTrue result.contains('Compiling module com.example.MyWidgetset'), result
        assertTrue result.contains('Linking succeeded'), result
    }
}
