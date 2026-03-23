package com.azuredoom.gradle.hytale

import groovy.json.JsonSlurper
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

class CreateModSkeletonTaskTest extends Specification {

    @TempDir
    File testProjectDir

    def "creates starter source file and manifest"() {
        given:
        def project = ProjectBuilder.builder()
                .withProjectDir(testProjectDir)
                .build()

        def task = project.tasks.create('createModSkeletonTest', CreateModSkeletonTask)
        task.javaSourceDirectory.set(project.layout.projectDirectory.dir('src/main/java'))
        task.resourcesDirectory.set(project.layout.projectDirectory.dir('src/main/resources'))
        task.manifestFile.set(project.layout.projectDirectory.file('src/main/resources/manifest.json'))
        task.manifestGroup.set('com.example.mods')
        task.modId.set('demo-mod')
        task.mainClass.set('com.example.mods.DemoMod')
        task.modDescription.set('Demo mod')
        task.modUrl.set('https://example.com')
        task.modCredits.set('Alice, Bob')
        task.hytaleVersion.set('1.0.0')
        task.includesPack.set(true)
        task.disabledByDefault.set(false)

        when:
        task.createSkeleton()

        then:
        new File(testProjectDir, 'src/main/java/com/example/mods/DemoMod.java').exists()
        new File(testProjectDir, 'src/main/resources/manifest.json').exists()
        new File(testProjectDir, 'src/main/resources/pack/.gitkeep').exists()

        and:
        def json = new JsonSlurper().parse(new File(testProjectDir, 'src/main/resources/manifest.json'))
        json.Group == 'com.example.mods'
        json.Name == 'demo-mod'
        json.Main == 'com.example.mods.DemoMod'
        json.ServerVersion == '1.0.0'
        json.IncludesAssetPack == true
    }
}