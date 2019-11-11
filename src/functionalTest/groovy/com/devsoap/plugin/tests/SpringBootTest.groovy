package com.devsoap.plugin.tests

import com.devsoap.plugin.GradleVaadinPlugin
import com.devsoap.plugin.tasks.CreateProjectTask
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import java.util.stream.Stream

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Created by john on 4/29/17.
 */
class SpringBootTest extends IntegrationTest {

    private String springBootVersion

    SpringBootTest() { }

    static Stream<String> getSpringBootVersions() {
        return Stream.of('2.4.2', '2.0.0.M7')
    }

    private void setup(String springBootVersion) {
        this.springBootVersion = springBootVersion
        super.makeBuildFile(projectDir)
    }

    @Override
    protected void applyBuildScriptRepositories(Path buildFile) {
        super.applyBuildScriptRepositories(buildFile)
        buildFile << """
                mavenLocal()
                maven { url 'https://plugins.gradle.org/m2/'}
                maven { url 'https://repo.spring.io/libs-snapshot'}
        """.stripMargin()
    }

    @Override
    protected void applyBuildScriptClasspathDependencies(Path buildFile) {
        super.applyBuildScriptClasspathDependencies(buildFile)
        buildFile << "classpath 'org.springframework.boot:spring-boot-gradle-plugin:${springBootVersion}'\n"
    }

    @Override
    protected void applyThirdPartyPlugins(Path buildFile) {
        super.applyThirdPartyPlugins(buildFile)
        buildFile << "apply plugin: 'org.springframework.boot'\n"
    }

    @MethodSource("getSpringBootVersions")
    @ParameterizedTest void 'Jar is built by default'(String springBootVersion) {
        setup(springBootVersion)
        def output = runFailureExpected('--info','build')

        assertTrue output.contains(':jar')
        assertFalse output.contains(':war')

        assertTrue output.contains('Applying JavaPluginAction')
        assertTrue output.contains('Applying SpringBootAction')
        assertFalse output.contains('Applying WarPluginAction')
        assertTrue output.contains('Applying VaadinPluginAction')

        assertTrue output.contains('Spring boot present, not applying WAR plugin by default.')
    }

    @MethodSource("getSpringBootVersions")
    @ParameterizedTest void 'War is built if applied'(String springBootVersion) {
        setup(springBootVersion)
        buildFile << "apply plugin: 'war'\n"

        def output = runFailureExpected('--info','build')

        assertTrue output.contains(':war')
        assertFalse output.contains(':jar')

        assertTrue output.contains('Applying JavaPluginAction')
        assertTrue output.contains('Applying SpringBootAction')
        assertTrue output.contains('Applying WarPluginAction')
        assertTrue output.contains('Applying VaadinPluginAction')

        assertTrue output.contains('Spring boot present, not applying WAR plugin by default.')
    }

    @MethodSource("getSpringBootVersions")
    @ParameterizedTest void 'Spring Boot Vaadin starter is included'(String springBootVersion) {
        setup(springBootVersion)
        String dependencyInfo = runWithArguments('dependencyInsight',
                '--configuration', GradleVaadinPlugin.CONFIGURATION_SPRING_BOOT,
                '--dependency', 'vaadin-spring-boot-starter')
        assertTrue dependencyInfo.contains('com.vaadin:vaadin-spring-boot-starter:3.')
    }

    @MethodSource("getSpringBootVersions")
    @ParameterizedTest void 'Use custom Spring Boot starter version'(String springBootVersion) {
        setup(springBootVersion)
        buildFile << "vaadinSpringBoot.starterVersion = '3.2.1'\n"

        String dependencyInfo = runWithArguments('dependencyInsight',
                '--configuration', GradleVaadinPlugin.CONFIGURATION_SPRING_BOOT,
                '--dependency', 'vaadin-spring-boot-starter')
        assertTrue dependencyInfo.contains('com.vaadin:vaadin-spring-boot-starter:3.2.1')
    }

