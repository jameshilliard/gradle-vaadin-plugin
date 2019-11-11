/*
 * Copyright 2018 John Ahlroos
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.devsoap.plugin.tests

import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * Tests the extension configurations added to the project
 */
class PluginExtensionConfigurationTest extends MultiProjectIntegrationTest {

    @Tag("WidgetsetCompile")
    @Test void 'Extension configurations are applied to sub projects'() {
        makeProject('project1')
        makeProject('project2')

        int serverPort = getPort()

        buildFile << """

            project(':project1') {
                vaadinRun {
                    serverPort ${serverPort}
                }
            }

            project(':project2') {

                dependencies {
                    implementation project(':project1')
                }

                vaadinCompile {
                    widgetset 'com.example.MyWidgetset'
                }

                vaadinRun {
                    serverPort ${serverPort}
                }
            }

        """

        // Just check it compiles
        runWithArguments(':project2:vaadinCompile')
    }
}
