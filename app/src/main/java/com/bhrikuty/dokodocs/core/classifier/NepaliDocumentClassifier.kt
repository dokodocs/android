package com.bhrikuty.dokodocs.core.classifier

import com.bhrikuty.dokodocs.core.image.DocumentQuad
import kotlin.math.abs

enum class DocumentCategory(
    val id: String,
    val nameEn: String,
    val nameNe: String,
    val targetAspect: Float,
    val iconName: String
) {
    CITIZENSHIP("citizenship", "Nepali Citizenship", "नागरिकता प्रमाणपत्र", 1.58f, "badge"),
    NATIONAL_ID("national_id", "National ID (NID)", "राष्ट्रिय परिचयपत्र", 1.58f, "id_card"),
    PASSPORT("passport", "Passport (MRP / e-Passport)", "राहदानी", 1.42f, "book"),
    DRIVING_LICENSE("driving_license", "Driving License", "सवारी चालक अनुमतिपत्र", 1.58f, "directions_car"),
    LALPURJA("lalpurja", "Land Ownership (Lalpurja)", "जग्गाधनी प्रमाणपुर्जा", 1.41f, "landscape"),
    ACADEMIC_CERTIFICATE("academic_cert", "Academic Certificate", "शैक्षिक प्रमाणपत्र", 0.707f, "school"),
    BANK_STATEMENT("bank_statement", "Bank Statement / Cheque", "बैंक स्टेटमेन्ट / चेक", 0.707f, "account_balance"),
    UTILITY_BILL("utility_bill", "Utility Bill / Receipt", "विद्युत/खानेपानी महसुल", 0.45f, "receipt"),
    GOVERNMENT_FORM("govt_form", "Government Application Form", "सरकारी निवेदन / फाराम", 0.707f, "description"),
    GENERAL_DOCUMENT("general_doc", "General Document", "साधारण कागजात", 0.707f, "article");

    companion object {
        fun fromId(id: String): DocumentCategory =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: GENERAL_DOCUMENT
    }
}

data class ClassificationResult(
    val category: DocumentCategory,
    val confidence: Float,
    val suggestedFolder: String,
    val isDoubleSidedExpected: Boolean
)

object NepaliDocumentClassifier {

    fun classify(
        width: Int,
        height: Int,
        quad: DocumentQuad? = null,
        selectedDocType: String? = null
    ): ClassificationResult {
        // If user explicitly chose a type
        if (!selectedDocType.isNullOrBlank() && selectedDocType != "auto") {
            val matched = when (selectedDocType) {
                "idCard" -> DocumentCategory.CITIZENSHIP
                "receipt" -> DocumentCategory.UTILITY_BILL
                "book" -> DocumentCategory.PASSPORT
                "a4" -> DocumentCategory.GENERAL_DOCUMENT
                else -> DocumentCategory.GENERAL_DOCUMENT
            }
            return ClassificationResult(
                category = matched,
                confidence = 0.95f,
                suggestedFolder = getFolderForCategory(matched),
                isDoubleSidedExpected = matched == DocumentCategory.CITIZENSHIP || matched == DocumentCategory.NATIONAL_ID || matched == DocumentCategory.DRIVING_LICENSE
            )
        }

        // Measure aspect ratio (width / height)
        val aspect = if (quad != null) {
            val w = (quad.topLeft.distanceTo(quad.topRight) + quad.bottomLeft.distanceTo(quad.bottomRight)) / 2f
            val h = (quad.topLeft.distanceTo(quad.bottomLeft) + quad.topRight.distanceTo(quad.bottomRight)) / 2f
            if (h > 0) w / h else width.toFloat() / height.toFloat()
        } else {
            width.toFloat() / height.toFloat()
        }

        // Aspect matches:
        // Landscape Card (1.58:1 +/- 0.15) -> Citizenship / NID / Driving License
        if (abs(aspect - 1.58f) <= 0.18f) {
            return ClassificationResult(
                category = DocumentCategory.CITIZENSHIP,
                confidence = 0.88f,
                suggestedFolder = "Identity Documents (परिचयपत्र)",
                isDoubleSidedExpected = true
            )
        }

        // Landscape Booklet / Passport photo spread (1.42:1 +/- 0.15)
        if (abs(aspect - 1.42f) <= 0.14f) {
            return ClassificationResult(
                category = DocumentCategory.PASSPORT,
                confidence = 0.82f,
                suggestedFolder = "Passports & Travel (राहदानी)",
                isDoubleSidedExpected = false
            )
        }

        // Portrait Standard Sheet (A4 / Letter 0.707:1 +/- 0.10)
        if (abs(aspect - 0.707f) <= 0.12f) {
            return ClassificationResult(
                category = DocumentCategory.GENERAL_DOCUMENT,
                confidence = 0.90f,
                suggestedFolder = "Documents (कागजातहरू)",
                isDoubleSidedExpected = false
            )
        }

        // Tall Narrow Receipt (aspect < 0.52)
        if (aspect < 0.52f) {
            return ClassificationResult(
                category = DocumentCategory.UTILITY_BILL,
                confidence = 0.85f,
                suggestedFolder = "Bills & Receipts (बिलहरू)",
                isDoubleSidedExpected = false
            )
        }

        return ClassificationResult(
            category = DocumentCategory.GENERAL_DOCUMENT,
            confidence = 0.70f,
            suggestedFolder = "Scans (स्क्यान)",
            isDoubleSidedExpected = false
        )
    }

    private fun getFolderForCategory(category: DocumentCategory): String = when (category) {
        DocumentCategory.CITIZENSHIP,
        DocumentCategory.NATIONAL_ID,
        DocumentCategory.DRIVING_LICENSE -> "Identity Documents (परिचयपत्र)"
        DocumentCategory.PASSPORT -> "Passports & Travel (राहदानी)"
        DocumentCategory.LALPURJA -> "Land & Property (जग्गा प्रमाण)"
        DocumentCategory.ACADEMIC_CERTIFICATE -> "Education & Certificates (शैक्षिक प्रमाणपत्र)"
        DocumentCategory.BANK_STATEMENT -> "Finance & Banking (बैंकिङ)"
        DocumentCategory.UTILITY_BILL -> "Bills & Receipts (बिलहरू)"
        DocumentCategory.GOVERNMENT_FORM -> "Government Forms (सरकारी फाराम)"
        DocumentCategory.GENERAL_DOCUMENT -> "General Scans (कागजातहरू)"
    }
}
