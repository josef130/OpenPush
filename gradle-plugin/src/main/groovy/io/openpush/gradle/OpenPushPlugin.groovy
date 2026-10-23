package io.openpush.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.GradleException
import javax.xml.parsers.DocumentBuilderFactory

/** Turns app/openpush.xml into Android resources. No network or credentials access. */
class OpenPushPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.pluginManager.withPlugin('com.android.application') {
            File config = project.file('openpush.xml')
            if (!config.isFile()) throw new GradleException('OpenPush: add the client configuration app/openpush.xml. Never use a Service Account file.')
            def factory = DocumentBuilderFactory.newInstance()
            factory.setFeature('http://apache.org/xml/features/disallow-doctype-decl', true)
            factory.setExpandEntityReferences(false)
            def document = factory.newDocumentBuilder().parse(config)
            def values = [:]
            def strings = document.getElementsByTagName('string')
            for (int i = 0; i < strings.length; i++) {
                def element = strings.item(i)
                String name = element.getAttribute('name')
                if (!(name ==~ /openpush_[a-z_]+/)) throw new GradleException('OpenPush: invalid configuration resource name.')
                values[name] = element.textContent
            }
            ['openpush_server_url','openpush_app_id','openpush_client_key','openpush_project_id'].each {
                if (!values[it]) throw new GradleException("OpenPush: missing ${it} in app/openpush.xml.")
            }
            def android = project.extensions.getByName('android')
            android.buildFeatures.resValues = true
            values.each { name, value -> android.defaultConfig.resValue('string', name, value) }
            // Local loopback client configurations must never enter a release APK.
            project.tasks.configureEach { task ->
                if (task.name ==~ /(?i).*(assemble|bundle|package).*release.*/) {
                    task.doFirst {
                        if (!values.openpush_server_url.startsWith('https://'))
                            throw new GradleException('OpenPush release requires a publicly reachable HTTPS server in app/openpush.xml.')
                    }
                }
            }
        }
    }
}
