package com.animepub.app

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** device registration, login, download checks and the saved account. **/
object Api {
    private const val BASE = "https://anipub.org"
    private const val PREFS = "account"

    class Gate(val status: String, val downloads: Int = 0)

    /** One HTTP reply: status code, body text, and the "anipub" login cookie if the server set one. **/
    private class Reply(val code: Int, val text: String, val cookie: String?)

    fun deviceId(ctx: Context): String =
        Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: ""

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun token(ctx: Context): String? = prefs(ctx).getString("aToken", null)
    fun name(ctx: Context): String = prefs(ctx).getString("name", "") ?: ""
    fun link(ctx: Context): String = prefs(ctx).getString("link", "") ?: ""

    fun saveAccount(ctx: Context, aToken: String, name: String, link: String) {
        prefs(ctx).edit().putString("aToken", aToken).putString("name", name).putString("link", link).apply()
    }

    fun clear(ctx: Context) { prefs(ctx).edit().clear().apply() }

    private fun post(path: String, body: JSONObject): Reply {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        return try {
            c.requestMethod = "POST"
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.instanceFollowRedirects = false
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            // Find "Set-Cookie: anipub=..." in this same response.
            val cookie = c.headerFields.entries
                .filter { it.key?.equals("Set-Cookie", ignoreCase = true) == true }
                .flatMap { it.value ?: emptyList() }
                .map { it.substringBefore(";") }
                .firstOrNull { it.startsWith("anipub=") }
            Reply(code, text, cookie)
        } catch (e: Exception) {
            Reply(-1, "", null)
        } finally {
            c.disconnect()
        }
    }

    private fun getWithCookie(path: String, cookie: String): String {
        val c = URL(BASE + path).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 15000
            c.readTimeout = 20000
            c.setRequestProperty("Cookie", cookie)
            c.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        } finally {
            c.disconnect()
        }
    }

    /** records this device as a guest on the server. **/
    fun register(ctx: Context): Boolean {
        val r = post("/sign-in/id", JSONObject().put("id", deviceId(ctx)))
        return try { JSONArray(r.text).optJSONObject(0)?.optBoolean("success") == true } catch (e: Exception) { false }
    }

    /**
     * Email and password login. Returns "" on success (and saves the account),
     * or a error message to show the user.
     **/
    fun login(ctx: Context, email: String, pass: String): String {
        val r = post("/Login", JSONObject().put("email", email).put("pass", pass))
        if (r.code < 0) return "No connection. Check your internet and try again."

        val cookie = r.cookie
        if (cookie == null) {
            // No cookie means the server refused. Its reply holds the message.
            val msg = try { JSONArray(r.text).optString(0) } catch (e: Exception) { "" }
            if (r.text.contains("Pending", ignoreCase = true)) {
                return "Your account is not verified yet. Check your email."
            }
            return if (msg.isNotEmpty() && msg != "/Home") msg else "Email or password is wrong."
        }

        // Logged in. Ask /getDet for the account token, name and photo.
        val det = try { JSONArray(getWithCookie("/getDet", cookie)).optJSONObject(0) } catch (e: Exception) { null }
        val aToken = det?.optString("aToken").orEmpty()
        if (aToken.isEmpty()) return "Could not load your account. Try again."
        saveAccount(ctx, aToken, det?.optString("name").orEmpty(), det?.optString("link").orEmpty())
        return ""
    }

    /**
     * Creates an account. Returns "" when the server says /Home (check your email),
     * otherwise the error message shows from the server.
     **/
    fun signUp(name: String, email: String, pass: String): String {
        val r = post("/Sign-Up", JSONObject().put("name", name).put("email", email).put("pass", pass))
        if (r.code < 0) return "No connection. Check your internet and try again."
        return try {
            when (val first = JSONArray(r.text).opt(0)) {
                "/Home" -> ""
                is JSONObject -> first.optString("error").ifEmpty { "Sign up failed. Try again." }
                else -> "Sign up failed. Try again."
            }
        } catch (e: Exception) {
            "Sign up failed. Try again."
        }
    }

    /** Runs before every download. Returns status "ok", "login" or "error". **/
    fun check(ctx: Context): Gate {
        val body = JSONObject().put("id", deviceId(ctx))
        token(ctx)?.let { body.put("aToken", it) }
        val r = post("/api/downloads/check", body)
        val o = try { JSONArray(r.text).optJSONObject(0) } catch (e: Exception) { null } ?: return Gate("error")
        if (o.optString("msge") == "login") return Gate("login")
        return if (o.optBoolean("success")) Gate("ok", o.optInt("downloads")) else Gate("error")
    }
}
