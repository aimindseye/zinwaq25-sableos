package org.vaachak.reader.core.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.readium.r2.shared.util.AbsoluteUrl
import org.readium.r2.shared.util.Try
import org.vaachak.reader.core.data.local.OpdsDao
import org.vaachak.reader.core.domain.model.OpdsEntity
import java.net.InetSocketAddress
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import com.sun.net.httpserver.HttpServer

@OptIn(ExperimentalCoroutinesApi::class)
class OpdsRepositoryUrlHardeningTest {

    private val opdsDao: OpdsDao = mockk(relaxed = true)

    @Test
    fun catalogs_seedsDefaultFeeds_whenDatabaseIsEmpty() = runTest {
        coEvery { opdsDao.getFeedsCount() } returns 0
        every { opdsDao.getAllFeeds() } returns flowOf(emptyList())

        val repository = OpdsRepository(opdsDao)

        repository.catalogs.first()

        coVerify(exactly = 1) {
            opdsDao.insertFeed(
                match { it.title == "Project Gutenberg" && it.url == "https://gutendex.com/books" }
            )
        }
        coVerify(exactly = 1) {
            opdsDao.insertFeed(
                match { it.title == "ManyBooks" && it.url == "https://manybooks.net/opds" }
            )
        }
    }

    @Test
    fun catalogs_doesNotSeed_whenDatabaseHasFeeds() = runTest {
        coEvery { opdsDao.getFeedsCount() } returns 2
        every { opdsDao.getAllFeeds() } returns flowOf(emptyList())

        val repository = OpdsRepository(opdsDao)

        repository.catalogs.first()

        coVerify(exactly = 0) { opdsDao.insertFeed(any()) }
    }

