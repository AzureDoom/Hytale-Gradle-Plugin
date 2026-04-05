package com.azuredoom.gradle.hytale

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.*
import org.gradle.work.DisableCachingByDefault

import javax.inject.Inject
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.Locale
import java.util.zip.ZipFile

@DisableCachingByDefault(because = "Downloads authenticated remote assets and may fall back to machine-local installs")
abstract class DownloadAssetsZipTask extends DefaultTask {
    @Input abstract Property<String> getHytaleVersion()
    @Input abstract Property<String> getPatchline()
    @Input abstract Property<String> getOauthBaseUrl()
    @Input abstract Property<String> getAccountBaseUrl()

    @Input
    @Optional
    abstract Property<String> getHytaleHomeOverride()

    @OutputFile abstract RegularFileProperty getResolvedAssetsZip()
    @OutputFile abstract RegularFileProperty getResolvedAssetsWrapper()
    @OutputFile abstract RegularFileProperty getTokenCacheFile()

    @Inject
    protected abstract FileSystemOperations getFs()

    @Inject
    protected abstract ProviderFactory getProviders()

    @Internal
    transient Closure<HttpClient> createHttpClientOverride

    @Internal
    transient Closure<Map> refreshTokenOverride

    @Internal
    transient Closure<Map> startDeviceFlowOverride

    @Internal
    transient Closure<HttpResponse<InputStream>> getJsonOverride

    @Internal
    transient Closure<Void> atomicCopyOverride

