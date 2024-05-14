package group.aelysium.rustyconnector.toolkit.velocity.events.family;

import group.aelysium.rustyconnector.toolkit.core.events.Event;
import group.aelysium.rustyconnector.toolkit.velocity.family.Family;
import group.aelysium.rustyconnector.toolkit.velocity.server.IMCLoader;

/**
 * Represents an MCLoader being unlocked on this family.
 */
public class MCLoaderUnlockedEvent implements Event {
    protected final Family family;
    protected final IMCLoader mcLoader;

    public MCLoaderUnlockedEvent(Family family, IMCLoader mcLoader) {
        this.family = family;
        this.mcLoader = mcLoader;
    }

    public Family family() {
        return family;
    }
    public IMCLoader mcLoader() {
        return mcLoader;
    }
}