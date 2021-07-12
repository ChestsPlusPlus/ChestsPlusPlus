package com.jamesdpeters.minecraft.chests.v1_17_R1;

import com.jamesdpeters.minecraft.chests.NPCProvider;
import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_17_R1.CraftServer;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.entity.HumanEntity;

import java.util.UUID;

public class NPC implements NPCProvider {
    public HumanEntity createHumanEntity() {
        MinecraftServer nmsServer = ((CraftServer) Bukkit.getServer()).getServer();
        ServerLevel nmsWorld = ((CraftWorld) Bukkit.getWorlds().get(0)).getHandle();
        GameProfile gameProfile = new GameProfile(UUID.randomUUID(), "Chests++NPC");
        ServerPlayer npc = new ServerPlayer(nmsServer, nmsWorld, gameProfile);
        return npc.getBukkitEntity();
    }
}
