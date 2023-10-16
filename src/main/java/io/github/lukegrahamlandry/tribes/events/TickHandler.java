package io.github.lukegrahamlandry.tribes.events;


import io.github.lukegrahamlandry.tribes.config.TribesConfig;
import io.github.lukegrahamlandry.tribes.init.NetworkHandler;
import io.github.lukegrahamlandry.tribes.item.TribeCompass;
import io.github.lukegrahamlandry.tribes.network.CompassChunkPacket;
import io.github.lukegrahamlandry.tribes.network.LandOwnerPacket;
import io.github.lukegrahamlandry.tribes.network.PacketOpenJoinGUI;
import io.github.lukegrahamlandry.tribes.tribe_data.LandClaimHelper;
import io.github.lukegrahamlandry.tribes.tribe_data.Tribe;
import io.github.lukegrahamlandry.tribes.tribe_data.TribesManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class TickHandler {
    private static int timer = 0;
    static int ONE_MINUTE = 60 * 20;
    @SubscribeEvent
    public static void updateLandOwnerAndCompassAndEffects(TickEvent.PlayerTickEvent event){
        if (event.player.getCommandSenderWorld().isClientSide() || timer % 10 != 0) return;

        // land owner display
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.player),
                new LandOwnerPacket(event.player.getUUID(), LandClaimHelper.getOwnerDisplayFor(event.player), LandClaimHelper.canAccessLandAt(event.player, event.player.blockPosition())));


        // tribe compass direction
        ItemStack stack = event.player.getItemInHand(InteractionHand.MAIN_HAND);
        if (stack.getItem() instanceof TribeCompass){
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.player),
                    new CompassChunkPacket(event.player.getUUID(), TribeCompass.caclulateTargetPosition((ServerPlayer) event.player, stack)));
        }


        // apply tribe effects
        Tribe tribe = TribesManager.getTribeOf(event.player.getUUID());
        if (tribe != null && timer % 80 == 0){  // without the modulo check the effects dont tick properly. ie wither never happens, regen always happens
            tribe.effects.forEach((effect, level) -> {
                event.player.addEffect(new MobEffectInstance(effect, 15*20, level-1));
            });
        }

        if (tribe == null && TribesConfig.isTribeRequired()){
            // no tribe and force tribes
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> (ServerPlayer) event.player), new PacketOpenJoinGUI((ServerPlayer) event.player));
        }
    }

    @SubscribeEvent
    public static void tickDeathPunishments(TickEvent.WorldTickEvent event){
        if (event.world.isClientSide()) return;
        timer++;

        if (timer >= ONE_MINUTE){
            timer = 0;
            for (Tribe tribe : TribesManager.getTribes()){
                if (tribe.claimDisableTime > 0){
                    tribe.claimDisableTime -= 1;
                    if (tribe.claimDisableTime < 0){
                        tribe.deathWasPVP = false;
                        tribe.deathIndex = 0;
                    }
                }
            }

            // remove inactive people from thier tribes
            RemoveInactives.check();
        }
    }
}
