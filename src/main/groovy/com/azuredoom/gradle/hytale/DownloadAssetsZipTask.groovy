package com.azuredoom.gradle.hytale

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.zip.ZipFile

abstract class DownloadAssetsZipTask extends DefaultTask {
    @Input abstract Property<String> getHytaleVersion()
    @Input abstract Property<String> getPatchline()
    @Input abstract Property<String> getOauthBaseUrl()
    @Input abstract Property<String> getAccountBaseUrl()

    @OutputFile
    abstract RegularFileProperty getResolvedAssetsZip()

    @OutputFile
    abstract RegularFileProperty getResolvedAssetsWrapper()

    @OutputFile
    abstract RegularFileProperty getTokenCacheFile()

    @TaskAction
    void download() {
        def oauthBaseUrl = this.oauthBaseUrl.get()
        def accountBaseUrl = this.accountBaseUrl.get()
        def patchline = this.patchline.get()
        def version = this.hytaleVersion.get()

        def resolvedWrapper = resolvedAssetsWrapper.get().asFile
        def resolvedAssetsZip = resolvedAssetsZip.get().asFile
        def tokenCacheFile = this.tokenCacheFile.get().asFile

        resolvedWrapper.parentFile.mkdirs()
        resolvedAssetsZip.parentFile.mkdirs()
        tokenCacheFile.parentFile.mkdirs()

        if (resolvedAssetsZip.exists() && resolvedAssetsZip.length() > 0) {
            logger.lifecycle("Using cached extracted Hytale assets zip: ${resolvedAssetsZip}")
            return
        }

        def json = new JsonSlurper()
        def client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()

        def postForm = { String url, Map<String, String> form ->
            def encoded = form.collect { k, v ->
                "${URLEncoder.encode(k, 'UTF-8')}=${URLEncoder.encode(v, 'UTF-8')}"
            }.join('&')

            def request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .header('Content-Type', 'application/x-www-form-urlencoded')
                    .POST(HttpRequest.BodyPublishers.ofString(encoded))
                    .build()

            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        }

        def getJson = { String url, String bearerToken ->
            def request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .header('Authorization', "Bearer ${bearerToken}")
                    .build()

            client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        }

        def loadTokens = {
            if (!tokenCacheFile.exists()) {
                return null
            }
            def parsed = json.parse(tokenCacheFile)
            [
                    access_token : parsed.accessToken ?: parsed.access_token,
                    refresh_token: parsed.refreshToken ?: parsed.refresh_token
            ]
        }

        def saveTokens = { Map tokenMap ->
            tokenCacheFile.text = JsonOutput.prettyPrint(JsonOutput.toJson([
                    accessToken : tokenMap.access_token,
                    refreshToken: tokenMap.refresh_token
            ]))
        }

        def refreshToken = { Map tokens ->
            def response = postForm(
                    "${oauthBaseUrl}/oauth2/token",
                    [
                            client_id    : 'hytale-server',
                            grant_type   : 'refresh_token',
                            refresh_token: tokens.refresh_token
                    ]
            )

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new GradleException("Refresh token failed with HTTP ${response.statusCode()}")
            }

            def parsed = json.parse(response.body())
            [
                    access_token : parsed.access_token,
                    refresh_token: parsed.refresh_token ?: tokens.refresh_token
            ]
        }

        def startDeviceFlow = {
            def deviceResp = postForm(
                    "${oauthBaseUrl}/oauth2/device/auth",
                    [
                            client_id: 'hytale-server',
                            scope    : 'openid offline auth:server'
                    ]
            )

            if (deviceResp.statusCode() < 200 || deviceResp.statusCode() >= 300) {
                throw new GradleException("Device auth start failed with HTTP ${deviceResp.statusCode()}")
            }

            def codeResponse = json.parse(deviceResp.body())
            def verificationUri = codeResponse.verification_uri?.toString()
            def userCode = codeResponse.user_code?.toString()
            def deviceCode = codeResponse.device_code?.toString()
            long intervalMillis = ((codeResponse.interval ?: 5) as Number).longValue() * 1000L

            if (!verificationUri || !userCode || !deviceCode) {
                throw new GradleException('Device auth response was missing required fields')
            }

            logger.lifecycle('''
Starting OAuth device code flow...
===================================================================
Open this URL in your browser:
''' + verificationUri + '''

Then enter this code:
''' + userCode + '''
===================================================================
'''.stripIndent().trim())

            long deadline = System.currentTimeMillis() + (100L * 1000L)
            Exception lastError = null

            while (System.currentTimeMillis() < deadline) {
                try {
                    def tokenResp = postForm(
                            "${oauthBaseUrl}/oauth2/token",
                            [
                                    client_id  : 'hytale-server',
                                    device_code: deviceCode,
                                    grant_type : 'urn:ietf:params:oauth:grant-type:device_code'
                            ]
                    )

                    if (tokenResp.statusCode() >= 200 && tokenResp.statusCode() < 300) {
                        def parsed = json.parse(tokenResp.body())
                        return [
                                access_token : parsed.access_token,
                                refresh_token: parsed.refresh_token
                        ]
                    }
                } catch (Exception e) {
                    lastError = e
                }

                sleep(intervalMillis)
            }

            throw new GradleException('Timed out waiting for device auth to complete', lastError)
        }

