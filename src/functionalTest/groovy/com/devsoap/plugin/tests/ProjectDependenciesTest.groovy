package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.CreateProjectTask
import org.junit.jupiter.api.Test

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertTrue

/**
 * Tests the injected dependencies
 */
class ProjectDependenciesTest extends IntegrationTest {

    @Override
    protected void applyThirdPartyPlugins(Path buildFile) {
        super.applyThirdPartyPlugins(buildFile)

        buildFile << """
            plugins {
                id "de.undercouch.download" version "3.2.0"
            }
        """.stripIndent()
    }

    @Test void 'Project has Vaadin extension'() {

        buildFile << """
            import com.devsoap.plugin.extensions.VaadinPluginExtension
            task testProperties {
                doLast {
                    println 'Has Vaadin property ' + project.hasProperty('vaadin')
                    println 'Has Vaadin extension ' + (project.extensions.getByName('vaadin') != null)
                    println 'Has Vaadin type ' + (project.extensions.getByType(VaadinPluginExtension) != null)
                }
            }
        """.stripIndent()

        def result = runWithArguments('testProperties')
        assertTrue result.contains( 'Has Vaadin property true'), result
        assertTrue result.contains( 'Has Vaadin extension true'), result
        assertTrue result.contains( 'Has Vaadin type true'), result
    }

    @Test void 'Project has Vaadin configurations'() {

        buildFile << """
            task testConfigurations {
                doLast {
                    def confs = project.configurations
    
                    println 'Server configuration ' + confs.hasProperty('vaadin-server')
                    println 'Client configuration ' + confs.hasProperty('vaadin-client')
                    println 'Javadoc configuration ' + confs.hasProperty('vaadin-javadoc')
    
                    println 'Testbench configuration ' + !confs.getByName('vaadin-testbench').dependencies.empty
                    println 'Push configuration ' + !confs.getByName('vaadin-push').dependencies.empty
                    println 'Groovy configuration ' + confs.hasProperty('vaadin-groovy')
                }
            }
        """.stripIndent()

        def result = runWithArguments('testConfigurations')
        assertTrue result.contains( 'Server configuration true'), result
        assertTrue result.contains( 'Client configuration true'), result
        assertTrue result.contains( 'Javadoc configuration true'), result

        assertTrue result.contains( 'Testbench configuration false'), result
        assertTrue result.contains( 'Push configuration false'), result
        assertTrue result.contains( 'Groovy configuration false'), result
    }

    @Test void 'Project has Vaadin repositories'() {

        buildFile << """
            task testRepositories {
                doLast {
                    def repositories = [
                        'Vaadin addons',
                        'Vaadin snapshots'
                    ]
    
                    repositories.each {
                        if ( !project.repositories.hasProperty(it) ) {
                            println 'Repository missing '+it
                        }
                    }
                }
            }    
        """.stripIndent()

        def result = runWithArguments('testRepositories')
        assertFalse result.contains( 'Repository missing'), result
    }

    @Test void 'Project has pre-compiled widgetset'() {

        buildFile << """
            task hasWidgetset {
                doLast {
                    def confs = project.configurations
                    def client = confs.getByName('vaadin-client').resolvedConfiguration
                    def artifacts = client.resolvedArtifacts
                    println 'Has client dependency ' + !artifacts.empty
                    println 'Has client-compiled dependency ' + !artifacts.findAll {
                        it.moduleVersion.id.name == 'vaadin-client-compiled'
                    }.empty
                }
            }
         """.stripIndent()

        def result = runWithArguments('hasWidgetset')
        assertTrue result.contains( 'Has client dependency true'), result
        assertTrue result.contains( 'Has client-compiled dependency true'), result
    }

    @Test void 'Client dependencies added when widgetset present'() {

        buildFile << """
            vaadinCompile {
               widgetset 'com.example.TestWidgetset'
            }

            task testClientDependencies {
                doLast {
                    def confs = project.configurations
                    def client = confs.getByName('vaadin-client').resolvedConfiguration
                    def artifacts = client.resolvedArtifacts
                    println 'Has client dependency ' + !artifacts.empty
                }
            }    
        """.stripIndent()

        def result = runWithArguments('testClientDependencies')
        assertTrue result.contains( 'Has client dependency true'), result
    }

