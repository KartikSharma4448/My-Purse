package com.mypurse.vault.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mypurse.vault.VaultViewModel
import com.mypurse.vault.data.*
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin
import kotlin.math.PI
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween

val Purple = Color(0xFF7052F3)
val Ink = Color(0xFF283147)
val Pale = Color(0xFFF8F9FF)
val palettes = listOf(
    listOf(Color(0xFF05BAF5),Color(0xFF078EAB),Color(0xFF77F8C8)),
    listOf(Color(0xFFC591FA),Color(0xFF6544E7),Color(0xFF5BB9FF)),
    listOf(Color(0xFFFFCA94),Color(0xFFFB8BAC),Color(0xFF94CFFF),Color(0xFFBF87ED))
)

@Composable
fun PurseApp(vm: VaultViewModel, activity: FragmentActivity) {
    val scheme = lightColorScheme(primary=Purple,background=Pale,surface=Color.White,onSurface=Ink,onBackground=Ink)
    MaterialTheme(colorScheme=scheme) {
        val state by vm.state.collectAsStateWithLifecycle()
        var introDone by rememberSaveable { mutableStateOf(false) }
        var pendingImport by remember { mutableStateOf<Uri?>(null) }
        val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingImport=it }
        val prefs = remember { VaultPreferences(activity) }
        val lockEnabled by prefs.lockEnabled.collectAsStateWithLifecycle(false)
        var unlocked by rememberSaveable { mutableStateOf(false) }
        val dashboardState=rememberSaveableStateHolder()
        LockOnBackground(lockEnabled) { unlocked=false }
        when {
            !introDone -> IntroVideo { introDone=true }
            state.fatal != null -> Box(Modifier.fillMaxSize().background(Pale).safeDrawingPadding(),contentAlignment=Alignment.Center) {
                Column(Modifier.padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Lock, "Vault unavailable", tint=Purple, modifier=Modifier.size(44.dp))
                    Text("Vault unavailable",style=MaterialTheme.typography.headlineSmall)
                    Text(state.fatal!!,Modifier.padding(top=12.dp))
                }
            }
            !state.ready -> Box(Modifier.fillMaxSize().background(Pale),contentAlignment=Alignment.Center) { CircularProgressIndicator(color=Purple) }
            lockEnabled && !unlocked -> UnlockScreen(activity) { unlocked=true }
            else -> dashboardState.SaveableStateProvider("dashboard") {
                Dashboard(vm,prefs,activity,pendingImport,{pendingImport=null},
                    {importPicker.launch(arrayOf("application/pdf","image/jpeg","image/png","image/webp"))}) {unlocked=true}
            }
        }
    }
}

