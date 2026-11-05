package net.minecraft.world.level.pathfinder;
import java.util.List;
import net.minecraft.core.BlockPos;
public class Path {
    private final List<Node> nodes; private final BlockPos target; private final float distToTarget; private final boolean reached;
    public Path(List<Node> nodes, BlockPos target, boolean reached) {
        this.nodes = nodes; this.target = target; this.reached = reached;
        this.distToTarget = nodes.isEmpty() ? Float.MAX_VALUE : nodes.get(nodes.size() - 1).distanceManhattan(target);
    }
    public int getNodeCount() { return nodes.size(); }
    public float getDistToTarget() { return distToTarget; }
    public Node getNode(int i) { return nodes.get(i); }
    public Node getEndNode() { return nodes.isEmpty() ? null : nodes.get(nodes.size() - 1); }
    public boolean canReach() { return reached; }
}
