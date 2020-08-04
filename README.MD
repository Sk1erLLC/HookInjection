# Hook Injection

Lightweight utility for injecting the instructions of hooks into `MethodNode`s

### Getting your hook's instructions:

```kotlin
getInstructions {

    // Reference your hook method. This can be static but it doesn't matter
    of(MyHookClass::hookMethod)
    
    // Specify the [MethodNode] into which you are injecting
    target(methodNode)
    
    // The [AbstractInsnNode] before which you will inject your code; you can also use [after] instead
    // If neither is used, the hook's instructions will be appended to the target method's instructions
    before(methodNode.instructions.first)

    // If your hook takes parameters, you must then specify what should be used for each one
    // There are a number of different options which can be used interchangeably:

    param(3) // The local variable with index 3 will be passed as the first parameter to the hook

    params(2, 4) // The local variables with the indexes 2 and 4 will be passed as the second and third parameters of the hook respectively
    
    param {
        // A set of instructions to load a value onto the stack, which will be treated as the fourth parameter of the hook
        // Koffee:
        iload_1
        iconst_2
        imul
    }

    param(LdcInsnNode(10f)) // An instruction to load a value onto the stack, which will be treated as the fifth parameter of the hook
    
    keepReturns // If you return directly after calling your hook, add this and don't add a return instruction
}
```
`getInstructions` returns an `InsnList`, with which you can then do what you please.

Your hook can also be injected directly into the target node, using `injectInstructions` instead of `getInstructions`.

In such a case, `target` can be replaced with [into] for clarity, but they are functionally identical.

If your hook contains any try-catch blocks, you should use `getInstructionsWithTryCatchNodes` (which returns `Pair<InsnList, List<TryCatchBlockNode>>`), or `injectInstructionsWithTryCatchNodes`.

### Dealing with IllegalAccessExceptions:

Due to the nature of this utility, you are likely to run into some of these. Have a look at what is being accessed, and if it something you have defined yourself, make sure it's public.
If it is a synthetic function, e.g. a lambda, then you should call `HookInjectionUtils.makeMethodsPublic(classNode)` on your hook class.

### General Tips
* It isn't recommended that you use `HookInjectionUtils` directly (other than for making synthetic methods public), but if you have to, e.g. you want to write your transformer in Java, everything should be (relatively?) well-documented inside the class. I will probably make some form of Java Builder in the future.
* If you run into any problems, either submit an Issue, make a pull-request to fix said issue, or DM me on Discord (LlamaLad7#7444)