        def addInstallCandidates = { List<File> candidates, File homeDir, String branch ->
            if (homeDir == null) {
                return
            }

            candidates << new File(homeDir, 'Assets.zip')
            candidates << new File(homeDir, "install/${branch}/package/game/latest/Assets.zip")
            candidates << new File(homeDir, "install/${branch}/game/latest/Assets.zip")
            candidates << new File(homeDir, "install/${branch}/latest/Assets.zip")
            candidates << new File(homeDir, "${branch}/package/game/latest/Assets.zip")
            candidates << new File(homeDir, "${branch}/game/latest/Assets.zip")
        }

        def resolveLocalAssetsZip = {
            def override = project.findProperty('hytale_home')?.toString()
            def candidates = []
            def branch = (patchline ?: '').trim()

            if (override) {
                def homeDir = new File(override)
                addInstallCandidates(candidates, homeDir, branch)
            } else {
                def userHome = System.getProperty('user.home')
                def osName = System.getProperty('os.name', '').toLowerCase(Locale.ROOT)

                if (osName.contains('win')) {
                    addInstallCandidates(candidates, new File("${userHome}/AppData/Roaming/Hytale"), branch)
                } else if (osName.contains('mac') || osName.contains('darwin')) {
                    addInstallCandidates(candidates, new File("${userHome}/Library/Application Support/Hytale"), branch)
                } else if (osName.contains('nux') || osName.contains('nix')) {
                    addInstallCandidates(candidates, new File("${userHome}/.var/app/com.hypixel.HytaleLauncher/data/Hytale"), branch)
                    addInstallCandidates(candidates, new File("${userHome}/.local/share/Hytale"), branch)
                }
            }

            candidates = candidates.findAll { it != null }
            def uniqueCandidates = []
            def seen = [] as Set
            candidates.each { candidate ->
                def key = candidate.absolutePath
                if (seen.add(key)) {
                    uniqueCandidates << candidate
                }
            }

            logger.lifecycle("Checking local Hytale Assets.zip fallback paths for patchline '${branch}':")
            uniqueCandidates.each { candidate ->
                logger.lifecycle(" - ${candidate.absolutePath}")
            }

            uniqueCandidates.find { it.exists() && it.isFile() && it.length() > 0 }
        }

        def validateZipFile = { File zipPath, String description ->
            ZipFile zip = null
            try {
                zip = new ZipFile(zipPath)
                if (!zip.entries().hasMoreElements()) {
                    throw new GradleException("${description} has no entries")
                }
            } finally {
                zip?.close()
            }
        }

        def copyResolvedAssetsZip = { File source ->
            def tmpAssetsZip = new File(resolvedAssetsZip.parentFile, resolvedAssetsZip.name + '.part')
            if (tmpAssetsZip.exists()) {
                tmpAssetsZip.delete()
            }

            project.copy {
                from source
                into resolvedAssetsZip.parentFile
                rename { tmpAssetsZip.name }
            }

            if (!tmpAssetsZip.exists() || tmpAssetsZip.length() == 0) {
                tmpAssetsZip.delete()
                throw new GradleException("Copied Assets.zip is empty from ${source}")
            }

            validateZipFile(tmpAssetsZip, "Copied Assets.zip from ${source}")

            if (resolvedAssetsZip.exists()) {
                resolvedAssetsZip.delete()
            }

            if (!tmpAssetsZip.renameTo(resolvedAssetsZip)) {
                project.copy {
                    from tmpAssetsZip
                    into resolvedAssetsZip.parentFile
                    rename { resolvedAssetsZip.name }
                }
                tmpAssetsZip.delete()
            }
        }

        Map tokens = loadTokens()
        Map activeTokens = null

        if (tokens?.refresh_token) {
            try {
                activeTokens = refreshToken(tokens)
                logger.lifecycle('Refreshed cached Hytale OAuth tokens')
            } catch (Exception e) {
                logger.lifecycle("Cached token refresh failed, starting device auth: ${e.message}")
            }
        }