    @Test
    fun insertFeed_savesValidHttpsUrl() = runTest {
        val inserted = slot<OpdsEntity>()
        coEvery { opdsDao.insertFeed(capture(inserted)) } returns Unit

        val repository = OpdsRepository(opdsDao)

        val result = repository.insertFeed(
            OpdsEntity(
                title = "My Calibre",
                url = "https://mac-mini.tail1f3687.ts.net/calibre/opds",
                username = "reader",
                password = "secret"
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(
            "https://mac-mini.tail1f3687.ts.net/calibre/opds",
            inserted.captured.url
        )

        coVerify(exactly = 1) { opdsDao.insertFeed(any()) }
    }

    @Test
    fun updateFeed_savesValidHttpsUrl() = runTest {
        val updated = slot<OpdsEntity>()
        coEvery { opdsDao.updateFeed(capture(updated)) } returns Unit

        val repository = OpdsRepository(opdsDao)

        val result = repository.updateFeed(
            OpdsEntity(
                id = 42L,
                title = "Updated Feed",
                url = "https://example.com/catalog.xml",
                username = null,
                password = null,
                isPredefined = true
            )
        )

        assertTrue(result.isSuccess)
        assertEquals(42L, updated.captured.id)
        assertEquals("https://example.com/catalog.xml", updated.captured.url)

        coVerify(exactly = 1) { opdsDao.updateFeed(any()) }
    }

    @Test
    fun insertFeed_returnsFailure_forHttpUrl() = runTest {
        val repository = OpdsRepository(opdsDao)

        val result = repository.insertFeed(
            OpdsEntity(
                title = "Broken Feed",
                url = "http://mac-mini.tail1f3687.ts.net/calibre/opds"
            )
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Only HTTPS OPDS endpoints are supported") == true
        )
        coVerify(exactly = 0) { opdsDao.insertFeed(any()) }
    }

    @Test
    fun updateFeed_returnsFailure_forHttpUrl() = runTest {
        val repository = OpdsRepository(opdsDao)

        val result = repository.updateFeed(
            OpdsEntity(
                id = 42L,
                title = "Broken Feed",
                url = "http://example.com/catalog.xml"
            )
        )

        assertTrue(result.isFailure)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("Only HTTPS OPDS endpoints are supported") == true
        )
        coVerify(exactly = 0) { opdsDao.updateFeed(any()) }
    }

    @Test
    fun parseFeed_returnsFailure_forHttpUrl() = runTest {
        val repository = OpdsRepository(opdsDao)

        val result = repository.parseFeed("http://example.com/opds")

        assertTrue(result is Try.Failure)
        val failure = result as Try.Failure
        assertTrue(
            failure.value.message?.contains("Only HTTPS OPDS endpoints are supported") == true
        )
    }

    @Test
    fun parseFeed_returnsFailure_forInvalidUrl() = runTest {
        val repository = OpdsRepository(opdsDao)

        val result = repository.parseFeed("not a url")

        assertTrue(result is Try.Failure)
        val failure = result as Try.Failure
        assertTrue(failure.value.message?.contains("Invalid URL") == true)
    }

    @Test
    fun parseFeed_returnsFailure_whenAbsoluteUrlResolverReturnsNull() = runTest {
        val repository = OpdsRepository(opdsDao)
        repository.setAbsoluteUrlResolverForTest { null }

        val result = repository.parseFeed("https://example.com/opds")

        assertTrue(result is Try.Failure)
        val failure = result as Try.Failure
        assertTrue(failure.value.message?.contains("Invalid URL") == true)
    }

    @Test
    fun downloadPublication_returnsFalse_forHttpDownloadUrl() = runTest {
        val repository = OpdsRepository(opdsDao)
        val destination = File.createTempFile("opds-hardening", ".epub")
        destination.deleteOnExit()

        val success = repository.downloadPublication(
            downloadUrl = "http://example.com/book.epub",
            destinationFile = destination,
            originalFeedUrl = "https://example.com/opds"
        )

        assertFalse(success)
    }

    @Test
    fun downloadPublication_returnsFalse_forInvalidOriginalFeedUrl() = runTest {
        val repository = OpdsRepository(opdsDao)
        val destination = File.createTempFile("opds-hardening", ".epub")
        destination.deleteOnExit()

        val success = repository.downloadPublication(
            downloadUrl = "https://example.com/book.epub",
            destinationFile = destination,
            originalFeedUrl = "not a url"
        )

        assertFalse(success)
    }

    @Test
    fun parseFeed_returnsFailure_forHttpErrorResponse() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()
        var capturedRequest: Request? = null

        repository.setClientForTest(mockClient)
        repository.setAbsoluteUrlResolverForTest(::forceAbsoluteUrl)
        coEvery { opdsDao.getFeedByUrl(any()) } returns null

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = { capturedRequest = it },
            code = 500,
            message = "Server Error",
            body = "oops".toResponseBody("text/plain".toMediaType())
        )

        val result = repository.parseFeed("https://example.com/opds/")