    protected HttpClient createHttpClient() {
        if (createHttpClientOverride != null) {
            return (HttpClient) createHttpClientOverride.call()
        }

        HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(30))
                .build()
    }

    protected Map loadTokens(File tokenFile, JsonSlurper json) {
        if (!tokenFile.exists()) {
            return null
        }
        def parsed = json.parse(tokenFile)
        [
                access_token : parsed.accessToken ?: parsed.access_token,
                refresh_token: parsed.refreshToken ?: parsed.refresh_token
        ]
    }

    protected void saveTokens(File tokenFile, Map tokenMap) {
        tokenFile.text = JsonOutput.prettyPrint(JsonOutput.toJson([
                accessToken : tokenMap.access_token,
                refreshToken: tokenMap.refresh_token
        ]))
    }

    protected Map refreshToken(HttpClient client, JsonSlurper json, String oauthBaseUrl, Map tokens) {
        if (refreshTokenOverride != null) {
            return (Map) refreshTokenOverride.call(client, json, oauthBaseUrl, tokens)
        }

        def response = postForm(client, "${oauthBaseUrl}/oauth2/token", [
                client_id    : 'hytale-server',
                grant_type   : 'refresh_token',
                refresh_token: tokens.refresh_token
        ])
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new GradleException("Refresh token failed with HTTP ${response.statusCode()}")
        }
        def parsed = json.parse(response.body())
        [
                access_token : parsed.access_token,
                refresh_token: parsed.refresh_token ?: tokens.refresh_token
        ]
    }

    protected Map startDeviceFlow(HttpClient client, JsonSlurper json, String oauthBaseUrl) {
        if (startDeviceFlowOverride != null) {
            return (Map) startDeviceFlowOverride.call(client, json, oauthBaseUrl)
        }

        def deviceResp = postForm(client, "${oauthBaseUrl}/oauth2/device/auth", [
                client_id: 'hytale-server',
                scope    : 'openid offline auth:server'
        ])
        if (deviceResp.statusCode() < 200 || deviceResp.statusCode() >= 300) {
            throw new GradleException("Device auth start failed with HTTP ${deviceResp.statusCode()}")
        }

        def codeResponse = json.parse(deviceResp.body())
        def verificationUri = codeResponse.verification_uri?.toString()
        def userCode = codeResponse.user_code?.toString()
        def deviceCode = codeResponse.device_code?.toString()
        def verificationUriComplete = codeResponse.verification_uri_complete?.toString()
        long intervalMillis = ((codeResponse.interval ?: 5) as Number).longValue() * 1000L

        if (!verificationUri || !userCode || !deviceCode) {
            throw new GradleException('Device auth response was missing required fields')
        }

        def completeUrl = verificationUriComplete ?: "${verificationUri}?user_code=${URLEncoder.encode(userCode, 'UTF-8')}"

        logger.lifecycle("""\
Starting OAuth device code flow...
===================================================================
Click to authenticate:
${completeUrl}

Or manually:
${verificationUri}
Code: ${userCode}
===================================================================
""".stripIndent().trim())

        long deadline = System.currentTimeMillis() + (100L * 1000L)
        Exception lastError = null
        while (System.currentTimeMillis() < deadline) {
            try {
                def tokenResp = postForm(client, "${oauthBaseUrl}/oauth2/token", [
                        client_id  : 'hytale-server',
                        device_code: deviceCode,
                        grant_type : 'urn:ietf:params:oauth:grant-type:device_code'
                ])
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

    protected HttpResponse<InputStream> postForm(HttpClient client, String url, Map form) {
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

    protected HttpResponse<InputStream> getJson(HttpClient client, String url, String bearerToken) {
        if (getJsonOverride != null) {
            return (HttpResponse<InputStream>) getJsonOverride.call(client, url, bearerToken)
        }

        def request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(60))
                .GET()
                .header('Authorization', "Bearer ${bearerToken}")
                .build()

        client.send(request, HttpResponse.BodyHandlers.ofInputStream())
    }

    protected void validateZipFile(File zipPath, String description) {
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

    protected void atomicCopy(File from, File to) {
        if (atomicCopyOverride != null) {
            atomicCopyOverride.call(from, to)
            return
        }

        Files.createDirectories(to.parentFile.toPath())
        Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }

    protected File resolveLocalAssetsZip(String patchline) {
        def candidates = []
        def override = hytaleHomeOverride.present ? hytaleHomeOverride.get() : null
        def branch = (patchline ?: '').trim()

        def addInstallCandidates = { File homeDir ->
            if (homeDir == null) return
            candidates << new File(homeDir, 'Assets.zip')
            candidates << new File(homeDir, "install/${branch}/package/game/latest/Assets.zip")
            candidates << new File(homeDir, "install/${branch}/game/latest/Assets.zip")
            candidates << new File(homeDir, "install/${branch}/latest/Assets.zip")
            candidates << new File(homeDir, "${branch}/package/game/latest/Assets.zip")
            candidates << new File(homeDir, "${branch}/game/latest/Assets.zip")
        }

        if (override) {
            addInstallCandidates(new File(override))
        } else {
            def userHome = System.getProperty('user.home')
            def osName = System.getProperty('os.name', '').toLowerCase(Locale.ROOT)
            if (osName.contains('win')) {
                addInstallCandidates(new File("${userHome}/AppData/Roaming/Hytale"))
            } else if (osName.contains('mac') || osName.contains('darwin')) {
                addInstallCandidates(new File("${userHome}/Library/Application Support/Hytale"))
            } else if (osName.contains('nux') || osName.contains('nix')) {
                addInstallCandidates(new File("${userHome}/.var/app/com.hypixel.HytaleLauncher/data/Hytale"))
                addInstallCandidates(new File("${userHome}/.local/share/Hytale"))
            }
        }

        candidates.find { it.exists() && it.isFile() && it.length() > 0 }
    }

    @TaskAction
    void download() {
        def version = hytaleVersion.get()
        def patch = patchline.get()
        def oauth = oauthBaseUrl.get()
        def account = accountBaseUrl.get()
        def wrapper = resolvedAssetsWrapper.get().asFile
        def assetsZip = resolvedAssetsZip.get().asFile
        def tokenFile = tokenCacheFile.get().asFile

        wrapper.parentFile.mkdirs()
        assetsZip.parentFile.mkdirs()
        tokenFile.parentFile.mkdirs()

        if (assetsZip.exists() && assetsZip.length() > 0) {
            logger.lifecycle("Using cached extracted Hytale assets zip: ${assetsZip}")
            return
        }

        def json = new JsonSlurper()
        def client = createHttpClient()

        Map tokens = loadTokens(tokenFile, json)
        Map activeTokens = null

        if (tokens?.refresh_token) {
            try {
                activeTokens = refreshToken(client, json, oauth, tokens)
                logger.lifecycle('Refreshed cached Hytale OAuth tokens')
            } catch (Exception e) {
                logger.lifecycle("Cached token refresh failed, starting device auth: ${e.message}")
            }
        }

        if (activeTokens == null) {
            activeTokens = startDeviceFlow(client, json, oauth)
        }

        if (!activeTokens?.access_token) {
            throw new GradleException('Did not obtain a valid Hytale access token')
        }

        if (activeTokens.refresh_token) {
            saveTokens(tokenFile, activeTokens)
        }

        Exception remoteFailure = null
        try {
            def assetLookupUrl = "${account}/game-assets/builds/${patch}/${version}.zip"
            def assetLookupResp = getJson(client, assetLookupUrl, activeTokens.access_token)
            if (assetLookupResp.statusCode() < 200 || assetLookupResp.statusCode() >= 300) {
                throw new GradleException("Asset bundle lookup failed with HTTP ${assetLookupResp.statusCode()} from ${assetLookupUrl}")
            }

            def assetLookup = json.parse(assetLookupResp.body())
            def bundleUrl = assetLookup.url?.toString()
            if (!bundleUrl) {
                throw new GradleException('Asset bundle lookup did not return a download url')
            }

            def tmpWrapper = new File(wrapper.parentFile, wrapper.name + '.part')
            client.send(
                    HttpRequest.newBuilder()
                            .uri(URI.create(bundleUrl))
                            .timeout(Duration.ofMinutes(30))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofFile(tmpWrapper.toPath())
            )

            if (!tmpWrapper.exists() || tmpWrapper.length() == 0) {
                throw new GradleException('Downloaded asset wrapper is empty')
            }

            atomicCopy(tmpWrapper, wrapper)

            def zipFile = new ZipFile(wrapper)
            try {
                def innerEntry = zipFile.getEntry('Assets.zip')
                if (innerEntry == null) {
                    def sample = zipFile.entries().toList().take(20)*.name
                    throw new GradleException("Wrapper did not contain Assets.zip. Found entries: ${sample}")
                }

                def tmpAssetsZip = new File(assetsZip.parentFile, assetsZip.name + '.part')
                tmpAssetsZip.withOutputStream { os ->
                    zipFile.getInputStream(innerEntry).withStream { ins -> os << ins }
                }
                validateZipFile(tmpAssetsZip, 'Extracted Assets.zip')
                atomicCopy(tmpAssetsZip, assetsZip)
            } finally {
                zipFile.close()
            }

            logger.lifecycle("Cached extracted Hytale assets zip at ${assetsZip}")
            return
        } catch (Exception e) {
            remoteFailure = e
            logger.warn("Failed to download remote Hytale assets, trying local install fallback: ${e.message}")
        }

        def localAssetsZip = resolveLocalAssetsZip(patch)
        if (localAssetsZip != null) {
            validateZipFile(localAssetsZip, "Copied Assets.zip from ${localAssetsZip}")
            atomicCopy(localAssetsZip, assetsZip)
            logger.lifecycle("Cached extracted Hytale assets zip at ${assetsZip}")
            return
        }

        throw new GradleException(
                "Failed to resolve Hytale assets from remote bundle and could not find a local Assets.zip fallback",
                remoteFailure
        )
    }
}