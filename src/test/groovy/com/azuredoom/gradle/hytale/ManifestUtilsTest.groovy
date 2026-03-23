package com.azuredoom.gradle.hytale

import spock.lang.Specification

class ManifestUtilsTest extends Specification {

    def "parseDepMap handles empty input"() {
        expect:
        ManifestUtils.parseDepMap(null) == [:]
        ManifestUtils.parseDepMap('') == [:]
    }

    def "parseDepMap parses comma separated key value pairs"() {
        expect:
        ManifestUtils.parseDepMap('modA=1.0.0, modB = 2.0.0') == [
                modA: '1.0.0',
                modB: '2.0.0'
        ]
    }

    def "parseDepMap ignores malformed entries"() {
        expect:
        ManifestUtils.parseDepMap('modA=1.0.0, brokenEntry, modB=2.0.0') == [
                modA: '1.0.0',
                modB: '2.0.0'
        ]
    }

    def "parseAuthors returns null for empty input"() {
        expect:
        ManifestUtils.parseAuthors(null) == null
        ManifestUtils.parseAuthors('') == null
    }

    def "parseAuthors returns manifest author objects"() {
        expect:
        ManifestUtils.parseAuthors('Alice, Bob') == [
                [Name: 'Alice'],
                [Name: 'Bob']
        ]
    }
}