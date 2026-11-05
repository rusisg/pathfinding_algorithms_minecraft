package dev.pathlab;

import net.fabricmc.api.ModInitializer;

/** Entry point. Prints what is active so you can see it in the log, then runs the self-check. */
public class PathLabMod implements ModInitializer {
    @Override
    public void onInitialize() {
        PathLabConfig.Settings s = PathLabConfig.ACTIVE;
        PathLog.say("=======================================================");
        PathLog.say("loaded. algorithm = " + s.algorithm()
                + (s.algorithm() == dev.pathlab.core.SearchAlgorithm.ASTAR ? " (heuristic weight " + s.weight() + ")" : "")
                + ", replacement " + (s.enabled() ? "ON" : "OFF (vanilla reference run)")
                + ", mobs = " + s.mobs() + ", run label = " + PathLabConfig.RUN);
        PathLog.say("=======================================================");
        SelfCheck.run();
    }
}
