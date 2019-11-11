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

import com.devsoap.plugin.GradleVaadinPlugin
import com.devsoap.plugin.Util
import groovy.transform.Memoized
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.util.VersionNumber

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * Checks the plugin version for a new version
 *
 *  @author John Ahlroos
 *  @since 1.2
 */
class VersionCheckTask extends DefaultTask {

    static final String NAME = "vaadinPluginVersionCheck"

    private static final String URL = "https://plugins.gradle.org/m2/com/devsoap/plugin/gradle-vaadin-plugin/maven-metadata.xml"

    private Path versionCacheFile

    VersionCheckTask() {
        project.afterEvaluate {
            versionCacheFile = project.buildDir.toPath().resolve('.vaadin-gradle-plugin-version.check')
            boolean firstRun = false
            if(!Files.exists(versionCacheFile)) {
                Files.createDirectories(versionCacheFile.parent)
                Files.createFile(versionCacheFile)
                firstRun = true
            }

            Duration cacheAge = Duration.between(Instant.now(), Files.getLastModifiedTime(versionCacheFile).toInstant())
            Duration cacheTime = Duration.ofDays(1)
            outputs.upToDateWhen { !firstRun && cacheTime > cacheAge }
            onlyIf { firstRun || cacheAge > cacheTime }
        }
    }

    /**
     * Checks for a new version
     */
    @TaskAction
    void run() {
        String overrideVersion = System.getProperty("GradleVaadinPlugin.version")
        VersionNumber pluginVersion
        if (overrideVersion != null) {
            pluginVersion = VersionNumber.parse(overrideVersion)
        } else {
            pluginVersion = VersionNumber.parse(GradleVaadinPlugin.version)
        }
        if(latestReleaseVersion > pluginVersion){
            project.logger.warn "!! A newer version of the Gradle Vaadin plugin is available, " +
                    "please upgrade to $latestReleaseVersion !!"
        }
        versionCacheFile.text = latestReleaseVersion.toString()
    }

    /**
     * Get the version cache file where previous version checks have been stored
     */
    @OutputFile
    Path getVersionCacheFile() {
        versionCacheFile
    }

    /**
     * Set the version cache file where previous version checks have been stored
     *
     * @param versionCacheFile
     *      the version cache file
     */
    void setVersionCacheFile(Path versionCacheFile) {
        this.versionCacheFile = versionCacheFile
    }

    /**
     * Gets the latest released Gradle plugin version
     *
     * @return
     *      the latest released version number
     */
    @Memoized
    static VersionNumber getLatestReleaseVersion() {
        VersionNumber version = VersionNumber.UNKNOWN
        try {
            HTTPBuilder http = Util.configureHttpBuilder(new HTTPBuilder(URL))
            def html = http.get(contentType : ContentType.XML)
            def matcher = html.versioning.release
            if (!matcher.isEmpty()) {
                version = VersionNumber.parse(matcher.text())
            }
        } catch (IOException | URISyntaxException e){
            version = VersionNumber.UNKNOWN
        }
        version
    }
}
