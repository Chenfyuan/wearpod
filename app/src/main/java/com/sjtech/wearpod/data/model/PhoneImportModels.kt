package com.sjtech.wearpod.data.model

data class PhoneImportSession(
    val sessionId: String,
    val shortCode: String,
    val mobileUrl: String,
    val expiresAtEpochMillis: Long,
)

enum class PhoneImportSessionStatus {
    PENDING,
    SUBMITTED,
    EXPIRED,
}

data class PhoneImportSessionSnapshot(
    val sessionId: String,
    val shortCode: String,
    val mobileUrl: String,
    val expiresAtEpochMillis: Long,
    val status: PhoneImportSessionStatus,
    val feedUrls: List<String> = emptyList(),
    val invalidCount: Int = 0,
    val duplicateCountWithinPayload: Int = 0,
)

data class PhoneImportPreview(
    val newFeedUrls: List<String>,
    val newCount: Int,
    val duplicateCount: Int,
    val invalidCount: Int,
)

data class PhoneImportResult(
    val importedSubscriptions: List<Subscription>,
    val duplicateCount: Int,
    val failedUrls: List<String>,
)
