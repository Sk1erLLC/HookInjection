/*
 *       Copyright (C) 2020-present Sk1er LLC <https://sk1er.club/>
 *
 *       This program is free software: you can redistribute it and/or modify
 *       it under the terms of the GNU Lesser General Public License as published
 *       by the Free Software Foundation, either version 3 of the License, or
 *       (at your option) any later version.
 *
 *       This program is distributed in the hope that it will be useful,
 *       but WITHOUT ANY WARRANTY; without even the implied warranty of
 *       MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *       GNU Lesser General Public License for more details.
 *
 *       You should have received a copy of the GNU Lesser General Public License
 *       along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package club.sk1er.hookinjection

import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.MethodNode

/**
 * ClassVisitor for extracting specific [MethodNode]s from classes
 * @author LlamaLad7
 */
internal class HookVisitor(private val hookName: String): ClassVisitor(Opcodes.ASM5) {
    var hookNode: MethodNode? = null
    override fun visitMethod(access: Int, name: String?,
                             desc: String?, signature: String?, exceptions: Array<String?>?): MethodVisitor? {

        if (name == hookName) {
            val methodNode = MethodNode(access, name, desc, signature, exceptions)
            hookNode = methodNode
            return hookNode
        }
        return null
    }
}