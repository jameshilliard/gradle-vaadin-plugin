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
package com.devsoap.plugin.tasks

import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc

/**
 * Builds the Javadoc jar
 *
 * @author John Ahlroos
 * @since 1.1
 */
@CacheableTask
class BuildJavadocJarTask extends Jar {

    static final String NAME = 'vaadinJavadocJar'

    private static final String JAVADOC_TASK_NAME = 'javadoc'

    BuildJavadocJarTask() {
        description = 'Creates a javadoc jar for the project'
        archiveClassifier.set(JAVADOC_TASK_NAME)
        dependsOn JAVADOC_TASK_NAME
        project.tasks.getByName(JAVADOC_TASK_NAME) { Javadoc task ->
            from task.destinationDir
        }
    }
}
