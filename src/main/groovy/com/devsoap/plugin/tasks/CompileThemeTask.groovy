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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.FileTree
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.tooling.BuildActionFailureException

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Attributes
import java.util.jar.JarInputStream
import java.util.jar.Manifest

/**
 * Compiles the SASS theme into CSS
 *
 * @author John Ahlroos
 * @since 1.0
 */
@CacheableTask
class CompileThemeTask extends DefaultTask {

    static final String NAME = 'vaadinThemeCompile'

    /**
     * Main CSS file
     */
    static final String STYLES_CSS = 'styles.css'

    /**
     * File pattern for searching for all styles.scss files recursively
     */
    static final String STYLES_SCSS_PATTERN = '**/styles.scss'

    private static final String CLASSPATH_SWITCH = '-cp'
    private static final String TEMPDIR_SWITCH = '-Djava.io.tmpdir'

    private static final String RUBY_MAIN_CLASS = 'org.jruby.Main'
    private static final String COMPASS_COMPILER = 'compass'
    private static final String LIBSASS_COMPILER = 'libsass'
    private static final String VAADIN_COMPILER = 'vaadin'

    private static final String STYLES_SCSS = 'styles.scss'

    @Input
    @Optional
    private final Property<String> themesDirectory = project.objects.property(String)
    private final Property<String> compiler = project.objects.property(String)
    private final Property<Boolean> compress = project.objects.property(Boolean)
    @Input
    private final Property<Boolean> useClasspathJar = project.objects.property(Boolean)
    @Input
    private final ListProperty<String> jvmArgs = project.objects.listProperty(String)

    /**
     * Creates a new theme compilation task
     */
    CompileThemeTask() {
        dependsOn('classes', BuildClassPathJar.NAME, UpdateAddonStylesTask.NAME)
        description = 'Compiles a Vaadin SASS theme into CSS'

        themesDirectory.set(null)
        compiler.set(VAADIN_COMPILER)
        compress.set(true)

        project.afterEvaluate {
            Path themesDirectory = Util.getThemesDirectory(project)
            inputs.dir themesDirectory
            inputs.files(project.fileTree(dir:themesDirectory, include : '**/*.scss').collect())
            outputs.files(project.fileTree(dir:themesDirectory, include:STYLES_SCSS_PATTERN).collect {
                File theme -> theme.toPath().parent.resolve(STYLES_CSS).toFile()
            })

            // Add classpath jar
            if ( getUseClasspathJar() ) {
                BuildClassPathJar pathJarTask = project.tasks.getByName(BuildClassPathJar.NAME) as BuildClassPathJar
                inputs.file(pathJarTask.archiveFile)
            }

            // Compress if needed
            finalizedBy project.tasks[CompressCssTask.NAME]
        }
    }

    /**
     * Get custom directory where themes can be found
     */
    String getThemesDirectory() {
        themesDirectory.getOrNull()
    }

    /**
     * Set custom directory where themes can be found
     */
    void setThemesDirectory(String directory) {
        themesDirectory.set(directory)
    }

    /**
     * Get theme compiler to use
     * <p>
     *     Available options are
     *     <ul>
     *         <li>vaadin - Vaadin's SASS Compiler</li>
     *         <li>compass - Compass's SASS Compiler</li>
     *         <li>libsass - Libsass SASS Compiler</li>
     *     </ul>
     */
    @Input
    String getCompiler() {
        compiler.get()
    }

    /**
     * Set theme compiler to use
     * <p>
     *     Available options are
     *     <ul>
     *         <li>vaadin - Vaadin's SASS Compiler</li>
     *         <li>compass - Compass's SASS Compiler</li>
     *         <li>libsass - Libsass SASS Compiler</li>
     *     </ul>
     */
    void setCompiler(String compiler) {
        this.compiler.set(compiler)
    }

    /**
     * Is theme compression in use
     */
    @Input
    Boolean getCompress() {
        compress.get()
    }

    /**
     * Enable theme compression
     */
    void setCompress(Boolean compress)  {
        this.compress.set(compress)
    }

    /**
     * Does the task use a classpath jar
     */
    Boolean getUseClasspathJar() {
        useClasspathJar.get()
    }

