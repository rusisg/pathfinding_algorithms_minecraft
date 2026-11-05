package net.minecraft.world.entity;
public class EntityType<T> {
    public final String id; public EntityType(String id) { this.id = id; }
    @Override public String toString() { return "entity.minecraft." + id; }
    public static final EntityType<Object> ZOMBIE = new EntityType<>("zombie");
    public static final EntityType<Object> IRON_GOLEM = new EntityType<>("iron_golem");
    public static final EntityType<Object> VILLAGER = new EntityType<>("villager");
}
