package com.aibuilt.pocketmasterai.data.model

import com.aibuilt.pocketmasterai.data.PocketMasterDevice

data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: ChatMessageType = ChatMessageType.Text
)

enum class ChatMessageType { Text, ToolStatus, TonePreview }

data class ToneSnapshotCard(
    val title: String,
    val chain: IntArray,
    val bypass: ByteArray,
    val models: ShortArray,
    val onApply: () -> Unit,
    val onSave: () -> Unit,
    val onSaveSlot: (Int) -> Unit
) {
    companion object {
        fun fromDeviceState(device: PocketMasterDevice, title: String): ToneSnapshotCard {
            return ToneSnapshotCard(
                title = title,
                chain = device.chain.value,
                bypass = device.bypass.value,
                models = device.models.value,
                onApply = {},
                onSave = {},
                onSaveSlot = {}
            )
        }
    }
}

data class ToolStatusPill(
    val text: String,
    val isDone: Boolean = false
)
