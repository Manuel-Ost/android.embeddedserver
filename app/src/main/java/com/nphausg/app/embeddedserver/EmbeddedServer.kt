/*
 * Created by nphau on 4/10/24, 7:04 PM
 * Copyright (c) 2024 . All rights reserved.
 * Last modified 4/10/24, 7:04 PM
 */

package com.nphausg.app.embeddedserver

import android.content.Context
import android.os.Build
import com.nphausg.app.embeddedserver.data.Database
import com.nphausg.app.embeddedserver.data.models.Cart
import com.nphausg.app.embeddedserver.utils.FileUtils
import com.nphausg.app.embeddedserver.utils.NetworkUtils
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpStatusCode.Companion.PartialContent
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.http.content.staticResources
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EmbeddedServer {

    private const val PORT = 6868
    private val ioScope = CoroutineScope(Dispatchers.IO)
    private const val FILE_NAME = "file.jpg"
    private const val MP3_FILE_NAME = "bye_bye_bye_nsync.mp3"

    private lateinit var appContext: Context
    private lateinit var filesDir: File

    fun init(context: Context) {
        appContext = context.applicationContext
        filesDir = File("/storage/emulated/0/Pictures")
    }

    private val server by lazy {
        embeddedServer(Netty, PORT) {
            // configures Cross-Origin Resource Sharing. CORS is needed to make calls from arbitrary
            // JavaScript clients, and helps us prevent issues down the line.
            install(CORS) {
                anyHost()
            }
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                })
            }
            routing {
                //  staticResources
                staticResources("/static", "") {
                    default("index.html")
                }
                get("/") {
                    okText(call, "Hello!! You are here in ${Build.MODEL}")
                }
                get("/fruits") {
                    okText(call, FileUtils.readText("data.json").also {
                        Database.FRUITS.addAll(FileUtils.decode<Cart>(it).items)
                    })
                }
                get("/fruits/{id}") {
                    val id = call.parameters["id"]
                    val fruit = Database.FRUITS.find { it.id == id }
                    if (fruit != null) {
                        okText(call, Json.encodeToString(fruit))
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
                get("/download") {
                    val file = File("files/$FILE_NAME")
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(
                            key = ContentDisposition.Parameters.FileName,
                            value = FILE_NAME
                        ).toString()
                    )
                    call.response.status(HttpStatusCode.OK)
                    call.respondFile(file)
                }
                get("/explorer/{subdir...}") {
                    val subdir = call.parameters.getAll("subdir")?.joinToString(File.separator) ?: ""
                    val dir = File(filesDir, subdir)
                    if (dir.exists() && dir.isDirectory) {
                        // Link zum übergeordneten Verzeichnis, falls nicht im Wurzelverzeichnis
                        val parentLink = if (subdir.isNotEmpty()) {
                            val parent = subdir.substringBeforeLast(File.separator, "")
                            "<a href=\"/explorer/$parent\">⬅️ .. (zurück)</a><br><br>"
                        } else {
                            ""
                        }
                        val files = dir.listFiles()?.joinToString("<br>") {
                            val path = if (subdir.isEmpty()) it.name else "$subdir/${it.name}"
                            if (it.isDirectory)
                                "<a href=\"/explorer/$path\">[Ordner] ${it.name}</a> " +
                                        "<a href=\"/download-zip/$path\">[Download ZIP]</a>"
                            else
                                "<a href=\"/download/$path\" download=\"${it.name}\">${it.name}</a> " +
                                        "<a href=\"/preview/$path\">[Vorschau]</a>"
                        } ?: ""
                        call.respondText(
                            "<html><body><h2>Inhalt von /$subdir</h2>$parentLink$files</body></html>",
                            ContentType.Text.Html
                        )
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Ordner nicht gefunden")
                    }
                }
                get("/download/{subdir...}") {
                    val subdir = call.parameters.getAll("subdir")?.joinToString(File.separator) ?: ""
                    val file = File(filesDir, subdir)
                    if (file.exists() && file.isFile) {
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(
                                ContentDisposition.Parameters.FileName, file.name
                            ).toString()
                        )
                        call.respondFile(file)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Datei nicht gefunden")
                    }
                }
                get("/download-zip/{subdir...}") {
                    val subdir = call.parameters.getAll("subdir")?.joinToString(File.separator) ?: ""
                    val dir = File(filesDir, subdir)
                    if (dir.exists() && dir.isDirectory) {
                        val zipFile = File.createTempFile("archive_", ".zip")
                        ZipOutputStream(zipFile.outputStream()).use { zipOut ->
                            fun zipDir(folder: File, basePath: String) {
                                folder.listFiles()?.forEach { file ->
                                    val entryName = if (basePath.isEmpty()) file.name else "$basePath/${file.name}"
                                    if (file.isDirectory) {
                                        zipDir(file, entryName)
                                    } else {
                                        zipOut.putNextEntry(ZipEntry(entryName))
                                        file.inputStream().use { it.copyTo(zipOut) }
                                        zipOut.closeEntry()
                                    }
                                }
                            }
                            zipDir(dir, "")
                        }
                        call.response.header(
                            HttpHeaders.ContentDisposition,
                            ContentDisposition.Attachment.withParameter(
                                ContentDisposition.Parameters.FileName, "${dir.name}.zip"
                            ).toString()
                        )
                        call.respondFile(zipFile)
                        zipFile.deleteOnExit()
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Verzeichnis nicht gefunden")
                    }
                }
                get("/preview/{subdir...}") {
                    val subdir = call.parameters.getAll("subdir")?.joinToString(File.separator) ?: ""
                    val file = File(filesDir, subdir)
                    if (file.exists() && file.isFile) {
                        val ext = file.extension.lowercase()
                        when (ext) {
                            "jpg", "jpeg", "png", "gif", "bmp", "webp" -> {
                                // Bild direkt anzeigen
                                call.respondFile(file)
                            }
                            "txt", "md", "csv", "log" -> {
                                // Textdatei als HTML anzeigen
                                val text = file.readText()
                                call.respondText(
                                    "<html><body><pre>${text.replace("<", "&lt;").replace(">", "&gt;")}</pre></body></html>",
                                    ContentType.Text.Html
                                )
                            }
                            else -> {
                                call.respondText("Keine Vorschau verfügbar", ContentType.Text.Plain)
                            }
                        }
                    } else {
                        call.respond(HttpStatusCode.NotFound, "Datei nicht gefunden")
                    }
                }
            }
        }
    }

    fun start() {
        ioScope.launch {
            try {
                server.start(wait = false)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun stop() {
        try {
            server.stop(1_000, 2_000)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val host: String
        get() = String.format("%s:%d", NetworkUtils.getLocalIpAddress(), PORT)

    private suspend fun okText(call: ApplicationCall, text: String) {
        call.respondText(
            text = text,
            status = HttpStatusCode.OK,
            contentType = ContentType.Application.Json
        )
    }
}