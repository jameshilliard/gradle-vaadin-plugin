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

import com.devsoap.plugin.Util
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path

/**
 * Compresses the theme styles with GZip
 *
 * @author John Ahlroos
 * @since 1.1
 */
@CacheableTask
class CompressCssTask extends DefaultTask {

    static final String NAME = 'vaadinThemeCompress'

    /**
     * Create a CSS compression task
     */
    CompressCssTask() {
        description = 'Compresses the theme with GZip'
        onlyIf = {
            CompileThemeTask themeConf = project.tasks.getByName(CompileThemeTask.NAME) as CompileThemeTask
            themeConf.compress
        }
        dependsOn(CompileThemeTask.NAME)
        project.afterEvaluate {
            Path themesDir = Util.getThemesDirectory(project)
            FileTree themes = project.fileTree(dir:themesDir.toFile(),
                    include:CompileThemeTask.STYLES_SCSS_PATTERN)
            themes.each { File themeFile ->
                Path theme = themeFile.toPath()
                Path dir = theme.parent
                inputs.file dir.resolve(CompileThemeTask.STYLES_CSS).toFile()
                outputs.file dir.resolve('styles.css.gz').toFile()
            }
        }
    }

    /**
     * Executes the Gzip compression on the remaining styles.css file. Must be executed after the theme is compiled
     */
    @TaskAction
    void run() {
        compress(project)
    }

    /**
     * Compresses the compiled CSS theme
     *
     * @param project
     *      the project
     * @param isRecompress
     *      are we re-compressing on-the-fly
     */
    static compress(Project project, boolean isRecompress=false) {
        Path themesDir = Util.getThemesDirectory(project)
        FileTree themes = project.fileTree(dir: themesDir, include: CompileThemeTask.STYLES_SCSS_PATTERN)
        themes.each { File themeFiles ->
            Path theme = themeFiles.toPath()
            Path dir = theme.parent
            Path stylesCss = dir.resolve(CompileThemeTask.STYLES_CSS)
            if (Files.exists(stylesCss)) {
                if(isRecompress) {
                    project.logger.lifecycle("Recompressing ${stylesCss.toAbsolutePath().toString()}...")
                } else {
                    project.logger.info("Compressing ${stylesCss.toAbsolutePath().toString()}...")
                }

                long start = System.currentTimeMillis()

                project.ant.gzip(src: stylesCss.toAbsolutePath().toString(), destfile: "${stylesCss.toAbsolutePath().toString()}.gz")

                long time = (System.currentTimeMillis()-start)/1000
                if ( isRecompress ) {
                    project.logger.lifecycle("Theme was recompressed in $time seconds")
                } else {
                    project.logger.info("Theme was compressed in $time seconds")
                }
            } else {
                project.logger.warn("Failed to find $theme pre-compiled styles.css file.")
            }
        }
    }
}

