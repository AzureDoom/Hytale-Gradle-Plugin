package com.azuredoom.gradle.hytale

import groovy.json.JsonSlurper
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class CreateManifestIfMissingTaskTest extends Specification {

	@TempDir
	File testProjectDir

	def "creates a default manifest when missing"() {
		given:
		def project = ProjectBuilder.builder()
				.withProjectDir(testProjectDir)
				.build()

		def manifestFile = new File(testProjectDir, 'src/main/resources/manifest.json')
		def taskProvider = project.tasks.register('createManifestIfMissingTest', CreateManifestIfMissingTask)
		def task = taskProvider.get()
		task.manifestFile.set(manifestFile)

		when:
		task.createIfMissing()

		then:
		manifestFile.exists()

		and:
		def json = new JsonSlurper().parse(manifestFile) as Map
		json == [
			Group               : '',
			Name                : '',
			Version             : '',
			Description         : '',
			Authors             : [],
			Website             : '',
			ServerVersion       : '',
			Dependencies        : [:],
			OptionalDependencies: [:],
			DisabledByDefault   : false,
			Main                : '',
			IncludesAssetPack   : false,
			UpdateChecker       : [:]
		]
	}

	def "does not overwrite an existing manifest"() {
		given:
		def project = ProjectBuilder.builder()
				.withProjectDir(testProjectDir)
				.build()

		def manifestFile = new File(testProjectDir, 'src/main/resources/manifest.json')
		manifestFile.parentFile.mkdirs()
		manifestFile.text = '{"Name":"already-there"}'

		def taskProvider = project.tasks.register('createManifestIfMissingTest', CreateManifestIfMissingTask)
		def task = taskProvider.get()
		task.manifestFile.set(manifestFile)

		when:
		task.createIfMissing()

		then:
		manifestFile.text == '{"Name":"already-there"}'
	}
}