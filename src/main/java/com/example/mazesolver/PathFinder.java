package com.example.mazesolver;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;

import java.util.*;

/**
 * A* over "standing positions" (feet block): flat moves, 1-block step-ups, drops up to 3.
 * If the goal can't be reached (too far / chunks not loaded yet) it returns a PARTIAL route
 * to the explored position that is closest to the goal.
 */
public final class PathFinder {
    private static final int MAX_NODES = 250000;
    private static final int MAX_RANGE = 512;
    private static final int MAX_DROP = 3;

    public static final class Result {
        public final List<BlockPos> path;
        public final boolean found;    // true = real route to the goal (or already there)
        public final boolean partial;  // true = only a route toward the goal
        Result(List<BlockPos> path, boolean found, boolean partial) {
            this.path = path; this.found = found; this.partial = partial;
        }
    }

    private static class Node {
        final BlockPos pos; final Node parent; final int g, f;
        Node(BlockPos pos, Node parent, int g, int f) { this.pos = pos; this.parent = parent; this.g = g; this.f = f; }
    }

    private PathFinder() {}

    public static Result find(World w, BlockPos start, BlockPos goal) {
        if (isGoal(start, goal)) return new Result(new ArrayList<BlockPos>(), true, false);

        PriorityQueue<Node> open = new PriorityQueue<Node>(64, new Comparator<Node>() {
            public int compare(Node a, Node b) { return Integer.compare(a.f, b.f); }
        });
        Map<BlockPos, Integer> best = new HashMap<BlockPos, Integer>();
        Node startNode = new Node(start, null, 0, heuristic(start, goal));
        open.add(startNode);
        best.put(start, 0);

        Node closest = startNode;
        int closestH = heuristic(start, goal);
        int expanded = 0;

        while (!open.isEmpty() && expanded++ < MAX_NODES) {
            Node cur = open.poll();
            Integer known = best.get(cur.pos);
            if (known != null && cur.g > known) continue;
            if (isGoal(cur.pos, goal)) return new Result(reconstruct(cur), true, false);

            int h = heuristic(cur.pos, goal);
            if (h < closestH) { closestH = h; closest = cur; }

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

        if (closest.parent == null) return new Result(new ArrayList<BlockPos>(), false, false);
        return new Result(reconstruct(closest), false, true);
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
        return new ArrayList<BlockPos>(out);
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
