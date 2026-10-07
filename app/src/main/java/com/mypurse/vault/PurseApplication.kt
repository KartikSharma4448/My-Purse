package com.mypurse.vault
import android.app.Application
import com.mypurse.vault.data.VaultRepository

class PurseApplication : Application() {
    val repository by lazy { VaultRepository(this) }
}
