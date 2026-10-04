package com.jamesdpeters.chestsplusplus.link;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;

/** Linked chests are always single: these split a double chest back into two singles. */
public final class DoubleChests {

    private DoubleChests() {}

    /** Splits {@code block} and its partner, if it is half of a double chest. */
    public static void split(Block block) {
        if (!(block.getBlockData() instanceof Chest data) || data.getType() == Chest.Type.SINGLE) return;
        Block partner = block.getRelative(partnerDirection(data));
        data.setType(Chest.Type.SINGLE);
        block.setBlockData(data, false);
        if (partner.getBlockData() instanceof Chest partnerData && partnerData.getType() != Chest.Type.SINGLE) {
            partnerData.setType(Chest.Type.SINGLE);
            partner.setBlockData(partnerData, false);
        }
    }

    /** Vanilla: a LEFT half's partner is clockwise of its facing, a RIGHT half's counter-clockwise. */
    public static BlockFace partnerDirection(Chest data) {
        boolean clockwise = data.getType() == Chest.Type.LEFT;
        return switch (data.getFacing()) {
            case NORTH -> clockwise ? BlockFace.EAST : BlockFace.WEST;
            case EAST -> clockwise ? BlockFace.SOUTH : BlockFace.NORTH;
            case SOUTH -> clockwise ? BlockFace.WEST : BlockFace.EAST;
            case WEST -> clockwise ? BlockFace.NORTH : BlockFace.SOUTH;
            default -> BlockFace.SELF;
        };
    }
}