    /**
     * Does the task use a classpath jar
     */
    void setUseClasspathJar(Boolean enabled) {
        useClasspathJar.set(enabled)
    }

    /**
     * Does the task use a classpath jar
     */
    void setUseClasspathJar(Provider<Boolean> enabled) {
        useClasspathJar.set(enabled)
    }

    /**
     * Extra jvm arguments passed the JVM running the compiler
     */
    String[] getJvmArgs() {
        jvmArgs.present ? jvmArgs.get().toArray(new String[jvmArgs.get().size()]) : null
    }

    /**
     * Extra jvm arguments passed the JVM running the compiler
     */
    void setJvmArgs(String... args) {
        jvmArgs.set(Arrays.asList(args))
    }

    @TaskAction
    void exec() {
        compile(project)
    }

    /**
     * Compiles the SASS themes into CSS
     *
     * @param project
     *      the project who's theme should be compiled
     * @param isRecompile
     *      is the compile a recompile
     */
    static compile(Project project, boolean isRecompile=false) {
        Path themesDir = Util.getThemesDirectory(project)

        project.logger.info("Compiling themes found in "+themesDir)

        FileTree themes = project.fileTree(dir:themesDir, include:STYLES_SCSS_PATTERN)

        project.logger.info("Found ${themes.files.size() } themes.")

        CompileThemeTask compileThemeTask = project.tasks.getByName(CompileThemeTask.NAME) as CompileThemeTask

        Path gemsDir
        if ( compileThemeTask.getCompiler() in [COMPASS_COMPILER] ) {
            gemsDir = installCompassGem(project)
        }

        Path unpackedThemesDir
        if ( compileThemeTask.getCompiler() in [COMPASS_COMPILER, LIBSASS_COMPILER] ) {
            unpackedThemesDir = unpackThemes(project)
        } else if(compileThemeTask.getThemesDirectory()) {
            // Must unpack themes for Valo to work when using custom directory
            unpackedThemesDir = unpackThemes(project)
        }

        themes.each { File themeFile ->
            Path theme = themeFile.toPath()
            Path dir = theme.parent

            if ( isRecompile ) {
                project.logger.lifecycle("Recompiling ${theme.toAbsolutePath().toString()}...")
            } else {
                project.logger.info("Compiling ${theme.toAbsolutePath().toString()}...")
            }

            def start = System.currentTimeMillis()

            Process process
            switch (project.vaadinThemeCompile.compiler) {
                case VAADIN_COMPILER:
                    Path targetCss = dir.resolve(STYLES_CSS)
                    if (compileThemeTask.getThemesDirectory()) {
                        Path sourceScss = unpackedThemesDir.resolve(dir.fileName.toString()).resolve(theme.fileName.toString())
                        process = executeVaadinSassCompiler(project, sourceScss, targetCss)
                    } else {
                        process = executeVaadinSassCompiler(project, theme, targetCss)
                    }
                    break
                case COMPASS_COMPILER:
                    process = executeCompassSassCompiler(project, gemsDir, unpackedThemesDir, dir)
                break
                case LIBSASS_COMPILER:
                    process = executeLibSassCompiler(project, dir, unpackedThemesDir)
                break
                default:
                    throw new BuildActionFailureException(
                            "Selected theme compiler \"${project.vaadinThemeCompile.compiler}\" is not valid",null)
            }

            boolean failed = false
            Util.logProcess(project, process, 'theme-compile.log') { String line ->
                if ( line.contains('error') ) {
                    project.logger.error(line)
                    failed = true
                    return false
                }
                true
            }

            int result = process.waitFor()

            long time = (System.currentTimeMillis()-start)/1000
            if (result != 0 || failed ) {
                // Cleanup possible css file
                Files.deleteIfExists(dir.resolve(STYLES_CSS))
                throw new BuildActionFailureException('Theme compilation failed. See error log for details.', null)
            } else if ( isRecompile ) {
                project.logger.lifecycle("Theme was recompiled in $time seconds")
            } else {
                project.logger.info("Theme was compiled in $time seconds")
            }
        }
    }

