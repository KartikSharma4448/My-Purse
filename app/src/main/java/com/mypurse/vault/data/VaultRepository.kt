package com.mypurse.vault.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.Room
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultRepository(private val context: Context) {
    private val root = File(context.noBackupFilesDir, "vault").apply { mkdirs() }
    private val importLock = Mutex()
    private val cipher: StreamingAead
    val dao: VaultDao
    val cards get() = dao.cards()
    val documents get() = dao.documents()
    init {
        System.loadLibrary("sqlcipher")
        StreamingAeadConfig.register()
        val keyset = AndroidKeysetManager.Builder().withSharedPref(context, "streaming_keyset", "vault_keys")
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
            .withMasterKeyUri("android-keystore://mypurse_documents_master")
            .build()
        check(keyset.isUsingKeystore) { "Secure key storage is unavailable on this device." }
        cipher = keyset.keysetHandle.getPrimitive(StreamingAead::class.java)
        dao = Room.databaseBuilder(context, VaultDatabase::class.java, File(context.noBackupFilesDir, "vault.db").absolutePath)
            .openHelperFactory(SupportOpenHelperFactory(databaseSecret())).addMigrations(VaultDatabase.MIGRATION_1_2).build().dao()
    }

    private fun databaseSecret(): ByteArray {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val file = File(context.noBackupFilesDir, "database.key")
        val alias = "mypurse_database_master"
        val existing = store.getKey(alias, null) as? SecretKey
        // Do not regenerate a missing key for an existing vault.
        check(existing != null || !file.exists()) { "The vault encryption key is unavailable." }
        val key = existing ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
        val aes = Cipher.getInstance("AES/GCM/NoPadding")
        if (file.exists()) {
            val bytes = file.readBytes()
            require(bytes.size > 28) { "The vault key is damaged." }
            aes.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, bytes.copyOfRange(0,12)))
            return aes.doFinal(bytes.copyOfRange(12,bytes.size))
        }
        check(!File(context.noBackupFilesDir,"vault.db").exists()) { "The database key is missing." }
        val secret = ByteArray(32).also { SecureRandom().nextBytes(it) }
        aes.init(Cipher.ENCRYPT_MODE,key)
        val temp = File(context.noBackupFilesDir,"database.key.tmp")
        temp.outputStream().use { it.write(aes.iv); it.write(aes.doFinal(secret)) }
        check(temp.renameTo(file)) { "Cannot save the vault key." }
        return secret
    }

    suspend fun tidy() = withContext(Dispatchers.IO) {
        File(context.cacheDir,"preview").deleteRecursively()
        File(context.cacheDir,"shared").deleteRecursively()
        dao.orphanCardImages().forEach {delete(it)}
        val valid = dao.documentFiles().toSet()
        root.listFiles()?.filter { it.name !in valid }?.forEach { it.delete() }
    }
    suspend fun save(card: VaultCard) = withContext(Dispatchers.IO) { dao.save(card) }
    suspend fun save(doc: VaultDocument) = withContext(Dispatchers.IO) { dao.save(doc) }
    suspend fun delete(card: VaultCard) = withContext(Dispatchers.IO) {
        dao.delete(card)
        listOf(card.frontImageId,card.backImageId).filter {it.isNotEmpty()}.forEach {id -> dao.document(id)?.let {delete(it)} }
    }
    suspend fun saveCard(card: VaultCard,front: Uri?,back: Uri?) = withContext(Dispatchers.IO) {
        val added=mutableListOf<VaultDocument>()
        val old=dao.card(card.id)
        var committed=false
        try {
            suspend fun image(uri: Uri?,existing: String): String {
                if(uri==null) return existing
                val doc=import(uri,"Card image").also {added+=it}
                require(doc.mime.startsWith("image/")) {"Choose an image for each side of the ID card."}
                thumbnail(doc)
                return doc.id
            }
            val updated=if(card.kind=="ID") card.copy(frontImageId=image(front,card.frontImageId),backImageId=image(back,card.backImageId))
                else card.copy(frontImageId="",backImageId="")
            require(updated.kind!="ID" || (updated.frontImageId.isNotEmpty() && updated.backImageId.isNotEmpty())) {"Add front and back images."}
            dao.save(updated)
            committed=true
            listOfNotNull(old?.frontImageId,old?.backImageId).filter {it.isNotEmpty() && it!=updated.frontImageId && it!=updated.backImageId}
                .forEach {id -> dao.document(id)?.let {delete(it)}}
        } catch(e: Exception) {if(!committed) added.forEach {delete(it)};throw e}
    }
    suspend fun cardImage(id: String): Bitmap {
        val doc=dao.document(id) ?: error("Card image unavailable")
        return thumbnail(doc)
    }
    suspend fun delete(doc: VaultDocument) = withContext(Dispatchers.IO) { dao.delete(doc); File(root,doc.fileName).delete(); Unit }

    suspend fun import(uri: Uri, category: String): VaultDocument = withContext(Dispatchers.IO) {
        importLock.withLock {
            var name = "Document"
            context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use {
                if(it.moveToFirst()) name = it.getString(0) ?: name
            }
            val id = UUID.randomUUID().toString()
            val staging = File(root,"$id.part")
            val destination = File(root,"$id.vault")
            try {
                var size = 0L
                val digest = MessageDigest.getInstance("SHA-256")
                var mime = ""
                val input = context.contentResolver.openInputStream(uri)?.buffered() ?: error("Cannot open this file.")
                input.use { source ->
                    source.mark(16)
                    val header = ByteArray(12)
                    val count = source.read(header)
                    source.reset()
                    mime = detectMime(header,count)
                    require(mime.isNotEmpty()) { "Choose a PDF, JPEG, PNG or WebP document." }
                    staging.outputStream().use { raw ->
                        cipher.newEncryptingStream(raw,id.toByteArray()).use { output ->
                            val buffer = ByteArray(65536)
                            while(true) {
                                val countRead = source.read(buffer)
                                if(countRead < 0) break
                                size += countRead
                                require(size <= MAX_DOCUMENT_BYTES) { "The maximum document size is 100 MB." }
                                digest.update(buffer,0,countRead)
                                output.write(buffer,0,countRead)
                            }
                        }
                    }
                }
                require(size > 0) { "This file is empty." }
                val hash = digest.digest().joinToString("") { "%02x".format(it) }
                dao.duplicate(hash)?.let { error("Already stored as ${it.title}.") }
                check(staging.renameTo(destination)) { "Cannot save this document. Check device storage." }
                val doc = VaultDocument(id,name.substringBeforeLast('.').take(100),name.take(180),mime,category,destination.name,hash,size)
                dao.save(doc)
                doc
            } catch(e: Exception) { staging.delete(); destination.delete(); throw e }
        }
    }

    suspend fun decryptedFile(doc: VaultDocument, share: Boolean = false): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir,if(share) "shared" else "preview").apply { mkdirs() }
        val extension = when(doc.mime) { "application/pdf" -> "pdf"; "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val file = File(dir,"${doc.id}-${UUID.randomUUID()}.$extension")
        try {
            File(root,doc.fileName).inputStream().use { input ->
                cipher.newDecryptingStream(input,doc.id.toByteArray()).use { decoded -> file.outputStream().use { decoded.copyTo(it) } }
            }
            file
        } catch(e: Exception) { file.delete(); throw e }
    }
    suspend fun thumbnail(doc: VaultDocument): Bitmap = withContext(Dispatchers.IO) {
        val file = decryptedFile(doc)
        try { render(file,doc.mime,0,450).first } finally { file.delete() }
    }
    suspend fun render(file: File,mime: String,page: Int,width: Int = 1000): Pair<Bitmap,Int> = withContext(Dispatchers.IO) {
        if(mime == "application/pdf") {
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    require(renderer.pageCount > 0)
                    renderer.openPage(page.coerceIn(0,renderer.pageCount-1)).use { pdf ->
                        val height = (width.toFloat()*pdf.height/pdf.width).toInt().coerceIn(1,4000)
                        val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        pdf.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        bitmap to renderer.pageCount
                    }
                }
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path,bounds)
            require(bounds.outWidth > 0) { "This image cannot be displayed." }
            var sample = 1
            while(bounds.outWidth/sample > width*2 || bounds.outHeight/sample > width*3) sample *= 2
            val bitmap = BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize = sample })
                ?: error("This image cannot be displayed.")
            bitmap to 1
        }
    }
    companion object {
        const val MAX_DOCUMENT_BYTES = 100L*1024*1024
        fun detectMime(header: ByteArray,length: Int): String = when {
            length >= 5 && String(header,0,5,Charsets.US_ASCII) == "%PDF-" -> "application/pdf"
            length >= 8 && header.take(8).toByteArray().contentEquals(byteArrayOf(137.toByte(),80,78,71,13,10,26,10)) -> "image/png"
            length >= 3 && header[0] == 255.toByte() && header[1] == 216.toByte() && header[2] == 255.toByte() -> "image/jpeg"
            length >= 12 && String(header,0,4,Charsets.US_ASCII) == "RIFF" && String(header,8,4,Charsets.US_ASCII) == "WEBP" -> "image/webp"
            else -> ""
        }
    }
}
