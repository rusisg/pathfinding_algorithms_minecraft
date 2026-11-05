package net.minecraft.world.level.pathfinder;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
public abstract class NodeEvaluator {
    public void prepare(PathNavigationRegion r, Mob m) { }
    public void done() { }
    public abstract Node getStart();
    public abstract Target getTarget(double x, double y, double z);
    public abstract int getNeighbors(Node[] out, Node pos);
}
