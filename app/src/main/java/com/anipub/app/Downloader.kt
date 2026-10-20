package com.animepub.app

import android.content.Context
import android.os.Environment
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap


class Downloader(private val ctx: Context, private val onUpdate: (String) -> Unit) {

    private val jobs = ConcurrentHashMap<String, Thread>()
    private val cancelled = ConcurrentHashMap.newKeySet<String>()

    private class Cancelled : Exception()

    companion object {
        /** Root folder that holds every anime folder. */
        fun root(ctx: Context): File =
            File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: ctx.filesDir, "AniPub").apply { mkdirs() }

        /** Makes a string safe to use as a folder name. */
        fun safeFolder(s: String): String =
            s.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(80).ifEmpty { "Other" }

        /** Makes a string safe to use as a file name - without extension. */
        fun safeFile(s: String): String =
            s.replace(Regex("[^A-Za-z0-9 ._()-]"), "_").trim().take(80).ifEmpty { "episode" }
    }

    fun dir(folder: String): File = File(root(ctx), safeFolder(folder)).apply { mkdirs() }

    fun start(key: String, url: String, folder: String, name: String, headers: Map<String, String>) {
        if (jobs.containsKey(key)) return
        cancelled.remove(key)
        val t = Thread { work(key, url, folder, name, headers) }
        jobs[key] = t
        t.start()
    }

    fun cancel(key: String) { cancelled.add(key) }

    private fun emit(key: String, name: String, status: String, pct: Int, speed: Long, eta: Long, msg: String = "") {
        onUpdate(
            JSONObject().put("key", key).put("name", name).put("status", status).put("pct", pct)
                .put("speed", speed).put("eta", eta).put("msg", msg).toString()
        )
    }

    private fun open(url: String, headers: Map<String, String>): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        if (c.responseCode !in 200..299) throw Exception("HTTP ${c.responseCode}")
        return c
    }

    private fun work(key: String, url: String, folder: String, name: String, h: Map<String, String>) {
        val isHls = url.substringBefore('?').endsWith(".m3u8")
        val out = File(dir(folder), safeFile(name) + if (isHls) ".ts" else ".mp4")
        try {
            emit(key, name, "running", 0, 0, -1)
            if (isHls) hls(key, name, url, h, out) else file(key, name, url, h, out)
            emit(key, name, "done", 100, 0, 0, out.name)
        } catch (e: Cancelled) {
            out.delete(); emit(key, name, "cancelled", 0, 0, 0)
        } catch (e: Exception) {
            out.delete(); emit(key, name, "error", 0, 0, 0, e.message ?: "failed")
        } finally {
            jobs.remove(key); cancelled.remove(key)
        }
    }

    private fun file(key: String, name: String, url: String, h: Map<String, String>, out: File) {
        val c = open(url, h)
        val total = c.contentLengthLong
        var done = 0L
        val t0 = System.currentTimeMillis()
        var last = 0L
        c.inputStream.use { ins ->
            FileOutputStream(out).use { os ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    if (cancelled.contains(key)) throw Cancelled()
                    val n = ins.read(buf)
                    if (n < 0) break
                    os.write(buf, 0, n)
                    done += n
                    val now = System.currentTimeMillis()
                    if (now - last > 500) {
                        last = now
                        val sp = done * 1000 / maxOf(1L, now - t0)
                        val pct = if (total > 0) (done * 100 / total).toInt() else 0
                        emit(key, name, "running", pct, sp, if (total > 0 && sp > 0) (total - done) / sp else -1)
                    }
                }
            }
        }
        c.disconnect()
    }

    private fun text(url: String, h: Map<String, String>): String {
        val c = open(url, h)
        return c.inputStream.bufferedReader().use { it.readText() }.also { c.disconnect() }
    }

    private fun hls(key: String, name: String, url: String, h: Map<String, String>, out: File) {
        var base = url
        var pl = text(base, h)
        if (pl.contains("#EXT-X-STREAM-INF")) {              // master playlist: pick highest bandwidth
            val lines = pl.lines()
            var best = ""
            var bw = -1L
            for (i in lines.indices) if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                val b = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLong() ?: 0L
                val u = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                if (u != null && b > bw) { bw = b; best = u.trim() }
            }
            if (best.isEmpty()) throw Exception("No playable variant")
            base = URL(URL(base), best).toString()
            pl = text(base, h)
        }
        if (Regex("#EXT-X-KEY:.*METHOD=(?!NONE)").containsMatchIn(pl)) throw Exception("Encrypted streams are not supported")
        val segs = pl.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (segs.isEmpty()) throw Exception("Empty playlist")
        val t0 = System.currentTimeMillis()
        var bytes = 0L
        FileOutputStream(out).use { os ->
            segs.forEachIndexed { i, s ->
                if (cancelled.contains(key)) throw Cancelled()
                val c = open(URL(URL(base), s).toString(), h)
                c.inputStream.use { ins -> bytes += ins.copyTo(os) }
                c.disconnect()
                val el = maxOf(1L, System.currentTimeMillis() - t0)
                val perSeg = el / (i + 1)
                emit(key, name, "running", (i + 1) * 100 / segs.size, bytes * 1000 / el, perSeg * (segs.size - i - 1) / 1000)
            }
        }
    }
}
