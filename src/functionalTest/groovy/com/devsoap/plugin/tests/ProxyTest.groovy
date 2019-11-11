package com.devsoap.plugin.tests

import com.devsoap.plugin.tasks.CompileWidgetsetTask
import com.devsoap.plugin.tasks.CreateProjectTask
import groovy.json.JsonSlurper
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockserver.client.MockServerClient
import org.mockserver.integration.ClientAndServer
import org.mockserver.model.HttpRequest
import org.mockserver.model.RequestDefinition

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Future

import static org.junit.jupiter.api.Assertions.assertTrue
import static org.junit.jupiter.api.Assertions.assertEquals
import static org.mockserver.integration.ClientAndServer.startClientAndServer

/**
 * Created by john on 1/19/17.
 */
class ProxyTest extends IntegrationTest {

    ClientAndServer proxy

    @BeforeEach
    void startProxy() {
        proxy = startClientAndServer(getPort())
    }

    @AfterEach
    void stopProxy() {
        Future<MockServerClient> clientFut = proxy.stop(false)
        MockServerClient client = clientFut.get()
        assertTrue client.hasStopped(), 'Proxy is still running after stop.'
    }

    @Test
    void 'Test widgetset CDN behind proxy'() {
        buildFile << """
            dependencies {
                implementation 'org.vaadin.addons:qrcode:+'
            }

            vaadinCompile {
                widgetsetCDN true
                widgetsetCDNConfig {
                    proxyEnabled true
                    proxyHost 'localhost'
                    proxyScheme 'http'
                    proxyPort ${proxy.port}
                }
            }
        """

        runWithArguments(CreateProjectTask.NAME)

        String result = runWithArguments('--info', CompileWidgetsetTask.NAME)
        assertTrue result.contains('Querying widgetset for'), result
        assertTrue result.contains('Widgetset is available, downloading...'), result
        assertTrue result.contains('Extracting widgetset'), result
        assertTrue result.contains('Generating AppWidgetset'), result

        Path appWidgetset = projectDir.resolve('src').resolve('main')
                .resolve('java').resolve('AppWidgetset.java')
        assertTrue Files.exists(appWidgetset), 'AppWidgetset.java was not created'

        Path widgetsetFolder = projectDir.resolve('src').resolve('main')
                .resolve('webapp').resolve('VAADIN').resolve('widgetsets')
        assertTrue Files.exists(widgetsetFolder), 'Widgetsets folder did not exist'
        assertTrue Files.list(widgetsetFolder).withCloseable { it.count() } == 1,
                'Widgetsets folder did not contain widgetset'

        Map request = getRequest('/api/compiler/download')
        assertTrue request.vaadinVersion.startsWith('8'), 'Vaadin version was not right'
        assertEquals request.compileStyle, 'OBF', 'Compile style not correct'

        Map qrCodeAddon = request.addons[0]
        assertEquals 'org.vaadin.addons', qrCodeAddon.groupId
        assertEquals 'qrcode', qrCodeAddon.artifactId
        assertEquals '2.1', qrCodeAddon.version
    }

    private Map getRequest(String path) {
        RequestDefinition[] json = proxy.retrieveRecordedRequests(HttpRequest.request(path))
        HttpRequest request = json[0] as HttpRequest
        Map jsonArray = new JsonSlurper().parseText(request.bodyAsJsonOrXmlString) as Map
        jsonArray
    }
}