@Composable
fun Dashboard(vm: VaultViewModel,prefs: VaultPreferences,activity: FragmentActivity,pendingImport: Uri?,onDismissImport: ()->Unit,onImport: ()->Unit,onAuthenticated: ()->Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var favorites by rememberSaveable { mutableStateOf(false) }
    var editorId by rememberSaveable { mutableStateOf<String?>(null) }
    val editor=state.cards.find {it.id==editorId}
    var addingCard by rememberSaveable { mutableStateOf(false) }
    var cardDetails by remember { mutableStateOf<VaultCard?>(null) }
    var documentDetails by remember { mutableStateOf<VaultDocument?>(null) }
    var editingDocument by remember { mutableStateOf<VaultDocument?>(null) }
    var deletingCard by remember { mutableStateOf<VaultCard?>(null) }
    var deletingDocument by remember { mutableStateOf<VaultDocument?>(null) }
    val cards = state.cards.filter { !favorites || it.favorite }
    val docs = state.documents.filter { !favorites || it.favorite }
    val pager = key(tab,favorites) {
        rememberPagerState(pageCount={ if(tab==0) cards.size else docs.size })
    }
    LaunchedEffect(tab,favorites) { if(pager.pageCount>0) pager.scrollToPage(0) }
    LaunchedEffect(state.message) {if(state.message!=null) vm.dismissMessage()}
    val add = {
        vm.dismissMessage()
        if(tab==0) { addingCard=true;editorId=null } else onImport()
    }
    Box(Modifier.fillMaxSize().background(Pale)) {
        val branch by animateFloatAsState(if(tab==1) -1f else 1f,tween(450),label="Ribbon branch")
        val phase=pager.currentPage+pager.currentPageOffsetFraction
        Ribbon(phase,branch,tab==1,Modifier.fillMaxSize())
        Column(Modifier.safeDrawingPadding().widthIn(max=760.dp).fillMaxSize().align(Alignment.TopCenter)) {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val phase=pager.currentPage+pager.currentPageOffsetFraction
                val viewportHeight=maxHeight
                val cardWidth=(maxWidth*.744f).coerceAtMost(400.dp)
                val cardHeight=maxOf(cardWidth*.63f,(208 * LocalDensity.current.fontScale.coerceIn(1f,1.8f)).dp.coerceAtMost(cardWidth*.90f))
                val documentHeight=cardWidth*.57f
                val itemHeight=if(tab==0) cardHeight+20.dp else documentHeight+26.dp
                val total = if(tab==0) cards.size else docs.size
                if(total==0) {
                    Spacer(Modifier.fillMaxSize())
                } else {
                    VerticalPager(state=pager,pageSize=PageSize.Fixed(itemHeight),contentPadding=PaddingValues(vertical=((maxHeight-itemHeight)/2).coerceAtLeast(0.dp)),
                        beyondViewportPageCount=1,pageSpacing=26.dp,modifier=Modifier.fillMaxSize().testTag("vault-carousel").semantics {stateDescription="${pager.currentPage+1} of $total"},key={ index ->
                            if(tab==0) cards.getOrNull(index)?.id ?: "card-placeholder-$index"
                            else docs.getOrNull(index)?.id ?: "document-placeholder-$index"
                        }) { index ->
                        val selectedCard=cards.getOrNull(index)
                        val selectedDocument=docs.getOrNull(index)
                        if((tab==0 && selectedCard==null) || (tab==1 && selectedDocument==null)) return@VerticalPager
                        val offset = ((pager.currentPage-index)+pager.currentPageOffsetFraction).coerceIn(-1f,1f)
                        Box(Modifier.fillMaxWidth().height(itemHeight),contentAlignment=Alignment.Center) {
                            val motion=Modifier.graphicsLayer {
                                scaleX=1f-offset.absoluteValue*.375f;scaleY=scaleX
                                rotationZ=offset*13f
                                val y=(.5f-offset*(itemHeight+26.dp).toPx()/viewportHeight.toPx()).coerceIn(0f,1f)
                                val center=if(tab==0) .69f-.24f*sin(PI.toFloat()*y)+.012f*sin(phase*.5f)
                                    else .38f+.24f*sin(PI.toFloat()*y)+.012f*sin(phase*.5f)
                                translationX=(center-.5f)*size.width
                                alpha=1f-offset.absoluteValue*.18f
                            }
                            val cardFace=motion.width(cardWidth)
                            if(tab==0) {
                                if(selectedCard!!.kind=="ID") FlippableCard(selectedCard,vm.repository!!,false,false,cardFace.height(cardHeight)) {cardDetails=selectedCard}
                                else BankCard(selectedCard,cardFace.height(cardHeight)) { cardDetails=selectedCard }
                            }
                            else DocumentTile(selectedDocument!!,vm.repository!!,cardFace.height(documentHeight)) { documentDetails=selectedDocument }
                        }
                    }
                }
                Column(Modifier.align(if(tab==0) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(22.dp)) {
                    GlassControl(Icons.Rounded.Add,if(tab==0) "Add card" else "Import document",!state.busy,add)
                    GlassControl(Icons.Rounded.Remove,if(tab==0) "Delete current card" else "Delete current document",total>0 && !state.busy) {
                        if(tab==0) deletingCard=cards.getOrNull(pager.currentPage) else deletingDocument=docs.getOrNull(pager.currentPage)
                    }
                }
                if(state.busy) LinearProgressIndicator(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal=24.dp),color=Purple)
            }
            BottomTabs(tab) {tab=it;favorites=false}
        }
    }
    if(addingCard || editor!=null) CardEditor(editor,vm.repository!!,{addingCard=false;editorId=null},state.busy,state.error) {card,front,back -> vm.saveCard(card,front,back) {addingCard=false;editorId=null}}
    cardDetails?.let { card ->
        val current=state.cards.find {it.id==card.id} ?: card
        CardDetails(current,vm.repository!!,{cardDetails=null},{editorId=current.id;cardDetails=null},{vm.save(current.copy(favorite=!current.favorite))},{deletingCard=current;cardDetails=null})
    }
    pendingImport?.let { uri -> ImportDialog(onDismissImport) { cat -> vm.import(uri,cat);onDismissImport() } }
    documentDetails?.let { doc ->
        val current=state.documents.find {it.id==doc.id} ?: doc
        DocumentViewer(current,vm.repository!!,{documentDetails=null},{editingDocument=current;documentDetails=null},{vm.save(current.copy(favorite=!current.favorite))},{deletingDocument=current;documentDetails=null})
    }
    editingDocument?.let { doc -> DocumentEditor(doc,{editingDocument=null},state.busy) {vm.save(it) {editingDocument=null}} }
    deletingCard?.let { card -> DeleteDialog(card.title,{deletingCard=null}) {vm.delete(card);deletingCard=null} }
    deletingDocument?.let { doc -> DeleteDialog(doc.title,{deletingDocument=null}) {vm.delete(doc);deletingDocument=null} }
}

