package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.CompileThemeTask
import com.devsoap.plugin.tasks.CreateThemeTask
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Stream

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Created by john on 18.1.2016.
 */
@Tag("ThemeCompile")
class CreateThemeTest extends IntegrationTest {

    String themeCompiler

    CreateThemeTest() {}

    static Stream<String> getThemeCompilers() {
        return Stream.of('vaadin', 'compass', 'libsass')
    }

    void setup(String themeCompiler) {
        this.themeCompiler = themeCompiler
        buildFile << "vaadinThemeCompile.compiler = '$themeCompiler'\n"
    }

    @MethodSource("getThemeCompilers")
    @ParameterizedTest void 'Create default theme'(String themeCompiler) {
        setup(themeCompiler)
        assertThemeCreatedAndCompiled()
    }

    @MethodSource("getThemeCompilers")
    @ParameterizedTest void 'Create default theme with classpath jar'(String themeCompiler) {
        setup(themeCompiler)
        buildFile << "vaadin.useClassPathJar = true"
        assertThemeCreatedAndCompiled()
    }

    @MethodSource("getThemeCompilers")
    @ParameterizedTest void 'Create theme with name'(String themeCompiler) {
        setup(themeCompiler)
        assertThemeCreatedAndCompiled('TestingTheme')
    }

    @MethodSource("getThemeCompilers")
    @ParameterizedTest void 'Create theme in custom theme directory'(String themeCompiler) {
        setup(themeCompiler)
        buildFile << "vaadinThemeCompile.themesDirectory = new File(project.buildDir, 'mythemedir').canonicalPath\n"
        runWithArguments(CreateThemeTask.NAME)

        Path themesDir = projectDir.resolve('build').resolve('mythemedir')
        assertThemeInDirectory(themesDir, projectDir.getFileName().toString())

        runWithArguments(CompileThemeTask.NAME)
        assertCompiledThemeInDirectory(themesDir, projectDir.getFileName().toString())
    }

    @MethodSource("getThemeCompilers")
    @ParameterizedTest void 'Create theme in custom external theme directory'(String themeCompiler) {
        setup(themeCompiler)
        Path customThemesDir = Files.createTempDirectory(projectDir.getFileName().toString() + "-" + '-themes')
        customThemesDir.toFile().deleteOnExit()
        println "Created themes in $customThemesDir"

        String escapedDir = customThemesDir.toAbsolutePath().toString().replace("\\","\\\\")
        buildFile << "vaadinThemeCompile.themesDirectory = '$escapedDir'"
        runWithArguments(CreateThemeTask.NAME)

        assertThemeInDirectory(customThemesDir, projectDir.getFileName().toString())

        runWithArguments(CompileThemeTask.NAME)
        assertCompiledThemeInDirectory(customThemesDir, projectDir.getFileName().toString())
    }

    @MethodSource("getThemeCompilers")
    @ParameterizedTest void 'Theme is compressed by default'(String themeCompiler) {
        setup(themeCompiler)
        assertThemeCreatedAndCompiled()
        assertCompressedThemeInDirectory(themesDir, projectDir.getFileName().toString())
    }

    @MethodSource("getThemeCompilers")
    @ParameterizedTest void 'Theme is not compressed if disabled'(String themeCompiler) {
        setup(themeCompiler)
        buildFile << "vaadinThemeCompile.compress = false"
        assertThemeCreatedAndCompiled()
        assertNoCompressedThemeInDirectory(themesDir, projectDir.getFileName().toString())
    }

    @MethodSource("getThemeCompilers")
    @ParameterizedTest void 'Build fails if compilation fails'(String themeCompiler) {
        setup(themeCompiler)

        runWithArguments(CreateThemeTask.NAME)

        Path stylesSCSS = themesDir.resolve(projectDir.getFileName().toString()).resolve('styles.scss')

        // Add garbage so compilation fails
        stylesSCSS << "@mixin ic_img(\$name) {.}"

        runFailureExpected(CompileThemeTask.NAME)

        Path stylesCSS = themesDir.resolve(projectDir.getFileName().toString()).resolve('styles.css')
        assertFalse Files.exists(stylesCSS), 'Compiled theme should not exist'
    }

    @MethodSource("getThemeCompilers")
    @ParameterizedTest void 'Use custom JVM args'(String themeCompiler) {
        setup(themeCompiler)
        buildFile << """
            vaadinThemeCompile{ 
                jvmArgs = ['-Dfoo.bar=baz'] 
        }""".stripIndent()

        assertThemeCreatedAndCompiled()
    }

    private void assertThemeCreatedAndCompiled(String themeName=null) {
        if ( themeName ) {
            runWithArguments(CreateThemeTask.NAME, "--name=$themeName")
        } else {
            runWithArguments(CreateThemeTask.NAME)
            themeName = projectDir.getFileName().toString()
        }

        assertThemeInDirectory(themesDir, themeName)

        runWithArguments(CompileThemeTask.NAME)
        assertCompiledThemeInDirectory(themesDir, themeName)
    }

    private static void assertThemeInDirectory(Path directory, String themeName) {
        assertTrue Files.exists(directory), "$directory does not exist"

        Path themeDir = directory.resolve(themeName)
        assertTrue Files.exists(themeDir), "$themeDir does not exist"

        Path addons = themeDir.resolve('addons.scss')
        assertTrue Files.exists(addons), 'addons.scss does not exist in ' + Files.list(themeDir).withCloseable { it.toArray().toArrayString() }

        Path theme = themeDir.resolve(themeName.toLowerCase()+'.scss')
        assertTrue Files.exists(theme), themeName.toLowerCase()+'.scss does not exist in ' + Files.list(themeDir).withCloseable { it.toArray().toArrayString() }

        Path styles = themeDir.resolve('styles.scss')
        assertTrue Files.exists(styles), 'styles.scss does not exist in ' + Files.list(themeDir).withCloseable { it.toArray().toArrayString() }
    }

    private static void assertCompiledThemeInDirectory(Path directory, String themeName) {
        assertThemeInDirectory(directory, themeName)

        Path themeDir = directory.resolve(themeName)
        assertTrue Files.exists(themeDir), "$themeDir does not exist"

        Path stylesCompiled = themeDir.resolve('styles.css')
        assertTrue Files.exists(stylesCompiled),
                "styles.css does not exist in theme dir, theme dir only contains " +
                Files.list(themeDir).withCloseable { it.toArray().toArrayString() }
    }

    private static void assertCompressedThemeInDirectory(Path directory, String themeName) {
        assertThemeInDirectory(directory, themeName)

        Path themeDir = directory.resolve(themeName)
        assertTrue Files.exists(themeDir), "$themeDir does not exist"

        Path stylesCompiled = themeDir.resolve('styles.css.gz')
        assertTrue Files.exists(stylesCompiled),
                "styles.css.gz does not exist in theme dir, theme dir only contains " +
                Files.list(themeDir).withCloseable { it.toArray().toArrayString() }
    }

    private static void assertNoCompressedThemeInDirectory(Path directory, String themeName) {
        assertThemeInDirectory(directory, themeName)

        Path themeDir = directory.resolve(themeName)
        assertTrue Files.exists(themeDir), "$themeDir does not exist"

        Path stylesCompiled = themeDir.resolve('styles.css.gz')
        assertFalse Files.exists(stylesCompiled),
                "styles.css.gz should not exist in theme dir, theme dir only contains " +
                Files.list(themeDir).withCloseable { it.toArray().toArrayString() }
    }

    private Path getThemesDir() {
        projectDir.resolve('src').resolve('main').resolve('webapp')
                .resolve('VAADIN').resolve('themes')
    }
}
