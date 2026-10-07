package com.mypurse.vault.ui

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.mypurse.vault.data.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.File

@Composable
fun DocumentViewer(doc: VaultDocument,repository: VaultRepository,onClose: ()->Unit,onEdit: ()->Unit,onFavorite: ()->Unit,onDelete: ()->Unit) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var file by remember {mutableStateOf<File?>(null)}
    var bitmap by remember {mutableStateOf<Bitmap?>(null)}
    var error by remember {mutableStateOf<String?>(null)}
    var page by remember {mutableIntStateOf(0)}
    var pageCount by remember {mutableIntStateOf(1)}
    var zoom by remember {mutableFloatStateOf(1f)}
    var pan by remember {mutableStateOf(Offset.Zero)}
    var sharing by remember {mutableStateOf(false)}
    LaunchedEffect(doc.id) {
        var decoded: File?=null
        try {decoded=repository.decryptedFile(doc);file=decoded}
        catch(e: Exception) {error=e.message ?: "Cannot open this document."}
        finally {if(!isActive) decoded?.delete()}
    }
    LaunchedEffect(file,page) {
        file?.let {
            bitmap=null;zoom=1f;pan=Offset.Zero
            try {val result=repository.render(it,doc.mime,page);bitmap=result.first;pageCount=result.second}
            catch(e: Exception) {error=e.message ?: "Preview unavailable."}
        }
    }
    DisposableEffect(Unit) {onDispose {file?.delete()} }
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=Pale) {
            Column(Modifier.safeDrawingPadding()) {
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.fillMaxWidth().padding(8.dp)) {
                    ToolIcon(Icons.AutoMirrored.Rounded.ArrowBack,"Back",onClick=onClose)
                    Text(doc.title,maxLines=1,modifier=Modifier.weight(1f),fontSize=17.sp)
                    ToolIcon(Icons.Rounded.Edit,"Edit document",onClick=onEdit)
                    ToolIcon(Icons.Rounded.Share,"Share document") {
                        if(!sharing) scope.launch {
                            sharing=true
                            try {
                                val exported=repository.decryptedFile(doc,true)
                                val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",exported)
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type=doc.mime;putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Share document"))
                            } catch(e: Exception) {error=e.message ?: "Cannot share this document."}
                            finally {sharing=false}
                        }
                    }
                }
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().pointerInput(Unit) {detectTransformGestures {_,movement,scale,_ -> zoom=(zoom*scale).coerceIn(1f,4f);pan=if(zoom==1f) Offset.Zero else pan+movement}},contentAlignment=Alignment.Center) {
                    if(bitmap!=null) Image(bitmap!!.asImageBitmap(),doc.title,Modifier.fillMaxSize().padding(18.dp).graphicsLayer {scaleX=zoom;scaleY=zoom;translationX=pan.x;translationY=pan.y})
                    else if(error==null) CircularProgressIndicator(color=Purple)
                    error?.let {Text(it,Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)}
                }
                Row(Modifier.fillMaxWidth().padding(horizontal=14.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick={page--},enabled=page>0) {Icon(Icons.Rounded.ChevronLeft,"Previous page")}
                    Text("${page+1} / $pageCount",modifier=Modifier.weight(1f))
                    IconButton(onClick={page++},enabled=page<pageCount-1) {Icon(Icons.Rounded.ChevronRight,"Next page")}
                    ToolIcon(Icons.Rounded.CenterFocusStrong,"Reset zoom") {zoom=1f;pan=Offset.Zero}
                    ToolIcon(if(doc.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,"Favorite document",doc.favorite,onFavorite)
                    ToolIcon(Icons.Rounded.DeleteOutline,"Delete document",onClick=onDelete)
                }
                Text("${doc.category} · ${readableSize(doc.size)}",fontSize=12.sp,color=Ink.copy(alpha=.6f),modifier=Modifier.padding(horizontal=26.dp,vertical=12.dp))
            }
        }
    }
}