@Composable
fun Ribbon(phase: Float,activeBranch: Float,left: Boolean,modifier: Modifier) {
    Canvas(modifier) {
        // Both ribbon branches and the carousel use the same normalized path.
        listOf(-1f,1f).forEach {branch ->
            val path=Path()
            val leftWidth=size.width*if(branch<0) .24f else .025f
            val rightWidth=size.width*if(branch>0) .24f else .025f
            for(i in 0..80) {
                val t=i/80f
                val center=curveCenter(t,phase,branch)
                val x=(if(left) 1f-center else center)*size.width-(if(left) rightWidth else leftWidth)
                if(i==0) path.moveTo(x,t*size.height) else path.lineTo(x,t*size.height)
            }
            for(i in 80 downTo 0) {
                val t=i/80f
                val center=curveCenter(t,phase,branch)
                path.lineTo((if(left) 1f-center else center)*size.width+(if(left) leftWidth else rightWidth),t*size.height)
            }
            path.close()
            val opacity=.25f+.22f*(1f-(branch-activeBranch).absoluteValue/2f)
            drawPath(path,Brush.verticalGradient(colorStops=arrayOf(
                0f to Color(0xFFC8CEFC).copy(alpha=0f),
                .14f to Color(0xFFC8CEFC).copy(alpha=opacity*.72f),
                .50f to Color(0xFFAABBF3).copy(alpha=opacity),
                .86f to Color(0xFFD8D1FC).copy(alpha=opacity*.72f),
                1f to Color(0xFFD8D1FC).copy(alpha=0f)
            )))
        }
    }
}

internal fun curveCenter(t: Float,phase: Float,branch: Float): Float {
    val split=sin(PI.toFloat()*t)
    return .95f-.45f*split+.02f*sin(phase*.2f)*split+branch*.065f*split*split
}

@Composable
fun ToolIcon(icon: ImageVector,label: String,selected: Boolean=false,onClick: ()->Unit) {
    IconButton(onClick,modifier=Modifier.size(44.dp)) { Icon(icon,label,tint=if(selected) Purple else Ink.copy(alpha=.65f),modifier=Modifier.size(22.dp)) }
}

@Composable
fun GlassControl(icon: ImageVector,label: String,enabled: Boolean=true,onClick: ()->Unit) {
    Surface(onClick=onClick,enabled=enabled,shape=CircleShape,color=Color(0xFF171A2C),shadowElevation=8.dp,
        modifier=Modifier.size(42.dp)) {
        Box(contentAlignment=Alignment.Center) { Icon(icon,label,tint=Purple.copy(alpha=if(enabled) 1f else .35f),modifier=Modifier.size(25.dp)) }
    }
}

@Composable
fun BottomTabs(tab: Int,onSelect: (Int)->Unit) {
    Surface(shape=RoundedCornerShape(40.dp),color=Color.White,shadowElevation=4.dp,
        modifier=Modifier.padding(horizontal=18.dp).fillMaxWidth().height(105.dp),border=BorderStroke(1.dp,Color.White)) {
        Row(Modifier.fillMaxSize().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            listOf("Cards","Documents").forEachIndexed { i,label ->
                Surface(onClick={onSelect(i)},shape=RoundedCornerShape(35.dp),color=if(i==tab) Color(0xFFF5F2FF) else Color.Transparent,
                    modifier=Modifier.weight(1f).fillMaxHeight()) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                        if(i==0) Box(Modifier.size(57.dp,36.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(palettes[2]))) {
                            Box(Modifier.padding(7.dp).size(11.dp,9.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFFFDD68)))
                        } else Icon(Icons.Rounded.Folder,"Documents folder",tint=Color(0xFFF4BD26),modifier=Modifier.size(36.dp))
                        Text(label,fontSize=12.sp,color=if(i==tab) Purple else Ink,maxLines=1)
                        Box(Modifier.padding(top=3.dp).size(44.dp,3.dp).clip(CircleShape).background(if(i==tab) Purple else Color.Transparent))
                    }
                }
                if(i==0) VerticalDivider(Modifier.height(42.dp).padding(horizontal=1.dp),color=Ink.copy(alpha=.14f))
            }
        }
    }
}

