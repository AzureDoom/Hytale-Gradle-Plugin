package com.azuredoom.gradle.hytale

import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

final class HytaleRepositoryConfigurer {
    private HytaleRepositoryConfigurer() {}

    static void configure(Project project) {
        project.repositories.mavenCentral()

        addMavenRepo(project, 'Hytale Server Release', 'https://maven.hytale.com/release')
        addMavenRepo(project, 'Hytale Server Pre-Release', 'https://maven.hytale.com/pre-release')
        addMavenRepo(project, 'Hytale-Mods.info Maven', 'https://maven.hytale-mods.dev/releases')
        addMavenRepo(project, 'PlaceholderAPI', 'https://repo.helpch.at/releases/')
        addMavenRepo(project, 'CurseMaven', 'https://cursemaven.com')
        addMavenRepo(project, 'AzureDoom Maven', 'https://maven.azuredoom.com/mods')
        addMavenRepo(project, 'Hytale Modding Maven', 'https://maven.hytalemodding.dev/releases')

        project.repositories.exclusiveContent { spec ->
            spec.forRepository {
                project.repositories.ivy { IvyArtifactRepository repo ->
                    repo.name = 'Modtale'
                    repo.url = project.uri('https://api.modtale.net/api/v1')
                    repo.patternLayout { layout ->
                        layout.artifact('projects/[module]/versions/[revision]/download')
                    }
                    repo.metadataSources { sources ->
                        sources.artifact()
                    }
                }
            }
            spec.filter { filter ->
                filter.includeGroup('modtale')
            }
        }
    }

    private static void addMavenRepo(Project project, String repoName, String repoUrl) {
        project.repositories.maven { MavenArtifactRepository repo ->
            repo.name = repoName
            repo.url = project.uri(repoUrl)
        }
    }
}
