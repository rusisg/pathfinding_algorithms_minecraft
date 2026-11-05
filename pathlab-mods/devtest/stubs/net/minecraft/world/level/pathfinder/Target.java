package net.minecraft.world.level.pathfinder;
public class Target extends Node {
    private float bestHeuristic = Float.MAX_VALUE; private Node bestNode; private boolean reached;
    public Target(Node n) { super(n.x, n.y, n.z); }
    public void updateBest(float d, Node node) { if (d < bestHeuristic) { bestHeuristic = d; bestNode = node; } }
    public Node getBestNode() { return bestNode; }
    public void setReached() { reached = true; }
    public boolean isReached() { return reached; }
}
