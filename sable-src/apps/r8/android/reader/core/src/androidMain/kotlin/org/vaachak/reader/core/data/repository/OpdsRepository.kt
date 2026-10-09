package org.vaachak.reader.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.readium.r2.opds.OPDS1Parser
import org.readium.r2.opds.OPDS2Parser
import org.readium.r2.shared.opds.ParseData
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.readium.r2.shared.util.Try.Failure
import org.readium.r2.shared.util.Try.Success
import org.vaachak.reader.core.data.local.OpdsDao
import org.vaachak.reader.core.domain.model.OpdsEntity
import org.vaachak.reader.core.utils.OpdsUrlUtils
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpdsRepository @Inject constructor(
    private val opdsDao: OpdsDao
) {
    private val uaChrome =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private val uaCalibre = "Stanza/3.2 iPhone/5.1"

    private val cookieStore = HashMap<String, List<Cookie>>()

    val catalogs: Flow<List<OpdsEntity>> = opdsDao.getAllFeeds()
        .onStart {
            val count = opdsDao.getFeedsCount()
            if (count == 0) {
                val gutenberg = OpdsEntity(
                    title = "Project Gutenberg",
                    url = "https://gutendex.com/books"
                )
                val manyBooks = OpdsEntity(
                    title = "ManyBooks",
                    url = "https://manybooks.net/opds"
                )
                opdsDao.insertFeed(gutenberg)
                opdsDao.insertFeed(manyBooks)
            }
        }

    val allFeeds: Flow<List<OpdsEntity>> = opdsDao.getAllFeeds()

    private var client: OkHttpClient = createClient()
    private var absoluteUrlResolver: (String) -> AbsoluteUrl? = { AbsoluteUrl(it) }

    internal fun setClientForTest(client: OkHttpClient) {
        this.client = client
    }

    internal fun setAbsoluteUrlResolverForTest(resolver: (String) -> AbsoluteUrl?) {
        absoluteUrlResolver = resolver
    }

    private fun createClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(false)
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val existing = cookieStore[url.host] ?: emptyList()
                    val updated = ArrayList(existing)

                    for (incoming in cookies) {
                        updated.removeAll { current ->
                            current.name == incoming.name &&
                                    current.domain == incoming.domain &&
                                    current.path == incoming.path
                        }
                        updated.add(incoming)
                    }

                    cookieStore[url.host] = updated
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    return cookieStore[url.host] ?: emptyList()
                }
            })
            .addNetworkInterceptor(Interceptor { chain ->
                val original = chain.request()
                val host = original.url.host.lowercase()

                val looksLikeSelfHosted =
                    host.contains("calibre") ||
                            host.contains("abs") ||
                            host.endsWith(".local") ||
                            host.endsWith(".home.arpa")

                val userAgent = if (looksLikeSelfHosted) uaCalibre else uaChrome

                val request = original.newBuilder()
                    .header("User-Agent", userAgent)
                    .header(
                        "Accept",
                        "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,application/opds+json,*/*;q=0.8"
                    )
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build()

                chain.proceed(request)
            })
            .build()
    }

    private fun normalizeHttpsUrl(urlStr: String): String {
        return OpdsUrlUtils.normalizeHttpsUrl(urlStr)
    }

    private suspend fun findEffectiveFeed(urlStr: String): OpdsEntity? {
        val cleanUrl = normalizeHttpsUrl(urlStr)

        var feed = opdsDao.getFeedByUrl(cleanUrl)
            ?: opdsDao.getFeedByUrl(cleanUrl.removeSuffix("/"))
            ?: opdsDao.getFeedByUrl("$cleanUrl/")

        if (feed != null) return feed

        if (cleanUrl.contains("/opds")) {
            val base = cleanUrl.substringBefore("/opds") + "/opds"
            feed = opdsDao.getFeedByUrl(base)
                ?: opdsDao.getFeedByUrl("$base/")
            if (feed != null) return feed
        }

        return null
    }

    private suspend fun executeRequest(url: String): Response {
        val secureUrl = normalizeHttpsUrl(url)
        val storedFeed = findEffectiveFeed(secureUrl)

        val requestBuilder = Request.Builder().url(secureUrl)

        if (
            storedFeed != null &&
            !storedFeed.username.isNullOrBlank() &&
            !storedFeed.password.isNullOrBlank()
        ) {
            requestBuilder.header(
                "Authorization",
                Credentials.basic(storedFeed.username, storedFeed.password)
            )
        }

        return withContext(Dispatchers.IO) {
            client.newCall(requestBuilder.build()).execute()
        }
    }

    suspend fun parseFeed(rawUrl: String): Try<ParseData, Exception> = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = normalizeHttpsUrl(rawUrl)
            val absoluteUrl = absoluteUrlResolver(cleanUrl)
                ?: return@withContext Failure(Exception("Invalid URL"))

            val response = try {
                executeRequest(cleanUrl)
            } catch (e: Exception) {
                return@withContext Failure(e)
            }

            if (!response.isSuccessful) {
                val message = "HTTP ${response.code}: ${response.message}"
                Timber.e(message)
                response.close()
                return@withContext Failure(Exception(message))
            }

            val bodyBytes = response.body?.bytes()
            response.close()

            val nonNullBodyBytes = bodyBytes
                ?: return@withContext Failure(Exception("Empty response"))

            if (nonNullBodyBytes.isEmpty()) {
                return@withContext Failure(Exception("Empty response"))
            }

            val contentStr = nonNullBodyBytes.toString(Charsets.UTF_8).trim()
            if (
                contentStr.contains("<!DOCTYPE html", ignoreCase = true) ||
                contentStr.contains("<html", ignoreCase = true)
            ) {
                return@withContext Failure(
                    Exception("Server returned HTML (login page or block page).")
                )
            }

            val safeBytes = sanitizeXml(nonNullBodyBytes)

            try {
                val opds2 = OPDS2Parser.parse(safeBytes, absoluteUrl)
                return@withContext Success(opds2)
            } catch (_: Exception) {
                // Fall through to OPDS 1.x parsing.
            }

            try {
                val cleanBytes = if (
                    safeBytes.size >= 3 &&
                    safeBytes[0] == 0xEF.toByte() &&
                    safeBytes[1] == 0xBB.toByte() &&
                    safeBytes[2] == 0xBF.toByte()
                ) {
                    safeBytes.copyOfRange(3, safeBytes.size)
                } else {
                    safeBytes
                }

                val opds1 = OPDS1Parser.parse(cleanBytes, absoluteUrl)
                return@withContext Success(opds1)
            } catch (e: Exception) {
                return@withContext Failure(Exception("XML error: ${e.message}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse OPDS feed")
            return@withContext Failure(e)
        }
    }

    suspend fun downloadPublication(
        downloadUrl: String,
        destinationFile: File,
        originalFeedUrl: String?
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val secureDownloadUrl = normalizeHttpsUrl(downloadUrl)
            val feedUrlForConfig = originalFeedUrl?.let(::normalizeHttpsUrl) ?: secureDownloadUrl
            val storedFeed = findEffectiveFeed(feedUrlForConfig)

            val requestBuilder = Request.Builder().url(secureDownloadUrl)

            if (
                storedFeed != null &&
                !storedFeed.username.isNullOrBlank() &&
                !storedFeed.password.isNullOrBlank()
            ) {
                requestBuilder.header(
                    "Authorization",
                    Credentials.basic(storedFeed.username, storedFeed.password)
                )
            }

            val response = client.newCall(requestBuilder.build()).execute()

            if (response.isSuccessful && response.body != null) {
                response.body!!.byteStream().use { input ->
                    FileOutputStream(destinationFile).use { output ->
                        input.copyTo(output)
                    }
                }
                response.close()
                return@withContext true
            }

            response.close()
        } catch (e: Exception) {
            Timber.e(e, "Failed to download publication")
        }

        return@withContext false
    }

    private fun sanitizeXml(rawXml: ByteArray): ByteArray {
        return try {
            val xmlString = rawXml.toString(Charsets.UTF_8)

            val cleanXml = xmlString.replace(
                Regex(
                    "<title[^>]*>\\s*<div[^>]*>(.*?)</div>\\s*</title>",
                    RegexOption.DOT_MATCHES_ALL
                )
            ) {
                "<title>${it.groupValues[1].trim()}</title>"
            }

            cleanXml.toByteArray(Charsets.UTF_8)
        } catch (_: Exception) {
            rawXml
        }
    }

    suspend fun insertFeed(feed: OpdsEntity): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedFeed = feed.copy(url = normalizeHttpsUrl(feed.url))
            opdsDao.insertFeed(normalizedFeed)
            Result.success("Catalog has been saved")
        } catch (e: Exception) {
            Timber.e(e, "Failed to save catalog")
            Result.failure(e)
        }
    }

    suspend fun updateFeed(feed: OpdsEntity): Result<String> = withContext(Dispatchers.IO) {
        try {
            val normalizedFeed = feed.copy(url = normalizeHttpsUrl(feed.url))
            opdsDao.updateFeed(normalizedFeed)
            Result.success("Catalog has been updated")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update catalog")
            Result.failure(e)
        }
    }

    suspend fun deleteFeed(feed: OpdsEntity): Result<String> = withContext(Dispatchers.IO) {
        try {
            opdsDao.deleteFeed(feed)
            Result.success("Catalog has been deleted")
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete catalog")
            Result.failure(e)
        }
    }
}
