package com.bhrikuty.dokodocs.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    object Folders : Screen("folders")
    object Tools : Screen("tools")
    object Settings : Screen("settings")
    object CameraScan : Screen("camera_scan")
    object CropEditor : Screen("crop_editor/{pageIndex}") {
        fun createRoute(pageIndex: Int) = "crop_editor/$pageIndex"
    }
    object ScanReview : Screen("scan_review")
    object DocumentDetail : Screen("document_detail/{documentId}") {
        fun createRoute(documentId: Long) = "document_detail/$documentId"
    }
    object SignatureDraw : Screen("signature_draw")
    object SignaturesList : Screen("signatures_list")
    object SignaturePlacement : Screen("signature_placement/{documentId}/{pageIndex}") {
        fun createRoute(documentId: Long, pageIndex: Int = 0) = "signature_placement/$documentId/$pageIndex"
    }
    object QrScanner : Screen("qr_scanner")
    object MergePdf : Screen("merge_pdf")
}
