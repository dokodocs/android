package com.bhrikuty.dokodocs.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bhrikuty.dokodocs.core.calendar.CalendarSystem
import com.bhrikuty.dokodocs.core.calendar.DateFormatter
import com.bhrikuty.dokodocs.core.classifier.ClassificationResult
import com.bhrikuty.dokodocs.core.classifier.NepaliDocumentClassifier
import com.bhrikuty.dokodocs.core.image.DocumentQuad
import com.bhrikuty.dokodocs.core.image.ImageProcessor
import com.bhrikuty.dokodocs.core.pdf.PageSizeFormat
import com.bhrikuty.dokodocs.core.quality.QualityAnalyzer
import com.bhrikuty.dokodocs.core.quality.QualityReport
import com.bhrikuty.dokodocs.core.redact.RedactionProcessor
import com.bhrikuty.dokodocs.core.redact.RedactionRect
import com.bhrikuty.dokodocs.data.local.DokoDocsDatabase
import com.bhrikuty.dokodocs.data.model.Document
import com.bhrikuty.dokodocs.data.model.Folder
import com.bhrikuty.dokodocs.data.model.Page
import com.bhrikuty.dokodocs.data.model.Signature
import com.bhrikuty.dokodocs.data.model.UserSettings
import com.bhrikuty.dokodocs.data.repository.DocumentRepository
import com.bhrikuty.dokodocs.data.repository.FolderRepository
import com.bhrikuty.dokodocs.data.repository.SettingsRepository
import com.bhrikuty.dokodocs.data.repository.SignatureRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DokoDocsDatabase.getInstance(application)
    val documentRepo = DocumentRepository(application, database)
    val folderRepo = FolderRepository(database)
    val settingsRepo = SettingsRepository(database)
    val signatureRepo = SignatureRepository(application, database)

    val settings: StateFlow<UserSettings> = settingsRepo.settings
        .combine(MutableStateFlow(Unit)) { s, _ -> s ?: UserSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedFolderId = MutableStateFlow<Long?>(null)
    val selectedFolderId = _selectedFolderId.asStateFlow()

    val allDocuments = documentRepo.allDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentDocuments = documentRepo.recentDocuments
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders = folderRepo.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSelectedFolder(folderId: Long?) {
        _selectedFolderId.value = folderId
    }

    fun toggleFavorite(document: Document) {
        viewModelScope.launch {
            documentRepo.toggleFavorite(document)
        }
    }

    fun deleteDocument(document: Document) {
        viewModelScope.launch {
            documentRepo.deleteDocument(document)
        }
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            folderRepo.createFolder(name)
        }
    }

    fun updateTitle(docId: Long, newTitle: String) {
        viewModelScope.launch {
            documentRepo.updateDocumentTitle(docId, newTitle)
        }
    }

    fun updateDate(docId: Long, date: java.util.Date) {
        viewModelScope.launch {
            documentRepo.updateDocumentDate(docId, date.time)
        }
    }

    fun getDateFormatter(): DateFormatter {
        val cal = if (settings.value.calendar.equals("bs", ignoreCase = true)) CalendarSystem.BS else CalendarSystem.AD
        return DateFormatter(cal)
    }
}

