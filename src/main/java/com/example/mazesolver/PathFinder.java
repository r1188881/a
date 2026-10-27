package com.example.mazesolver;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.*;

/** A* over "standing positions" (feet block): flat moves, 1-block step-ups, drops up to 3. */
public final class PathFinder {
    private static final int MAX_NODES = 40000;
    private static final int MAX_RANGE = 128;
    private static final int MAX_DROP = 3;

    private static class Node {
        final BlockPos pos; final Node parent; final int g, f;
        Node(BlockPos pos, Node parent, int g, int f) { this.pos = pos; this.parent = parent; this.g = g; this.f = f; }
    }

    private PathFinder() {}

    public static List<BlockPos> find(World w, BlockPos start, BlockPos goal) {
        PriorityQueue<Node> open = new PriorityQueue<Node>(64, new Comparator<Node>() {
            public int compare(Node a, Node b) { return Integer.compare(a.f, b.f); }
        });
        Map<BlockPos, Integer> best = new HashMap<BlockPos, Integer>();
        open.add(new Node(start, null, 0, heuristic(start, goal)));
        best.put(start, 0);
        int expanded = 0;

        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            Node cur = open.poll();
            Integer known = best.get(cur.pos);
            if (known != null && cur.g > known) continue;
            if (isGoal(cur.pos, goal)) return reconstruct(cur);

            for (BlockPos next : neighbors(w, cur.pos)) {
                if (Math.abs(next.getX() - start.getX()) > MAX_RANGE
                        || Math.abs(next.getZ() - start.getZ()) > MAX_RANGE) continue;
                int g = cur.g + 1 + Math.abs(next.getY() - cur.pos.getY());
                Integer old = best.get(next);
                if (old == null || g < old) {
                    best.put(next, g);
                    open.add(new Node(next, cur, g, g + heuristic(next, goal)));
                }
            }
        }
        return new ArrayList<BlockPos>(); // no path
    }

    private static boolean isGoal(BlockPos p, BlockPos goal) {
        int dh = Math.abs(p.getX() - goal.getX()) + Math.abs(p.getZ() - goal.getZ());
        int dy = p.getY() - goal.getY();
        return dh <= 1 && dy >= 0 && dy <= 1;
    }

    private static int heuristic(BlockPos a, BlockPos b) {
        return Math.abs(a.getX() - b.getX()) + Math.abs(a.getY() - b.getY()) + Math.abs(a.getZ() - b.getZ());
    }

    private static List<BlockPos> reconstruct(Node n) {
        LinkedList<BlockPos> out = new LinkedList<BlockPos>();
        for (; n.parent != null; n = n.parent) out.addFirst(n.pos);
        return out;
    }

    private static List<BlockPos> neighbors(World w, BlockPos pos) {
        List<BlockPos> out = new ArrayList<BlockPos>(4);
        for (EnumFacing d : EnumFacing.Plane.HORIZONTAL.facings()) {
            BlockPos n = pos.offset(d);
            if (!w.isBlockLoaded(n)) continue;

            if (free(w, n) && free(w, n.up())) {
                // flat move or drop
                BlockPos c = n;
                for (int drop = 0; drop <= MAX_DROP; drop++) {
                    if (solidFloor(w, c)) { out.add(c); break; }
                    BlockPos below = c.down();
                    if (!free(w, below)) break;
                    c = below;
                }
            } else if (free(w, pos.up(2)) && standable(w, n.up())) {
                // step up one block
                out.add(n.up());
            }
        }
        return out;
    }

    private static boolean standable(World w, BlockPos p) {
        return free(w, p) && free(w, p.up()) && solidFloor(w, p);
    }

    public static boolean free(World w, BlockPos p) {
        Block b = w.getBlockState(p).getBlock();
        return b.getCollisionBoundingBox(w, p, w.getBlockState(p)) == null && !dangerous(b);
    }

    public static boolean solidFloor(World w, BlockPos p) {
        Block b = w.getBlockState(p.down()).getBlock();
        return b.isFullCube() && b.getMaterial().blocksMovement() && !dangerous(b);
    }

    private static boolean dangerous(Block b) {
        Material m = b.getMaterial();
        return m == Material.lava || m == Material.fire
                || b == Blocks.cactus || b == Blocks.web;
    }
}
