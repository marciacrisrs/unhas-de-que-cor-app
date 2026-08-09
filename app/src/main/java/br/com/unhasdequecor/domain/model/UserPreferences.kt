package br.com.unhasdequecor.domain.model

data class UserPreferences(
    val preferredStyles: Set<NailStyle> = emptySet(),
    val displayName: String = "",
)