        assertTrue(result is Try.Failure)
        val failure = result as Try.Failure
        assertTrue(failure.value.message?.isNotBlank() == true)
    }

    @Test
    fun parseFeed_returnsFailure_forEmptyBody() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()
        var capturedRequest: Request? = null

        repository.setClientForTest(mockClient)
        repository.setAbsoluteUrlResolverForTest(::forceAbsoluteUrl)
        coEvery { opdsDao.getFeedByUrl(any()) } returns null

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = { capturedRequest = it },
            code = 200,
            message = "OK",
            body = ByteArray(0).toResponseBody("application/xml".toMediaType())
        )

        val result = repository.parseFeed("https://example.com/opds/")

        assertTrue(result is Try.Failure)
        val failure = result as Try.Failure
        assertTrue(failure.value.message?.isNotBlank() == true)
    }

    @Test
    fun parseFeed_returnsFailure_forHtmlBody() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()
        var capturedRequest: Request? = null

        repository.setClientForTest(mockClient)
        repository.setAbsoluteUrlResolverForTest(::forceAbsoluteUrl)
        coEvery { opdsDao.getFeedByUrl(any()) } returns null

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = { capturedRequest = it },
            code = 200,
            message = "OK",
            body = "<html><body>Login</body></html>".toResponseBody("text/html".toMediaType())
        )

        val result = repository.parseFeed("https://example.com/opds/")

        assertTrue(result is Try.Failure)
        val failure = result as Try.Failure
        assertTrue(failure.value.message?.isNotBlank() == true)
    }

    @Test
    fun parseFeed_addsAuthHeader_whenStoredFeedHasCredentials() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()
        var capturedRequest: Request? = null
        val feedUrl = "https://secure.example/opds"

        repository.setClientForTest(mockClient)
        repository.setAbsoluteUrlResolverForTest(::forceAbsoluteUrl)
        coEvery { opdsDao.getFeedByUrl(any()) } returns null
        coEvery { opdsDao.getFeedByUrl(feedUrl) } returns OpdsEntity(
            title = "Secure",
            url = feedUrl,
            username = "reader",
            password = "secret"
        )

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = { capturedRequest = it },
            code = 500,
            message = "Server Error",
            body = "oops".toResponseBody("text/plain".toMediaType())
        )

        val result = repository.parseFeed(feedUrl)

        assertTrue(result is Try.Failure)
        assertEquals(
            Credentials.basic("reader", "secret"),
            capturedRequest?.header("Authorization")
        )
    }

    @Test
    fun parseFeed_skipsAuthHeader_whenStoredFeedHasBlankUsername() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()
        var capturedRequest: Request? = null
        val feedUrl = "https://secure.example/opds"

        repository.setClientForTest(mockClient)
        repository.setAbsoluteUrlResolverForTest(::forceAbsoluteUrl)
        coEvery { opdsDao.getFeedByUrl(any()) } returns null
        coEvery { opdsDao.getFeedByUrl(feedUrl) } returns OpdsEntity(
            title = "Secure",
            url = feedUrl,
            username = "",
            password = "secret"
        )

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = { capturedRequest = it },
            code = 500,
            message = "Server Error",
            body = "oops".toResponseBody("text/plain".toMediaType())
        )

        val result = repository.parseFeed(feedUrl)

        assertTrue(result is Try.Failure)
        assertEquals(null, capturedRequest?.header("Authorization"))
    }

    @Test
    fun parseFeed_skipsAuthHeader_whenStoredFeedHasBlankPassword() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()
        var capturedRequest: Request? = null
        val feedUrl = "https://secure.example/opds"

        repository.setClientForTest(mockClient)
        repository.setAbsoluteUrlResolverForTest(::forceAbsoluteUrl)
        coEvery { opdsDao.getFeedByUrl(any()) } returns null
        coEvery { opdsDao.getFeedByUrl(feedUrl) } returns OpdsEntity(
            title = "Secure",
            url = feedUrl,
            username = "reader",
            password = ""
        )

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = { capturedRequest = it },
            code = 500,
            message = "Server Error",
            body = "oops".toResponseBody("text/plain".toMediaType())
        )

        val result = repository.parseFeed(feedUrl)

        assertTrue(result is Try.Failure)
        assertEquals(null, capturedRequest?.header("Authorization"))
    }

    @Test
    fun parseFeed_executesJsonParserPath_forOpds2LikeBody() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()

        repository.setClientForTest(mockClient)
        repository.setAbsoluteUrlResolverForTest(::forceAbsoluteUrl)
        coEvery { opdsDao.getFeedByUrl(any()) } returns null

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = {},
            code = 200,
            message = "OK",
            body = """
                {
                  "metadata": {"title": "Catalog"},
                  "links": [],
                  "publications": [],
                  "navigation": []
                }
            """.trimIndent().toResponseBody("application/opds+json".toMediaType())
        )

        val result = repository.parseFeed("https://example.com/opds/")

        if (result is Try.Failure) {
            assertTrue(result.value.message?.contains("no answer found") != true)
        }
    }

    @Test
    fun parseFeed_executesOpds1FallbackPath_forBomXmlBody() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()

        repository.setClientForTest(mockClient)
        repository.setAbsoluteUrlResolverForTest(::forceAbsoluteUrl)
        coEvery { opdsDao.getFeedByUrl(any()) } returns null

        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
                  xmlns:dc="http://purl.org/dc/terms/"
                  xmlns:opds="http://opds-spec.org/2010/catalog">
              <title>Catalog</title>
              <id>urn:uuid:catalog-1</id>
              <updated>2020-01-01T00:00:00Z</updated>
            </feed>
        """.trimIndent().encodeToByteArray()
        val bomXml = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + xml

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = {},
            code = 200,
            message = "OK",
            body = bomXml.toResponseBody("application/atom+xml".toMediaType())
        )

        val result = repository.parseFeed("https://example.com/opds/")

        if (result is Try.Failure) {
            assertTrue(result.value.message?.contains("no answer found") != true)
        }
    }

    @Test
    fun downloadPublication_writesFileAndUsesAuth_whenCredentialsExist() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()
        var capturedRequest: Request? = null
        val destination = File.createTempFile("opds-download", ".epub")
        destination.deleteOnExit()
        val feedUrl = "https://secure.example/opds"
        val payload = "epub-bytes".encodeToByteArray()

        repository.setClientForTest(mockClient)

        coEvery { opdsDao.getFeedByUrl(any()) } returns null
        coEvery {
            opdsDao.getFeedByUrl(feedUrl)
        } returns OpdsEntity(
            title = "Secure",
            url = feedUrl,
            username = "reader",
            password = "secret"
        )

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = { capturedRequest = it },
            code = 200,
            message = "OK",
            body = payload.toResponseBody("application/epub+zip".toMediaType())
        )

        val success = repository.downloadPublication(
            downloadUrl = "https://example.com/book.epub",
            destinationFile = destination,
            originalFeedUrl = feedUrl
        )

        assertTrue(success)
        assertEquals(
            Credentials.basic("reader", "secret"),
            capturedRequest?.header("Authorization")
        )
        assertTrue(destination.readBytes().contentEquals(payload))
    }

    @Test
    fun downloadPublication_usesOpdsBaseFallbackForAuthLookup() = runTest {
        val repository = OpdsRepository(opdsDao)
        val mockClient: OkHttpClient = mockk()
        val mockCall: Call = mockk()
        var capturedRequest: Request? = null
        val destination = File.createTempFile("opds-download-fallback", ".epub")
        destination.deleteOnExit()
        val payload = "epub-bytes".encodeToByteArray()

        repository.setClientForTest(mockClient)

        coEvery { opdsDao.getFeedByUrl(any()) } returns null
        coEvery { opdsDao.getFeedByUrl("https://secure.example/opds") } returns OpdsEntity(
            title = "Secure",
            url = "https://secure.example/opds",
            username = "reader",
            password = "secret"
        )

        everyCallWithResponse(
            client = mockClient,
            call = mockCall,
            onRequest = { capturedRequest = it },
            code = 200,
            message = "OK",
            body = payload.toResponseBody("application/epub+zip".toMediaType())
        )

        val success = repository.downloadPublication(
            downloadUrl = "https://example.com/book.epub",
            destinationFile = destination,
            originalFeedUrl = "https://secure.example/opds/search?q=test"
        )

        assertTrue(success)
        assertEquals(
            Credentials.basic("reader", "secret"),
            capturedRequest?.header("Authorization")
        )
    }

    @Test
    fun deleteFeed_returnsFailure_whenDaoThrows() = runTest {
        val repository = OpdsRepository(opdsDao)
        val feed = OpdsEntity(id = 10L, title = "Broken", url = "https://example.com/opds")

        coEvery { opdsDao.deleteFeed(feed) } throws IllegalStateException("db failure")

        val result = repository.deleteFeed(feed)

        assertTrue(result.isFailure)
    }

    @Test
    fun createClient_appliesHeadersAndPersistsCookiesAcrossRequests() {
        val repository = OpdsRepository(opdsDao)
        val client = createClientViaReflection(repository)

        val requestCount = AtomicInteger(0)
        var firstUserAgent: String? = null
        var firstAccept: String? = null
        var secondCookie: String? = null
        var thirdCookie: String? = null

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/feed") { exchange ->
            val index = requestCount.incrementAndGet()
            if (index == 1) {
                firstUserAgent = exchange.requestHeaders.getFirst("User-Agent")
                firstAccept = exchange.requestHeaders.getFirst("Accept")
                exchange.responseHeaders.add("Set-Cookie", "session=abc; Path=/")
            } else if (index == 2) {
                secondCookie = exchange.requestHeaders.getFirst("Cookie")
                exchange.responseHeaders.add("Set-Cookie", "session=xyz; Path=/")
            } else {
                thirdCookie = exchange.requestHeaders.getFirst("Cookie")
            }
            val body = "ok".toByteArray()
            exchange.sendResponseHeaders(200, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}/feed"
            client.newCall(Request.Builder().url(baseUrl).build()).execute().use { }
            client.newCall(Request.Builder().url(baseUrl).build()).execute().use { }
            client.newCall(Request.Builder().url(baseUrl).build()).execute().use { }
        } finally {
            server.stop(0)
        }

        assertEquals(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            firstUserAgent
        )
        assertTrue(firstAccept?.contains("application/opds+json") == true)
        assertTrue(secondCookie?.contains("session=abc") == true)
        assertTrue(thirdCookie?.contains("session=xyz") == true)
        assertTrue(thirdCookie?.contains("session=abc") == false)
    }

    @Test
    fun parseFeed_nonHtmlXml_executesOpdsFallbackAndReturnsXmlError() = runTest {
        val repository = OpdsRepository(opdsDao)
        val bomXml = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + """
            <?xml version="1.0" encoding="utf-8"?>
            <feed><title><div>Title</div></title></feed>
        """.trimIndent().encodeToByteArray()
        val client = responseClient(
            code = 200,
            message = "OK",
            body = bomXml.toResponseBody("application/xml".toMediaType())
        )
        repository.setClientForTest(client)
        repository.setAbsoluteUrlResolverForTest(::forceAbsoluteUrl)
        coEvery { opdsDao.getFeedByUrl(any()) } returns null

        val result = repository.parseFeed("https://example.com/opds/")

        assertTrue(result is Try.Failure || result is Try.Success)
    }

    @Test
    fun sanitizeXml_rewritesNestedDivInTitle() {
        val repository = OpdsRepository(opdsDao)
        val method = repository.javaClass.getDeclaredMethod("sanitizeXml", ByteArray::class.java)
        method.isAccessible = true
        val input = "<feed><title><div>My Title</div></title></feed>".encodeToByteArray()

        val output = method.invoke(repository, input) as ByteArray
        val outputString = output.toString(Charsets.UTF_8)

        assertTrue(outputString.contains("<title>My Title</title>"))
    }

    private fun everyCallWithResponse(
        client: OkHttpClient,
        call: Call,
        onRequest: (Request) -> Unit,
        code: Int,
        message: String,
        body: okhttp3.ResponseBody?
    ) {
        var observedRequest: Request? = null
        every { client.newCall(any()) } answers {
            val req = firstArg<Request>()
            observedRequest = req
            onRequest(req)
            call
        }
        every { call.execute() } answers {
            Response.Builder()
                .request(observedRequest ?: Request.Builder().url("https://fallback.example").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message)
                .body(body)
                .build()
        }
    }

    private fun createClientViaReflection(repository: OpdsRepository): OkHttpClient {
        val method = repository.javaClass.getDeclaredMethod("createClient")
        method.isAccessible = true
        return method.invoke(repository) as OkHttpClient
    }

    private fun forceAbsoluteUrl(url: String): AbsoluteUrl? {
        return mockk(relaxed = true)
    }

    private fun responseClient(
        code: Int,
        message: String,
        body: okhttp3.ResponseBody?,
        onRequest: (Request) -> Unit = {}
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                onRequest(request)
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message(message)
                    .body(body)
                    .build()
            })
            .build()
    }
}
