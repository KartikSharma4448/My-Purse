package com.mypurse.vault.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mypurse.vault.data.*
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch

@Composable
fun CopyButton(label: String,value: String,sensitive: Boolean=false) {
    val context=LocalContext.current
    if(value.isNotBlank()) ToolIcon(Icons.Rounded.ContentCopy,"Copy $label") {
        val clip=ClipData.newPlainText(label,value)
        if(sensitive) clip.description.extras=PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
        if(android.os.Build.VERSION.SDK_INT<33) Toast.makeText(context,"$label copied",Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun CopyField(label: String,value: String,sensitive: Boolean=false,display: String=value) {
    Row(verticalAlignment=Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label,fontSize=12.sp,color=Ink.copy(alpha=.65f))
            Text(if(value.isBlank()) "Not added" else display,color=Ink)
        }
        CopyButton(label,value,sensitive)
    }
}

@Composable
fun CopyImageButton(id: String,repository: VaultRepository,side: String) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var copying by remember {mutableStateOf(false)}
    ToolIcon(Icons.Rounded.ContentCopy,"Copy $side image") {
        if(!copying) scope.launch {
            copying=true
            try {
                val doc=repository.dao.document(id) ?: error("Image unavailable")
                val file=repository.decryptedFile(doc,share=true)
                val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
                val clip=ClipData.newUri(context.contentResolver,"$side image",uri)
                clip.description.extras=PersistableBundle().apply {putBoolean("android.content.extra.IS_SENSITIVE",true)}
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
                if(android.os.Build.VERSION.SDK_INT<33) Toast.makeText(context,"Image copied",Toast.LENGTH_SHORT).show()
            } catch(e: Exception) {Toast.makeText(context,e.message ?: "Cannot copy image",Toast.LENGTH_SHORT).show()}
            finally {copying=false}
        }
    }
}

@Composable
fun CardImage(id: String,repository: VaultRepository,label: String,modifier: Modifier=Modifier) {
    val result by key(id) {produceState<Result<android.graphics.Bitmap>?>(null) {value=runCatching {repository.cardImage(id)}}}
    Box(modifier.background(Color.White),contentAlignment=Alignment.Center) {
        val bitmap=result?.getOrNull()
        when {
            bitmap!=null -> Image(bitmap.asImageBitmap(),label,Modifier.fillMaxSize(),contentScale=ContentScale.Fit)
            result==null -> CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)
            else -> Icon(Icons.Rounded.BrokenImage,"Image unavailable",tint=Purple)
        }
    }
}

@Composable
fun FlippableCard(card: VaultCard,repository: VaultRepository,back: Boolean,revealCvv: Boolean,modifier: Modifier,onFlip: ()->Unit) {
    val angle by animateFloatAsState(if(back) 180f else 0f,tween(480),label="Card flip")
    Box(modifier.graphicsLayer {rotationY=angle;cameraDistance=14*density}) {
        Box(Modifier.fillMaxSize().graphicsLayer {rotationY=if(angle>90f) 180f else 0f}) {
            if(card.kind=="ID") {
                val side=if(angle>90f) "Back" else "Front"
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Color.White)
                    .border(1.dp,Color.White,RoundedCornerShape(24.dp)).clickable(onClick=onFlip)
                    .semantics {contentDescription="ID card ${card.title}, $side"}) {
                    CardImage(if(angle>90f) card.backImageId else card.frontImageId,repository,"$side of ${card.title}",Modifier.fillMaxSize().padding(10.dp))
                }
            } else if(angle<=90f) BankCard(card,Modifier.fillMaxSize(),onFlip)
            else Column(Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(palettes[card.color.coerceIn(0,2)]))
                .clickable(onClick=onFlip).semantics {contentDescription="Back of ${card.title}"}) {
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().height(40.dp).background(Color(0xFF252735)))
                Row(Modifier.padding(20.dp).fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(38.dp).background(Color.White.copy(alpha=.85f)))
                    Column(Modifier.padding(start=14.dp),horizontalAlignment=Alignment.End) {
                        Text("CVV",fontSize=11.sp,color=Color.White)
                        Text(if(card.cvv.isEmpty()) "---" else if(revealCvv) card.cvv else "•••",fontSize=20.sp,color=Color.White)
                    }
                }
                Text(card.network,color=Color.White,modifier=Modifier.padding(horizontal=20.dp))
            }
        }
    }
}
