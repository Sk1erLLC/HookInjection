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

import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import java.io.IOException
import java.lang.Integer.max

/**
 * Hook injection utilities
 * If possible, you should use the Kotlin DSL: [HookInjectionBuilder]
 * @author LlamaLad7
 */
class HookInjectionUtils {
    companion object {
        @JvmStatic
        fun getMethodInstructions(
            methodNode: MethodNode,
            remapReturns: Boolean,
            localVariableOffset: Int,
            vararg paramIndexes: Int
        ): InsnList {
            val list = methodNode.instructions
            val static = methodNode.access and Opcodes.ACC_STATIC == Opcodes.ACC_STATIC
            val end = LabelNode()

            if (remapReturns) {
                list.add(end)
            }

            val offset = if (static) 0 else 1
            val it = list.iterator()

            loop@ while (it.hasNext()) {
                val node = it.next()

                if (node is VarInsnNode) {
                    for (i in offset until paramIndexes.size + offset) {
                        if (node.`var` == i) {
                            node.`var` = paramIndexes[i - offset]
                            continue@loop
                        }
                    }
                    node.`var` += localVariableOffset

                } else if (node is IincInsnNode) {
                    for (i in offset until paramIndexes.size + offset) {
                        if (node.`var` == i) {
                            node.`var` = paramIndexes[i - offset]
                            continue@loop
                        }
                    }
                    node.`var` += localVariableOffset

                } else if (remapReturns && node.opcode in Opcodes.IRETURN..Opcodes.RETURN) {
                    list.set(node, JumpInsnNode(Opcodes.GOTO, end))
                }
            }

            return list
        }

        @JvmStatic
        fun getMethodInstructions(
                owner: Class<*>,
                name: String,
                localVariableOffset: Int,
                vararg paramIndexes: Int
        ): InsnList {
            return getMethodInstructions(
                owner,
                name,
                true,
                localVariableOffset,
                *paramIndexes
            )
        }

        /**
         * Used to inline a hook
         * localVariableOffset should normally be generated using [getSuggestedStartingIndex]
         *
         * @param owner         The [Class] which contains the hook
         * @param name          The name of the hook
         * @param remapReturns  Whether return instructions should be replaced with instructions to GOTO the end of the hook (Can be omitted; default value is true)
         * @param localVariableOffset The greatest index which should not be used in the resulting [InsnList]
         * @param paramIndexes  Each paramIndex should be the index of a local variable which should be passed to the hook
         *                      If not all your parameters are local variables, use [getMethodInstructionsWithNewVars]
         * @return              An [InsnList] which can be inserted in place of the hook
         */
        @JvmStatic
        fun getMethodInstructions(
                owner: Class<*>,
                name: String,
                remapReturns: Boolean,
                localVariableOffset: Int,
                vararg paramIndexes: Int
        ): InsnList {
            return getMethodInstructions(
                getMethodNode(owner, name),
                remapReturns,
                localVariableOffset,
                *paramIndexes
            )
        }

        /**
         * @param owner The class which contains the method to be found
         * @param name  The name of the method to be found
         * @return      A MethodNode of the specified method (hopefully!)
         */
        @JvmStatic
        fun getMethodNode(owner: Class<*>, name: String): MethodNode {
            val hookVisitor = HookVisitor(name)
            val stream = owner.classLoader.getResourceAsStream(owner.name.replace('.', '/') + ".class")
            try {
                stream.use {
                    val classReader = ClassReader(it)
                    classReader.accept(hookVisitor, 0)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            if (hookVisitor.hookNode == null) error("The method ${owner.name}::${name} could not be found!")
            return hookVisitor.hookNode!!
        }

        /**
         * Used to inline a hook
         * localVariableOffset should normally be generated using [getSuggestedStartingIndex]
         *
         * @param owner         The [Class] which contains the hook
         * @param name          The name of the hook
         * @param remapReturns  Whether return instructions should be replaced with instructions to GOTO the end of the hook (Can be omitted; default value is true)
         * @param localVariableOffset The greatest index which should not be used in the resulting [InsnList]
         * @param params        Each "param" should be an [InsnList] containing the instructions to load a value onto the stack, and should correspond to a parameter of your hook
         * @return              An [InsnList] which can be inserted in place of the hook
         */
        @JvmStatic
        fun getMethodInstructionsWithNewVars(
                owner: Class<*>,
                name: String,
                remapReturns: Boolean,
                localVariableOffset: Int,
                vararg params: InsnList
        ): InsnList {
            val methodNode =
                getMethodNode(owner, name)
            val methodArgs = Type.getArgumentTypes(methodNode.desc)
            val localVariableIndexes = mutableListOf<Int>()
            val paramsStoring = InsnList()
            var index = localVariableOffset

            for ((i, paramList) in params.withIndex()) {
                index++
                localVariableIndexes.add(index)
                paramsStoring.add(paramList)
                paramsStoring.add(VarInsnNode(methodArgs[i].descriptor.storeOpcode, index))
            }

            val finalInstructionList =
                getMethodInstructions(
                    methodNode,
                    remapReturns,
                    index,
                    *localVariableIndexes.toIntArray()
                )
            finalInstructionList.insertBefore(finalInstructionList.first, paramsStoring)
            return finalInstructionList
        }

        @JvmStatic
        fun getMethodInstructionsWithNewVars(
                owner: Class<*>,
                name: String,
                localVariableOffset: Int,
                vararg params: InsnList
        ): InsnList =
            getMethodInstructionsWithNewVars(
                owner,
                name,
                true,
                localVariableOffset,
                *params
            )

        /**
         * @param methodNode        The [MethodNode] into which the hook will be injected
         * @param insertionPoint    The [AbstractInsnNode] before which the hook will be injected, can be null if all index uses should be considered
         * @return                  The greatest local variable index which is used before insertionPoint in methodNode's instructions
         */
        @JvmStatic
        fun getSuggestedStartingIndex(methodNode: MethodNode, insertionPoint: AbstractInsnNode?): Int {
            var maxIndex = 0
            for (node in methodNode.instructions) {
                if (node === insertionPoint) break
                if (node is VarInsnNode && node.`var` > maxIndex) {
                    maxIndex = when (node.opcode) {
                        Opcodes.LSTORE, Opcodes.LLOAD, Opcodes.DSTORE, Opcodes.DLOAD -> node.`var` + 1
                        else -> node.`var`
                    }
                } else if (node is IincInsnNode && node.`var` > maxIndex) {
                    maxIndex = node.`var`
                }
            }

            return max(
                    maxIndex,
                    Type.getArgumentTypes(methodNode.desc).size) - if (methodNode.access and Opcodes.ACC_STATIC == Opcodes.ACC_STATIC) 1 else 0

        }

        /**
         * Returns the opcode used to store a value, given its descriptor
         */
        val String.storeOpcode
            get() = when (this) {
                "Z", "B", "C", "S", "I" -> Opcodes.ISTORE
                "J" -> Opcodes.LSTORE
                "F" -> Opcodes.FSTORE
                "D" -> Opcodes.DSTORE
                else -> Opcodes.ASTORE
            }

        @JvmStatic
        fun makeMethodsPublic(classNode: ClassNode) {
            classNode.methods.forEach {
                if (it.access and Opcodes.ACC_PUBLIC == 0) {
                    it.access =
                        it.access and Opcodes.ACC_PRIVATE.inv() and Opcodes.ACC_PROTECTED.inv() or Opcodes.ACC_PUBLIC
                }
            }
        }
    }
}