package com.azuredoom.gradle.hytale

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import spock.lang.TempDir

import java.net.http.HttpRequest
import java.net.http.HttpClient.Redirect
import java.net.http.HttpClient.Version
import java.net.http.WebSocket
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters
import java.net.http.HttpResponse
import java.net.http.HttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class DownloadAssetsZipTaskTest extends Specification {

	@TempDir
	Path tempDir

	private DownloadAssetsZipTask newTask() {
		def project = ProjectBuilder.builder().withProjectDir(tempDir.toFile()).build()
		def task = project.tasks.register('downloadAssetsZip', DownloadAssetsZipTask).get()
		task.hytaleVersion.set('1.0.0')
		task.patchline.set('release')
		task.oauthBaseUrl.set('https://oauth.example.test')
		task.accountBaseUrl.set('https://account.example.test')
		task.resolvedAssetsWrapper.set(project.layout.projectDirectory.file('build/cache/wrapper.jar'))
		task.resolvedAssetsZip.set(project.layout.projectDirectory.file('build/cache/Assets.zip'))
		task.tokenCacheFile.set(project.layout.projectDirectory.file('build/cache/tokens.json'))
		task
	}

	private static void writeZip(File file, Map<String, byte[]> entries) {
		file.parentFile.mkdirs()
		new ZipOutputStream(file.newOutputStream()).withCloseable { zos ->
			entries.each { name, content ->
				zos.putNextEntry(new ZipEntry(name))
				zos.write(content)
				zos.closeEntry()
			}
		}
	}

	def "uses cached extracted assets zip and exits early"() {
		given:
		def task = newTask()
		writeZip(task.resolvedAssetsZip.get().asFile, ['dummy.txt': 'ok'.bytes])

		def called = false
		task.createHttpClientOverride = {
			called = true
			throw new IllegalStateException('createHttpClient should not be called when cached Assets.zip exists')
		} as Closure<HttpClient>

		when:
		task.download()

		then:
		!called
	}

	def "refresh token success downloads wrapper and extracts Assets zip"() {
		given:
		def task = newTask()
		def wrapper = tempDir.resolve('remote-wrapper.jar').toFile()
		def innerAssets = tempDir.resolve('inner-assets.zip').toFile()
		writeZip(innerAssets, ['asset.txt': 'hello'.bytes])
		writeZip(wrapper, ['Assets.zip': innerAssets.bytes])

		task.tokenCacheFile.get().asFile.parentFile.mkdirs()
		task.tokenCacheFile.get().asFile.text = '{"refreshToken":"refresh-1"}'

		task.refreshTokenOverride = { HttpClient client, JsonSlurper json, String oauthBaseUrl, Map tokens ->
			[access_token: 'access-1', refresh_token: 'refresh-2']
		}
		task.getJsonOverride = { HttpClient client, String url, String bearerToken ->
			fakeJsonResponse('{"url":"https://download.example.test/wrapper.jar"}')
		}
		task.atomicCopyOverride = { File from, File to ->
			to.parentFile.mkdirs()
			Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING)
		} as Closure<Void>
		task.createHttpClientOverride = {
			fakeHttpClient { req, handler ->
				def tmpFile = new File(
						task.resolvedAssetsWrapper.get().asFile.parentFile,
						task.resolvedAssetsWrapper.get().asFile.name + '.part'
						)
				tmpFile.parentFile.mkdirs()
				Files.copy(wrapper.toPath(), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
				[
					statusCode: { -> 200 },
					body      : {
						-> tmpFile.toPath()
					}
				] as HttpResponse<Path>
			}
		}

		when:
		task.download()

		then:
		task.resolvedAssetsZip.get().asFile.exists()
		new ZipFile(task.resolvedAssetsZip.get().asFile).entries().hasMoreElements()
		task.tokenCacheFile.get().asFile.text.contains('refresh-2')
	}

	def "remote failure falls back to local assets zip"() {
		given:
		def task = newTask()
		def localHome = tempDir.resolve('local-hytale').toFile()
		def localAssets = new File(localHome, 'install/release/latest/Assets.zip')
		writeZip(localAssets, ['asset.txt': 'fallback'.bytes])
		task.hytaleHomeOverride.set(localHome.absolutePath)

		task.createHttpClientOverride = {
			fakeHttpClient { req, handler ->
				throw new UnsupportedOperationException('unused')
			}
		}
		task.startDeviceFlowOverride = { HttpClient client, JsonSlurper json, String oauthBaseUrl ->
			[access_token: 'access-1', refresh_token: 'refresh-1']
		}
		task.getJsonOverride = ({ HttpClient client, String url, String bearerToken ->
			throw new GradleException('lookup failed')
		} as Closure)

		when:
		task.download()

		then:
		task.resolvedAssetsZip.get().asFile.exists()
		task.resolvedAssetsZip.get().asFile.length() > 0
	}

	def "fails when wrapper does not contain Assets zip"() {
		given:
		def task = newTask()
		def wrapper = tempDir.resolve('bad-wrapper.jar').toFile()
		writeZip(wrapper, ['not-assets.txt': 'oops'.bytes])
		task.hytaleHomeOverride.set(tempDir.resolve('no-local-hytale').toString())

		task.startDeviceFlowOverride = { HttpClient client, JsonSlurper json, String oauthBaseUrl ->
			[access_token: 'access-1', refresh_token: 'refresh-1']
		}
		task.getJsonOverride = { HttpClient client, String url, String bearerToken ->
			fakeJsonResponse('{"url":"https://download.example.test/wrapper.jar"}')
		}
		task.createHttpClientOverride = {
			fakeHttpClient { req, handler ->
				def tmpFile = new File(
						task.resolvedAssetsWrapper.get().asFile.parentFile,
						task.resolvedAssetsWrapper.get().asFile.name + '.part'
						)
				tmpFile.parentFile.mkdirs()
				Files.copy(wrapper.toPath(), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
				[
					statusCode: { -> 200 },
					body      : {
						-> tmpFile.toPath()
					}
				] as HttpResponse<Path>
			}
		}

		when:
		task.download()

		then:
		def ex = thrown(GradleException)
		ex.message.contains('Failed to resolve Hytale assets')
		ex.cause.message.contains('Wrapper did not contain Assets.zip')
	}

	private static HttpClient fakeHttpClient(Closure sendImpl) {
		return new HttpClient() {
					@Override
					Optional<CookieHandler> cookieHandler() {
						Optional.empty()
					}

					@Override
					Optional<Duration> connectTimeout() {
						Optional.of(Duration.ofSeconds(30))
					}

					@Override
					Redirect followRedirects() {
						Redirect.NORMAL
					}

					@Override
					Optional<ProxySelector> proxy() {
						Optional.empty()
					}

					@Override
					SSLContext sslContext() {
						SSLContext ctx = SSLContext.getInstance('TLS')
						ctx.init(null, null, new SecureRandom())
						ctx
					}

					@Override
					SSLParameters sslParameters() {
						new SSLParameters()
					}

					@Override
					Optional<Authenticator> authenticator() {
						Optional.empty()
					}

					@Override
					Version version() {
						Version.HTTP_1_1
					}

					@Override
					Optional<Executor> executor() {
						Optional.empty()
					}

					@Override
					<T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
						(HttpResponse<T>) sendImpl.call(request, responseBodyHandler)
					}

					@Override
					<T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
						CompletableFuture.completedFuture((HttpResponse<T>) sendImpl.call(request, responseBodyHandler))
					}

					@Override
					<T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler, HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
						CompletableFuture.completedFuture((HttpResponse<T>) sendImpl.call(request, responseBodyHandler))
					}

					@Override
					WebSocket.Builder newWebSocketBuilder() {
						throw new UnsupportedOperationException('unused')
					}
				}
	}

	private static HttpResponse<String> fakeJsonResponse(String body) {
		[
			statusCode: { -> 200 },
			body      : {
				-> body
			}
		] as HttpResponse<String>
	}
}