data class ScannedPageItem(
    val bitmap: Bitmap,
    var quad: DocumentQuad? = null,
    var filter: String = "original",
    var qualityReport: QualityReport? = null,
    var classification: ClassificationResult? = null,
    var redactions: List<RedactionRect> = emptyList(),
    var sideLabel: String? = null // "Front" or "Back"
)

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val database = DokoDocsDatabase.getInstance(application)
    private val documentRepo = DocumentRepository(application, database)

    private val _scannedPages = MutableStateFlow<List<ScannedPageItem>>(emptyList())
    val scannedPages: StateFlow<List<ScannedPageItem>> = _scannedPages.asStateFlow()

    private val _selectedDocumentType = MutableStateFlow("auto")
    val selectedDocumentType = _selectedDocumentType.asStateFlow()

    private val _isFlashEnabled = MutableStateFlow(false)
    val isFlashEnabled = _isFlashEnabled.asStateFlow()

    private val _isBatchMode = MutableStateFlow(false)
    val isBatchMode = _isBatchMode.asStateFlow()

    private val _isIdBothSidesMode = MutableStateFlow(false)
    val isIdBothSidesMode = _isIdBothSidesMode.asStateFlow()

    private val _idScanStep = MutableStateFlow(0) // 0 = Scan Front, 1 = Scan Back, 2 = Both Done
    val idScanStep = _idScanStep.asStateFlow()

    fun addCapturedPage(bitmap: Bitmap, quad: DocumentQuad? = null) {
        val defaultQuad = quad ?: DocumentQuad.defaultFromSize(bitmap.width.toFloat(), bitmap.height.toFloat())
        val side = if (_isIdBothSidesMode.value) {
            if (_scannedPages.value.isEmpty()) "Front Side" else "Back Side"
        } else null

        val newItem = ScannedPageItem(
            bitmap = bitmap,
            quad = defaultQuad,
            sideLabel = side
        )

        val updatedList = _scannedPages.value + newItem
        _scannedPages.value = updatedList

        if (_isIdBothSidesMode.value) {
            _idScanStep.value = updatedList.size.coerceAtMost(2)
        }

        val pageIdx = updatedList.size - 1
        // Run quality and classification analysis in background
        viewModelScope.launch(Dispatchers.Default) {
            val report = QualityAnalyzer.analyzeQuality(bitmap, defaultQuad)
            val classification = NepaliDocumentClassifier.classify(
                bitmap.width,
                bitmap.height,
                defaultQuad,
                _selectedDocumentType.value
            )

            withContext(Dispatchers.Main) {
                val current = _scannedPages.value.toMutableList()
                if (pageIdx in current.indices) {
                    current[pageIdx] = current[pageIdx].copy(
                        qualityReport = report,
                        classification = classification
                    )
                    _scannedPages.value = current
                }
            }
        }
    }

    fun updatePageQuad(index: Int, quad: DocumentQuad) {
        val list = _scannedPages.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(quad = quad)
            _scannedPages.value = list

            // Re-run quality check for new quad
            val page = list[index]
            viewModelScope.launch(Dispatchers.Default) {
                val report = QualityAnalyzer.analyzeQuality(page.bitmap, quad)
                withContext(Dispatchers.Main) {
                    val current = _scannedPages.value.toMutableList()
                    if (index in current.indices) {
                        current[index] = current[index].copy(qualityReport = report)
                        _scannedPages.value = current
                    }
                }
            }
        }
    }

    fun updatePageFilter(index: Int, filter: String) {
        val list = _scannedPages.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(filter = filter)
            _scannedPages.value = list
        }
    }

    fun addRedaction(index: Int, redaction: RedactionRect) {
        val list = _scannedPages.value.toMutableList()
        if (index in list.indices) {
            val updatedRedactions = list[index].redactions + redaction
            list[index] = list[index].copy(redactions = updatedRedactions)
            _scannedPages.value = list
        }
    }

    fun clearRedactions(index: Int) {
        val list = _scannedPages.value.toMutableList()
        if (index in list.indices) {
            list[index] = list[index].copy(redactions = emptyList())
            _scannedPages.value = list
        }
    }

    fun removePage(index: Int) {
        val list = _scannedPages.value.toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            _scannedPages.value = list
            if (_isIdBothSidesMode.value) {
                _idScanStep.value = list.size.coerceAtMost(2)
            }
        }
    }

    fun toggleFlash() {
        _isFlashEnabled.value = !_isFlashEnabled.value
    }

    fun toggleBatchMode() {
        _isBatchMode.value = !_isBatchMode.value
        if (_isBatchMode.value) {
            _isIdBothSidesMode.value = false
        }
    }

    fun toggleIdBothSidesMode() {
        _isIdBothSidesMode.value = !_isIdBothSidesMode.value
        if (_isIdBothSidesMode.value) {
            _isBatchMode.value = false
            _selectedDocumentType.value = "idCard"
            _idScanStep.value = _scannedPages.value.size.coerceAtMost(2)
        }
    }

    fun setDocumentType(type: String) {
        _selectedDocumentType.value = type
        if (type == "idCard") {
            _isIdBothSidesMode.value = true
        }
    }

    fun clearSession() {
        _scannedPages.value = emptyList()
        _idScanStep.value = 0
    }

    fun saveDocument(
        title: String,
        folderId: Long? = null,
        pageSizeFormat: PageSizeFormat = PageSizeFormat.A4,
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val pages = _scannedPages.value
            if (pages.isEmpty()) return@launch

            // Process redactions on bitmaps if any
            val bitmaps = pages.map { page ->
                if (page.redactions.isNotEmpty()) {
                    RedactionProcessor.applyRedactions(page.bitmap, page.redactions)
                } else {
                    page.bitmap
                }
            }
            val quads = pages.map { it.quad }
            val filters = pages.map { it.filter }

            val docId = documentRepo.saveScanSession(
                title = title,
                pageBitmaps = bitmaps,
                quads = quads,
                filters = filters,
                folderId = folderId,
                pageSizeFormat = pageSizeFormat
            )
            clearSession()
            onSuccess(docId)
        }
    }
}
