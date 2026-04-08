package com.azuredoom.gradle.hytale

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS

class HytaleJvmDoctorTaskFunctionalTest extends Specification {

	@TempDir
	File projectDir

	def "hytaleJvmDoctor runs and outputs expected info"() {
		given:
		new File(projectDir, "settings.gradle") << """
rootProject.name = "test-project"
"""
		new File(projectDir, "build.gradle") << """
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools'
}

hytaleTools {
    hytaleVersion = '1.0.0'
    manifestGroup = 'com.test'
    modId = 'testmod'
    mainClass = 'com.test.Main'
}
"""

		when:
		def result = GradleRunner.create()
				.withProjectDir(projectDir)
				.withPluginClasspath()
				.withArguments("hytaleJvmDoctor")
				.build()

		then:
		result.task(":hytaleJvmDoctor").outcome == SUCCESS
		result.output.contains("Java executable:")
		result.output.contains("Resolution source:")
		result.output.contains("JetBrains Runtime detected:")
	}

	def "hytaleJvmDoctor respects configured jbrHome"() {
		given:
		if (isWindows()) {
			return
		}

		File fakeJbr = new File(projectDir, "fake-jbr")
		File bin = new File(fakeJbr, "bin")
		assert bin.mkdirs()

		File fakeJava = new File(bin, "java")
		fakeJava.text = '''#!/bin/sh
if [ "$1" = "-XshowSettings:properties" ]; then
  echo "java.vendor = JetBrains" 1>&2
  echo "java.vm.vendor = JetBrains" 1>&2
  exit 0
fi

if [ "$1" = "-XX:+AllowEnhancedClassRedefinition" ]; then
  exit 0
fi

if [ "$1" = "-XX:HotswapAgent=fatjar" ]; then
  exit 0
fi

exit 0
'''
		assert fakeJava.setExecutable(true)

		new File(projectDir, "settings.gradle") << "rootProject.name='test'"
		new File(projectDir, "build.gradle") << """
plugins {
    id 'java'
    id 'com.azuredoom.hytale-tools'
}

hytaleTools {
    hytaleVersion = '1.0.0'
    manifestGroup = 'com.test'
    modId = 'testmod'
    mainClass = 'com.test.Main'
    jbrHome = '${fakeJbr.absolutePath.replace("\\", "\\\\")}'
}
"""

		when:
		def result = GradleRunner.create()
				.withProjectDir(projectDir)
				.withPluginClasspath()
				.withArguments("hytaleJvmDoctor")
				.build()

		then:
		result.task(":hytaleJvmDoctor").outcome == SUCCESS
		result.output.contains("Resolution source: configured:jbrHome")
		result.output.contains("Java home: ${fakeJbr.absolutePath}")
	}

	private static boolean isWindows() {
		System.getProperty("os.name").toLowerCase().contains("win")
	}
}