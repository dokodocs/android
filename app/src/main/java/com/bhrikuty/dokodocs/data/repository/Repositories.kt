package com.bhrikuty.dokodocs.data.repository

import android.content.Context
import android.graphics.Bitmap
import com.bhrikuty.dokodocs.core.image.DocumentQuad
import com.bhrikuty.dokodocs.core.image.ImageProcessor
import com.bhrikuty.dokodocs.core.pdf.PageSizeFormat
import com.bhrikuty.dokodocs.core.pdf.PdfGenerator
import com.bhrikuty.dokodocs.core.pdf.PdfPageSource
import com.bhrikuty.dokodocs.data.local.DokoDocsDatabase
import com.bhrikuty.dokodocs.data.model.Document
import com.bhrikuty.dokodocs.data.model.Folder
import com.bhrikuty.dokodocs.data.model.Page
import com.bhrikuty.dokodocs.data.model.Signature
import com.bhrikuty.dokodocs.data.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentRepository(
    private val context: Context,
    private val database: DokoDocsDatabase
) {
    private val documentDao = database.documentDao()
    private val pageDao = database.pageDao()

    val allDocuments: Flow<List<Document>> = documentDao.getAllDocuments()
    val recentDocuments: Flow<List<Document>> = documentDao.getRecentDocuments(10)
    val favoriteDocuments: Flow<List<Document>> = documentDao.getFavoriteDocuments()

    fun getDocumentsInFolder(folderId: Long): Flow<List<Document>> =
        documentDao.getDocumentsInFolder(folderId)

    fun searchDocuments(query: String): Flow<List<Document>> =
        documentDao.searchDocuments(query)

    suspend fun getDocumentById(id: Long): Document? = documentDao.getDocumentById(id)

    fun getPagesForDocument(documentId: Long): Flow<List<Page>> =
        pageDao.getPagesForDocument(documentId)

    suspend fun getPagesForDocumentSync(documentId: Long): List<Page> =
        pageDao.getPagesForDocumentSync(documentId)

    /**
     * Saves a captured scan session as a new Document and generates the final PDF.
     */
    suspend fun saveScanSession(
        title: String,
        pageBitmaps: List<Bitmap>,
        quads: List<DocumentQuad?>,
        filters: List<String>,
        folderId: Long? = null,
        pageSizeFormat: PageSizeFormat = PageSizeFormat.A4
    ): Long = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val docDir = File(context.filesDir, "documents/doc_$timestamp").apply { mkdirs() }

        val pageEntities = mutableListOf<Page>()
        val pdfSources = mutableListOf<PdfPageSource>()

        for (i in pageBitmaps.indices) {
            val rawBitmap = pageBitmaps[i]
            val quad = quads.getOrNull(i)
            val filter = filters.getOrNull(i) ?: "original"

            // Perspective warp if quad is provided
            val warpedBitmap = if (quad != null) {
                ImageProcessor.warpPerspective(rawBitmap, quad)
            } else {
                rawBitmap
            }

            // Apply selected filter to get enhanced high-contrast scan
            val processedBitmap = ImageProcessor.applyFilter(warpedBitmap, filter)

            // Save raw original & processed image
            val origFile = File(docDir, "orig_page_$i.jpg")
            val procFile = File(docDir, "proc_page_$i.jpg")

            ImageProcessor.saveBitmapToFile(rawBitmap, origFile, 95)
            ImageProcessor.saveBitmapToFile(processedBitmap, procFile, 92)

            pdfSources.add(
                PdfPageSource(
                    imagePath = procFile.absolutePath,
                    filter = "original", // already filtered in procFile
                    rotation = 0f
                )
            )

            pageEntities.add(
                Page(
                    documentId = 0, // Assigned after document insertion
                    pageOrder = i,
                    originalImagePath = origFile.absolutePath,
                    localImagePath = procFile.absolutePath,
                    filter = filter,
                    width = warpedBitmap.width,
                    height = warpedBitmap.height,
                    rotation = 0
                )
            )
        }

        // Generate output PDF
        val pdfFile = File(docDir, "${title.replace("[^a-zA-Z0-9_.-]".toRegex(), "_")}.pdf")
        PdfGenerator.generatePdf(
            context = context,
            pages = pdfSources,
            outputFile = pdfFile,
            pageSizeFormat = pageSizeFormat
        )

        // Insert Document into Database
        val docId = documentDao.insert(
            Document(
                title = title.ifBlank { "Scan ${SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date())}" },
                createdAt = timestamp,
                updatedAt = timestamp,
                folderId = folderId,
                pageCount = pageEntities.size,
                localPdfPath = pdfFile.absolutePath,
                sizeBytes = pdfFile.length()
            )
        )

        // Insert Pages
        val finalPages = pageEntities.map { it.copy(documentId = docId) }
        pageDao.insertAll(finalPages)

        docId
    }

    suspend fun toggleFavorite(document: Document) = withContext(Dispatchers.IO) {
        documentDao.update(document.copy(isFavorite = !document.isFavorite, updatedAt = System.currentTimeMillis()))
    }

    suspend fun moveToFolder(docId: Long, folderId: Long?) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(docId) ?: return@withContext
        documentDao.update(doc.copy(folderId = folderId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateDocumentTitle(docId: Long, newTitle: String) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(docId) ?: return@withContext
        documentDao.update(doc.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateDocumentDate(docId: Long, timestamp: Long) = withContext(Dispatchers.IO) {
        val doc = documentDao.getDocumentById(docId) ?: return@withContext
        documentDao.update(doc.copy(createdAt = timestamp, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteDocument(document: Document) = withContext(Dispatchers.IO) {
        // Delete pages and DB entry
        pageDao.deletePagesForDocument(document.id)
        documentDao.delete(document)
        // Cleanup file storage
        try {
            File(document.localPdfPath).parentFile?.deleteRecursively()
        } catch (e: Exception) {
            // Ignore cleanup failure
        }
    }
}

class FolderRepository(private val database: DokoDocsDatabase) {
    private val folderDao = database.folderDao()

    val allFolders: Flow<List<Folder>> = folderDao.getAllFolders()

    suspend fun createFolder(name: String): Long = withContext(Dispatchers.IO) {
        folderDao.insert(Folder(name = name.trim()))
    }

    suspend fun deleteFolder(folder: Folder) = withContext(Dispatchers.IO) {
        if (!folder.isDefault) {
            folderDao.delete(folder)
        }
    }
}

class SignatureRepository(
    private val context: Context,
    private val database: DokoDocsDatabase
) {
    private val signatureDao = database.signatureDao()

    val allSignatures: Flow<List<Signature>> = signatureDao.getAllSignatures()

    suspend fun saveSignature(name: String, bitmap: Bitmap): Long = withContext(Dispatchers.IO) {
        val sigDir = File(context.filesDir, "signatures").apply { mkdirs() }
        val file = File(sigDir, "sig_${System.currentTimeMillis()}.png")
        ImageProcessor.savePngToFile(bitmap, file)

        signatureDao.insert(
            Signature(
                name = name.ifBlank { "Signature" },
                imagePath = file.absolutePath
            )
        )
    }

    suspend fun deleteSignature(signature: Signature) = withContext(Dispatchers.IO) {
        signatureDao.delete(signature)
        try {
            File(signature.imagePath).delete()
        } catch (e: Exception) {
            // Ignore
        }
    }
}

class SettingsRepository(private val database: DokoDocsDatabase) {
    private val userSettingsDao = database.userSettingsDao()

    val settings: Flow<UserSettings?> = userSettingsDao.getSettings()

    suspend fun updateTheme(theme: String) = withContext(Dispatchers.IO) {
        val current = userSettingsDao.getSettingsSync() ?: UserSettings()
        userSettingsDao.insertOrUpdate(current.copy(theme = theme))
    }

    suspend fun updateLanguage(lang: String) = withContext(Dispatchers.IO) {
        val current = userSettingsDao.getSettingsSync() ?: UserSettings()
        userSettingsDao.insertOrUpdate(current.copy(language = lang))
    }

    suspend fun updateCalendar(calendar: String) = withContext(Dispatchers.IO) {
        val current = userSettingsDao.getSettingsSync() ?: UserSettings()
        userSettingsDao.insertOrUpdate(current.copy(calendar = calendar))
    }

    suspend fun setOnboardingComplete() = withContext(Dispatchers.IO) {
        val current = userSettingsDao.getSettingsSync() ?: UserSettings()
        userSettingsDao.insertOrUpdate(current.copy(onboardingComplete = true))
    }
}
