package app.morphe.patches.mxplayer.interaction.shortcuts

import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.immutable.ImmutableClassDef
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction10x
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x
import com.android.tools.smali.dexlib2.writer.io.FileDataStore
import com.android.tools.smali.dexlib2.writer.pool.DexPool
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createTempDirectory
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DexMethodRegisterBoundsVerifyTest {

    @Test
    fun `instructionRegisters collects move-result index`() {
        val insn = ImmutableInstruction11x(Opcode.MOVE_RESULT, 15)
        assertEquals(listOf(15), instructionRegisters(insn))
    }

    @Test
    fun `verifyDexMethodRegisterBounds rejects out of range register`() {
        val dex = writeTinyDex(moveResultRegister = 15, registerCount = 10)
        val apk = zipApk(dex)
        val error = assertThrows<IllegalArgumentException> {
            verifyDexMethodRegisterBounds(apk)
        }
        assertTrue(error.message!!.contains("v15"))
        assertTrue(error.message!!.contains(".registers 10"))
    }

    @Test
    fun `verifyDexMethodRegisterBounds accepts in-range register`() {
        val dex = writeTinyDex(moveResultRegister = 3, registerCount = 10)
        val apk = zipApk(dex)
        val report = verifyDexMethodRegisterBounds(apk)
        assertTrue(report.contains("PASSED"))
    }

    private fun writeTinyDex(moveResultRegister: Int, registerCount: Int): File {
        val moveResult = ImmutableInstruction11x(Opcode.MOVE_RESULT, moveResultRegister)
        val ret = ImmutableInstruction10x(Opcode.RETURN_VOID)
        val impl = ImmutableMethodImplementation(
            registerCount,
            listOf(moveResult, ret),
            null,
            null,
        )
        val method = ImmutableMethod(
            "Ltest/RegBounds;",
            "run",
            emptyList(),
            "V",
            AccessFlags.PUBLIC.value or AccessFlags.STATIC.value,
            null,
            null,
            impl,
        )
        val cls = ImmutableClassDef(
            "Ltest/RegBounds;",
            AccessFlags.PUBLIC.value,
            "Ljava/lang/Object;",
            null,
            null,
            emptyList(),
            emptyList(),
            listOf(method),
        )
        val pool = DexPool(Opcodes.getDefault())
        pool.internClass(cls)
        val out = createTempDirectory("reg-bounds-dex").resolve("classes.dex").toFile()
        pool.writeTo(FileDataStore(out))
        return out
    }

    private fun zipApk(dexFile: File): File {
        val apk = Files.createTempFile("reg-bounds", ".apk").toFile()
        ZipOutputStream(apk.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            dexFile.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
        return apk
    }
}