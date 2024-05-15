package group.aelysium.rustyconnector.toolkit.velocity.family.scalar_family;

import group.aelysium.rustyconnector.toolkit.velocity.family.load_balancing.ILoadBalancer;
import group.aelysium.rustyconnector.toolkit.velocity.family.whitelist.IWhitelist;
import org.jetbrains.annotations.NotNull;

public interface IRootFamily extends IScalarFamily {
    boolean catchDisconnectingPlayers();

    record Settings(
            @NotNull String id,
            @NotNull ILoadBalancer.Settings loadBalancer,
            boolean catchDisconnectingPlayers,
            String displayName,
            String parent,
            IWhitelist.Settings whitelist
    ) {}
}
