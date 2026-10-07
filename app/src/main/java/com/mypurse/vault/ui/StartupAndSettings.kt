package com.mypurse.vault.ui

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.media3.ui.AspectRatioFrameLayout
import com.mypurse.vault.data.readableSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.vaultSettings by preferencesDataStore("settings")
class VaultPreferences(context: Context) {
    private val data=context.applicationContext.vaultSettings
    private val lock=booleanPreferencesKey("app_lock")
    val lockEnabled=data.data.map {it[lock] ?: false}
    suspend fun setLock(value: Boolean) { data.edit {it[lock]=value} }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun IntroVideo(onDone: ()->Unit) {
    val context=LocalContext.current
    val owner=LocalLifecycleOwner.current
    val finish by rememberUpdatedState(onDone)
    val player=remember {
        ExoPlayer.Builder(context).build().apply {
            volume=0f
            setMediaItem(MediaItem.fromUri(Uri.parse("asset:///loading-animation.mp4")))
            prepare();playWhenReady=true
        }
    }
    DisposableEffect(player,owner) {
        val listener=object: Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {if(state==Player.STATE_ENDED) finish()}
            override fun onPlayerError(error: PlaybackException) {finish()}
        }
        player.addListener(listener)
        val observer=LifecycleEventObserver {_,event -> if(event==Lifecycle.Event.ON_STOP) player.pause() else if(event==Lifecycle.Event.ON_START) player.play()}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer);player.removeListener(listener);player.release()}
    }
    LaunchedEffect(Unit) {delay(20000);finish()}
    Box(Modifier.fillMaxSize().background(Pale)) {
        AndroidView(factory={PlayerView(it).apply {this.player=player;useController=false;resizeMode=AspectRatioFrameLayout.RESIZE_MODE_FIT;setShutterBackgroundColor(android.graphics.Color.rgb(248,249,255))}},modifier=Modifier.fillMaxSize())
        IconButton(onClick=onDone,modifier=Modifier.align(Alignment.BottomEnd).safeDrawingPadding().padding(18.dp)) {Icon(Icons.Rounded.SkipNext,"Skip intro",tint=Purple)}
    }
}

@Composable
fun LockOnBackground(enabled: Boolean,onLock: ()->Unit) {
    val owner=LocalLifecycleOwner.current
    val callback by rememberUpdatedState(onLock)
    DisposableEffect(owner,enabled) {
        val observer=LifecycleEventObserver {_,event -> if(enabled && event==Lifecycle.Event.ON_STOP) callback()}
        owner.lifecycle.addObserver(observer)
        onDispose {owner.lifecycle.removeObserver(observer)}
    }
}

private fun authenticators(): Int = if(Build.VERSION.SDK_INT >= 30)
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
else BiometricManager.Authenticators.BIOMETRIC_WEAK

fun authenticate(activity: FragmentActivity,onSuccess: ()->Unit,onError: (String)->Unit) {
    val prompt=BiometricPrompt(activity,ContextCompat.getMainExecutor(activity),object: BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {onSuccess()}
        override fun onAuthenticationError(code: Int,message: CharSequence) {onError(message.toString())}
    })
    val info=BiometricPrompt.PromptInfo.Builder().setTitle("Unlock My Purse")
        .setSubtitle("Your private vault")
        .setAllowedAuthenticators(authenticators())
    if(Build.VERSION.SDK_INT < 30) info.setNegativeButtonText("Cancel")
    prompt.authenticate(info.build())
}

@Composable
fun UnlockScreen(activity: FragmentActivity,onUnlocked: ()->Unit) {
    var error by remember {mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().background(Pale).safeDrawingPadding().padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Icon(Icons.Rounded.Lock,"Locked vault",tint=Purple,modifier=Modifier.size(60.dp))
        Text("My Purse",style=MaterialTheme.typography.headlineMedium,modifier=Modifier.padding(top=24.dp))
        Button(onClick={authenticate(activity,onUnlocked){error=it}},modifier=Modifier.padding(top=24.dp)) {Text("Unlock")}
        error?.let {Text(it,fontSize=13.sp,modifier=Modifier.padding(top=12.dp))}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(prefs: VaultPreferences,activity: FragmentActivity,bytes: Long,cards: Int,documents: Int,onAuthenticated: ()->Unit,onClose: ()->Unit) {
    val scope=rememberCoroutineScope()
    val enabled by prefs.lockEnabled.collectAsState(initial=false)
    var error by remember {mutableStateOf<String?>(null)}
    ModalBottomSheet(onDismissRequest=onClose,containerColor=Pale) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=26.dp).padding(bottom=32.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("Settings",style=MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Rounded.Fingerprint,null,tint=Purple)
                Column(Modifier.weight(1f).padding(start=14.dp)) {Text("App lock");Text("Fingerprint or device screen lock",fontSize=12.sp,color=Ink.copy(alpha=.6f))}
                Switch(enabled,{value ->
                    if(!value) authenticate(activity,{scope.launch {prefs.setLock(false)}}){error=it}
                    else {
                        val available=BiometricManager.from(activity).canAuthenticate(authenticators())
                        if(available!=BiometricManager.BIOMETRIC_SUCCESS) error="Set up a screen lock in Android settings first."
                        else authenticate(activity,{onAuthenticated();scope.launch {prefs.setLock(true)}}){error=it}
                    }
                })
            }
            HorizontalDivider()
            Text("Storage",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {Text("Documents");Text(readableSize(bytes),color=Purple)}
            Text("$cards cards · $documents documents",fontSize=13.sp,color=Ink.copy(alpha=.65f))
            Text("Files stay on this device. Uninstalling My Purse or clearing app data deletes the vault. Keep original copies of important documents.",fontSize=13.sp,color=Ink.copy(alpha=.65f))
            PrivacyPolicyButton()
            Text("My Purse ${com.mypurse.vault.BuildConfig.VERSION_NAME}",fontSize=12.sp,color=Ink.copy(alpha=.5f))
            error?.let {Text(it,color=MaterialTheme.colorScheme.error,fontSize=13.sp)}
        }
    }
}
