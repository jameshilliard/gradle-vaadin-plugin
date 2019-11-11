package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.UpdateWidgetsetTask
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assertions.assertFalse

/**
 * Created by john on 20.1.2016.
 */
class UpdateWidgetsetTest extends IntegrationTest {

    @Test void 'No Widgetset generated without property'() {
        runWithArguments(UpdateWidgetsetTask.NAME)
        assertFalse Files.exists(widgetsetFile)
    }

    @Test void 'No Widgetset generated when widgetset management off'() {
        buildFile << "vaadinCompile.widgetset = 'com.example.MyWidgetset'\n"
        buildFile << "vaadinCompile.manageWidgetset = false"
        runWithArguments(UpdateWidgetsetTask.NAME)
        assertFalse Files.exists(widgetsetFile)
    }

    @Test void 'Widgetset generated into resource folder'() {
        buildFile << "vaadinCompile.widgetset = 'com.example.MyWidgetset'"
        runWithArguments(UpdateWidgetsetTask.NAME)
        assertTrue Files.exists(widgetsetFile)
    }

    @Test void 'Widgetset file contains addon widgetset inherits'() {
        buildFile << "vaadinCompile.widgetset = 'com.example.MyWidgetset'\n"
        buildFile << """
            dependencies {
                implementation 'org.vaadin.addons:qrcode:+'
            }
        """

        runWithArguments(UpdateWidgetsetTask.NAME)
        assertTrue widgetsetFile.text.contains('<inherits name="fi.jasoft.qrcode.QrcodeWidgetset" />')
    }

    @Test void 'Widgetset file contains inherits from sub-project dependencies'() {
        buildFile << "vaadinCompile.widgetset = 'com.example.MyWidgetset'\n"

        // Setup project 1
        Path project1Dir = projectDir.resolve('project1')
        Files.createDirectories(project1Dir)
        Path buildFile1 = makeBuildFile(project1Dir)
        buildFile1 << """
            dependencies {
                implementation 'org.vaadin.addons:qrcode:+'
            }
        """

        // Setup project 2
        Path project2Dir = projectDir.resolve('project2')
        Files.createDirectories(project2Dir)
        Path buildFile2 = makeBuildFile(project2Dir)
        buildFile2 << "vaadinCompile.widgetset = 'com.example.MyWidgetset'\n"
        buildFile2 << """
            dependencies {
                implementation project(':project1')
            }
        """

        // Setup settings.gradle
        settingsFile << """
            include 'project1'
            include 'project2'
        """

        runWithArguments(':project2:' + UpdateWidgetsetTask.NAME)
        assertTrue getWidgetsetFile(project2Dir).text.contains('<inherits name="fi.jasoft.qrcode.QrcodeWidgetset" />')
    }

    @Test void 'AppWidgetset created when project contains addon dependencies'() {
        buildFile << """
            dependencies {
                implementation 'org.vaadin.addons:qrcode:+'
            }
        """
        runWithArguments(UpdateWidgetsetTask.NAME)
        assertTrue Files.exists(appWidgetsetFile)
        assertTrue appWidgetsetFile.text.contains('<inherits name="fi.jasoft.qrcode.QrcodeWidgetset" />')
    }

    @Test void 'AppWidgetset created when dependant project contains widgetset file'() {

        // Setup addon project
        Path project1Dir = projectDir.resolve('project1')
        Files.createDirectories(project1Dir)

        Path widgetsetFile = getWidgetsetFile(project1Dir)
        Files.createDirectories(widgetsetFile.parent)
        Files.createFile(widgetsetFile)
        widgetsetFile.text = """
            <module>
                <inherits name="com.vaadin.DefaultWidgetSet" />
            </module>
        """.stripIndent()

        Path buildFile1 = makeBuildFile(project1Dir)
        buildFile1 << "vaadinCompile.widgetset = 'com.example.MyWidgetset'\n"

        // Setup demo project
        Path project2Dir = projectDir.resolve('project2')
        Files.createDirectories(project2Dir)
        Path buildFile2 = makeBuildFile(project2Dir)
        buildFile2 << """
            dependencies {
                implementation project(':project1')
            }
        """

        // Setup settings.gradle
        settingsFile << """
            include 'project1'
            include 'project2'
        """

        runWithArguments(':project2:' + UpdateWidgetsetTask.NAME)
        assertTrue getAppWidgetsetFile(project2Dir).text.contains('<inherits name="com.example.MyWidgetset" />')
    }

    @Test void 'If legacy mode, use compatibility widgetset'() {
        buildFile << "vaadinCompile.widgetset = 'com.example.MyWidgetset'\n"
        buildFile << """
            dependencies {
                implementation("com.vaadin:vaadin-compatibility-server:8.12.2")
                implementation("com.vaadin:vaadin-compatibility-client:8.12.2")
                implementation("com.vaadin:vaadin-compatibility-shared:8.12.2")
                implementation 'org.vaadin.addons:qrcode:+'
            }
        """

        runWithArguments(UpdateWidgetsetTask.NAME)
        assertTrue widgetsetFile.text.contains('<inherits name="com.vaadin.v7.Vaadin7WidgetSet" />')
        assertFalse widgetsetFile.text.contains('<inherits name="com.vaadin.DefaultWidgetSet" />')
    }

    private Path getWidgetsetFile(Path projectDir = this.projectDir, String fileName='MyWidgetset') {
        projectDir.resolve('src').resolve('main').resolve('resources')
                .resolve('com').resolve('example').resolve("${fileName}.gwt.xml")
    }

    private Path getAppWidgetsetFile(Path projectDir = this.projectDir) {
        projectDir.resolve('src').resolve('main').resolve('resources')
                .resolve("AppWidgetset.gwt.xml")
    }
}
