package com.bookzzang.android.data

import com.bookzzang.android.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

data class BookSummary(
    val isbn13: String?, val title: String, val authors: List<String>, val publisher: String?,
    val coverUrl: String?, val pageCount: Int?, val thicknessMm: Double?, val readingStatus: ReadingStatus? = null,
    val favorite: Boolean = false, val rating: Double? = null, val reviewText: String? = null,
    val startedOn: String? = null, val finishedOn: String? = null
)
enum class ReadingStatus(val label: String) { WANT_TO_READ("읽고 싶어요"), READING("읽는 중"), READ("읽었어요") }

class BookzzangApi {
    private val baseUrl = BuildConfig.BOOKZZANG_API_BASE_URL.trimEnd('/')

    fun search(query: String): List<BookSummary> {
        require(baseUrl.isNotBlank()) { "local.properties에 BOOKZZANG_API_BASE_URL을 설정하세요." }
        val encoded = URLEncoder.encode(query, Charsets.UTF_8.name())
        val body = request("$baseUrl/api/public/books?query=$encoded&size=20")
        return JSONArray(body).let { result -> List(result.length()) { index -> result.getJSONObject(index).toBook() } }
    }

    fun findBook(isbn13: String): BookSummary =
        JSONObject(request("$baseUrl/api/public/books/isbn/$isbn13")).toBook()

    fun registerBook(accessToken: String, isbn13: String, status: ReadingStatus, favorite: Boolean = false,
                     rating: Double? = null, reviewText: String? = null, startedOn: String? = null,
                     finishedOn: String? = null) {
        val payload = JSONObject().put("isbn13", isbn13).put("readingStatus", status.name)
            .put("favorite", favorite).apply {
                rating?.let { put("rating", it) }
                reviewText?.takeIf(String::isNotBlank)?.let { put("reviewText", it) }
                startedOn?.takeIf(String::isNotBlank)?.let { put("startedOn", it) }
                finishedOn?.takeIf(String::isNotBlank)?.let { put("finishedOn", it) }
            }.toString()
        request("$baseUrl/api/me/books", "POST", accessToken, payload)
    }

    fun loadShelf(accessToken: String): List<BookSummary> {
        val body = request("$baseUrl/api/me/books", token = accessToken)
        return JSONArray(body).let { result -> List(result.length()) { index -> result.getJSONObject(index).toBook() } }
    }

    fun reorderShelf(accessToken: String, books: List<BookSummary>) {
        val isbnOrder = JSONArray().apply {
            books.forEach { book -> put(requireNotNull(book.isbn13) { "ISBN이 없는 도서는 순서를 저장할 수 없습니다." }) }
        }
        request(
            "$baseUrl/api/me/shelf/order",
            method = "PUT",
            token = accessToken,
            body = JSONObject().put("isbn13s", isbnOrder).toString()
        )
    }

    fun isEmailAvailable(email: String): Boolean {
        val body = request("$baseUrl/api/public/auth/email-availability", "POST", body = JSONObject().put("email", email).toString())
        return JSONObject(body).getBoolean("available")
    }

    fun signUp(email: String, password: String, nickname: String, gender: String?, ageGroup: Int?) {
        val payload = JSONObject().put("email", email).put("password", password).put("nickname", nickname).apply {
            gender?.let { put("gender", it) }
            ageGroup?.let { put("ageGroup", it) }
        }.toString()
        request("$baseUrl/api/public/auth/signup", "POST", body = payload)
    }

    fun onboard(accessToken: String, name: String, gender: String) {
        val payload = JSONObject().put("name", name).put("gender", gender).toString()
        request("$baseUrl/api/me/onboarding", "POST", accessToken, payload)
    }

    private fun request(url: String, method: String = "GET", token: String? = null, body: String? = null): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = method
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            body?.let {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { out -> out.write(it.toByteArray()) }
            }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(connection.responseCode in 200..299) { requestErrorMessage(connection.responseCode) }
            response
        } finally { connection.disconnect() }
    }

    private fun requestErrorMessage(statusCode: Int): String = when (statusCode) {
        400 -> "입력한 내용을 확인해 주세요."
        403 -> "이메일 인증을 먼저 완료해 주세요."
        409 -> "이미 가입된 이메일입니다. 로그인해 주세요."
        429 -> "요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."
        503 -> "인증 서비스 설정이 아직 완료되지 않았습니다."
        else -> "요청 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
    }

    private fun JSONObject.toBook() = BookSummary(
        isbn13 = nullableString("isbn13"), title = nullableString("title").orEmpty(),
        authors = optJSONArray("authors")?.let { array -> List(array.length()) { array.getString(it) } }.orEmpty(),
        publisher = nullableString("publisher"), coverUrl = nullableString("coverImageUrl"),
        pageCount = optInt("pageCount").takeIf { it > 0 },
        thicknessMm = if (has("thicknessMm") && !isNull("thicknessMm")) optDouble("thicknessMm") else null,
        readingStatus = optString("readingStatus").takeIf(String::isNotBlank)?.let { value ->
            runCatching { ReadingStatus.valueOf(value) }.getOrNull()
        },
        favorite = optBoolean("favorite", false),
        rating = if (has("rating") && !isNull("rating")) optDouble("rating") else null,
        reviewText = nullableString("reviewText"),
        startedOn = nullableString("startedOn"),
        finishedOn = nullableString("finishedOn")
    )

    private fun JSONObject.nullableString(name: String): String? =
        optString(name).trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}
