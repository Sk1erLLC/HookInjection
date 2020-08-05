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

import club.sk1er.hookinjection.HookInjectionUtils.Companion.storeOpcode
import codes.som.anthony.koffee.BlockAssembly
import org.objectweb.asm.Opcodes
import org.objectweb.asm.Type
import org.objectweb.asm.tree.*
import kotlin.jvm.internal.FunctionReference
import kotlin.reflect.KClass
import kotlin.reflect.KFunction

/**
 * Hook Injection DSL
 * See README for usage
 * @author LlamaLad7
 */
class HookInjectionBuilder {
    private var target: MethodNode? = null
    private var params = mutableListOf<Any>()
    private var injectBefore: AbstractInsnNode? = null
    private var injectAfter: AbstractInsnNode? = null
    private var methodNode: MethodNode? = null
    private var shouldRemapReturns = true

    var instructions = InsnList()
    var tryCatchBlocks = mutableListOf<TryCatchBlockNode>()

    fun finalize() {
        val target = target ?: error("You appear not to have specified a target MethodNode! Please do so with either the `target` or `into` function")
        val methodNode = methodNode ?: error("You appear not to have specified a hook to inject! Please do so with the `of` function")

        val methodArgs = Type.getArgumentTypes(methodNode.desc)
        val localVariableIndexes = mutableListOf<Int>()
        val paramSetupInstructions = InsnList()
        var index = HookInjectionUtils.getSuggestedStartingIndex(
            target,
            injectBefore ?: injectAfter?.next
        )

        val hookIsStatic = methodNode.access and Opcodes.ACC_STATIC != 0
        val hookParamOffset = if (hookIsStatic) 0 else 1
        val hookParamRange = hookParamOffset until methodArgs.size + hookParamOffset

        for (instruction in methodNode.instructions) {
            if (instruction is VarInsnNode && instruction.opcode in Opcodes.ISTORE..Opcodes.ASTORE && instruction.`var` in hookParamRange) {
                for (paramIndex in params.indices) {
                    val newIndex = paramIndex + hookParamOffset
                    if (params[paramIndex] is Int && newIndex == instruction.`var`) {
                        params[paramIndex] = VarInsnNode(instruction.opcode - 33, instruction.`var`)
                    }
                }
            }
        }

        for ((i, param) in params.withIndex()) {
            when (param) {
                is Int -> localVariableIndexes.add(param)

                is AbstractInsnNode -> {
                    index++
                    localVariableIndexes.add(index)
                    paramSetupInstructions.add(param)
                    paramSetupInstructions.add(VarInsnNode(methodArgs[i].descriptor.storeOpcode, index))
                }

                is InsnList -> {
                    index++
                    localVariableIndexes.add(index)
                    paramSetupInstructions.add(param)
                    paramSetupInstructions.add(VarInsnNode(methodArgs[i].descriptor.storeOpcode, index))
                }

                else -> error("Mate this really shouldn't have happened. How have you done this?! If you used reflection I will be very cross >:(")
            }
        }

        val finalInstructionList =
            HookInjectionUtils.getMethodInstructions(
                methodNode,
                shouldRemapReturns,
                index - methodArgs.size + if (methodNode.access and Opcodes.ACC_STATIC == Opcodes.ACC_STATIC) 1 else 0,
                *localVariableIndexes.toIntArray()
            )
        finalInstructionList.insertBefore(finalInstructionList.first, paramSetupInstructions)
        instructions.add(finalInstructionList)
        tryCatchBlocks.addAll(methodNode.tryCatchBlocks)
    }

    fun inject() {
        when {
            injectBefore != null -> target?.instructions?.insertBefore(injectBefore, instructions)
            injectAfter != null -> target?.instructions?.insert(injectAfter, instructions)
            else -> target?.instructions?.add(instructions)
        }
    }

    fun injectTryCatchNodes() {
        if (tryCatchBlocks.isNotEmpty()) {
            target?.tryCatchBlocks?.addAll(tryCatchBlocks)
        }
    }

    fun of(hook: KFunction<*>) {
        val owner = ((hook as FunctionReference).owner as KClass<*>).java
        methodNode = HookInjectionUtils.getMethodNode(owner, hook.name)
    }

    fun from(hook: KFunction<*>) = of(hook)

    fun into(methodNode: MethodNode) {
        target = methodNode
    }

    fun target(methodNode: MethodNode) = into(methodNode)

    fun before(abstractInsnNode: AbstractInsnNode) {
        injectBefore = abstractInsnNode
    }

    fun after(abstractInsnNode: AbstractInsnNode) {
        injectAfter = abstractInsnNode
    }

    fun param(index: Int) {
        params.add(index)
    }

    fun params(vararg indexes: Int) {
        params.addAll(indexes.toTypedArray())
    }

    fun param(abstractInsnNode: AbstractInsnNode) {
        params.add(abstractInsnNode)
    }

    fun param(insnList: InsnList) {
        params.add(insnList)
    }

    fun param(routine: BlockAssembly.() -> Unit) {
        val blockAssembly = BlockAssembly(InsnList(), mutableListOf())
        routine(blockAssembly)
        assert(blockAssembly.tryCatchBlocks.size == 0)
        params.add(blockAssembly.instructions)
    }

    val keepReturns: Unit
        get() {
            shouldRemapReturns = false
        }
}

fun injectInstructions(routine: HookInjectionBuilder.() -> Unit) {
    val hookBuilder = HookInjectionBuilder()
    routine(hookBuilder)
    hookBuilder.finalize()
    hookBuilder.inject()
}

fun injectInstructionsWithTryCatchNodes(routine: HookInjectionBuilder.() -> Unit) {
    val hookBuilder = HookInjectionBuilder()
    routine(hookBuilder)
    hookBuilder.finalize()
    hookBuilder.inject()
    hookBuilder.injectTryCatchNodes()
}

fun getInstructions(routine: HookInjectionBuilder.() -> Unit): InsnList {
    val hookBuilder = HookInjectionBuilder()
    routine(hookBuilder)
    hookBuilder.finalize()
    return hookBuilder.instructions
}

fun getInstructionsWithTryCatchNodes(routine: HookInjectionBuilder.() -> Unit): Pair<InsnList, List<TryCatchBlockNode>> {
    val hookBuilder = HookInjectionBuilder()
    routine(hookBuilder)
    hookBuilder.finalize()
    return Pair(hookBuilder.instructions, hookBuilder.tryCatchBlocks)
}