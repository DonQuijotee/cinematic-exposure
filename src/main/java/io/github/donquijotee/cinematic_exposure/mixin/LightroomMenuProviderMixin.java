package io.github.donquijotee.cinematic_exposure.mixin;

import dev.dyslexic.cinematic.station.MenuActions;
import dev.dyslexic.cinematic.station.StationKind;
import io.github.mortuusars.exposure.Exposure;
import io.github.mortuusars.exposure.world.block.entity.LightroomBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adapt only menu opening: Exposure needs its block position in NeoForge's existing open-menu payload. */
@Mixin(value=MenuActions.class,remap=false)
public abstract class LightroomMenuProviderMixin {
    @Inject(method="provider",at=@At("HEAD"),cancellable=true)
    private static void cinematicExposure$provider(ServerPlayer player,BlockPos pos,StationKind kind,
            CallbackInfoReturnable<MenuProvider> cir) {
        if(kind!=StationKind.ADDON || !(player.level().getBlockEntity(pos) instanceof LightroomBlockEntity lightroom)) return;
        cir.setReturnValue(new MenuProvider() {
            @Override public Component getDisplayName() { return lightroom.getDisplayName(); }
            @Override public AbstractContainerMenu createMenu(int id,Inventory inventory,Player owner) {
                lightroom.setLastPlayer(player);
                lightroom.setChanged();
                player.awardStat(Exposure.Stats.INTERACT_WITH_LIGHTROOM);
                return lightroom.createMenu(id,inventory,owner);
            }
            @Override public void writeClientSideData(AbstractContainerMenu menu,RegistryFriendlyByteBuf buffer) {
                buffer.writeBlockPos(pos);
            }
        });
    }
}
