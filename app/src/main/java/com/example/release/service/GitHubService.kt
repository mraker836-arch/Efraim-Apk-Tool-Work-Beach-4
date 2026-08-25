package com.example.release.service

import com.example.release.model.CiErrorCategory
import com.example.release.model.CiFailureRecord
import com.example.release.model.ReleaseArtifactInfo
import com.example.release.model.ReleaseMetadata
import com.example.release.model.WorkflowRunStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class GitHubService {

    suspend fun getRepositoryInfo(
        owner: String,
        repo: String,
        token: String?
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "EFRAIM-APK-Workbench-Tool-M")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()
                Result.success(JSONObject(response))
            } else {
                val errorStream = conn.errorStream
                val errorBody = errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(Exception("GitHub API Error ($responseCode): $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun listWorkflowRuns(
        owner: String,
        repo: String,
        workflowFileName: String = "android-release.yml",
        token: String?
    ): Result<List<JSONObject>> = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFileName/runs?per_page=10")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "EFRAIM-APK-Workbench-Tool-M")
                if (!token.isNullOrBlank()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
                connectTimeout = 10000
                readTimeout = 10000
            }

            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                val runsArray = json.optJSONArray("workflow_runs") ?: JSONArray()
                val list = mutableListOf<JSONObject>()
                for (i in 0 until runsArray.length()) {
                    list.add(runsArray.getJSONObject(i))
                }
                Result.success(list)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(Exception("Failed to fetch workflow runs ($responseCode): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun triggerWorkflowDispatch(
        owner: String,
        repo: String,
        workflowFileName: String = "android-release.yml",
        ref: String = "main",
        inputs: Map<String, Any>,
        token: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (token.isBlank()) {
                return@withContext Result.failure(Exception("GitHub token required to dispatch workflow."))
            }

            val url = URL("https://api.github.com/repos/$owner/$repo/actions/workflows/$workflowFileName/dispatches")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", "EFRAIM-APK-Workbench-Tool-M")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
            }

            val payload = JSONObject().apply {
                put("ref", ref)
                put("inputs", JSONObject(inputs))
            }

            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(payload.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            if (responseCode == 204 || responseCode in 200..299) {
                Result.success(true)
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $responseCode"
                Result.failure(Exception("Workflow dispatch failed ($responseCode): $err"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
