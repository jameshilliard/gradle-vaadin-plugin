package com.devsoap.plugin.tests

import com.devsoap.plugin.categories.WidgetsetAndThemeCompile
import com.devsoap.plugin.tasks.BuildClassPathJar
import com.devsoap.plugin.tasks.CreateComponentTask
import com.devsoap.plugin.tasks.CreateProjectTask
import com.devsoap.plugin.tasks.CreateThemeTask
import org.junit.Test
import org.junit.experimental.categories.Category

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.jar.Manifest

import static org.junit.Assert.assertNotNull
import static org.junit.Assert.assertTrue

/**
 * Created by john on 1/11/17.
 */
@Category(WidgetsetAndThemeCompile)
class MultimoduleWidgetsetThemeTest extends MultiProjectIntegrationTest {

    @Test void 'Multimodule project with shared widgetset and theme'() {
        File widgetsetModule = makeProject('widgetset-module')
        File widgetsetBuildFile = makeBuildFile(widgetsetModule)
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

        runWithArguments(":$widgetsetModule.name:$CreateComponentTask.NAME", '--name=MyLabel')

        File themeModule = makeProject('theme-module')
        File themeModuleBuildFile = makeBuildFile(themeModule)
        themeModuleBuildFile << """
            vaadinAddon {
                title 'app-theme'
                version '1'
            }

            // Package theme into jar for use by apps
            jar.dependsOn 'vaadinThemeCompile'
            jar.from 'src/main/webapp'
        """.stripIndent()

        runWithArguments(":$themeModule.name:$CreateThemeTask.NAME", '--name=AppTheme')

        File appModule = makeProject('app')
        File appBuildFile = makeBuildFile(appModule)
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
        Paths.get(appModule.canonicalPath, 'src', 'main', 'webapp').deleteDir()

        // Generate war
        String result = runWithArguments(':app:war')
        assertTrue result, result.contains('BUILD SUCCESSFUL')
    }

    @Test void 'Multimodule project with classpath jar'() {

        buildFile = makeBuildFile(projectDir.root)
        buildFile << """
            vaadin.useClassPathJar = true

            dependencies {
                implementation project(':theme-module')
            }
        """.stripIndent()

        File themeModule = makeProject('theme-module')
        File themeModuleBuildFile = makeBuildFile(themeModule)
        themeModuleBuildFile << """
            vaadinAddon {
                title 'app-theme'
                version '1'
            }

            // Package theme into jar for use by apps
            jar.dependsOn 'vaadinThemeCompile'
            jar.from 'src/main/webapp'
        """.stripIndent()

        runWithArguments("$themeModule.name:$CreateThemeTask.NAME", '--name=AppTheme')
        Thread.sleep(1000)
        runWithArguments(CreateProjectTask.NAME)
        runWithArguments(BuildClassPathJar.NAME)

        Path manifest = Paths.get(projectDir.root.canonicalPath,
                'build', 'tmp', 'vaadinClassPathJar', 'MANIFEST.MF')
        assertTrue 'Manifest did not exist', Files.exists(manifest)

        InputStream stream = Files.newInputStream(manifest)
        Manifest m = new Manifest(stream)
        String cp = m.mainAttributes.getValue("Class-Path")
        assertNotNull 'Attribute Class-Path not found in attributes '+m.mainAttributes, cp
        assertTrue 'Jar theme-module-1.jar not found in Class-Path '+cp, cp.contains('theme-module-1.jar')

    }
}