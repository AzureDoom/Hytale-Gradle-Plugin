package com.azuredoom.gradle.hytale

import org.gradle.api.logging.Logger

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import java.util.regex.Pattern

final class HytaleSourceJavadocInjector {
	private HytaleSourceJavadocInjector() {}

	private static final int FETCH_THREADS = 8

	static void inject(File sourcesDir, String docsBaseUrl, File cacheDir, Logger logger) {
		if (!sourcesDir.isDirectory()) {
			logger.warn("Hytale Javadocs injection skipped; sources dir does not exist: {}", sourcesDir)
			return
		}

		File apiRoot = new File(sourcesDir, 'com/hypixel/hytale/server')

		if (!apiRoot.isDirectory()) {
			logger.warn("Hytale Javadocs injection skipped; API root does not exist: {}", apiRoot)
			return
		}

		List<File> javaFiles = []
		apiRoot.eachFileRecurse { File f ->
			if (f.name.endsWith('.java')) {
				javaFiles.add(f)
			}
		}

		if (javaFiles.isEmpty()) {
			logger.lifecycle("Hytale Javadocs injection finished: no Java files found")
			return
		}

		prefetchAll(javaFiles, sourcesDir, docsBaseUrl, cacheDir, logger)

		AtomicInteger changed = new AtomicInteger(0)

		javaFiles.each { File javaFile ->
			if (injectIntoFile(javaFile, sourcesDir, docsBaseUrl, cacheDir, logger)) {
				changed.incrementAndGet()
			}
		}

		logger.lifecycle("Hytale Javadocs injection finished: {} changed / {} visited", changed.get(), javaFiles.size())
	}

	private static void prefetchAll(List<File> javaFiles, File sourcesDir, String docsBaseUrl, File cacheDir, Logger logger) {
		String normalizedBase = BasicUtils.normalizeBaseUrl(docsBaseUrl)

		List<String> toFetch = javaFiles.collectMany { File javaFile ->
			if (javaFile.getText('UTF-8').contains('<strong>Hytale API docs:</strong>')) {
				return []
			}

			String fqcn = BasicUtils.fqcnForJavaFile(javaFile, sourcesDir)
			if (fqcn == null) return []

			File cached = BasicUtils.cacheFileForClass(cacheDir, fqcn, '.html')
			File missing = BasicUtils.cacheFileForClass(cacheDir, fqcn, '.missing')

			return (cached.isFile() || missing.isFile()) ? [] : [fqcn]
		} as List<String>

		if (toFetch.isEmpty()) return

			logger.lifecycle("Hytale Javadocs: prefetching {} uncached pages with {} threads...", toFetch.size(), FETCH_THREADS)

		def pool = Executors.newFixedThreadPool(FETCH_THREADS)
		try {
			def futures = toFetch.collect { String fqcn ->
				pool.submit({
					fetchDocsHtml(fqcn, normalizedBase, cacheDir, logger)
				} as Runnable)
			}

			futures.each { Future<?> future ->
				future.get()
			}
		} finally {
			pool.shutdown()
		}
	}

	private static boolean injectIntoFile(File javaFile, File sourcesDir, String docsBaseUrl, File cacheDir, Logger logger) {
		String source = javaFile.getText('UTF-8')
		if (source.contains('<strong>Hytale API docs:</strong>')) {
			return false
		}

		String fqcn = BasicUtils.fqcnForJavaFile(javaFile, sourcesDir)
		if (fqcn == null) {
			return false
		}

		String normalizedBase = BasicUtils.normalizeBaseUrl(docsBaseUrl)
		String html = fetchDocsHtml(fqcn, normalizedBase, cacheDir, logger)
		if (!html) {
			return false
		}

		String className = javaFile.name.substring(0, javaFile.name.length() - '.java'.length())

		ClassDocs docs = parseDocs(html, className)
		if (docs == null || (!docs.classDoc && docs.members.isEmpty())) {
			logger.info("No parseable hosted Hytale Javadocs found for {}", fqcn)
			return false
		}

		String updated = source

		if (docs.classDoc) {
			updated = injectClassJavadoc(updated, className, docs.classDoc)
		}

		docs.members.each { MemberDocs member ->
			if (member.constructor) {
				updated = injectConstructorJavadoc(updated, className, member)
			} else {
				updated = injectMethodJavadoc(updated, member)
			}
		}

		if (updated != source) {
			javaFile.setText(updated, 'UTF-8')
			logger.lifecycle("Injected Hytale Javadocs into {}", javaFile.name)
			return true
		}

		return false
	}