        if (activeTokens == null) {
            activeTokens = startDeviceFlow()
        }

        if (!activeTokens?.access_token) {
            throw new GradleException('Did not obtain a valid Hytale access token')
        }

        if (activeTokens.refresh_token) {
            saveTokens(activeTokens)
        }

        Exception remoteFailure = null

        try {
            def assetLookupUrl = "${accountBaseUrl}/game-assets/builds/${patchline}/${version}.zip"
            def assetLookupResp = getJson(assetLookupUrl, activeTokens.access_token)

            if (assetLookupResp.statusCode() < 200 || assetLookupResp.statusCode() >= 300) {
                throw new GradleException("Asset bundle lookup failed with HTTP ${assetLookupResp.statusCode()} from ${assetLookupUrl}")
            }

            def assetLookup = json.parse(assetLookupResp.body())
            def bundleUrl = assetLookup.url?.toString()
            if (!bundleUrl) {
                throw new GradleException('Asset bundle lookup did not return a download url')
            }

            logger.lifecycle("Downloading Hytale asset wrapper for ${patchline}-${version}...")
            logger.lifecycle("Resolved asset download URL: ${bundleUrl}")

            def tmpWrapper = new File(resolvedWrapper.parentFile, resolvedWrapper.name + '.part')
            if (tmpWrapper.exists()) {
                tmpWrapper.delete()
            }

            def downloadReq = HttpRequest.newBuilder()
                    .uri(URI.create(bundleUrl))
                    .timeout(Duration.ofMinutes(30))
                    .GET()
                    .build()

            def downloadResp = client.send(downloadReq, HttpResponse.BodyHandlers.ofFile(tmpWrapper.toPath()))
            if (downloadResp.statusCode() < 200 || downloadResp.statusCode() >= 300) {
                tmpWrapper.delete()
                throw new GradleException("Asset wrapper download failed with HTTP ${downloadResp.statusCode()}")
            }

            if (!tmpWrapper.exists() || tmpWrapper.length() == 0) {
                tmpWrapper.delete()
                throw new GradleException('Downloaded asset wrapper is empty')
            }

            if (resolvedWrapper.exists()) {
                resolvedWrapper.delete()
            }

            if (!tmpWrapper.renameTo(resolvedWrapper)) {
                project.copy {
                    from tmpWrapper
                    into resolvedWrapper.parentFile
                    rename { resolvedWrapper.name }
                }
                tmpWrapper.delete()
            }

            def zipFile = new ZipFile(resolvedWrapper)
            try {
                def innerEntry = zipFile.getEntry('Assets.zip')
                if (innerEntry == null) {
                    def sample = zipFile.entries().toList().take(20)*.name
                    throw new GradleException("Wrapper did not contain Assets.zip. Found entries: ${sample}")
                }

                def tmpAssetsZip = new File(resolvedAssetsZip.parentFile, resolvedAssetsZip.name + '.part')
                if (tmpAssetsZip.exists()) {
                    tmpAssetsZip.delete()
                }

                tmpAssetsZip.withOutputStream { os ->
                    zipFile.getInputStream(innerEntry).withStream { ins ->
                        os << ins
                    }
                }

                if (!tmpAssetsZip.exists() || tmpAssetsZip.length() == 0) {
                    tmpAssetsZip.delete()
                    throw new GradleException('Extracted Assets.zip is empty')
                }

                validateZipFile(tmpAssetsZip, 'Extracted Assets.zip')

                if (resolvedAssetsZip.exists()) {
                    resolvedAssetsZip.delete()
                }

                if (!tmpAssetsZip.renameTo(resolvedAssetsZip)) {
                    project.copy {
                        from tmpAssetsZip
                        into resolvedAssetsZip.parentFile
                        rename { resolvedAssetsZip.name }
                    }
                    tmpAssetsZip.delete()
                }
            } finally {
                zipFile.close()
            }

            logger.lifecycle("Cached extracted Hytale assets zip at ${resolvedAssetsZip}")
            return
        } catch (Exception e) {
            remoteFailure = e
            logger.warn("Failed to download remote Hytale assets, trying local install fallback: ${e.message}")
        }

        def localAssetsZip = resolveLocalAssetsZip()
        if (localAssetsZip != null) {
            logger.lifecycle("Using local Hytale Assets.zip fallback: ${localAssetsZip.absolutePath}")
            copyResolvedAssetsZip(localAssetsZip)
            logger.lifecycle("Cached extracted Hytale assets zip at ${resolvedAssetsZip}")
            return
        }

        throw new GradleException(
                "Failed to resolve Hytale assets from remote bundle and could not find a local Assets.zip fallback",
                remoteFailure
        )
    }
}