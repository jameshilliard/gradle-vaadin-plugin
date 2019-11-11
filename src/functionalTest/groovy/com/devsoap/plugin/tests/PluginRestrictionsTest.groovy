package com.devsoap.plugin.tests

import static org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Created by john on 1/6/15.
 */
class PluginRestrictionsTest extends IntegrationTest {

    @Test void 'No Vaadin 6 support'() {
        buildFile << """
            vaadin {
                version '6.8.0'
            }
        """.stripMargin()

        assertTrue runFailureExpected().contains('Plugin no longer supports Vaadin 6')
    }
}