	private static String fetchDocsHtml(String fqcn, String normalizedBase, File cacheDir, Logger logger) {
		String path = fqcn.replace('.', '/')

		File cached  = BasicUtils.cacheFileForClass(cacheDir, fqcn, '.html')
		File missing = BasicUtils.cacheFileForClass(cacheDir, fqcn, '.missing')

		if (cached.isFile())  return cached.getText('UTF-8')
		if (missing.isFile()) return null

		cached.parentFile.mkdirs()
		missing.parentFile.mkdirs()

		URI uri = new URI(normalizedBase).resolve(path + '.html')
		HttpURLConnection connection = null

		try {
			connection = (HttpURLConnection) uri.toURL().openConnection()
			connection.setConnectTimeout(1500)
			connection.setReadTimeout(3000)
			connection.setRequestMethod('GET')
			connection.setInstanceFollowRedirects(true)

			int status = connection.responseCode

			if (status == 404) {
				BasicUtils.atomicWrite(missing, uri.toString())
				return null
			}

			if (status < 200 || status >= 300) {
				logger.info("No hosted Hytale Javadocs found at {}: HTTP {}", uri, status)
				BasicUtils.atomicWrite(missing, "${uri} HTTP ${status}")
				return null
			}

			String html = connection.inputStream.getText('UTF-8')
			BasicUtils.atomicWrite(cached, html)
			return html
		} catch (Throwable throwable) {
			logger.info("No hosted Hytale Javadocs found at {}: {}", uri, throwable.message)
			BasicUtils.atomicWrite(missing, "${uri} ${throwable.class.name}: ${throwable.message}")
			return null
		} finally {
			connection?.disconnect()
		}
	}


	private static ClassDocs parseDocs(String html, String className) {
		ClassDocs docs = new ClassDocs()
		docs.classDoc = extractClassDescription(html)

		extractDetailSections(html).each { DetailSection section ->
			MemberDocs member = parseMemberSection(section, className)
			if (member != null) {
				docs.members.add(member)
			}
		}

		return docs
	}

	private static String extractClassDescription(String html) {
		def sectionMatcher = html =~ /(?s)<section\s+class="class-description"[^>]*>(.*?)<\/section>/
		if (sectionMatcher.find()) {
			String sectionHtml = sectionMatcher.group(1)
			def blockMatcher = sectionHtml =~ /(?s)<div\s+class="block">(.*?)<\/div>/
			if (blockMatcher.find()) {
				return BasicUtils.cleanHtml(blockMatcher.group(1))
			}
			return null
		}

		String classRegion = extractClassRegion(html)

		def h1Matcher = classRegion =~ /(?s)<h1[^>]*>\s*Class\s+[^<]+<\/h1>.*?<div\s+class="block">(.*?)<\/div>/
		if (h1Matcher.find()) {
			return BasicUtils.cleanHtml(h1Matcher.group(1))
		}

		def typeSigMatcher = classRegion =~ /(?s)<div\s+class="type-signature"[^>]*>.*?<\/div>\s*<div\s+class="block">(.*?)<\/div>/
		if (typeSigMatcher.find()) {
			return BasicUtils.cleanHtml(typeSigMatcher.group(1))
		}

		return null
	}

	private static String extractClassRegion(String html) {
		int detailStart = html.indexOf('<section class="detail"')
		if (detailStart > 0) {
			return html.substring(0, detailStart)
		}

		def cutMatcher = html =~ /(?i)<h2[^>]*>\s*(Method|Field|Constructor)\s+Detail\s*<\/h2>/
		if (cutMatcher.find()) {
			return html.substring(0, cutMatcher.start())
		}

		return html
	}

