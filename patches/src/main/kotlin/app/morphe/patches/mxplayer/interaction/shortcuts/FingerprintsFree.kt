package app.morphe.patches.mxplayer.interaction.shortcuts

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.literal
import app.morphe.patcher.opcode
import app.morphe.patcher.string
import app.morphe.patches.mxplayer.shared.Constants.ACTIVITY_SCREEN_CLASS
import com.android.tools.smali.dexlib2.Opcode

/** Free 3.5.3 customize-items adapter [Lrbd$a;] (string-keyed rows). */
internal object FreeCustomizeItemsOnBindFingerprint : Fingerprint(
    definingClass = "Lrbd\$a;",
    name = "onBindViewHolder",
    returnType = "V",
    parameters = listOf(
        "Landroidx/recyclerview/widget/RecyclerView\$a0;",
        "I",
    ),
    filters = listOf(
        opcode(Opcode.SPARSE_SWITCH),
        string("shuffle"),
        string("screenshot", location = MatchAfterWithin(120)),
        fieldAccess(
            name = "j",
            type = "I",
            definingClass = "Lrbd\$a;",
            opcode = Opcode.IGET,
            location = MatchAfterWithin(250),
        ),
    ),
)

/** [Lqbd;] toggle listener for customize checkboxes. */
internal object FreeCustomizeItemsToggleFingerprint : Fingerprint(
    definingClass = "Lqbd;",
    name = "onCheckedChanged",
    returnType = "V",
    parameters = listOf("Landroid/widget/CompoundButton;", "Z"),
    filters = listOf(
        opcode(Opcode.SPARSE_SWITCH),
        string("shuffle"),
        string("shortcuts_flag.3", location = MatchAfterWithin(400)),
    ),
)

/** [ActivityScreen.Ya] toolbar rebuild; flags via [Lb6f;.f1]. */
internal object FreeActivityScreenBuildShortcutsFingerprint : Fingerprint(
    definingClass = ACTIVITY_SCREEN_CLASS,
    name = "Ya",
    returnType = "V",
    filters = listOf(
        fieldAccess(
            name = "b0",
            type = "Ljava/util/ArrayList;",
            definingClass = ACTIVITY_SCREEN_CLASS,
            opcode = Opcode.IGET_OBJECT,
        ),
        fieldAccess(
            name = "f1",
            type = "I",
            definingClass = "Lb6f;",
            opcode = Opcode.SGET,
            location = MatchAfterWithin(30),
        ),
        opcode(Opcode.NEW_INSTANCE, location = MatchAfterWithin(40)),
    ),
)

/** [Lrbd;] shortcuts customize panel ([Lrbd;.O]). */
internal object FreeMenuMoreShortcutsPanelFingerprint : Fingerprint(
    definingClass = "Lrbd;",
    name = "onViewCreated",
    returnType = "V",
    parameters = listOf("Landroid/view/View;", "Landroid/os/Bundle;"),
    filters = listOf(
        opcode(Opcode.INVOKE_VIRTUAL, location = MatchAfterWithin(200)),
        fieldAccess(
            name = "O",
            type = "Landroidx/recyclerview/widget/RecyclerView;",
            definingClass = "Lrbd;",
            opcode = Opcode.IGET_OBJECT,
            location = MatchAfterWithin(120),
        ),
    ),
)

internal object FreeShortcutAdapterOnClickFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/widget/b\$g\$a;",
    name = "onClick",
    returnType = "V",
    filters = listOf(
        string("Unknown shortcut click event."),
    ),
)

internal object FreeShortcutAdapterCreateViewHolderFingerprint : Fingerprint(
    definingClass = "Lcom/mxtech/videoplayer/widget/b;",
    name = "onCreateViewHolder",
    returnType = "Landroidx/recyclerview/widget/RecyclerView\$a0;",
    parameters = listOf("Landroid/view/ViewGroup;", "I"),
    filters = listOf(opcode(Opcode.PACKED_SWITCH)),
)