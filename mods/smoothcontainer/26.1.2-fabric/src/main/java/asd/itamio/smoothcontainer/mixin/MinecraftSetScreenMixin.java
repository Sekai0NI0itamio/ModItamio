package asd.itamio.smoothcontainer.mixin;

import asd.itamio.smoothcontainer.ContainerOpenHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftSetScreenMixin {

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void smoothcontainer$onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen == null) {
            return;
        }
        if (ContainerOpenHandler.tryDefer(screen)) {
            ci.cancel();
        }
    }
}
