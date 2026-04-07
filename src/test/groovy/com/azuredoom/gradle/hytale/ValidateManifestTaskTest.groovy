package com.azuredoom.gradle.hytale

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class ValidateManifestTaskTest extends Specification {

	@TempDir
	File testProjectDir

	def "passes for a valid manifest"() {
		given:
		def project = ProjectBuilder.builder()
				.withProjectDir(testProjectDir)
				.build()

		def manifestFile = new File(testProjectDir, 'src/main/resources/manifest.json')
		manifestFile.parentFile.mkdirs()
		manifestFile.text = '''
        {
          "Group": "com.example.mods",
          "Name": "demo-mod",
          "Version": "1.0.0",
          "Description": "Demo mod",
          "Authors": [{"Name":"Alice"}],
          "Website": "https://example.com",
          "ServerVersion": "1.0.0",
          "Dependencies": {"core":"1.0.0"},
          "OptionalDependencies": {},
          "DisabledByDefault": false,
          "Main": "com.example.mods.DemoMod",
          "IncludesAssetPack": false,
          "UpdateChecker": {}
        }
        '''

		def taskProvider = project.tasks.register('validateManifestTest', ValidateManifestTask)
		def task = taskProvider.get()
		task.manifestFile.set(manifestFile)
		task.manifestGroup.set('com.example.mods')
		task.modId.set('demo-mod')
		task.mainClass.set('com.example.mods.DemoMod')
		task.hytaleVersion.set('1.0.0')
		task.manifestDependencies.set('core=1.0.0')
		task.manifestOptionalDependencies.set('')
		task.includesPack.set(false)

		when:
		task.validateManifest()

		then:
		noExceptionThrown()
	}

	def "fails for missing required fields"() {
		given:
		def project = ProjectBuilder.builder()
				.withProjectDir(testProjectDir)
				.build()

		def manifestFile = new File(testProjectDir, 'src/main/resources/manifest.json')
		manifestFile.parentFile.mkdirs()
		manifestFile.text = '''
        {
          "Group": "",
          "Name": "",
          "Version": "1.0.0",
          "ServerVersion": "",
          "Dependencies": {},
          "OptionalDependencies": {},
          "Main": "",
          "IncludesAssetPack": false
        }
        '''

		def taskProvider = project.tasks.register('validateManifestTest', ValidateManifestTask)
		def task = taskProvider.get()
		task.manifestFile.set(manifestFile)
		task.manifestGroup.set('com.example.mods')
		task.modId.set('demo-mod')
		task.mainClass.set('com.example.mods.DemoMod')
		task.hytaleVersion.set('1.0.0')
		task.manifestDependencies.set('')
		task.manifestOptionalDependencies.set('')
		task.includesPack.set(false)

		when:
		task.validateManifest()

		then:
		def ex = thrown(GradleException)
		ex.message.contains("Manifest field 'Group' is missing or blank.")
		ex.message.contains("Manifest field 'Name' is missing or blank.")
		ex.message.contains("Manifest field 'ServerVersion' is missing or blank.")
		ex.message.contains("Manifest field 'Main' is missing or blank.")
	}
}