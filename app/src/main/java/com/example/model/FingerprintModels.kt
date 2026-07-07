package com.matepazy.spectre.model

enum class SignalCategory(
    val title: String,
    val description: String,
    val collectionType: String
) {
    PASSIVE(
        title = "Passive",
        description = "Readable instantly by any application without any runtime permission prompts.",
        collectionType = "Freely Collectible"
    ),
    NEEDS_PERMISSION(
        title = "Needs Permission",
        description = "Requires standard Android runtime permission gates. Restrictive but accessible.",
        collectionType = "User Consent Required"
    ),
    ADVANCED(
        title = "Advanced",
        description = "Clever side-channel queries, browser configurations, and installation properties.",
        collectionType = "Side-Channel & Query"
    )
}

data class DetailedItem(
    val label: String,
    val value: String,
    val description: String? = null,
    val iconName: String? = null
)

data class DetailedGroup(
    val categoryName: String?,
    val items: List<DetailedItem>
)

data class FingerprintSignal(
    val id: String,
    val name: String,
    val description: String,
    val category: SignalCategory,
    val rawValue: String,
    val narrative: String,
    val threatScore: Int, // 0 to 10
    val permissionName: String? = null,
    val detailedData: List<DetailedGroup>? = null
)