    /**
     * Creates a process that runs the Vaadin SASS compiler
     *
     * @param project
     *      the project to compile the SASS themes for
     * @param themePath
     *      the path of the theme
     * @param targetCSSFile
     *      the CSS file to compile into
     * @return
     *      the process that runs the compiler
     */
    private static Process executeVaadinSassCompiler(Project project, Path themeDir, Path targetCSSFile) {
        CompileThemeTask compileThemeTask = project.tasks.getByName(CompileThemeTask.NAME) as CompileThemeTask

        def compileProcess = [Util.getJavaBinary(project)]
        if ( compileThemeTask.getJvmArgs() ) {
            compileProcess += compileThemeTask.getJvmArgs() as List
        }

        compileProcess += ["$TEMPDIR_SWITCH=${compileThemeTask.temporaryDir.canonicalPath}"]
        compileProcess += [CLASSPATH_SWITCH,  Util.getCompileClassPathOrJar(project).asPath]
        compileProcess += 'com.vaadin.sass.SassCompiler'
        compileProcess += [themeDir.toAbsolutePath().toString(), targetCSSFile.toAbsolutePath().toString()]
        compileProcess.execute([], project.buildDir)
    }

    /**
     * Installs the compass gem with JRuby into a directory
     *
     * @param project
     *      the project to install to
     * @return
     *      the directory where the gem was installed
     */
    private static Path installCompassGem(Project project) {
        Path gemsDir = project.buildDir.toPath().resolve('jruby').resolve('gems')
        if ( !Files.exists(gemsDir) ) {
            Files.createDirectories(gemsDir)

            project.logger.info("Installing compass ruby gem...")
            def gemProcess = [Util.getJavaBinary(project)]
            gemProcess += [CLASSPATH_SWITCH,  Util.getCompileClassPathOrJar(project).asPath]
            gemProcess += RUBY_MAIN_CLASS
            gemProcess += "-S gem install -i $gemsDir --no-document compass".tokenize()

            project.logger.debug(gemProcess.toString())
            gemProcess = gemProcess.execute([
                    "GEM_PATH=${gemsDir.toAbsolutePath().toString()}",
                    "PATH=${gemsDir.resolve('bin').toAbsolutePath().toString()}"
            ], project.buildDir)

            Util.logProcess(project, gemProcess, 'compass-gem-install.log'){ true }
            def result = gemProcess.waitFor()
            if ( result != 0 ) {
                throw new BuildActionFailureException("Installing Compass ruby gem failed. " +
                        "See compass-gem-install.log for further information.",null)
            }
        }
        gemsDir
    }