    @Test void 'Client dependencies added when widgetset is automatically detected'() {
        buildFile << """
            task testClientDependencies {
                doLast {
                    def confs = project.configurations
                    def client = confs.getByName('vaadin-server').resolvedConfiguration
                    def artifacts = client.resolvedArtifacts
                    println 'Has client dependency ' + !artifacts.empty
                }
            }
        """.stripIndent()

        runWithArguments(CreateProjectTask.NAME, '--widgetset=com.example.MyWidgetset')

        def result = runWithArguments('testClientDependencies')
        assertTrue result.contains( 'Has client dependency true'), result
    }

    @Test void 'Vaadin version is resolved'() {

        buildFile << """
            vaadin {
                version '8.12.2'               
            }
            
            vaadinCompile {
                widgetset 'com.example.TestWidgetset'
            }

            task verifyVaadinVersion {
                doLast {
                    def server = project.configurations.getByName('vaadin-server').resolvedConfiguration
                    println 'server:'
                    server.resolvedArtifacts.each {              
                        if ( it.moduleVersion.id.group.equals('com.vaadin') ) {
                            println 'Vaadin Server ' + it.moduleVersion.id.version
                        }
                    }
                    def client = project.configurations.getByName('vaadin-client').resolvedConfiguration
                    println 'client:'
                    client.resolvedArtifacts.each {
                        if ( it.moduleVersion.id.group.equals('com.vaadin') ) {
                            println 'Vaadin Client ' + it.moduleVersion.id.version
                        }
                    }
                }
            }
        """.stripIndent()

        def result = runWithArguments('verifyVaadinVersion')
        assertTrue result.contains( 'Vaadin Server 8.12.2'), result
        assertTrue result.contains( 'Vaadin Client 8.12.2'), result
    }

    @Test void 'Project has Testbench dependencies'() {

        buildFile << """
            vaadinTestbench {
                enabled true
            }

            task verifyTestbenchPresent {
                doLast {
                    def confs = project.configurations
                    println 'Testbench configuration ' + confs.hasProperty('vaadin-testbench')
    
                    def testbench = confs.getByName('vaadin-testbench')
                    println 'Testbench artifacts ' + !testbench.empty
                }
            }
        """.stripIndent()

        def result = runWithArguments('verifyTestbenchPresent')
        assertTrue result.contains( 'Testbench configuration true'), result
        assertTrue result.contains( 'Testbench artifacts true'), result
    }

    @Test void 'Vaadin version blacklist'() {

        buildFile << """
             dependencies {
                implementation 'com.vaadin:vaadin-sass-compiler:+'
                implementation 'com.vaadin:vaadin-client-compiler-deps:+'
                implementation 'com.vaadin:vaadin-cdi:+'
                implementation 'com.vaadin:vaadin-spring:+'
                implementation 'com.vaadin:vaadin-spring-boot:+'
            }

            task evaluateVersionBlacklist {
                doLast {
                    project.configurations.compileClasspath.dependencies.each {
                        if ( it.version.equals(project.vaadin.version) ) {
                            println 'Version blacklist failed for ' + it
                        }
                    }
                }
            }
        """.stripIndent()

        def result = runWithArguments('evaluateVersionBlacklist')
        assertFalse result.contains( 'Version blacklist failed for'), result
    }

    @Test void 'Maven Central and Local are included'() {

        buildFile << """
            task testMavenCentralLocal {
                doLast {
                    def repos = project.repositories
                    if ( repos.hasProperty(ArtifactRepositoryContainer.DEFAULT_MAVEN_CENTRAL_REPO_NAME) ) {
                        println 'Has Maven Central'
                    }
                    if ( repos.hasProperty(ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME) ) {
                        println 'Has Maven Local'
                    }
                }
            }
        """.stripIndent()

        def result = runWithArguments('testMavenCentralLocal')
        assertTrue result.contains( 'Has Maven Central'), result
        assertTrue result.contains( 'Has Maven Local'), result
    }

    @Test void 'Dependency without version'() {
        buildFile << """
            String lib = 'libs/qrcode-2.1.jar'
            dependencies {
                 implementation files(lib)
            }
            task downloadFile(type: de.undercouch.gradle.tasks.download.Download) {
                src 'http://vaadin.com/nexus/content/repositories/vaadin-addons/' +
                    'org/vaadin/addons/qrcode/2.1/qrcode-2.1.jar'
                dest lib
            }
        """.stripIndent()

        runWithArguments(CreateProjectTask.NAME, 'downloadFile')
        runWithArguments('vaadinCompile')
    }
}
