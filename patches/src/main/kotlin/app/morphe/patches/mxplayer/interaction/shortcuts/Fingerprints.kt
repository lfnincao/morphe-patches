package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import com.android.tools.smali.dexlib2.Opcode

// In-player customize items (MenuMoreFragment / igc$a). Dex types verified on MX 3.4.0 AD.
internal object IgCustomizeItemsAdapterInitFingerprint : Fingerprint(
    definingClass = "Ligc\$a;",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        string("list_shortcuts_eng"),
        string("screenshot"),
    ),
)

internal object IgCustomizeItemsOnBindFingerprint : Fingerprint(
    definingClass = "Ligc\$a;",
    name = "onBindViewHolder",
    returnType = "V",
    parameters = listOf(
        "Landroidx/recyclerview/widget/RecyclerView\$a0;",
        "I",
    ),
    filters = listOf(
        string("vertical_flip"),
        string("customise_items"),
    ),
)

internal object IgCustomizeItemsToggleFingerprint : Fingerprint(
    definingClass = "Lhgc;",
    returnType = "V",
    parameters = listOf("Landroid/widget/CompoundButton;", "Z"),
    filters = listOf(
        string("shuffle"),
        string("shortcuts_flag.3"),
    ),
)

// Settings > Customize items (TunerControl$a$c) and listener (preference/j).
internal object TunerCustomizeItemsAdapterInitFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/preference/TunerControl\$a\$c;",
    returnType = "V",
    parameters = listOf("Landroid/content/Context;"),
    filters = listOf(
        string("list_shortcuts_eng"),
        string("screenshot"),
    ),
)

internal object TunerCustomizeItemsOnBindFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/preference/TunerControl\$a\$c;",
    name = "onBindViewHolder",
    returnType = "V",
    parameters = listOf(
        "Landroidx/recyclerview/widget/RecyclerView\$a0;",
        "I",
    ),
    filters = listOf(
        string("vertical_flip"),
        string("customise_items"),
    ),
)

internal object TunerCustomizeItemsToggleFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/preference/j;",
    returnType = "V",
    parameters = listOf("Landroid/widget/CompoundButton;", "Z"),
    filters = listOf(
        string("shuffle"),
    ),
)

// ActivityScreen.bb() mirrors audio-effect bit 0x8000 on h6e.f1 (BlazeFTL/FTL-Patches shortcuts_flag style).
internal object ActivityScreenBuildShortcutsFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/ActivityScreen;",
    name = "bb",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            name = "f1",
            type = "I",
            definingClass = "Lh6e;",
            opcode = Opcode.SGET,
        ),
        opcode(Opcode.CONST, location = MatchAfterImmediately()),
        opcode(Opcode.AND_INT_2ADDR, location = MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, location = MatchAfterImmediately()),
        fieldAccess(
            name = "ic_audio_effect",
            definingClass = "Lc1g;",
            opcode = Opcode.SGET,
            location = MatchAfterWithin(40),
        ),
        fieldAccess(
            name = "t0",
            type = "Lwuh;",
            opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterWithin(20),
        ),
    ),
)

internal object ShortcutAdapterOnClickFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/widget/b\$g\$a;",
    name = "onClick",
    returnType = "V",
    filters = listOf(
        string("Unknown shortcut click event."),
    ),
)

internal object ShortcutAdapterOnCreateViewHolderFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/widget/b;",
    name = "onCreateViewHolder",
    returnType = "Landroidx/recyclerview/widget/RecyclerView\$a0;",
    filters = listOf(
        opcode(Opcode.PACKED_SWITCH),
        fieldAccess(
            name = "screen_shortcut_image",
            definingClass = "Lp4g;",
            opcode = Opcode.SGET,
            location = MatchAfterWithin(15),
        ),
    ),
)