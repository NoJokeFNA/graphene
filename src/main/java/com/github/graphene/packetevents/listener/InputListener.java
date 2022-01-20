package com.github.graphene.packetevents.listener;

import com.github.graphene.Main;
import com.github.graphene.entity.ItemEntity;
import com.github.graphene.user.User;
import com.github.graphene.util.ServerUtil;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.impl.PacketReceiveEvent;
import com.github.retrooper.packetevents.event.impl.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.Equipment;
import com.github.retrooper.packetevents.protocol.player.EquipmentSlot;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.wrapper.play.client.*;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityAnimation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEquipment;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHeldItemChange;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class InputListener implements PacketListener {
    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        User user = (User) event.getPlayer();
        if (WrapperPlayClientPlayerFlying.isFlying(event.getPacketType())) {
            Vector3d position = user.getEntityInformation().getLocation().getPosition();
            double shortestDistance = ItemEntity.PICKUP_DISTANCE;
            ItemEntity shortestItemEntity = null;
            for (ItemEntity itemEntity : Main.ITEM_ENTITIES) {
                double dist = itemEntity.getPosition().distance(position);
                if (dist <= shortestDistance) {
                    shortestDistance = dist;
                    shortestItemEntity = itemEntity;
                    if (dist == ItemEntity.PICKUP_DISTANCE) {
                        break;
                    }
                }
            }
            if (shortestItemEntity != null) {
                //Pick it up for them(also destroy)
                shortestItemEntity.pickup(user, Main.USERS);
            }
        } else if (event.getPacketType() == PacketType.Play.Client.CHAT_MESSAGE) {
            WrapperPlayClientChatMessage chatMessage = new WrapperPlayClientChatMessage(event);
            String msg = chatMessage.getMessage();
            Main.LOGGER.info(user.getUsername() + ": " + msg);
            //Prefix the display message with the player's name in green, and then a colon and their message in white
            Component displayComponent = Component.text(user.getUsername()).color(NamedTextColor.GREEN)
                    .append(Component.text(": " + msg).color(NamedTextColor.WHITE).asComponent()).asComponent();
            //Send it to everyone(including the sender)
            ServerUtil.broadcastMessage(displayComponent);
        } else if (event.getPacketType() == PacketType.Play.Client.HELD_ITEM_CHANGE) {
            WrapperPlayClientHeldItemChange heldItemChange = new WrapperPlayClientHeldItemChange(event);
            int slot = heldItemChange.getSlot();
            user.currentSlot = slot;
            @Nullable ItemStack item = user.getHotbarIndex(slot);

            for (User player : Main.USERS) {
                if (player.getEntityId() != user.getEntityId()) {
                    List<Equipment> equipment = new ArrayList<>();
                    if (item == null) {
                        item = ItemStack.builder().type(ItemTypes.AIR).amount(64).build();
                    }
                    equipment.add(new Equipment(EquipmentSlot.MAINHAND, item));
                    WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(user.getEntityId(), equipment);
                    player.sendPacket(equipmentPacket);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Client.PLAYER_DIGGING) {
            WrapperPlayClientPlayerDigging digging = new WrapperPlayClientPlayerDigging(event);
            WrapperPlayClientPlayerDigging.Action action = digging.getAction();
            if (action == WrapperPlayClientPlayerDigging.Action.DROP_ITEM ||
                    action == WrapperPlayClientPlayerDigging.Action.DROP_ITEM_STACK) {
                ItemStack item = user.getCurrentItem();
                //TODO Remove debug
                if (item != null) {
                    int newAmount = action == WrapperPlayClientPlayerDigging.Action.DROP_ITEM ? 1 : item.getAmount();
                    ItemStack entity = item.copy();
                    entity.setAmount(newAmount);
                    if (item.isEmpty()) {
                        user.setCurrentItem(null);
                    } else {
                        user.getCurrentItem().shrink(newAmount);
                    }
                    user.updateHotbar();
                    ItemEntity itemEntity = new ItemEntity(user.getEntityInformation().getLocation().getPosition(),
                            entity);
                    itemEntity.spawn(user, Main.USERS);
                }
            }
        } else if (event.getPacketType() == PacketType.Play.Client.INTERACT_ENTITY) {
            WrapperPlayClientInteractEntity interactEntity = new WrapperPlayClientInteractEntity(event);
            WrapperPlayClientInteractEntity.InteractAction action = interactEntity.getAction();
            if (action == WrapperPlayClientInteractEntity.InteractAction.ATTACK) {
                int targetEntityId = interactEntity.getEntityId();
                User targetPlayer = null;
                for (User player : Main.USERS) {
                    if (player.getEntityId() == targetEntityId) {
                        targetPlayer = player;
                        break;
                    }
                }
                if (targetPlayer != null) {
                    //Send us the damage animation packet
                    WrapperPlayServerEntityAnimation animation = new WrapperPlayServerEntityAnimation(targetEntityId,
                            WrapperPlayServerEntityAnimation.EntityAnimationType.TAKE_DAMAGE);
                    //TODO Remove optional criticals
                    WrapperPlayServerEntityAnimation animation2 = new WrapperPlayServerEntityAnimation(targetEntityId,
                            WrapperPlayServerEntityAnimation.EntityAnimationType.CRITICAL_EFFECT);
                    //TODO Velocity calculation
                    //TODO Health system
                    user.sendPacket(animation);
                    user.sendPacket(animation2);
                }
            }
        }
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        User user = (User) event.getPlayer();
        if (event.getPacketType() == PacketType.Play.Server.HELD_ITEM_CHANGE) {
            WrapperPlayServerHeldItemChange heldItemChange = new WrapperPlayServerHeldItemChange(event);
            int slot = heldItemChange.getSlot();
            user.currentSlot = slot;
            @Nullable ItemStack item = user.getHotbarIndex(slot);
            for (User player : Main.USERS) {
                if (player.getEntityId() != user.getEntityId()) {
                    List<Equipment> equipment = new ArrayList<>();
                    if (item == null) {
                        item = ItemStack.builder().type(ItemTypes.AIR).amount(64).build();
                    }
                    equipment.add(new Equipment(EquipmentSlot.MAINHAND, item));
                    WrapperPlayServerEntityEquipment equipmentPacket = new WrapperPlayServerEntityEquipment(user.getEntityId(), equipment);
                    player.sendPacket(equipmentPacket);
                }
            }
        }
    }
}