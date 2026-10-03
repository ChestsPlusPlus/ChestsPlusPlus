package com.jamesdpeters.chestsplusplus.model;

import com.jamesdpeters.chestsplusplus.core.BlockPos;
import org.bukkit.block.BlockFace;

/** A linked block: its position, the face its display sits on, and the owning group's id. */
public record Node(BlockPos pos, BlockFace facing, long groupId) {}
