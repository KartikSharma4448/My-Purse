package com.mypurse.vault

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import com.mypurse.vault.data.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.UUID

class DashboardFlowTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun addCardAndBrowseImportedDocument() {
        compose.waitUntil(25000) {compose.onAllNodesWithContentDescription("Add card").fetchSemanticsNodes().isNotEmpty()}
        screenshot("empty-cards")
        compose.onNodeWithContentDescription("Add card").performClick()
        compose.onNodeWithText("Card title").performTextInput("Everyday")
        compose.onNodeWithText("Bank name").performTextInput("Finaci")
        compose.onNodeWithText("RuPay").performScrollTo().performClick()
        compose.onNodeWithText("Holder name").performTextInput("Demo Holder")
        compose.onNodeWithText("Card number (optional)").performTextInput("4242424242424242")
        compose.onNodeWithText("Valid upto MM/YY").performScrollTo().performTextInput("12/30")
        compose.onNodeWithText("CVV (back)").performScrollTo().performTextInput("123")
        compose.onNodeWithText("Save card").performScrollTo().performClick()
        compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Card Everyday, Finaci").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Card Everyday, Finaci").performClick()
        compose.onNodeWithText("Everyday").assertIsDisplayed()
        compose.onNodeWithContentDescription("Show or hide number").performClick()
        screenshot("card-details")
        compose.onNodeWithText("4242 4242 4242 4242").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Flip card").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show or hide CVV").performClick()
        compose.onNodeWithContentDescription("Back of Everyday").assertIsDisplayed()
        screenshot("bank-back")
        compose.onNodeWithContentDescription("Copy CVV").performScrollTo().performClick()
        compose.runOnIdle {
            val clipboard=compose.activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            org.junit.Assert.assertEquals("123",clipboard.primaryClip!!.getItemAt(0).text.toString())
            if(android.os.Build.VERSION.SDK_INT>=28) clipboard.clearPrimaryClip()
        }
        compose.onNodeWithContentDescription("Close card details").performClick()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val repo=(context.applicationContext as PurseApplication).repository
        val createdCard=runBlocking {repo.cards.first().first {it.title=="Everyday" && it.holder=="Demo Holder"}}
        val extras=listOf(VaultCard(UUID.randomUUID().toString(),"Travel","Finaci","Demo Holder","5555555555554444","02/30",color=1),VaultCard(UUID.randomUUID().toString(),"Personal","Finaci","Demo Holder","4111111111111111","08/29",color=2))
        val fixtureDir=File(context.cacheDir,"shared").apply {mkdirs()}
        val pdfFile=File(fixtureDir,"Personal Documents.pdf")
        val pdf=PdfDocument()
        try {
            val page=pdf.startPage(PdfDocument.PageInfo.Builder(400,550,1).create())
            page.canvas.drawText("Personal Documents",35f,70f,Paint().apply {textSize=24f;color=android.graphics.Color.rgb(70,80,110)})
            repeat(8) {page.canvas.drawRect(35f,120f+it*28,320f-it*10,130f+it*28,Paint().apply {color=android.graphics.Color.rgb(195,205,225)})}
            pdf.finishPage(page);pdfFile.outputStream().use {pdf.writeTo(it)}
        } finally {pdf.close()}
        var imported: VaultDocument?=null
        val otherDocs=mutableListOf<VaultDocument>()
        val imageFiles=mutableListOf<File>()
        try {
            runBlocking {
                extras.forEach {repo.save(it)}
                listOf("Identity","Certificates").forEachIndexed {index,category ->
                    val image=Bitmap.createBitmap(400,500,Bitmap.Config.ARGB_8888)
                    image.eraseColor(android.graphics.Color.WHITE)
                    val canvas=Canvas(image)
                    canvas.drawText(category,35f,75f,Paint().apply {textSize=28f;color=android.graphics.Color.rgb(90,70,140)})
                    repeat(6) {canvas.drawRect(35f,120f+it*30,330f-it*20,130f+it*30,Paint().apply {color=android.graphics.Color.rgb(190,180,220+index*10)})}
                    val input=File(fixtureDir,"$category.png");imageFiles+=input
                    input.outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}
                    otherDocs+=repo.import(FileProvider.getUriForFile(context,"${context.packageName}.files",input),category)
                }
                imported=repo.import(FileProvider.getUriForFile(context,"${context.packageName}.files",pdfFile),"Personal")
            }
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Card Everyday, Finaci").fetchSemanticsNodes().isNotEmpty()}
            screenshot("cards")
            compose.onNodeWithText("Documents").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Document Personal Documents").fetchSemanticsNodes().isNotEmpty()}
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Preview Personal Documents").fetchSemanticsNodes().isNotEmpty()}
            screenshot("documents")
            compose.onNodeWithContentDescription("Document Personal Documents").performTouchInput {swipeUp(startY=height*.85f,endY=height*.15f,durationMillis=600)}
            compose.onNodeWithTag("vault-carousel").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"2 of 3"))
            screenshot("documents-scrolled")
            compose.onNodeWithTag("vault-carousel").performTouchInput {swipeDown()}
            compose.onNodeWithTag("vault-carousel").assert(androidx.compose.ui.test.SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"1 of 3"))
            compose.onNodeWithContentDescription("Document Personal Documents").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Reset zoom").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("1 / 1").assertIsDisplayed()
            screenshot("document-viewer")
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithText("Cards").performClick()
            compose.onNodeWithContentDescription("Card Everyday, Finaci").assertIsDisplayed()
        } finally {
            runBlocking {extras.forEach {repo.delete(it)};repo.delete(createdCard);imported?.let {repo.delete(it)};otherDocs.forEach {repo.delete(it)}}
            imageFiles.forEach {it.delete()}
            pdfFile.delete()
        }
    }
    @Test fun idCardFrontBackAndImageCopy() {
        compose.waitUntil(25000) {compose.onAllNodesWithContentDescription("Add card").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithContentDescription("Add card").performClick()
        compose.onNodeWithText("ID Card").performClick()
        compose.onNodeWithContentDescription("Upload Front image").assertExists()
        compose.onNodeWithContentDescription("Upload Back image").assertExists()
        compose.onNodeWithText("Save card").performScrollTo().performClick()
        compose.onNodeWithText("Enter a card title.").assertExists()
        compose.onNodeWithContentDescription("Close").performScrollTo().performClick()
        val context=compose.activity
        val repo=(context.applicationContext as PurseApplication).repository
        val files=listOf("Front","Back").mapIndexed {index,side ->
            val file=File(File(context.cacheDir,"shared").apply {mkdirs()},"id-$side-${UUID.randomUUID()}.png")
            val bitmap=Bitmap.createBitmap(600,380,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(if(index==0) android.graphics.Color.rgb(210,238,235) else android.graphics.Color.rgb(224,213,247))
            Canvas(bitmap).drawText("DEMO ID - $side",40f,100f,Paint().apply {textSize=38f;color=android.graphics.Color.DKGRAY})
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
            file
        }
        val id=UUID.randomUUID().toString()
        val card=VaultCard(id,"Personal ID","","","","",kind="ID")
        try {
            runBlocking {repo.saveCard(card,FileProvider.getUriForFile(context,"${context.packageName}.files",files[0]),FileProvider.getUriForFile(context,"${context.packageName}.files",files[1]))}
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("ID card Personal ID, Front").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithContentDescription("ID card Personal ID, Front").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Front of Personal ID").fetchSemanticsNodes().isNotEmpty()}
            screenshot("id-front")
            compose.onNodeWithContentDescription("Flip card").performClick()
            compose.waitUntil(15000) {compose.onAllNodesWithContentDescription("Back of Personal ID").fetchSemanticsNodes().isNotEmpty()}
            screenshot("id-back")
            compose.onNodeWithContentDescription("Copy Back image").performClick()
            compose.waitUntil(10000) {
                val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.primaryClip?.getItemAt(0)?.uri!=null
            }
            compose.runOnIdle {
                val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                context.contentResolver.openInputStream(clipboard.primaryClip!!.getItemAt(0).uri)!!.use {
                    org.junit.Assert.assertArrayEquals(files[1].readBytes(),it.readBytes())
                }
                if(android.os.Build.VERSION.SDK_INT>=28) clipboard.clearPrimaryClip()
            }
            compose.onNodeWithContentDescription("Close card details").performClick()
        } finally {
            runBlocking {repo.dao.card(id)?.let {repo.delete(it)}}
            files.forEach {it.delete()}
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val output=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/mypurse-$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(output).use {it.readBytes()}
    }
}
