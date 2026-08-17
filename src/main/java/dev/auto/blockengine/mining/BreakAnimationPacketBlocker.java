package dev.auto.blockengine.mining;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerBlockBreakAnimation;

public final class BreakAnimationPacketBlocker {
    private static PacketListenerCommon listener;

    private BreakAnimationPacketBlocker() {
    }

    public static void register() {
        if (listener != null) {
            return;
        }

        listener = PacketEvents.getAPI().getEventManager().registerListener(new PacketListenerAbstract(PacketListenerPriority.HIGHEST) {
            @Override
            public void onPacketSend(PacketSendEvent event) {
                if (event.getPacketType() != PacketType.Play.Server.BLOCK_BREAK_ANIMATION) {
                    return;
                }

                WrapperPlayServerBlockBreakAnimation packet = new WrapperPlayServerBlockBreakAnimation(event);
                if (MiningManager.getInstance().blocksExternalClear(
                        packet.getBlockPosition(),
                        packet.getEntityId(),
                        packet.getDestroyStage()
                ) || DebugBreakAnimationManager.getInstance().blocksReset(
                        packet.getBlockPosition(),
                        packet.getDestroyStage()
                )) {
                    event.setCancelled(true);
                }
            }
        });
    }

    public static void unregister() {
        if (listener == null) {
            return;
        }

        PacketEvents.getAPI().getEventManager().unregisterListener(listener);
        listener = null;
    }
}



