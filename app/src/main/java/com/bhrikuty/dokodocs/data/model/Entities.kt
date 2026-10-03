package com.bhrikuty.dokodocs.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "folders"
)
data class Folder(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val parentId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val isSmartFolder: Boolean = false,
    val isFavorite: Boolean = false,
    val isDefault: Boolean = false
)

@Entity(
    tableName = "documents",
    foreignKeys = [
        ForeignKey(
            entity = Folder::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("folderId")]
)
data class Document(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val folderId: Long? = null,
    val pageCount: Int = 0,
    val localPdfPath: String,
    val fileType: String = "pdf",
    val sizeBytes: Long = 0,
    val isFavorite: Boolean = false,
    val isArchived: Boolean = false,
    val isTrashed: Boolean = false,
    val trashedAt: Long? = null,
    val colorLabel: String? = null,
    val passwordProtected: Boolean = false
)

@Entity(
    tableName = "pages",
    foreignKeys = [
        ForeignKey(
            entity = Document::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class Page(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val documentId: Long,
    val pageOrder: Int,
    val originalImagePath: String,
    val localImagePath: String,
    val filter: String = "original",
    val cropCoordinates: String? = null, // JSON / CSV of 4 corners
    val width: Int? = null,
    val height: Int? = null,
    val rotation: Int = 0,
    val documentType: String = "auto"
)

@Entity(
    tableName = "signatures"
)
data class Signature(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val imagePath: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "user_settings"
)
data class UserSettings(
    @PrimaryKey
    val id: Int = 0,
    val storageMode: String = "local",
    val defaultFileNaming: String = "scan_{date}_{n}",
    val defaultQuality: String = "medium",
    val defaultColorMode: String = "original",
    val theme: String = "system",       // "system", "light", "dark"
    val language: String = "en",        // "en", "ne", "hi"
    val calendar: String = "ad",        // "ad" (Gregorian) or "bs" (Bikram Sambat)
    val watermarkOnBatch: Boolean = true,
    val watermarkOnSingle: Boolean = false,
    val watermarkPosition: String = "bottom_right",
    val appLockEnabled: Boolean = false,
    val biometricEnabled: Boolean = false,
    val onboardingComplete: Boolean = false
)
