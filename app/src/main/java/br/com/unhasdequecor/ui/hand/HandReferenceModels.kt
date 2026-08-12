@file:Suppress("MatchingDeclarationName")

package br.com.unhasdequecor.ui.hand

internal data class HandReferenceActions(
    val onBack: () -> Unit,
    val onOpenSamplePicker: () -> Unit,
    val onOpenReplaceSheet: () -> Unit,
    val onOpenRemoveConfirm: () -> Unit,
    val onConfirmUserPhoto: () -> Unit,
    val onDiscardUserPhoto: () -> Unit,
)
