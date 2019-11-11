package com.devsoap.plugin.tests

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Tag
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.nio.file.Path
import java.util.stream.Stream

/**
 * Smoke test for different gradle versions
 */
@Tag("WidgetsetAndThemeCompile")
class GradleVersionTest extends IntegrationTest {

    String gradleVersion

    GradleVersionTest() {}

    static Stream<String> getGradleVersions() {
        return Stream.of('6.8.3')
    }

    IntegrationTest setupTest(IntegrationTest test) {
        test.gradleVersion = gradleVersion
        test.projectDir = projectDir
        test.buildFile = buildFile
        test.settingsFile = settingsFile
        test.startTime = startTime
        test
    }

    @MethodSource("getGradleVersions")
    @ParameterizedTest void 'Compile Widgetset'(String gradleVersion) {
        this.gradleVersion = gradleVersion
        setupTest(new CompileWidgetsetTest(){
            protected GradleRunner setupRunner(Path projectDir) {
                return GradleVersionTest.this.setupRunner(projectDir)
            }
        }).'Widgetset defined, manual widgetset detected and compiled'()
    }

    @MethodSource("getGradleVersions")
    @ParameterizedTest void 'Compile Theme'(String gradleVersion) {
        this.gradleVersion = gradleVersion
        setupTest(new CreateThemeTest(){
            protected GradleRunner setupRunner(Path projectDir) {
                return GradleVersionTest.this.setupRunner(projectDir)
            }
        }).'Create default theme'('vaadin')
    }

    @MethodSource("getGradleVersions")
    @ParameterizedTest void 'Ensure dependencies are correct'(String gradleVersion) {
        this.gradleVersion = gradleVersion
        setupTest(new ProjectDependenciesTest(){
            protected GradleRunner setupRunner(Path projectDir) {
                return GradleVersionTest.this.setupRunner(projectDir)
            }
        }).'Project has Vaadin configurations'()
    }
}
