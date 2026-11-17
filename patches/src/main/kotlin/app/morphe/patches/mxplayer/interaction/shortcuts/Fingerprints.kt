package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patches.mxplayer.shared.Constants.ACTIVITY_SCREEN_CLASS
import com.android.tools.smali.dexlib2.Opcode

// MenuMoreFragment customize-items adapter (Lo20$a), Pro 1.93.4 — bind via packed-switch on index.
internal object CustomizeItemsOnBindFingerprint : Fingerprint(
    definingClass = "Lo20\$a;",
    name = "l",
    returnType = "V",
    parameters = listOf(
        "Landroidx/recyclerview/widget/RecyclerView\$z;",
        "I",
    ),
    filters = listOf(
        opcode(Opcode.PACKED_SWITCH),
        literal(0x1000, location = MatchAfterWithin(25)),
    ),
)

internal object CustomizeItemsToggleFingerprint : Fingerprint(
    definingClass = "Ln20;",
    returnType = "V",
    parameters = listOf("Landroid/widget/CompoundButton;", "Z"),
    filters = listOf(
        opcode(Opcode.PACKED_SWITCH),
        literal(0x1000),
        string("shortcuts_flag.3"),
    ),
)

// ActivityScreen.M5() — screenshot shortcut block uses LV90.e1 & 0x1000 and menu string 0x7f1206aa.
internal object ActivityScreenBuildShortcutsFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    name = "M5",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            name = "e1",
            type = "I",
            definingClass = "LV90;",
            opcode = Opcode.SGET,
        ),
        literal(0x1000),
        literal(0x7f1206aa),
        fieldAccess(
            name = "P0",
            type = "LPp0;",
            opcode = Opcode.IPUT_OBJECT,
            location = MatchAfterWithin(25),
        ),
        fieldAccess(
            name = "A0",
            type = "Ljava/util/ArrayList;",
            definingClass = ACTIVITY_SCREEN_CLASS,
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterWithin(8),
        ),
    ),
)

internal object ShortcutAdapterOnClickFingerprint : Fingerprint(
    definingClass = "LSp0\$g\$a;",
    name = "onClick",
    returnType = "V",
    filters = listOf(
        string("Unknown shortcut click event."),
    ),
)

internal object ShortcutAdapterCreateViewHolderFingerprint : Fingerprint(
    definingClass = "LSp0;",
    name = "n",
    returnType = "Landroidx/recyclerview/widget/RecyclerView\$z;",
    parameters = listOf("I", "Landroid/view/ViewGroup;"),
    filters = listOf(opcode(Opcode.PACKED_SWITCH)),
)