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

import com.devsoap.plugin.TemplateUtil
import com.devsoap.plugin.Util
import groovy.transform.PackageScope
import groovy.util.logging.Log
import org.apache.commons.lang.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarInputStream
import java.util.jar.Manifest

/**
 * Updates the GWT module XML file with correct imports
 *
 * @author John Ahlroos
 * @since 1.0
 */
@Log
class UpdateWidgetsetTask extends DefaultTask {

    static final String NAME = 'vaadinUpdateWidgetset'

    private static final String PUBLIC_FOLDER = 'public'
    private static final String CSS_FILE_POSTFIX = 'css'
    private static final String SCSS_FILE_POSTFIX = 'scss'
    private static final String GWT_MODULE_XML_POSTFIX = '.gwt.xml'
    private static final String DEFAULT_WIDGETSET = 'com.vaadin.DefaultWidgetSet'
    private static final String DEFAULT_LEGACY_V6_WIDGETSET = 'com.vaadin.terminal.gwt.DefaultWidgetSet'
    private static final String DEFAULT_LEGACY_V7_WIDGETSET = 'com.vaadin.v7.Vaadin7WidgetSet'
    private static final String DOT = '.'
    private static final String JAVA_FILE_POSTFIX = ".java"

    UpdateWidgetsetTask() {
        description = 'Updates the widgetset xml file'
        onlyIf { Task task ->
            CompileWidgetsetTask compileTask = project.tasks.getByName(CompileWidgetsetTask.NAME) as CompileWidgetsetTask
            compileTask.manageWidgetset && !compileTask.widgetsetCDN && Util.getWidgetset(task.project)
        }
    }

    /**
     * Updates the widgetset XML file
     */
    @TaskAction
    void run() {
       ensureWidgetPresent(project)
    }

    /**
     * Ensures that the widgetset XML file is present in the project. If it is not, then a new one is generated
     *
     * @param project
     *      the project to check
     * @param widgetsetFQN
     *      the fully qualified name of the widgetset
     * @return
     *      the widgetset file
     */
    @PackageScope
    static Path ensureWidgetPresent(Project project, String widgetsetFQN=Util.getWidgetset(project)) {
        CompileWidgetsetTask compileWidgetsetTask = project.tasks.getByName(CompileWidgetsetTask.NAME) as CompileWidgetsetTask
        if (!compileWidgetsetTask.manageWidgetset || compileWidgetsetTask.widgetsetCDN || !widgetsetFQN) {
            return null
        }

        Path widgetsetFile = Util.resolveWidgetsetFile(project)

        if ( !widgetsetFile ) {
            // No widgetset file detected, create one
            Path resourceDir = project.sourceSets.main.resources.srcDirs.first().toPath()
            widgetsetFile = resourceDir.resolve(
                    TemplateUtil.convertFQNToFilePath(widgetsetFQN, GWT_MODULE_XML_POSTFIX))
            Files.createDirectories(widgetsetFile.parent)
            Files.createFile(widgetsetFile)
        }

        updateWidgetset(widgetsetFile, widgetsetFQN, project)
        widgetsetFile
    }

    private static updateWidgetset(Path widgetsetFile, String widgetsetFQN, Project project) {
        CompileWidgetsetTask compileWidgetsetTask = project.tasks.getByName(CompileWidgetsetTask.NAME) as CompileWidgetsetTask

        Map substitutions = [:]
        substitutions['inherits'] = getInherits(project)
        substitutions['sourcePaths'] = compileWidgetsetTask.sourcePaths
        substitutions['configurationProperties'] = getConfigurationProperties()
        substitutions['properties'] = getGWTProperties(project)
        substitutions['linkers'] = getLinkers(project)
        substitutions['stylesheets'] = getClientStylesheets(project)
        substitutions['collapsePermutations'] = compileWidgetsetTask.collapsePermutations

        String widgetsetGenerator = getWidgetsetGenerator(project, widgetsetFQN)
        if ( widgetsetGenerator ) {
            substitutions['widgetsetGenerator'] = widgetsetGenerator
        }

        // Write widgetset file
        TemplateUtil.writeTemplate('Widgetset.xml', widgetsetFile.parent, widgetsetFile.getFileName().toString(), substitutions, true)
    }

