package info.proteo.curtain.domain.model

data class SelectionGroup(
    val id: String,
    val curtainLinkId: String,
    val name: String,
    val color: String,
    val proteins: List<String>,
    val isActive: Boolean,
    val createdAt: Long,
    val modifiedAt: Long
)

data class ProteinSelection(
    val proteinId: String,
    val groupIds: List<String>
)
