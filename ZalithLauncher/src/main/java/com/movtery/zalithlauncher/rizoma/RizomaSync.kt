package com.movtery.zalithlauncher.rizoma

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream

/**
 * Atualização do modpack pelo mesmo manifest do launcher do PC (release "modpack" no GitHub).
 * Só baixa o que mudou (SHA-1). Zips com "extrair" são extraídos na pasta indicada.
 */
object RizomaSync {
    const val MANIFEST =
        "https://github.com/Gabirubs/RizomaMC-Launcher-Releases/releases/download/modpack/manifest.json"

    data class Arquivo(val caminho: String, val url: String, val sha1: String, val tamanho: Long, val extrair: String?)

    data class Plano(val versao: String, val baixar: List<Arquivo>, val apagar: List<File>, val bytes: Long) {
        val emDia get() = baixar.isEmpty() && apagar.isEmpty()
    }

    private fun abrir(url: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.instanceFollowRedirects = true
        c.connectTimeout = 20000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", "RizomaMCMobile/1.0")
        if (c.responseCode != 200) throw IllegalStateException("Falha ao baixar ($url): ${c.responseCode}")
        return c
    }

    suspend fun verificar(jogo: File): Plano = withContext(Dispatchers.IO) {
        val json = JSONObject(abrir(MANIFEST).inputStream.bufferedReader().use { it.readText() })
        val lista = json.getJSONArray("arquivos")
        val remotos = (0 until lista.length()).map { lista.getJSONObject(it) }
            .filter { o -> !o.has("plataformas") || o.getJSONArray("plataformas").let { p -> (0 until p.length()).any { p.getString(it) == "android" } } }
            .map { Arquivo(it.getString("caminho"), it.getString("url"), it.getString("sha1"), it.getLong("tamanho"), it.optString("extrair").ifEmpty { null }) }
        val gerenciadas = json.optJSONArray("gerenciadas")?.let { g -> (0 until g.length()).map { g.getString(it) } } ?: listOf("mods")

        val baixar = remotos.filter { r ->
            val f = File(jogo, r.caminho)
            !f.isFile || f.length() != r.tamanho || sha1(f.inputStream()) != r.sha1
        }
        val esperados = remotos.map { it.caminho }.toSet()
        val apagar = gerenciadas.flatMap { pasta ->
            File(jogo, pasta).walkTopDown().filter { it.isFile }
                .filter { it.relativeTo(jogo).invariantSeparatorsPath !in esperados }.toList()
        }
        Plano(json.optString("versao", "0"), baixar, apagar, baixar.sumOf { it.tamanho })
    }

    suspend fun aplicar(jogo: File, plano: Plano, progresso: (feito: Long, total: Long) -> Unit) = withContext(Dispatchers.IO) {
        plano.apagar.forEach { it.delete() }
        var feito = 0L
        val buf = ByteArray(1 shl 16)
        for (r in plano.baixar) {
            val destino = File(jogo, r.caminho)
            destino.parentFile?.mkdirs()
            val parte = File(destino.path + ".part")
            abrir(r.url).inputStream.use { ent ->
                parte.outputStream().use { sai ->
                    while (true) {
                        val n = ent.read(buf)
                        if (n < 0) break
                        sai.write(buf, 0, n)
                        feito += n
                        progresso(feito, plano.bytes)
                    }
                }
            }
            if (sha1(parte.inputStream()) != r.sha1) {
                parte.delete()
                throw IllegalStateException("Arquivo corrompido no download: ${r.caminho}")
            }
            destino.delete()
            parte.renameTo(destino)
            r.extrair?.let { extrair(destino, File(jogo, it)) }
        }
    }

    private fun extrair(zip: File, pasta: File) {
        val base = pasta.canonicalPath
        ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val alvo = File(pasta, e.name)
                if (e.isDirectory || !alvo.canonicalPath.startsWith(base)) continue
                alvo.parentFile?.mkdirs()
                alvo.outputStream().use { z.copyTo(it) }
            }
        }
    }

    private fun sha1(input: InputStream): String = input.use { s ->
        val md = MessageDigest.getInstance("SHA-1")
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = s.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
        md.digest().joinToString("") { "%02x".format(it) }
    }
}
