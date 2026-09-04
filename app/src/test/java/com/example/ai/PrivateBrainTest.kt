package com.example.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ai.inference.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class PrivateBrainTest {

    private lateinit var context: Context
    private var httpServer: HttpServer? = null
    private var serverPort: Int = 0

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        httpServer?.stop(0)
        httpServer = null
    }

    private fun startLocalHttpServer(handler: HttpHandler): String {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/", handler)
        server.executor = null
        server.start()
        httpServer = server
        serverPort = server.address.port
        return "http://127.0.0.1:$serverPort"
    }

    @Test
    fun testProviderUrlConfiguration() {
        val config = PrivateBrainConfig(
            baseUrl = "http://192.168.1.100:8000",
            apiKey = "test-secret-key",
            selectedModel = "llama3.1:8b",
            serverType = PrivateBrainServerType.OPENAI_COMPATIBLE
        )
        val provider = OpenAiPrivateBrainProvider(config)
        assertNotNull(provider)

        val ollamaConfig = PrivateBrainConfig(
            baseUrl = "http://192.168.1.100:11434",
            serverType = PrivateBrainServerType.OLLAMA
        )
        val ollamaProvider = OllamaPrivateBrainProvider(ollamaConfig)
        assertNotNull(ollamaProvider)
    }

    @Test
    fun testModelDiscovery_OpenAiCompatible() = runBlocking {
        val baseUrl = startLocalHttpServer { exchange ->
            if (exchange.requestMethod == "GET" && exchange.requestURI.path == "/v1/models") {
                val json = """
                    {
                        "object": "list",
                        "data": [
                            {"id": "llama3.1:8b", "object": "model"},
                            {"id": "qwen2.5-coder:7b", "object": "model"}
                        ]
                    }
                """.trimIndent()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, json.toByteArray().size.toLong())
                exchange.responseBody.write(json.toByteArray())
                exchange.close()
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }

        val config = PrivateBrainConfig(baseUrl = baseUrl)
        val provider = OpenAiPrivateBrainProvider(config)

        assertTrue(provider.isAvailable())
        val models = provider.listModels()
        assertEquals(2, models.size)
        assertTrue(models.contains("llama3.1:8b"))
        assertTrue(models.contains("qwen2.5-coder:7b"))
    }

    @Test
    fun testModelDiscovery_Ollama() = runBlocking {
        val baseUrl = startLocalHttpServer { exchange ->
            if (exchange.requestMethod == "GET" && exchange.requestURI.path == "/api/tags") {
                val json = """
                    {
                        "models": [
                            {"name": "mistral:7b", "model": "mistral:7b"},
                            {"name": "deepseek-coder:6.7b", "model": "deepseek-coder:6.7b"}
                        ]
                    }
                """.trimIndent()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, json.toByteArray().size.toLong())
                exchange.responseBody.write(json.toByteArray())
                exchange.close()
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }

        val config = PrivateBrainConfig(baseUrl = baseUrl, serverType = PrivateBrainServerType.OLLAMA)
        val provider = OllamaPrivateBrainProvider(config)

        assertTrue(provider.isAvailable())
        val models = provider.listModels()
        assertEquals(2, models.size)
        assertTrue(models.contains("mistral:7b"))
        assertTrue(models.contains("deepseek-coder:6.7b"))
    }

    @Test
    fun testUnavailableServer_RejectsModelLoad() = runBlocking {
        // Point to an inactive port where no server is running
        val config = PrivateBrainConfig(baseUrl = "http://127.0.0.1:54321/v1")
        val service = InferenceService(context)
        service.updatePrivateBrainConfig(config)

        assertFalse("Service must report server is unavailable", service.getActiveProvider().isAvailable())

        try {
            service.loadModel("llama3.1:8b")
            fail("Must throw PrivateBrainUnavailableException when server is offline")
        } catch (e: PrivateBrainUnavailableException) {
            assertTrue("Exception message should describe unavailability", e.message!!.contains("unavailable", ignoreCase = true))
            assertFalse("Local model must not be marked as loaded", service.isLocalModelLoaded())
        }
    }

    @Test
    fun testInvalidApiKey_ThrowsAuthException() = runBlocking {
        val baseUrl = startLocalHttpServer { exchange ->
            val authHeader = exchange.requestHeaders.getFirst("Authorization")
            if (authHeader != "Bearer valid-secret-key") {
                val errJson = """{"error": {"message": "Invalid API key provided", "code": "invalid_api_key"}}"""
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(401, errJson.toByteArray().size.toLong())
                exchange.responseBody.write(errJson.toByteArray())
                exchange.close()
            } else {
                exchange.sendResponseHeaders(200, -1)
                exchange.close()
            }
        }

        val config = PrivateBrainConfig(baseUrl = baseUrl, apiKey = "bad-key")
        val provider = OpenAiPrivateBrainProvider(config)

        try {
            provider.listModels()
            fail("Must throw PrivateBrainAuthException on HTTP 401")
        } catch (e: PrivateBrainAuthException) {
            assertTrue("Error message should mention authentication", e.message!!.contains("Authentication failed", ignoreCase = true))
        }
    }

    @Test
    fun testModelNotFoundResponse_ThrowsModelNotFoundException() = runBlocking {
        val baseUrl = startLocalHttpServer { exchange ->
            if (exchange.requestURI.path.endsWith("/chat/completions")) {
                val errJson = """{"error": {"message": "The model 'non-existent-model' does not exist"}}"""
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(404, errJson.toByteArray().size.toLong())
                exchange.responseBody.write(errJson.toByteArray())
                exchange.close()
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }

        val config = PrivateBrainConfig(baseUrl = baseUrl)
        val provider = OpenAiPrivateBrainProvider(config)

        try {
            provider.generate(
                messages = listOf(ChatMessage(sender = "user", text = "Hello")),
                model = "non-existent-model",
                temperature = 0.7f,
                maxTokens = 100
            )
            fail("Must throw PrivateBrainModelNotFoundException on HTTP 404")
        } catch (e: PrivateBrainModelNotFoundException) {
            assertTrue("Error message should mention model not found", e.message!!.contains("not found", ignoreCase = true))
        }
    }

    @Test
    fun testSuccessfulRealResponseParsing() = runBlocking {
        val realModelOutput = "VULNERABILITY DETECTED: Exported ContentProvider with unrestricted read permissions."
        val baseUrl = startLocalHttpServer { exchange ->
            if (exchange.requestURI.path.endsWith("/chat/completions")) {
                val json = """
                    {
                        "id": "chatcmpl-test-123",
                        "choices": [
                            {
                                "index": 0,
                                "message": {
                                    "role": "assistant",
                                    "content": "$realModelOutput"
                                },
                                "finish_reason": "stop"
                            }
                        ]
                    }
                """.trimIndent()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, json.toByteArray().size.toLong())
                exchange.responseBody.write(json.toByteArray())
                exchange.close()
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }

        val config = PrivateBrainConfig(baseUrl = baseUrl)
        val provider = OpenAiPrivateBrainProvider(config)

        val result = provider.generate(
            messages = listOf(ChatMessage(sender = "user", text = "Audit this provider")),
            model = "llama3.1:8b",
            temperature = 0.5f,
            maxTokens = 256
        )

        assertEquals("Should parse actual content from HTTP response", realModelOutput, result)
    }

    @Test
    fun testRealStreamingResponseParsing() = runBlocking {
        val baseUrl = startLocalHttpServer { exchange ->
            if (exchange.requestURI.path.endsWith("/chat/completions")) {
                exchange.responseHeaders.set("Content-Type", "text/event-stream")
                exchange.sendResponseHeaders(200, 0)
                val os = exchange.responseBody

                val chunk1 = "data: {\"choices\": [{\"delta\": {\"content\": \"Analysis: \"}}]}\n\n"
                val chunk2 = "data: {\"choices\": [{\"delta\": {\"content\": \"Manifest inspected.\"}}]}\n\n"
                val done = "data: [DONE]\n\n"

                os.write(chunk1.toByteArray(StandardCharsets.UTF_8))
                os.flush()
                os.write(chunk2.toByteArray(StandardCharsets.UTF_8))
                os.flush()
                os.write(done.toByteArray(StandardCharsets.UTF_8))
                os.flush()
                os.close()
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }

        val config = PrivateBrainConfig(baseUrl = baseUrl)
        val provider = OpenAiPrivateBrainProvider(config)

        val chunks = provider.generateStream(
            messages = listOf(ChatMessage(sender = "user", text = "Stream inspect")),
            model = "llama3.1:8b",
            temperature = 0.5f,
            maxTokens = 256
        ).toList()

        assertEquals(2, chunks.size)
        assertEquals("Analysis: ", chunks[0])
        assertEquals("Manifest inspected.", chunks[1])
    }

    @Test
    fun testEmptyResponseRejection() = runBlocking {
        val baseUrl = startLocalHttpServer { exchange ->
            if (exchange.requestURI.path.endsWith("/chat/completions")) {
                val emptyContentJson = """
                    {
                        "choices": [
                            {
                                "message": {
                                    "role": "assistant",
                                    "content": "   "
                                }
                            }
                        ]
                    }
                """.trimIndent()
                exchange.responseHeaders.set("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, emptyContentJson.toByteArray().size.toLong())
                exchange.responseBody.write(emptyContentJson.toByteArray())
                exchange.close()
            } else {
                exchange.sendResponseHeaders(404, -1)
                exchange.close()
            }
        }

        val config = PrivateBrainConfig(baseUrl = baseUrl)
        val provider = OpenAiPrivateBrainProvider(config)

        try {
            provider.generate(
                messages = listOf(ChatMessage(sender = "user", text = "Hello")),
                model = "llama3.1:8b",
                temperature = 0.7f,
                maxTokens = 100
            )
            fail("Must throw PrivateBrainResponseException when model returns empty text")
        } catch (e: PrivateBrainResponseException) {
            assertTrue("Error message should mention empty content", e.message!!.contains("empty", ignoreCase = true))
        }
    }

    @Test
    fun testTimeoutHandling() = runBlocking {
        val baseUrl = startLocalHttpServer { exchange ->
            // Intentionally sleep longer than timeout
            try {
                Thread.sleep(2500)
            } catch (ignored: InterruptedException) {}
            exchange.sendResponseHeaders(200, -1)
            exchange.close()
        }

        // Configure short 1-second timeout
        val config = PrivateBrainConfig(
            baseUrl = baseUrl,
            connectTimeoutSeconds = 1,
            readTimeoutSeconds = 1
        )
        val provider = OpenAiPrivateBrainProvider(config)

        try {
            provider.generate(
                messages = listOf(ChatMessage(sender = "user", text = "Hello")),
                model = "llama3.1:8b",
                temperature = 0.7f,
                maxTokens = 100
            )
            fail("Must throw PrivateBrainTimeoutException on timeout")
        } catch (e: PrivateBrainTimeoutException) {
            assertTrue("Error message should mention timeout", e.message!!.contains("timed out", ignoreCase = true))
        }
    }

    @Test
    fun testOfflineModeWhenNotConfigured() = runBlocking {
        val service = InferenceService(context)
        // Reset configuration to empty
        service.updatePrivateBrainConfig(PrivateBrainConfig(baseUrl = ""))

        val streamOutput = service.generateStream("Audit APK").toList()
        assertEquals("When unconfigured, must output exact message", listOf("Private Brain is not configured."), streamOutput)
    }

    @Test
    fun testCloudFallbackDisabledByDefault() = runBlocking {
        val service = InferenceService(context)
        service.updatePrivateBrainConfig(PrivateBrainConfig(baseUrl = "", enableCloudFallback = false))
        service.setMode(AiMode.CLOUD)

        val streamOutput = service.generateStream("Audit APK").toList()
        assertTrue("Cloud mode without explicit fallback enablement must emit notice",
            streamOutput.any { it.contains("Cloud AI mode is disabled in Private Brain settings", ignoreCase = true) })
    }
}