    @MethodSource("getSpringBootVersions")
    @ParameterizedTest void 'Validate Spring Boot executable jar'(String springBootVersion) {
        setup(springBootVersion)
        buildFile << "vaadinCompile.widgetset = 'com.example.springboottest.MyWidgetset'\n"

        configureSpringBootProject()

        getSpringBootJar().withCloseable { JarFile jar ->

            // Libs
            assertJarContents(jar, 'vaadin-server not found in jar', 'BOOT-INF/lib/vaadin-server')
            assertJarContents(jar, 'spring-boot-starter not found in jar', 'BOOT-INF/lib/spring-boot-starter')
            assertJarContents(jar, 'vaadin-spring-boot not found in jar', 'BOOT-INF/lib/vaadin-spring-boot')

            // Static resources
            assertJarContents(jar, 'Widgetset not found in jar',
                    'BOOT-INF/classes/VAADIN/widgetsets/com.example.springboottest.MyWidgetset/')
            assertJarContents(jar, 'Theme not found in jar', 'BOOT-INF/classes/VAADIN/themes/MyApp/')

            // Classes
            assertJarContents(jar, 'UI not found in jar',
                    'BOOT-INF/classes/com/example/springboottest/MyAppUI.class')

            assertJarContents(jar, 'App not found in jar',
                    'BOOT-INF/classes/com/example/springboottest/MyAppApplication.class')
            assertJarContents(jar, 'Spring Boot loader not found in jar', 'org/springframework/boot/loader/')
        }
    }

    @MethodSource("getSpringBootVersions")
    @ParameterizedTest void 'Vaadin push dependencies are included'(String springBootVersion) {
        setup(springBootVersion)
        configureSpringBootProject()
        getSpringBootJar().withCloseable { JarFile jar ->
            assertNull jar.entries().find { it.name.startsWith('BOOT-INF/lib/vaadin-push')},
                    'vaadin-push should not be found in jar'
        }

        buildFile << "vaadin.push = true\n"

        getSpringBootJar().withCloseable { JarFile jar ->
            assertJarContents(jar, 'vaadin-push not found in jar','BOOT-INF/lib/vaadin-push')
        }
    }

    @MethodSource("getSpringBootVersions")
    @ParameterizedTest void 'Vaadin compile dependencies are included'(String springBootVersion) {
        setup(springBootVersion)
        configureSpringBootProject()

        buildFile << """
        dependencies {
            vaadinCompile 'commons-lang:commons-lang:2.6'
        }
        """.stripIndent()

        getSpringBootJar().withCloseable { JarFile jar ->
            assertJarContents(jar, 'vaadinCompile dependency not found in jar','BOOT-INF/lib/commons-lang-2.6')
        }
    }

    private static void assertJarContents(JarFile jar, String message, String path) {
        assertNotNull jar.entries().find { it.name.startsWith(path)},
                message + "\n" + jar.entries().collect {it.name}.collect().join("\n")

    }

    private JarFile getSpringBootJar() {
        runWithArguments('clean', springBoot1 ? 'bootRepackage': 'assemble')

        Path jarFile = projectDir.resolve('build').resolve('libs')
                .resolve(projectDir.fileName.toString()+'.jar')
        assertTrue Files.exists(jarFile), 'jar did not exist'

        new JarFile(jarFile.toFile())
    }

    private void configureSpringBootProject() {
        runWithArguments(CreateProjectTask.NAME, '--package=com.example.springboottest', '--name=MyApp')
        if(springBoot1){
            buildFile << "springBoot.mainClass = 'com.example.springboottest.MyAppApplication'\n"
        } else {
            buildFile << "bootJar.mainClassName = 'com.example.springboottest.MyAppApplication'\n"
        }
    }

    private boolean isSpringBoot1() {
        springBootVersion.startsWith("1")
    }
}