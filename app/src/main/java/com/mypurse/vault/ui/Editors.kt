package com.mypurse.vault.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalDensity
import com.mypurse.vault.data.*
import java.util.UUID
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.PasswordVisualTransformation
import coil.compose.AsyncImage

@Composable
fun CardEditor(card: VaultCard?,repository: VaultRepository,onClose: ()->Unit,busy: Boolean,saveMessage: String?,onSave: (VaultCard,Uri?,Uri?)->Unit) {
    val keyboard=LocalSoftwareKeyboardController.current
    var title by rememberSaveable {mutableStateOf(card?.title ?: "")}
    var bank by rememberSaveable {mutableStateOf(card?.bank ?: "")}
    var holder by rememberSaveable {mutableStateOf(card?.holder ?: "")}
    var number by rememberSaveable {mutableStateOf(card?.number ?: "")}
    var expiry by rememberSaveable {mutableStateOf(card?.expiry ?: "")}
    var notes by rememberSaveable {mutableStateOf(card?.notes ?: "")}
    var color by rememberSaveable {mutableIntStateOf(card?.color ?: 0)}
    var kind by rememberSaveable {mutableStateOf(card?.kind ?: "BANK")}
    var network by rememberSaveable {mutableStateOf(card?.network ?: "Visa")}
    var cvv by rememberSaveable {mutableStateOf(card?.cvv ?: "")}
    var front by rememberSaveable {mutableStateOf<Uri?>(null)}
    var back by rememberSaveable {mutableStateOf<Uri?>(null)}
    val frontPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {if(it!=null) front=it}
    val backPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {if(it!=null) back=it}
    var error by remember {mutableStateOf<String?>(null)}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.widthIn(max=560.dp).fillMaxWidth().padding(16.dp).heightIn(max=760.dp),shape=RoundedCornerShape(24.dp),color=Pale) {
            Column(Modifier.verticalScroll(rememberScrollState()).imePadding().padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {Text(if(card==null) "Add card" else "Edit card",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f));ToolIcon(Icons.Rounded.Close,"Close",onClick=onClose)}
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf("BANK" to "Bank Card","ID" to "ID Card").forEachIndexed {index,(value,label) ->
                        SegmentedButton(selected=kind==value,onClick={kind=value},shape=SegmentedButtonDefaults.itemShape(index,2)) {Text(label)}
                    }
                }
                OutlinedTextField(title,{title=it.take(50)},label={Text("Card title")},singleLine=true,modifier=Modifier.fillMaxWidth())
                if(kind=="BANK") {
                OutlinedTextField(bank,{bank=it.take(40)},label={Text("Bank name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                Text("Card type",style=MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    listOf("RuPay","Visa","Mastercard").forEach {value -> FilterChip(selected=network==value,onClick={network=value},label={Text(value)})}
                }
                OutlinedTextField(holder,{holder=it.take(60)},label={Text("Holder name")},singleLine=true,modifier=Modifier.fillMaxWidth())
                OutlinedTextField(number,{number=it.filter(Char::isDigit).take(19)},label={Text("Card number (optional)")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(expiry,{expiry=it.take(5)},label={Text("Valid upto MM/YY")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Ascii),modifier=Modifier.fillMaxWidth())
                OutlinedTextField(cvv,{cvv=it.filter(Char::isDigit).take(3)},label={Text("CVV (back)")},singleLine=true,visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.NumberPassword),modifier=Modifier.fillMaxWidth())
                } else {
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        ImageUpload("Front",front,card?.frontImageId.orEmpty(),repository,Modifier.weight(1f)) {frontPicker.launch(arrayOf("image/jpeg","image/png","image/webp"))}
                        ImageUpload("Back",back,card?.backImageId.orEmpty(),repository,Modifier.weight(1f)) {backPicker.launch(arrayOf("image/jpeg","image/png","image/webp"))}
                    }
                }
                OutlinedTextField(notes,{notes=it.take(1000)},label={Text("Notes (optional)")},maxLines=3,modifier=Modifier.fillMaxWidth())
                Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    palettes.forEachIndexed {i,colors -> Box(Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(colors)).border(if(i==color) 3.dp else 0.dp,Purple,CircleShape).clickable {color=i},contentAlignment=Alignment.Center) {if(i==color) Icon(Icons.Rounded.Check,"Selected color",tint=Color.White)} }
                }
                error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
                if(error==null && saveMessage!=null) Text(saveMessage,color=MaterialTheme.colorScheme.error,fontSize=13.sp)
                Button(onClick={
                    error=if(kind=="BANK") CardValidation.error(title,bank,holder,number,expiry)
                        ?: if(cvv.isNotEmpty() && cvv.length!=3) "Enter a 3-digit CVV, or leave it empty." else null
                    else when {
                        title.isBlank() -> "Enter a card title."
                        front==null && card?.frontImageId.isNullOrEmpty() -> "Upload the front image."
                        back==null && card?.backImageId.isNullOrEmpty() -> "Upload the back image."
                        else -> null
                    }
                    if(error==null) {
                        keyboard?.hide()
                        onSave(VaultCard(card?.id ?: UUID.randomUUID().toString(),title.trim(),if(kind=="BANK") bank.trim() else "",if(kind=="BANK") holder.trim() else "",if(kind=="BANK") number else "",if(kind=="BANK") expiry else "",notes.trim(),color,card?.favorite ?: false,card?.createdAt ?: System.currentTimeMillis(),kind,network,if(kind=="BANK") cvv else "",card?.frontImageId.orEmpty(),card?.backImageId.orEmpty()),if(kind=="ID") front else null,if(kind=="ID") back else null)
                    }
                },enabled=!busy,modifier=Modifier.fillMaxWidth()) {Icon(Icons.Rounded.Check,null);Spacer(Modifier.width(8.dp));Text(if(busy) "Saving..." else "Save card")}
                PrivacyPolicyButton()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetails(card: VaultCard,repository: VaultRepository,onClose: ()->Unit,onEdit: ()->Unit,onFavorite: ()->Unit,onDelete: ()->Unit) {
    var reveal by remember {mutableStateOf(false)}
    var flipped by remember {mutableStateOf(false)}
    var revealCvv by remember {mutableStateOf(false)}
    val cardHeight=(208 * LocalDensity.current.fontScale.coerceIn(1f,1.8f)).dp
    ModalBottomSheet(onDismissRequest=onClose,containerColor=Pale,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(card.title,style=MaterialTheme.typography.headlineSmall,modifier=Modifier.weight(1f))
                ToolIcon(Icons.Rounded.Close,"Close card details",onClick=onClose)
            }
            FlippableCard(card,repository,flipped,revealCvv,Modifier.fillMaxWidth().height(cardHeight)) {flipped=!flipped;revealCvv=false}
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                Text(if(flipped) "Back" else "Front",style=MaterialTheme.typography.labelLarge)
                ToolIcon(Icons.Rounded.Flip,"Flip card") {flipped=!flipped;revealCvv=false}
                if(card.kind=="ID") CopyImageButton(if(flipped) card.backImageId else card.frontImageId,repository,if(flipped) "Back" else "Front")
                if(flipped && card.kind=="BANK") ToolIcon(if(revealCvv) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,"Show or hide CVV") {revealCvv=!revealCvv}
            }
            Row {ToolIcon(Icons.Rounded.Edit,"Edit card",onClick=onEdit);ToolIcon(if(card.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,"Favorite card",card.favorite,onFavorite);Spacer(Modifier.weight(1f));ToolIcon(Icons.Rounded.DeleteOutline,"Delete card",onClick=onDelete)}
            if(card.kind=="BANK") {
            CopyField("Bank name",card.bank)
            CopyField("Card type",card.network)
            Text("Card number",fontSize=12.sp,color=Ink.copy(alpha=.55f))
            Row(verticalAlignment=Alignment.CenterVertically) {Text(if(card.number.isEmpty()) "Not added" else if(reveal) card.number.chunked(4).joinToString(" ") else "•••• •••• •••• ${card.number.takeLast(4)}",modifier=Modifier.weight(1f));ToolIcon(if(reveal) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,"Show or hide number") {reveal=!reveal};CopyButton("Card number",card.number,true)}
            CopyField("Card holder name",card.holder)
            CopyField("Valid upto",card.expiry)
            if(flipped) CopyField("CVV",card.cvv,true,if(revealCvv) card.cvv else "•••")
            } else CopyField("Card title",card.title)
            if(card.notes.isNotBlank()) CopyField("Notes",card.notes,true)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ImageUpload(label: String,uri: Uri?,imageId: String,repository: VaultRepository,modifier: Modifier,onChoose: ()->Unit) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Surface(onClick=onChoose,shape=RoundedCornerShape(12.dp),border=BorderStroke(1.dp,Purple.copy(alpha=.3f)),modifier=Modifier.fillMaxWidth().aspectRatio(1.4f)) {
            when {
                uri!=null -> AsyncImage(uri,"$label image selected",Modifier.fillMaxSize())
                imageId.isNotEmpty() -> CardImage(imageId,repository,"$label image",Modifier.fillMaxSize())
                else -> Box(contentAlignment=Alignment.Center) {Icon(Icons.Rounded.AddPhotoAlternate,"Upload $label image",tint=Purple)}
            }
        }
        Text(label,style=MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ImportDialog(onClose: ()->Unit,onImport: (String)->Unit) {
    var category by remember {mutableStateOf("Personal")}
    AlertDialog(onDismissRequest=onClose,title={Text("Import document")},text={Column {CategoryOptions(category){category=it};PrivacyPolicyButton()}},
        confirmButton={TextButton(onClick={onImport(category)}) {Text("Import")}},dismissButton={TextButton(onClick=onClose) {Text("Cancel")}})
}

@Composable
fun CategoryOptions(selected: String,onSelect: (String)->Unit) {
    Column {listOf("Personal","Identity","Certificates","Other").forEach {cat -> Row(Modifier.fillMaxWidth().clickable {onSelect(cat)},verticalAlignment=Alignment.CenterVertically) {RadioButton(selected==cat,{onSelect(cat)});Text(cat)} }}
}

@Composable
fun DocumentEditor(doc: VaultDocument,onClose: ()->Unit,busy: Boolean,onSave: (VaultDocument)->Unit) {
    var title by remember {mutableStateOf(doc.title)}
    var category by remember {mutableStateOf(doc.category)}
    AlertDialog(onDismissRequest=onClose,title={Text("Edit document")},text={Column {OutlinedTextField(title,{title=it.take(100)},label={Text("Title")},singleLine=true);Spacer(Modifier.height(12.dp));CategoryOptions(category){category=it}}},
        confirmButton={TextButton(onClick={onSave(doc.copy(title=title.trim(),category=category))},enabled=title.isNotBlank() && !busy) {Text("Save")}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}

@Composable
fun DeleteDialog(title: String,onClose: ()->Unit,onDelete: ()->Unit) {
    AlertDialog(onDismissRequest=onClose,title={Text("Delete $title?")},text={Text("This removes it from your vault. Original files outside My Purse are unchanged.")},
        confirmButton={TextButton(onClick=onDelete){Text("Delete",color=MaterialTheme.colorScheme.error)}},dismissButton={TextButton(onClick=onClose){Text("Cancel")}})
}
