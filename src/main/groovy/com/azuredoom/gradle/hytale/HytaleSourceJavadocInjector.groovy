package com.azuredoom.gradle.hytale

import org.gradle.api.logging.Logger

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicInteger
import java.util.regex.Matcher
import java.util.regex.Pattern

final class HytaleSourceJavadocInjector {
	private HytaleSourceJavadocInjector() {}

	// Max parallel HTTP fetches. Stays polite to the docs server and
	// avoids overwhelming the Gradle worker thread pool.
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

		// Collect all java files first
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

		// Phase 1 — parallel prefetch: fire off HTTP requests for all FQCNs that
		// aren't already cached. This turns N sequential round-trips into a single
		// parallel burst and is the main reason first-run is slow.
		prefetchAll(javaFiles, sourcesDir, docsBaseUrl, cacheDir, logger)

		// Phase 2 — serial injection: read cache, parse, inject. No network I/O here
		// so there's no benefit to parallelising writes (avoids concurrent file mutation).
		AtomicInteger changed = new AtomicInteger(0)

		javaFiles.each { File javaFile ->
			if (injectIntoFile(javaFile, sourcesDir, docsBaseUrl, cacheDir, logger)) {
				changed.incrementAndGet()
			}
		}

		logger.lifecycle("Hytale Javadocs injection finished: {} changed / {} visited", changed.get(), javaFiles.size())
	}

	/**
	 * Fires parallel HTTP fetches for every FQCN that has no cached .html or .missing file.
	 * Results are written to the cache so the serial injection phase only reads from disk.
	 */
	private static void prefetchAll(List<File> javaFiles, File sourcesDir, String docsBaseUrl, File cacheDir, Logger logger) {
		String normalizedBase = docsBaseUrl.endsWith('/') ? docsBaseUrl : docsBaseUrl + '/'

		// Build the list of FQCNs that actually need fetching
		List<String> toFetch = javaFiles.collectMany { File javaFile ->
			if (javaFile.getText('UTF-8').contains('<strong>Hytale API docs:</strong>')) {
				return []  // already injected, skip
			}
			String fqcn = fqcnFor(javaFile, sourcesDir)
			if (fqcn == null) return []
			String path = fqcn.replace('.', '/')
			File cached  = new File(cacheDir, path + '.html')
			File missing = new File(cacheDir, path + '.missing')
			(cached.isFile() || missing.isFile()) ? [] : [fqcn]
		}

		if (toFetch.isEmpty()) return

			logger.lifecycle("Hytale Javadocs: prefetching {} uncached pages with {} threads...", toFetch.size(), FETCH_THREADS)

		def pool = Executors.newFixedThreadPool(FETCH_THREADS)
		try {
			List<Future<?>> futures = toFetch.collect { String fqcn ->
				pool.submit({
					fetchDocsHtml(fqcn, normalizedBase, cacheDir, logger)
				} as Runnable)
			}
			// Wait for all fetches to complete and propagate any exceptions
			futures.each { it.get() }
		} finally {
			pool.shutdown()
		}
	}

	private static boolean injectIntoFile(File javaFile, File sourcesDir, String docsBaseUrl, File cacheDir, Logger logger) {
		String source = javaFile.getText('UTF-8')
		if (source.contains('<strong>Hytale API docs:</strong>')) {
			return false
		}

		String fqcn = fqcnFor(javaFile, sourcesDir)
		if (fqcn == null) {
			return false
		}

		String normalizedBase = docsBaseUrl.endsWith('/') ? docsBaseUrl : docsBaseUrl + '/'
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

	/**
	 * Fetches (or returns from cache) the Javadoc HTML for the given FQCN.
	 * normalizedBase must already have a trailing slash.
	 * Thread-safe: cache file writes use a tmp-then-rename strategy.
	 */
	private static String fetchDocsHtml(String fqcn, String normalizedBase, File cacheDir, Logger logger) {
		String path = fqcn.replace('.', '/')

		File cached  = new File(cacheDir, path + '.html')
		File missing = new File(cacheDir, path + '.missing')

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
				atomicWrite(missing, uri.toString())
				return null
			}

			if (status < 200 || status >= 300) {
				logger.info("No hosted Hytale Javadocs found at {}: HTTP {}", uri, status)
				atomicWrite(missing, "${uri} HTTP ${status}")
				return null
			}

			String html = connection.inputStream.getText('UTF-8')
			atomicWrite(cached, html)
			return html
		} catch (Throwable throwable) {
			logger.info("No hosted Hytale Javadocs found at {}: {}", uri, throwable.message)
			atomicWrite(missing, "${uri} ${throwable.class.name}: ${throwable.message}")
			return null
		} finally {
			connection?.disconnect()
		}
	}

	/** Writes content to a temp file then atomically renames it, avoiding partial reads from parallel threads. */
	private static void atomicWrite(File target, String content) {
		File tmp = new File(target.parentFile, target.name + '.tmp')
		tmp.setText(content, 'UTF-8')
		tmp.renameTo(target)
	}

	private static String fqcnFor(File javaFile, File sourcesDir) {
		String rel = sourcesDir.toPath().relativize(javaFile.toPath()).toString()
		if (!rel.endsWith('.java')) {
			return null
		}
		return rel
				.substring(0, rel.length() - '.java'.length())
				.replace(File.separatorChar, '.' as char)
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
		// Pattern 1: modern Javadoc — extract the <section class="class-description"> content
		// first, then search for a block div *within* that string only. This prevents the
		// lazy (?s) dot from crossing the section boundary into method detail sections.
		def sectionMatcher = html =~ /(?s)<section\s+class="class-description"[^>]*>(.*?)<\/section>/
		if (sectionMatcher.find()) {
			String sectionHtml = sectionMatcher.group(1)
			def blockMatcher = sectionHtml =~ /(?s)<div\s+class="block">(.*?)<\/div>/
			if (blockMatcher.find()) {
				return cleanHtml(blockMatcher.group(1))
			}
			// Section exists but has no block — class has no description, don't fall through.
			return null
		}

		// Patterns 2 & 3: older Javadoc — restrict search to the region before any detail sections.
		String classRegion = extractClassRegion(html)

		def h1Matcher = classRegion =~ /(?s)<h1[^>]*>\s*Class\s+[^<]+<\/h1>.*?<div\s+class="block">(.*?)<\/div>/
		if (h1Matcher.find()) {
			return cleanHtml(h1Matcher.group(1))
		}

		def typeSigMatcher = classRegion =~ /(?s)<div\s+class="type-signature"[^>]*>.*?<\/div>\s*<div\s+class="block">(.*?)<\/div>/
		if (typeSigMatcher.find()) {
			return cleanHtml(typeSigMatcher.group(1))
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
					id: decodeHtml(matcher.group(1)),
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
			String heading = cleanHtml(matcher.group(1))
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
			return cleanHtml(matcher.group(1))
		}
		return null
	}

	private static Map<String, String> extractParams(String html) {
		Map<String, String> params = [:]

		def matcher = html =~ /(?s)<dd>\s*<code>([^<]+)<\/code>\s*-\s*(.*?)<\/dd>/
		while (matcher.find()) {
			String name = cleanHtml(matcher.group(1))
			String description = cleanHtml(matcher.group(2))
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
			String name = cleanHtml(matcher.group(1))
			String description = cleanHtml(matcher.group(2))

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
			return cleanHtml(matcher.group(1))
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
				toJavadoc(doc, indent) + '\n' + indent + declaration
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
				toJavadoc(doc, indent) + '\n' + indent + declarationStart
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
				toJavadoc(doc, indent) + '\n' + indent + declarationStart
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

	private static String cleanHtml(String html) {
		return decodeHtml(html)
				.replaceAll(/(?i)<br\s*\/?>/, '\n')
				.replaceAll(/(?s)<[^>]+>/, '')
				.replaceAll(/[ \t\r\f]+/, ' ')
				.replaceAll(/\n\s+/, '\n')
				.trim()
	}

	private static String decodeHtml(String value) {
		return value
				.replace('&nbsp;', ' ')
				.replace('&#160;', ' ')
				.replace('&lt;', '<')
				.replace('&gt;', '>')
				.replace('&amp;', '&')
				.replace('&quot;', '"')
				.replace('&#39;', "'")
				.trim()
	}

	private static String toJavadoc(String text, String indent) {
		def lines = text.readLines()

		return indent + '/**\n' +
				indent + ' * <p><strong>Hytale API docs:</strong></p>\n' +
				lines.collect { indent + ' * ' + it }.join('\n') +
				'\n' + indent + ' */'
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