package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfoReturnable<R> { public R value; public boolean cancelled; public void setReturnValue(R r) { value = r; cancelled = true; } }