@Composable
fun EmptyVault(tab: Int,filtered: Boolean,modifier: Modifier,onAdd: ()->Unit) {
    val shape=RoundedCornerShape(25.dp)
    Box(modifier.aspectRatio(if(tab==0) 1.6f else .85f).clip(shape).background(Color.White.copy(alpha=.56f))
        .border(1.5.dp,Color.White,shape).clickable(onClick=onAdd).padding(24.dp),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(if(tab==0) Icons.Rounded.CreditCard else Icons.Rounded.Description,null,tint=Purple.copy(alpha=.45f),modifier=Modifier.size(50.dp))
            Text(if(filtered) "No matches" else if(tab==0) "No cards yet" else "No documents yet",color=Ink.copy(alpha=.7f),fontSize=16.sp,modifier=Modifier.padding(top=12.dp))
        }
    }
}

@Composable
fun BankCard(card: VaultCard,modifier: Modifier=Modifier,onClick: ()->Unit={}) {
    val colors=palettes[card.color.coerceIn(0,2)]
    val shape=RoundedCornerShape(25.dp)
    Box(modifier.clip(shape).background(Brush.linearGradient(colors)).border(1.dp,Color.White.copy(alpha=.8f),shape)
        .clickable(onClick=onClick).semantics {contentDescription="Card ${card.title}, ${card.bank}"}) {
        Canvas(Modifier.matchParentSize()) {
            val p=Path().apply {moveTo(size.width*.12f,0f);cubicTo(size.width*.6f,size.height*.5f,size.width*.7f,size.height*.65f,size.width*.75f,size.height);lineTo(size.width,size.height);lineTo(size.width,0f);close()}
            drawPath(p,Color.White.copy(alpha=.16f));drawLine(Color.White.copy(alpha=.7f),Offset(size.width*.12f,0f),Offset(size.width,size.height*.8f),1.dp.toPx())
        }
        Column(Modifier.fillMaxSize().padding(22.dp),verticalArrangement=Arrangement.SpaceBetween) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(card.bank,fontSize=21.sp,color=Color.White,modifier=Modifier.weight(1f),maxLines=1,overflow=TextOverflow.Ellipsis)
                if(card.favorite) Icon(Icons.Rounded.Star,null,tint=Color.White,modifier=Modifier.size(17.dp))
                Box(Modifier.padding(start=8.dp).size(32.dp,22.dp)) {
                    Box(Modifier.size(21.dp).clip(CircleShape).background(Color.White.copy(alpha=.92f)))
                    Box(Modifier.padding(start=12.dp).size(21.dp).clip(CircleShape).background(Color.White.copy(alpha=.65f)))
                }
            }
            Box(Modifier.size(39.dp,30.dp).clip(RoundedCornerShape(6.dp)).background(Brush.linearGradient(listOf(Color(0xFFFFF0A1),Color(0xFFE3B84E))))) {
                Canvas(Modifier.matchParentSize()) {drawLine(Color(0xFFB89442),Offset(size.width*.35f,0f),Offset(size.width*.35f,size.height),1.dp.toPx());drawLine(Color(0xFFB89442),Offset(0f,size.height*.5f),Offset(size.width,size.height*.5f),1.dp.toPx())}
            }
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text("••••  ••••  ••••",color=Color.White,fontSize=19.sp,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f))
                Text(card.number.takeLast(4).ifEmpty {"••••"},color=Color.White,fontSize=19.sp,modifier=Modifier.padding(start=10.dp))
            }
            Row {
                Column(Modifier.weight(1f)) {Text("Card Holder Name",color=Color.White.copy(alpha=.8f),fontSize=9.sp);Text(card.holder,color=Color.White,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis)}
                Column {Text("Expiry Date",color=Color.White.copy(alpha=.8f),fontSize=9.sp);Text(card.expiry.ifEmpty {"--/--"},color=Color.White,fontSize=13.sp)}
            }
        }
    }
}

@Composable
fun DocumentTile(doc: VaultDocument,repository: VaultRepository,modifier: Modifier,onClick: ()->Unit) {
    val preview by produceState<Result<android.graphics.Bitmap>?>(null,doc.id) {value=runCatching {repository.thumbnail(doc)}}
    val bitmap=preview?.getOrNull()
    val colors=when(doc.category) {"Identity"->palettes[1];"Certificates"->palettes[2];else->palettes[0]}
    val shape=RoundedCornerShape(23.dp)
    Box(modifier.clip(shape).background(Brush.linearGradient(colors)).border(1.dp,Color.White,shape)
        .clickable(onClick=onClick).semantics {contentDescription="Document ${doc.title}"}) {
        if(bitmap!=null) Image(bitmap.asImageBitmap(),"Preview ${doc.title}",Modifier.fillMaxSize().graphicsLayer {alpha=.37f},contentScale=androidx.compose.ui.layout.ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(colors.map {it.copy(alpha=.64f)})))
        Text(doc.title,fontSize=22.sp,color=Color(0xFF651B54),maxLines=2,overflow=TextOverflow.Ellipsis,
            modifier=Modifier.align(Alignment.Center).padding(18.dp),textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}
