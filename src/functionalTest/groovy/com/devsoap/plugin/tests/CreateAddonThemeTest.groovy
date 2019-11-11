package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.CreateAddonThemeTask
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path

/**
 * Created by john on 19.1.2016.
 */
class CreateAddonThemeTest extends IntegrationTest {

    @Test void 'Create default theme'() {
        runWithArguments(CreateAddonThemeTask.NAME)
        assertThemeInDirectory('MyAddonTheme')
    }

    @Test void 'Create theme with name'() {
        runWithArguments(CreateAddonThemeTask.NAME, '--name=TestingTheme')
        assertThemeInDirectory('TestingTheme')
    }

    private void assertThemeInDirectory(String themeName) {

        Path addonsDir = projectDir.resolve('src').resolve('main')
                .resolve('resources').resolve('VAADIN').resolve('addons')
        Assertions.assertTrue Files.exists(addonsDir)

        Path themeDir = addonsDir.resolve(themeName)
        Assertions.assertTrue Files.exists(themeDir)

        Path theme = themeDir.resolve(themeName+".scss")
        Assertions.assertTrue Files.exists(theme)
    }
}
