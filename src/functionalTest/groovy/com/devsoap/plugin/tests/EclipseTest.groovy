package com.devsoap.plugin.tests

import groovy.transform.Memoized
import groovy.util.logging.Log
import groovy.util.slurpersupport.GPathResult
import groovy.util.slurpersupport.Node
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

import java.nio.file.Path

import static org.junit.jupiter.api.Assertions.assertEquals

/**
 * Created by john on 12/6/16.
 */
@Log
class EclipseTest extends IntegrationTest {

    @Override
    @BeforeEach
    void setup() {
        super.setup()
        buildFile << "apply plugin: 'eclipse-wtp'\n"
    }

    @Test void 'Default facets are applied'() {

        runWithArguments('eclipse')

        List<Node> fixedFacets = facetedProject.childNodes().findAll { Node node -> node.name() == 'fixed' }
        assertEquals 2, fixedFacets.size(), 'There should be two installed facets'
        assertEquals 'jst.java', fixedFacets[0].attributes()['facet'], 'First should be Java facet'
        assertEquals 'jst.web', fixedFacets[1].attributes()['facet'], 'Second should be Web facet'

        List<Node> installedFacets = facetedProject.childNodes().findAll { Node node -> node.name() == 'installed' }
        assertEquals 3, installedFacets.size(), 'There should be three installed facets'
    }

    @Test void 'Preserve custom facets'() {
        buildFile << "eclipse { wtp { facet { facet name: 'wst.jsdt.web', version: '1.0' } } }"

        runWithArguments('eclipse')

        List<Node> installedFacets = facetedProject.childNodes().findAll { Node node ->
            node.name() == 'installed' &&
            node.attributes()['facet'] == 'wst.jsdt.web' &&
            node.attributes()['version'] == '1.0'
        }
        assertEquals 1, installedFacets.size(), 'The facet should still be installed'
    }

    @Memoized
    private GPathResult getFacetedProject() {
        Path settingsDir = projectDir.resolve('.settings')
        Path projectFacetConfigFile = settingsDir.resolve('org.eclipse.wst.common.project.facet.core.xml')
        new XmlSlurper().parse(projectFacetConfigFile.toFile())
    }
}
