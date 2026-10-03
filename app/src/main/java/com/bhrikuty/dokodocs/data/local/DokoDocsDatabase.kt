package com.bhrikuty.dokodocs.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.bhrikuty.dokodocs.data.model.Document
import com.bhrikuty.dokodocs.data.model.Folder
import com.bhrikuty.dokodocs.data.model.Page
import com.bhrikuty.dokodocs.data.model.Signature
import com.bhrikuty.dokodocs.data.model.UserSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        Document::class,
        Page::class,
        Folder::class,
        Signature::class,
        UserSettings::class
    ],
    version = 1,
    exportSchema = false
)
abstract class DokoDocsDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun pageDao(): PageDao
    abstract fun folderDao(): FolderDao
    abstract fun signatureDao(): SignatureDao
    abstract fun userSettingsDao(): UserSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: DokoDocsDatabase? = null

        fun getInstance(context: Context): DokoDocsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DokoDocsDatabase::class.java,
                    "dokodocs.db"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed default "My Documents" folder and default settings
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = getInstance(context)
                            database.folderDao().insert(
                                Folder(name = "My Documents", isDefault = true, isFavorite = true)
                            )
                            database.userSettingsDao().insertOrUpdate(
                                UserSettings(
                                    id = 0,
                                    theme = "system",
                                    language = "en",
                                    calendar = "ad",
                                    onboardingComplete = false
                                )
                            )
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
