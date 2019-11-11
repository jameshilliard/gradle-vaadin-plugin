package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.CreateProjectTask
import org.junit.jupiter.api.Test

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Created by john on 7/17/17.
 */
class PluginVersionCheckTest extends IntegrationTest {

    @Test void 'Version check is performed first'() {
        String output = runWithArguments(CreateProjectTask.NAME)
        List<String> lines = output.tokenize('\n')
        for (int i = 0; i < lines.size(); i++) {
            if(lines[i].trim().startsWith(":")) {
                assertEquals ":vaadinPluginVersionCheck", lines[i].trim(), output
                break
            }
        }
    }

    @Test void 'Version check disabled'() {
        String output = runWithArguments(CreateProjectTask.NAME, '-x', 'vaadinPluginVersionCheck')
        assertFalse output.contains(':vaadinPluginVersionCheck')
    }

    @Test void 'Version check is cached'() {
        String firstRunOutput = runWithArguments(CreateProjectTask.NAME)
        assertFalse firstRunOutput.contains('vaadinPluginVersionCheck SKIPPED')

        String secondRunOutput = runWithArguments(CreateProjectTask.NAME)
        assertTrue secondRunOutput.contains('vaadinPluginVersionCheck SKIPPED')
    }
}
