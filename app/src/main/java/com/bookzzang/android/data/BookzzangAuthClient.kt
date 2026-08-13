package com.bookzzang.android.data

import com.bookzzang.android.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject

data class AuthSession(val accessToken: String, val refreshToken: String)

class BookzzangAuthClient {
    private val baseUrl = BuildConfig.BOOKZZANG_API_BASE_URL.trimEnd('/')
    fun signIn(email: String, password: String): AuthSession = toSession(post("/api/public/auth/login", JSONObject().put("email", email).put("password", password)))
    fun refresh(refreshToken: String): AuthSession = toSession(post("/api/public/auth/token/refresh", JSONObject().put("refreshToken", refreshToken)))
    fun logout(refreshToken: String) { post("/api/public/auth/logout", JSONObject().put("refreshToken", refreshToken)) }
    private fun post(path: String, body: JSONObject): JSONObject {
        require(baseUrl.isNotBlank()) { "API base URL is required" }
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "POST"; connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json"); connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            val response = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) throw IllegalStateException(errorMessage(connection.responseCode))
            if (connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) JSONObject() else JSONObject(response)
        } finally { connection.disconnect() }
    }
    private fun toSession(response: JSONObject) = AuthSession(response.getString("accessToken"), response.getString("refreshToken"))
    private fun errorMessage(status: Int) = when (status) {
        401 -> "이메일 또는 비밀번호가 올바르지 않습니다."
        409 -> "이미 가입된 이메일입니다. 로그인해 주세요."
        else -> "인증 요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
    }
}