    /**
     * Unpacks the themes found on classpath into a temporary directory.
     *
     * @param project
     *      the project where to search fro the themes
     * @return
     *      returns the directory where the themes has been unpacked
     */
    private static Path unpackThemes(Project project) {
        // Unpack Vaadin and addon themes
        Path unpackedVaadinDir = project.file(project.buildDir).toPath().resolve('VAADIN')
        Path unpackedThemesDir = project.file(unpackedVaadinDir).toPath().resolve('themes')
        Path unpackedAddonsThemesDir = project.file(unpackedVaadinDir).toPath().resolve('addons')
        Files.createDirectories(unpackedThemesDir)
        Files.createDirectories(unpackedAddonsThemesDir)

        project.logger.info("Unpacking themes to $unpackedThemesDir")
        def themesAttribute = new Attributes.Name('Vaadin-Stylesheets')
        def bundleName = new Attributes.Name('Bundle-Name')
        project.configurations.all { Configuration conf ->
            conf.allDependencies.each { Dependency dependency ->
                if ( dependency in ProjectDependency ) {
                    def dependentProject = dependency.dependencyProject
                    if ( dependentProject.hasProperty(VAADIN_COMPILER) ) {
                        dependentProject.copy{
                            from Util.getThemesDirectory(project)
                            into unpackedThemesDir
                        }
                    }
                } else if (Util.isResolvable(project, conf) && !Util.isDeprecated(conf)) {
                    conf.files(dependency).each { File file ->
                        file.withInputStream { InputStream stream ->
                            new JarInputStream(stream).withCloseable { JarInputStream jarStream ->
                                Manifest mf = jarStream.manifest
                                Attributes attributes = mf?.mainAttributes
                                String value = attributes?.getValue(themesAttribute)
                                Boolean themesValue = attributes?.getValue(bundleName) in ['vaadin-themes',
                                                                                           'Vaadin Themes'] //since 8.1
                                if ( value || themesValue ) {
                                    project.logger.info("Unpacking $file")
                                    project.copy {
                                        includeEmptyDirs = false
                                        from project.zipTree(file)
                                        into unpackedVaadinDir
                                        include 'VAADIN/themes/**/*', 'VAADIN/addons/**/*'
                                        eachFile { details ->
                                            details.path -= 'VAADIN'
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Copy project theme into unpacked directory
        project.logger.info "Copying project theme into $unpackedThemesDir"
        project.copy {
            from Util.getThemesDirectory(project)
            into unpackedThemesDir
        }

        unpackedThemesDir
    }

    /**
     * Creates a process that runs the Compass compiler with JRuby
     *
     * @param project
     *      the project to run the compiler on
     * @param gemsDir
     *      the gem directory which contains the Compass Ruby gem
     * @param unpackedThemesDir
     *      the directory where themes are unpacked
     * @param themeDir
     *      the target directory
     * @return
     *      the process that runs the compiler
     */
    private static Process executeCompassSassCompiler(Project project, Path gemsDir, Path unpackedThemesDir,
                                                      Path themeDir) {
        Path themePath = unpackedThemesDir.resolve(themeDir.fileName.toString())

        String compassCompile = '-S compass compile '
        compassCompile += "--sass-dir $themePath "
        compassCompile += "--css-dir $themeDir "
        compassCompile += "--images-dir $themePath "
        compassCompile += "--javascripts-dir $themePath "
        compassCompile += '--relative-assets'

        project.logger.info("Compiling $themePath with compass compiler")

        CompileThemeTask compileThemeTask = project.tasks.getByName(CompileThemeTask.NAME) as CompileThemeTask

        List compileProcess = [Util.getJavaBinary(project)]
        if ( compileThemeTask.getJvmArgs() ) {
            compileProcess += compileThemeTask.getJvmArgs() as List
        }

        compileProcess += ["$TEMPDIR_SWITCH=${compileThemeTask.temporaryDir.canonicalPath}"]
        compileProcess += [CLASSPATH_SWITCH,  Util.getCompileClassPathOrJar(project).asPath]
        compileProcess += RUBY_MAIN_CLASS
        compileProcess += compassCompile.tokenize()

        project.logger.debug(compileProcess.toString())
        compileProcess.execute([
                "GEM_PATH=${gemsDir.toAbsolutePath().toString()}",
                "PATH=${gemsDir.resolve('bin').toAbsolutePath().toString()}"
        ], project.buildDir)
    }

    private static Process executeLibSassCompiler(Project project, Path themeDir, Path unpackedThemesDir) {

        Path stylesScss = themeDir.resolve(STYLES_SCSS)
        Path stylesCss = themeDir.resolve(STYLES_CSS)

        project.logger.info("Compiling $themeDir with libsass compiler")

        CompileThemeTask compileThemeTask = project.tasks.getByName(CompileThemeTask.NAME) as CompileThemeTask

        List compileProcess = [Util.getJavaBinary(project)]
        if ( compileThemeTask.getJvmArgs() ) {
            compileProcess += compileThemeTask.getJvmArgs() as List
        }

        compileProcess += ["$TEMPDIR_SWITCH=${compileThemeTask.temporaryDir.canonicalPath}"]
        compileProcess += [CLASSPATH_SWITCH,  Util.getCompileClassPathOrJar(project).asPath]
        compileProcess += 'com.devsoap.plugin.LibSassCompiler'
        compileProcess += [stylesScss.toAbsolutePath().toString(), stylesCss.toAbsolutePath().toString(), unpackedThemesDir.toAbsolutePath().toString()]

        project.logger.debug(compileProcess.toString())

        compileProcess.execute([], project.buildDir)
    }
}