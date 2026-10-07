package com.mypurse.vault

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mypurse.vault.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VaultStorageTest {
    @Test fun encryptedCardRoundTripAndDocumentImport() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=(context.applicationContext as PurseApplication).repository
        val id=UUID.randomUUID().toString()
        val card=VaultCard(id,"Test card","Test bank","PRIVATE HOLDER $id","4242424242424242","12/30",network="RuPay",cvv="123")
        val file=File(context.cacheDir,"test-$id.pdf")
        val pdf=PdfDocument()
        try {
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(300,400,1).create())
            page.canvas.drawText("Private document $id",20f,50f,Paint().apply {textSize=18f})
            pdf.finishPage(page)
            file.outputStream().use {pdf.writeTo(it)}
        } finally {pdf.close()}
        var doc: VaultDocument?=null
        try {
            repo.save(card)
            assertEquals(card,repo.cards.first().find {it.id==id})
            val database=File(context.noBackupFilesDir,"vault.db").readBytes()
            assertFalse(String(database,Charsets.ISO_8859_1).contains("PRIVATE HOLDER"))
            assertFalse(String(database.take(16).toByteArray()).startsWith("SQLite format 3"))
            doc=repo.import(Uri.fromFile(file),"Personal")
            val encrypted=File(context.noBackupFilesDir,"vault/${doc.fileName}")
            assertFalse(encrypted.readBytes().contentEquals(file.readBytes()))
            val decoded=repo.decryptedFile(doc)
            assertArrayEquals(file.readBytes(),decoded.readBytes())
            decoded.delete()
            val thumbnail=repo.thumbnail(doc)
            assertTrue(thumbnail.width>0)
            try {repo.import(Uri.fromFile(file),"Personal");fail("Duplicate must be rejected")} catch(e: IllegalStateException) {assertTrue(e.message!!.contains("Already stored"))}
            assertFalse(File(context.noBackupFilesDir,"vault").listFiles()!!.any {it.extension=="part"})
            val bytes=encrypted.readBytes();bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();encrypted.writeBytes(bytes)
            try {repo.decryptedFile(doc);fail("Tampered ciphertext must be rejected")} catch(_: Exception) {}
        } finally {
            repo.delete(card);doc?.let {repo.delete(it)};file.delete()
        }
    }
    @Test fun failedIdImportRollsBackAndDeletionRemovesBothImages() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=(context.applicationContext as PurseApplication).repository
        val files=(0..1).map {side ->
            File(context.cacheDir,"side-$side-${UUID.randomUUID()}.png").also {file ->
                val bitmap=android.graphics.Bitmap.createBitmap(120,80,android.graphics.Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(if(side==0) android.graphics.Color.RED else android.graphics.Color.BLUE)
                file.outputStream().use {bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            }
        }
        val card=VaultCard(UUID.randomUUID().toString(),"ID test","","","","",kind="ID")
        try {
            val before=repo.dao.documentFiles().toSet()
            try {
                repo.saveCard(card,Uri.fromFile(files[0]),Uri.fromFile(File(context.cacheDir,"missing-image.png")))
                fail("An incomplete pair must not be saved")
            } catch(_: java.io.FileNotFoundException) {}
            assertEquals(before,repo.dao.documentFiles().toSet())
            assertNull(repo.dao.card(card.id))
            repo.saveCard(card,Uri.fromFile(files[0]),Uri.fromFile(files[1]))
            val saved=repo.dao.card(card.id)!!
            assertNotEquals(saved.frontImageId,saved.backImageId)
            assertFalse(repo.documents.first().any {it.id==saved.frontImageId || it.id==saved.backImageId})
            assertTrue(repo.cardImage(saved.backImageId).width>0)
            val stored=listOf(saved.frontImageId,saved.backImageId).map {repo.dao.document(it)!!}
            stored.forEachIndexed {index,doc -> assertFalse(File(context.noBackupFilesDir,"vault/${doc.fileName}").readBytes().contentEquals(files[index].readBytes()))}
            repo.delete(saved)
            stored.forEach {assertNull(repo.dao.document(it.id));assertFalse(File(context.noBackupFilesDir,"vault/${it.fileName}").exists())}
        } finally {repo.dao.card(card.id)?.let {repo.delete(it)};files.forEach {it.delete()}}
    }
}
