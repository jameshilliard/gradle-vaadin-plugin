/*
* Copyright 2012 John Ahlroos
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package fi.jasoft.plugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;
import fi.jasoft.plugin.ui.TemplateUtil;

class CreateComponentTask extends DefaultTask {

    public CreateComponentTask(){
        description = "Creates a new Vaadin Component."
    }

 	@TaskAction
    public void run() {

    	if(project.vaadin.widgetset == null){
    		println "No widgetset found. Please define a widgetset using the vaadin.widgetset property."
    		return
    	}

    	Console console = System.console()
    	if(console == null){
    		println "Create project task needs a console but could not get one. Quitting..."
    		return;
    	}

    	String componentName = console.readLine('\nComponent Name (MyComponent): ')
    	if(componentName == ''){
    		componentName = 'MyComponent'
    	}

    	File widgetsetFile = new File('src/main/java/'+project.vaadin.widgetset.replaceAll(/\./,'/')+".gwt.xml")
    	File widgetsetDir = new File(widgetsetFile.parent)

    	String widgetsetName = project.vaadin.widgetset.tokenize('.').last()
    	String widgetsetPackage = project.vaadin.widgetset.replaceAll('.'+widgetsetName,'')
    	
    	def substitutions = [:]
    	substitutions['%PACKAGE%'] = widgetsetPackage
    	substitutions['%COMPONENT_NAME%'] = componentName
    	substitutions['%COMPONENT_STYLENAME%'] = componentName.toLowerCase()

    	if(project.vaadin.version.startsWith("6")){
    		substitutions['%PACKAGE_CLIENT%'] = substitutions['%PACKAGE%'] + '.client.ui'
			File clientui = new File(widgetsetDir.canonicalPath+'/client/ui')
    		clientui.mkdirs()

    		TemplateUtil.writeTemplate("MyComponent.java.vaadin6", widgetsetDir, componentName+".java", substitutions)	
    		TemplateUtil.writeTemplate("VMyComponent.java.vaadin6", clientui, "V${componentName}.java", substitutions)	
    	} else {
    		substitutions['%PACKAGE_CLIENT%'] = substitutions['%PACKAGE%'] + '.client.' + componentName.toLowerCase()
    		File clientui = new File(widgetsetDir.canonicalPath + '/client/' + componentName.toLowerCase())
    		clientui.mkdirs()

    		TemplateUtil.writeTemplate("MyComponent.java", widgetsetDir, componentName+".java", substitutions)	
    		TemplateUtil.writeTemplate("MyComponentWidget.java", clientui, componentName+"Widget.java", substitutions)
    		TemplateUtil.writeTemplate("MyComponentConnector.java", clientui, componentName+"Connector.java", substitutions)
    	}
    }
}