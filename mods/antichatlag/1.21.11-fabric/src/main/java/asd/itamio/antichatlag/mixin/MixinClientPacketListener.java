package asd.itamio.antichatlag.mixin;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.entity.player.ProfileKeyPair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin that prevents the chat signing encoder from being initialized.
 * <p>
 * When {@link ClientPacketListener#setKeyPair(ProfileKeyPair)} is called
 * (typically during the tick loop), it creates a LocalChatSession and replaces
 * the default {@code UNSIGNED} encoder with an RSA signature encoder. For
 * offline-mode players without proper Mojang authentication keys, this RSA
 * signing operation causes the entire game to freeze every time a chat message
 * is sent.
 * <p>
 * By cancelling {@code setKeyPair}, the encoder remains as {@code UNSIGNED},
 * which simply returns null for the message signature — eliminating the freeze.
 */
@Mixin(ClientPacketListener.class)
public abstract class MixinClientPacketListener {

    @Inject(method = "setKeyPair", at = @At("HEAD"), cancellable = true)
    private void onSetKeyPair(ProfileKeyPair profileKeyPair, CallbackInfo ci) {
        // Block the key pair assignment to prevent RSA signing
        // This keeps signedMessageEncoder = UNSIGNED (returns null signatures)
        ci.cancel();
    }
}