	private static List<DetailSection> extractDetailSections(String html) {
		List<DetailSection> sections = []

		Pattern sectionPattern = Pattern.compile(
				/(?s)<section\s+class="detail"\s+id="([^"]+)"[^>]*>(.*?)(?=<section\s+class="detail"\s+id="|<\/section>\s*<\/li>|<\/ul>|<\/main>)/
				)

		Matcher matcher = sectionPattern.matcher(html)
		while (matcher.find()) {
			sections.add(new DetailSection(
					id: BasicUtils.decodeHtml(matcher.group(1)),
					html: matcher.group(2)
					))
		}

		return sections
	}

	private static MemberDocs parseMemberSection(DetailSection section, String className) {
		String name = extractMemberName(section, className)
		if (!name) {
			return null
		}

		MemberDocs member = new MemberDocs()
		member.name = name
		member.constructor = name == className

		String description = extractFirstBlock(section.html)
		if (description) {
			member.description = description
		}

		member.params.putAll(extractParams(section.html))

		String returns = extractDtDd(section.html, 'Returns:')
		if (returns) {
			member.returns = returns
		}

		member.throwsDocs.putAll(extractThrows(section.html))

		if (!member.description && member.params.isEmpty() && !member.returns && member.throwsDocs.isEmpty()) {
			return null
		}

		return member
	}

	private static String extractMemberName(DetailSection section, String className) {
		def matcher = section.html =~ /(?s)<h3[^>]*>\s*(.*?)\s*<\/h3>/
		if (matcher.find()) {
			String heading = BasicUtils.cleanHtml(matcher.group(1))
			if (heading) {
				return heading
			}
		}

		String id = section.id
		int paren = id.indexOf('(')
		if (paren > 0) {
			String candidate = id.substring(0, paren)
			if (candidate == '<init>') {
				return className
			}
			return candidate
		}

		return null
	}

	private static String extractFirstBlock(String html) {
		def matcher = html =~ /(?s)<div\s+class="block">(.*?)<\/div>/
		if (matcher.find()) {
			return BasicUtils.cleanHtml(matcher.group(1))
		}
		return null
	}

	private static Map<String, String> extractParams(String html) {
		Map<String, String> params = [:]

		def matcher = html =~ /(?s)<dd>\s*<code>([^<]+)<\/code>\s*-\s*(.*?)<\/dd>/
		while (matcher.find()) {
			String name = BasicUtils.cleanHtml(matcher.group(1))
			String description = BasicUtils.cleanHtml(matcher.group(2))
			if (name && description) {
				params[name] = description
			}
		}

		return params
	}

	private static Map<String, String> extractThrows(String html) {
		Map<String, String> throwsDocs = [:]

		def matcher = html =~ /(?s)<dd>\s*<code>([^<]+)<\/code>\s*-\s*(.*?)<\/dd>/
		while (matcher.find()) {
			String name = BasicUtils.cleanHtml(matcher.group(1))
			String description = BasicUtils.cleanHtml(matcher.group(2))

			if (!name || !description) {
				continue
			}

			if (html.contains('Throws:') || html.contains('Thrown:')) {
				throwsDocs[name] = description
			}
		}

		return throwsDocs
	}

	private static String extractDtDd(String html, String label) {
		String quoted = Pattern.quote(label)

		def matcher = html =~ /(?s)<dt>\s*${quoted}\s*<\/dt>\s*<dd>(.*?)<\/dd>/
		if (matcher.find()) {
			return BasicUtils.cleanHtml(matcher.group(1))
		}

		return null
	}

	private static String injectClassJavadoc(String source, String className, String doc) {
		Pattern pattern = Pattern.compile(
				"(?m)^(\\s*)((?:public|protected|private)\\s+(?:final\\s+|abstract\\s+)?(?:class|interface|enum|record)\\s+${Pattern.quote(className)}\\b)"
				)

		Matcher matcher = pattern.matcher(source)
		if (!matcher.find()) {
			return source
		}

		if (hasJavadocImmediatelyBefore(source, matcher.start())) {
			return source
		}

		String indent = matcher.group(1)
		String declaration = matcher.group(2)

		String replacement = Matcher.quoteReplacement(
				BasicUtils.toJavadoc(doc, indent) + '\n' + indent + declaration
				)

		return matcher.replaceFirst(replacement)
	}

	private static String injectConstructorJavadoc(String source, String className, MemberDocs member) {
		String doc = member.toJavadocText()
		if (!doc) {
			return source
		}

		Pattern pattern = Pattern.compile(
				"(?m)^(\\s*)((?:@[^\\n]+\\n\\s*)*(?:public|protected|private)\\s+${Pattern.quote(className)}\\s*\\()"
				)

		Matcher matcher = pattern.matcher(source)
		if (!matcher.find()) {
			return source
		}

		if (hasJavadocImmediatelyBefore(source, matcher.start())) {
			return source
		}

		String indent = matcher.group(1)
		String declarationStart = matcher.group(2)

		String replacement = Matcher.quoteReplacement(
				BasicUtils.toJavadoc(doc, indent) + '\n' + indent + declarationStart
				)

		return matcher.replaceFirst(replacement)
	}

	private static String injectMethodJavadoc(String source, MemberDocs member) {
		String doc = member.toJavadocText()
		if (!doc) {
			return source
		}

		Pattern pattern = Pattern.compile(
				"(?m)^(\\s*)((?:@[^\\n]+\\n\\s*)*(?:public|protected|private)\\s+(?:static\\s+|final\\s+|synchronized\\s+|native\\s+|abstract\\s+|default\\s+)*[\\w.<>, ?\\[\\]]+\\s+${Pattern.quote(member.name)}\\s*\\()"
				)

		Matcher matcher = pattern.matcher(source)
		if (!matcher.find()) {
			return source
		}

		if (hasJavadocImmediatelyBefore(source, matcher.start())) {
			return source
		}

		String indent = matcher.group(1)
		String declarationStart = matcher.group(2)

		String replacement = Matcher.quoteReplacement(
				BasicUtils.toJavadoc(doc, indent) + '\n' + indent + declarationStart
				)

		return matcher.replaceFirst(replacement)
	}

	private static boolean hasJavadocImmediatelyBefore(String source, int start) {
		String before = source.substring(0, start)
		String[] lines = before.readLines()

		int i = lines.size() - 1

		while (i >= 0 && lines[i].trim().isEmpty()) {
			i--
		}

		if (i < 0) {
			return false
		}

		while (i >= 0 && lines[i].trim().startsWith('@')) {
			i--

			while (i >= 0 && lines[i].trim().isEmpty()) {
				i--
			}
		}

		if (i < 0) {
			return false
		}

		return lines[i].trim() == '*/'
	}

	private static final class DetailSection {
		String id
		String html
	}

	private static final class ClassDocs {
		String classDoc
		List<MemberDocs> members = []
	}

	private static final class MemberDocs {
		String name
		boolean constructor
		String description
		Map<String, String> params = [:]
		String returns
		Map<String, String> throwsDocs = [:]

		String toJavadocText() {
			List<String> lines = []

			if (description) {
				lines.add(description)
			}

			if (!params.isEmpty()) {
				if (!lines.isEmpty()) {
					lines.add('')
				}

				params.each { String name, String description ->
					lines.add("@param ${name} ${description}")
				}
			}

			if (returns) {
				if (!lines.isEmpty()) {
					lines.add('')
				}

				lines.add("@return ${returns}")
			}

			if (!throwsDocs.isEmpty()) {
				if (!lines.isEmpty()) {
					lines.add('')
				}

				throwsDocs.each { String name, String description ->
					lines.add("@throws ${name} ${description}")
				}
			}

			return lines.join('\n')
		}
	}
}