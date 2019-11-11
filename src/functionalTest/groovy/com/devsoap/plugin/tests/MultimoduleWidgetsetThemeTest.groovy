package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.BuildClassPathJar
import com.devsoap.plugin.tasks.CreateComponentTask
import com.devsoap.plugin.tasks.CreateProjectTask
import com.devsoap.plugin.tasks.CreateThemeTask
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Manifest

import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Created by john on 1/11/17.
 */
@Tag("WidgetsetAndThemeCompile")
class MultimoduleWidgetsetThemeTest extends MultiProjectIntegrationTest {

    @Test void 'Multimodule project with shared widgetset and theme'() {
        Path widgetsetModule = makeProject('widgetset-module')
        Path widgetsetBuildFile = makeBuildFile(widgetsetModule)
        widgetsetBuildFile << """
            vaadinCompile {
                widgetset 'com.example.MyWidgetset'
            }

            vaadinAddon {
                title 'app-widgetset'
                version '1'
            }

            // Package widgetset into jar for use by apps
            jar.dependsOn 'vaadinCompile'
            jar.from 'src/main/webapp'
        """.stripIndent()

        runWithArguments(":${widgetsetModule.fileName.toString()}:$CreateComponentTask.NAME", '--name=MyLabel')

        Path themeModule = makeProject('theme-module')
        Path themeModuleBuildFile = makeBuildFile(themeModule)
        themeModuleBuildFile << """
            vaadinAddon {
                title 'app-theme'
                version '1'
            }

            // Package theme into jar for use by apps
            jar.dependsOn 'vaadinThemeCompile'
            jar.from 'src/main/webapp'
        """.stripIndent()

        runWithArguments(":${themeModule.getFileName().toString()}:$CreateThemeTask.NAME", '--name=AppTheme')

        Path appModule = makeProject('app')
        Path appBuildFile = makeBuildFile(appModule)
        appBuildFile << """
            // Disable widgetset compilation as widgetset
            // is served from widgetset-module
            project.tasks.vaadinCompile.enabled = false
            project.tasks.vaadinUpdateWidgetset.enabled = false

            // Disable theme compilation as theme is pre-compiled
            // in another module
            project.tasks.vaadinThemeCompile.enabled = false
            project.tasks.vaadinUpdateAddonStyles.enabled = false

            dependencies {
                implementation project(':theme-module')
                implementation project(':widgetset-module')
            }
        """.stripIndent()

        runWithArguments(":app:$CreateProjectTask.NAME")

        // Remove generated theme from app
        appModule.resolve('src').resolve('main').resolve('webapp').deleteDir()

        // Generate war
        String result = runWithArguments(':app:war')
        assertTrue result.contains('BUILD SUCCESSFUL'), result
    }

    @Test void 'Multimodule project with classpath jar'() {

        buildFile = makeBuildFile(projectDir)
        buildFile << """
            vaadin.useClassPathJar = true

            dependencies {
                implementation project(':theme-module')
            }
        """.stripIndent()

        Path themeModule = makeProject('theme-module')
        Path themeModuleBuildFile = makeBuildFile(themeModule)
        themeModuleBuildFile << """
            vaadinAddon {
                title 'app-theme'
                version '1'
            }

            // Package theme into jar for use by apps
            jar.dependsOn 'vaadinThemeCompile'
            jar.from 'src/main/webapp'
        """.stripIndent()

        runWithArguments("${themeModule.getFileName().toString()}:$CreateThemeTask.NAME", '--name=AppTheme')
        runWithArguments(CreateProjectTask.NAME)
        runWithArguments(BuildClassPathJar.NAME)

        Path manifest = projectDir.resolve('build').resolve('tmp')
                .resolve('vaadinClassPathJar').resolve('MANIFEST.MF')
        assertTrue Files.exists(manifest), 'Manifest did not exist'

        Files.newInputStream(manifest).withCloseable { InputStream stream ->
            Manifest m = new Manifest(stream)
            String cp = m.mainAttributes.getValue("Class-Path")
            assertNotNull 'Attribute Class-Path not found in attributes '+m.mainAttributes, cp
            assertTrue cp.contains('theme-module-1.jar'), 'Jar theme-module-1.jar not found in Class-Path '+cp
        }

    }
}