    /**
     * Scans the child projects of the given project for GWT inherits
     *
     * @param project
     *      The root project to scan, inherits from this project will *NOT* be included
     * @param scannedProjects
     *      the scanned projects, includes the root project
     * @return
     *      the found inherit statements
     */
    @PackageScope
    static Set<String> findInheritsInDependencies(Project project, List<Project> scannedProjects = []) {
        Set<String> inherits = []

        // Scan child projects for their source inherits
        if ( scannedProjects.size() > 0 ) {
            inherits.addAll(findInheritsInProject(project))
        }

        scannedProjects << project

        // Scan child projects for their addon inherits
        def attribute = new Attributes.Name('Vaadin-Widgetsets')
        project.configurations.all.each { Configuration conf ->
            conf.allDependencies.each { Dependency dependency ->
                if ( dependency in ProjectDependency ) {
                    Project dependentProject = ((ProjectDependency) dependency).dependencyProject
                    if ( !(dependentProject in scannedProjects) ) {
                        inherits.addAll(findInheritsInDependencies(dependentProject, scannedProjects))
                    }
                } else if (Util.isResolvable(project, conf) && !Util.isDeprecated(conf)) {
                    conf.files(dependency).each { File file ->
                        if ( file.file && file.name.endsWith('.jar') ) {
                            file.withInputStream { InputStream stream ->
                                new JarInputStream(stream).withCloseable { JarInputStream jarStream ->
                                    Manifest mf = jarStream.getManifest()
                                    Attributes attributes = mf?.mainAttributes
                                    String widgetsetsValue = attributes?.getValue(attribute)
                                    if (widgetsetsValue && !dependency.name.startsWith('vaadin-client')) {
                                        List<String> widgetsets = widgetsetsValue?.split(',')?.collect { it.trim() }
                                        widgetsets?.each { String widgetset ->
                                            if ( widgetset != DEFAULT_WIDGETSET &&
                                                    widgetset != DEFAULT_LEGACY_V6_WIDGETSET &&
                                                    widgetset != DEFAULT_LEGACY_V7_WIDGETSET) {
                                                inherits.add(widgetset)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        inherits
    }

    /**
     * Scans the project sources sets and searches for GWT module XML files and converts their paths
     * into inherit statements
     *
     * @param project
     *  the project to scan
     * @return
     *      a list of inherit statements
     */
    private static Set<String> findInheritsInProject(Project project) {
        if ( !project.hasProperty('vaadin') ) {
            return []
        }

        Set<String> inherits = []

        def scan = { File srcDirFile ->
            Path srcDir = srcDirFile.toPath()
            if ( Files.exists(srcDir) ) {
                project.fileTree(srcDir.toAbsolutePath().toFile())
                        .include("**/*/*$GWT_MODULE_XML_POSTFIX")
                        .each { File srcFile ->
                    Path file = srcFile.toPath()
                    if ( Files.exists(file) && Files.isRegularFile(file) ) {
                        String path = file.relativize(srcDir).toString()
                        String widgetset = TemplateUtil.convertFilePathToFQN(path, GWT_MODULE_XML_POSTFIX)
                        inherits.add(widgetset)
                    }
                }
            }
        }

        Util.getMainSourceSet(project).srcDirs.each(scan)

        project.sourceSets.main.resources.srcDirs.each(scan)

        inherits
    }

    private static Map<String, Object> getGWTProperties(Project project) {
        Map<String, Object> properties = [:]

        CompileWidgetsetTask compileWidgetsetTask = project.tasks.getByName(CompileWidgetsetTask.NAME) as CompileWidgetsetTask

        def ua = 'gecko1_8,safari'
        if ( !compileWidgetsetTask.userAgent ) {
            if ( Util.isOperaUserAgentSupported(project) ) {
                ua += ',opera'
            }
            if ( Util.isIE10UserAgentSupported(project) ) {
                ua += ',ie10'
            }
        } else {
            ua = compileWidgetsetTask.userAgent
        }
        properties.put('user.agent', ua)

        if ( compileWidgetsetTask.profiler ) {
            properties.put('vaadin.profiler', true)
        }

        if ( !compileWidgetsetTask.logging ) {
            properties.put('gwt.logging.enabled', false)
        }
        properties
    }

    private static Set<String> getInherits(Project project) {
        CompileWidgetsetTask compileWidgetsetTask = project.tasks.getByName(CompileWidgetsetTask.NAME) as CompileWidgetsetTask

        Set<String> inherits
        if(Util.isLegacyVaadin8Project(project)) {
            inherits = [DEFAULT_LEGACY_V7_WIDGETSET]
        } else {
            inherits = [DEFAULT_WIDGETSET]
        }

        // Scan classpath for Vaadin addons and inherit their widgetsets
        inherits.addAll(findInheritsInDependencies(project))

        // Custom inherits
        if ( compileWidgetsetTask.extraInherits ) {
            inherits.addAll(compileWidgetsetTask.extraInherits)
        }

        inherits
    }

    private static Map<String, Object> getConfigurationProperties() {
        Map<String, Object> configurationProperties = [:]
        configurationProperties['devModeRedirectEnabled'] = true
        configurationProperties
    }

    private static String getWidgetsetGenerator(Project project, String widgetsetFQN) {
        CompileWidgetsetTask compileWidgetsetTask = project.tasks.getByName(CompileWidgetsetTask.NAME) as CompileWidgetsetTask

        String name, pkg, filename
        if ( compileWidgetsetTask.widgetsetGenerator == null ) {
            name = widgetsetFQN.tokenize(DOT).last()
            pkg = widgetsetFQN.replace(DOT + name, '')
            filename = name + "Generator.java"

        } else {
            name = compileWidgetsetTask.widgetsetGenerator.tokenize(DOT).last()
            pkg = compileWidgetsetTask.widgetsetGenerator.replace(DOT + name, '')
            filename = name + JAVA_FILE_POSTFIX
        }

        if ( Util.getMainSourceSet(project).srcDirs.isEmpty() ) {
            throw new GradleException('No source sets was found.')
        }

        Path javaDir = Util.getMainSourceSet(project).srcDirs.first().toPath()
        Path f = javaDir.resolve(TemplateUtil.convertFQNToFilePath(pkg)).resolve(filename)
        if ( Files.exists(f) || compileWidgetsetTask.widgetsetGenerator != null ) {
            return  "${pkg}.${StringUtils.removeEnd(filename, JAVA_FILE_POSTFIX)}"
        }
        null
    }

    private static Map<String, Object> getLinkers(Project project) {
        Map<String, Object> linkers = [:]

        File[] clientSCSS = TemplateUtil.getFilesFromPublicFolder(project, SCSS_FILE_POSTFIX)
        if ( clientSCSS.length > 0 ) {
            linkers.put('scssintegration', 'com.vaadin.sass.linker.SassLinker')
        }
        linkers
    }

    private static List<String> getClientStylesheets(Project project) {
        List<String> stylesheets = []

        File[] clientSCSS = TemplateUtil.getFilesFromPublicFolder(project, SCSS_FILE_POSTFIX)
        clientSCSS.each {
            stylesheets.add(
                    Util.replaceExtension(
                            Util.getRelativePathForFile(PUBLIC_FOLDER, it),
                            SCSS_FILE_POSTFIX,
                            CSS_FILE_POSTFIX
                    )
            )
        }

        File[] clientCSS = TemplateUtil.getFilesFromPublicFolder(project, CSS_FILE_POSTFIX)
        clientCSS.each {
            stylesheets.add(
                    Util.getRelativePathForFile(PUBLIC_FOLDER, it)
            )
        }

        stylesheets
    }
}