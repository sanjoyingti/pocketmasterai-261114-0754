package com.aibuilt.pocketmasterai.data.agent

object AgentToolDefinitions {

    data class ToolDef(
        val name: String,
        val description: String
    )

    // A single unified tool schema usable by Gemini function calling OR OpenAI tools.
    val TOOLS = listOf(
        ToolDef(
            name = "get_device_state",
            description = "Get the current live state of the Sonicake Pocket Master: signal chain order, bypass state, models in each block, preset name, and global levels."
        ),
        ToolDef(
            name = "list_presets",
            description = "List all user presets (P01-P50) and their saved names on the device."
        ),
        ToolDef(
            name = "select_preset",
            description = "Load a preset by slot name (P01-P50, F01-F50) into the current edit buffer. This overwrites the working tone until the user saves."
        ),
        ToolDef(
            name = "set_block_state",
            description = "Enable or bypass a single block in the signal chain. Block numbers: 0=NR (noise gate), 1=FX1, 2=DRV (drive), 3=AMP (amp), 4=IR (cab/IR), 5=EQ, 6=FX2, 7=DLY, 8=RVB. bypass=true disables the block."
        ),
        ToolDef(
            name = "set_block_model",
            description = "Change the model inside a block. block=0..8, modelId=numeric model index from the device map."
        ),
        ToolDef(
            name = "set_parameter",
            description = "Set a numeric parameter for a block. block=0..8, paramId=numeric parameter ID, value=0..127."
        ),
        ToolDef(
            name = "set_chain_reorder",
            description = "Reorder the full 9-slot signal chain by block number. Only one block per position."
        ),
        ToolDef(
            name = "set_global_levels",
            description = "Set USB output, main output, and headphone volume (0..100 each)."
        ),
        ToolDef(
            name = "save_preset",
            description = "Save the current edit buffer to a user preset slot (P01-P50) with an optional 1-10 character name."
        ),
        ToolDef(
            name = "web_search",
            description = "Search the web for guitar rig details, amp settings, pedals, cab/mic, and pedalboard layouts for a specific song, solo, or guitarist. Returns a list of (title, snippet, url)."
        ),
        ToolDef(
            name = "fetch_page",
            description = "Fetch the text content of a specific webpage (e.g. a tone tutorial or rig report) to extract exact amp/pedal/cab/mic and EQ settings."
        ),
        ToolDef(
            name = "tone_snapshot",
            description = "Capture the current working tone as a snapshot that can be restored later (undo/revert). Returns a short snapshot id."
        )
    )
}
