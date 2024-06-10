package group.aelysium.rustyconnector.toolkit.velocity.family;

import group.aelysium.rustyconnector.toolkit.common.absolute_redundancy.Particle;
import group.aelysium.rustyconnector.toolkit.velocity.connection.IPlayerConnectable;

import java.util.Optional;

public interface IFamily extends IPlayerConnectable, Particle {
    String id();
    String displayName();

    /**
     * Fetches a reference to the parent of this family.
     * The parent of this family should always be either another family, or the root family.
     * If this family is the root family, this method will always return `null`.
     * @return {@link IFamily}
     */
    Optional<Particle.Flux<IFamily>> parent();

    /**
     * Returns this family's {@link IFamilyConnector}.
     * The family's connector is used to handle players when they connect or disconnect from this family.
     * @return {@link IFamilyConnector}
     */
    IFamilyConnector connector();
}
