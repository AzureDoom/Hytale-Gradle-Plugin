package com.azuredoom.gradle.hytale

import groovy.json.JsonSlurper
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class UpdatePluginManifestTaskTest extends Specification {

    @TempDir
    File testProjectDir

    def "updateManifest writes expected manifest fields"() {
        given:
        def project = ProjectBuilder.builder()
                .withProjectDir(testProjectDir)
                .build()
        project.version = '1.2.3'

        def manifestFile = new File(testProjectDir, 'manifest.json')
        manifestFile.text = '''
        {
          "Group": "",
          "Name": "",
          "Version": "",
          "Description": "",
          "Authors": [],
          "Website": "",
          "ServerVersion": "",
          "Dependencies": {},
          "OptionalDependencies": {},
          "DisabledByDefault": false,
          "Main": "",
          "IncludesAssetPack": false,
          "UpdateChecker": {}
        }
        '''

        def taskProvider = project.tasks.register('updatePluginManifestTest', UpdatePluginManifestTask)
        def task = taskProvider.get()
        task.manifestFile.set(manifestFile)
        task.manifestGroup.set('com.example.mods')
        task.modId.set('examplemod')
        task.versionString.set('1.2.3')
        task.modDescription.set('Example mod')
        task.modCredits.set('Alice, Bob')
        task.modUrl.set('https://example.com')
        task.hytaleVersion.set('1.0.0')
        task.manifestDependencies.set('core=1.0.0')
        task.manifestOptionalDependencies.set('helper=2.0.0')
        task.disabledByDefault.set(true)
        task.mainClass.set('com.example.mods.ExampleMod')
        task.includesPack.set(true)
        task.curseforgeId.set('123456')

        when:
        task.updateManifest()

        then:
        def json = new JsonSlurper().parse(manifestFile) as Map
        json.Group == 'com.example.mods'
        json.Name == 'examplemod'
        json.Version == '1.2.3'
        json.Description == 'Example mod'
        json.Authors == [[Name: 'Alice'], [Name: 'Bob']]
        json.Website == 'https://example.com'
        json.ServerVersion == '1.0.0'
        json.Dependencies == [core: '1.0.0']
        json.OptionalDependencies == [helper: '2.0.0']
        json.DisabledByDefault == true
        json.Main == 'com.example.mods.ExampleMod'
        json.IncludesAssetPack == true
        json.UpdateChecker == [CurseForge: '123456']
    }
}