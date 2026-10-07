package com.mypurse.vault

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.mypurse.vault.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CardMigrationTest {
    @Test fun versionOneCardSurvivesMigration() = runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val name="migration-${UUID.randomUUID()}.db"
        val schema=instrumentation.context.assets.open("com.mypurse.vault.data.VaultDatabase/1.json").bufferedReader().use {JSONObject(it.readText())}.getJSONObject("database")
        val db=context.openOrCreateDatabase(name,0,null)
        try {
            val tables=schema.getJSONArray("entities")
            for(index in 0 until tables.length()) {
                val table=tables.getJSONObject(index)
                db.execSQL(table.getString("createSql").replace("\${TABLE_NAME}",table.getString("tableName")))
                val indices=table.optJSONArray("indices") ?: org.json.JSONArray()
                for(i in 0 until indices.length()) db.execSQL(indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}",table.getString("tableName")))
            }
            db.execSQL("INSERT INTO cards (id,title,bank,holder,number,expiry,notes,color,favorite,createdAt) VALUES ('legacy','Saved card','Bank','Holder','4242424242424242','12/30','Keep me',1,1,1)")
            db.version=1
        } finally {db.close()}
        val upgraded=Room.databaseBuilder(context,VaultDatabase::class.java,name).addMigrations(VaultDatabase.MIGRATION_1_2).build()
        try {
            val card=upgraded.dao().cards().first().single()
            assertEquals("Saved card",card.title)
            assertEquals("Keep me",card.notes)
            assertTrue(card.favorite)
            assertEquals("BANK",card.kind)
            assertEquals("Visa",card.network)
            assertEquals("",card.cvv)
            assertEquals("",card.frontImageId)
        } finally {upgraded.close();context.deleteDatabase(name)}
    }
}
