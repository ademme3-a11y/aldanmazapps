package com.aldanmaz.drivedashboard.nav

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Ag katmani soyutlamasi; testlerde sahte istemci verilebilir. */
interface NavHttpClient {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String
    suspend fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): String
}

/**
 * HttpURLConnection tabanli istemci: IO dispatcher'inda calisir (ana is parcacigini bloklamaz),
 * zaman asimi ve HTTP hatalarini tipli [NavException]'a cevirir, tanimlayici User-Agent gonderir
 * (Nominatim/Photon/Valhalla kullanim politikalari bunu ister).
 */
class DefaultNavHttp(
    private val userAgent: String,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000
) : NavHttpClient {

    override suspend fun get(url: String, headers: Map<String, String>): String =
        request(url, "GET", null, headers)

    override suspend fun postJson(url: String, body: String, headers: Map<String, String>): String =
        request(url, "POST", body, headers)

    private suspend fun request(url: String, method: String, body: String?, headers: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = connectTimeoutMs
                    readTimeout = readTimeoutMs
                    requestMethod = method
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept", "application/json")
                    headers.forEach { (k, v) -> setRequestProperty(k, v) }
                    if (body != null) {
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw NavException.Http(code, text.take(300))
                text
            } catch (e: NavException) {
                throw e
            } catch (e: SocketTimeoutException) {
                throw NavException.Timeout()
            } catch (e: UnknownHostException) {
                throw NavException.Offline()
            } catch (e: ConnectException) {
                throw NavException.Offline()
            } catch (e: SSLException) {
                throw NavException.Network("güvenli bağlantı kurulamadı")
            } catch (e: IOException) {
                throw NavException.Network(e.message)
            } finally {
                conn?.disconnect()
            }
        }
}
