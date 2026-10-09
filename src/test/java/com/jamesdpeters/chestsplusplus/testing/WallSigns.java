package com.jamesdpeters.chestsplusplus.testing;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.sign.Side;
import org.bukkit.entity.Player;
import org.bukkit.event.block.SignChangeEvent;
import org.mockbukkit.mockbukkit.block.data.WallSignDataMock;

/** Writes link signs: a wall sign on the north face of a block, as a player finishing the sign editor. */
public final class WallSigns {

    private WallSigns() {}

    public static SignChangeEvent write(Player player, Block target, String header, String name) {
        return write(player, target, header, name, Material.OAK_WALL_SIGN, Material.OAK_SIGN);
    }

    public static SignChangeEvent write(Player player, Block target, String header, String name, Material wallSign, Material item) {
        Block sign = target.getRelative(BlockFace.NORTH);
        sign.setType(wallSign);
        SignData data = new SignData(wallSign, item);
        data.setFacing(BlockFace.NORTH);
        sign.setBlockData(data);
        SignChangeEvent event = new SignChangeEvent(sign, player,
                List.of(Component.text(header), Component.text(name), Component.empty(), Component.empty()), Side.FRONT);
        Bukkit.getPluginManager().callEvent(event);
        return event;
    }

    /** MockBukkit doesn't implement {@code getPlacementMaterial}, which gives the sign's item back. */
    private static final class SignData extends WallSignDataMock {

        private final Material item;

        private SignData(Material wallSign, Material item) {
            super(wallSign);
            this.item = item;
        }

        private SignData(SignData other) {
            super(other);
            this.item = other.item;
        }

        @Override
        public Material getPlacementMaterial() {
            return item;
        }

        @Override
        public SignData clone() {
            return new SignData(this);
        }
    }
}
