package group.aelysium.central;

import group.aelysium.rustyconnector.common.events.EventManager;
import group.aelysium.rustyconnector.common.cache.MessageCacheService;
import group.aelysium.rustyconnector.common.config.common.UUIDConfig;
import group.aelysium.rustyconnector.common.crypt.AESCryptor;
import group.aelysium.rustyconnector.common.config.common.PrivateKeyConfig;
import group.aelysium.rustyconnector.common.lang.LangService;
import group.aelysium.rustyconnector.common.packets.BuiltInIdentifications;
import group.aelysium.rustyconnector.common.packets.MCLoader;
import group.aelysium.rustyconnector.common.packets.SendPlayerPacket;
import group.aelysium.central.config.ConnectorsConfig;
import group.aelysium.rustyconnector.common.messenger.implementors.redis.RedisConnection;
import group.aelysium.rustyconnector.common.messenger.implementors.redis.RedisConnector;
import group.aelysium.central.config.DefaultConfig;
import group.aelysium.event_handlers.OnConnection;
import group.aelysium.event_handlers.OnDisconnection;
import group.aelysium.event_handlers.OnTimeout;
import group.aelysium.lib.dynamic_teleport.DynamicTeleportService;
import group.aelysium.lib.dynamic_teleport.handlers.CoordinateRequestListener;
import group.aelysium.lib.magic_link.MagicLink;
import group.aelysium.lib.magic_link.handlers.HandshakeFailureListener;
import group.aelysium.lib.magic_link.handlers.HandshakeStalePingListener;
import group.aelysium.lib.magic_link.handlers.HandshakeSuccessListener;
import group.aelysium.lib.ranked_game_interface.handlers.RankedGameImplodedListener;
import group.aelysium.lib.ranked_game_interface.handlers.RankedGameReadyListener;
import group.aelysium.lib.server_info.ServerInfoService;
import group.aelysium.rustyconnector.toolkit.common.logger.PluginLogger;
import group.aelysium.rustyconnector.toolkit.common.magic_link.IMessengerConnection;
import group.aelysium.rustyconnector.toolkit.common.magic_link.IMessengerConnector;
import group.aelysium.rustyconnector.toolkit.common.magic_link.packet.MCLoaderPacketBuilder;
import group.aelysium.rustyconnector.toolkit.common.packet.Packet;
import group.aelysium.rustyconnector.toolkit.common.serviceable.ServiceableService;
import group.aelysium.rustyconnector.toolkit.common.serviceable.interfaces.Service;
import group.aelysium.rustyconnector.toolkit.mc_loader.central.IMCLoaderFlame;
import group.aelysium.rustyconnector.toolkit.mc_loader.central.IMCLoaderTinder;
import group.aelysium.rustyconnector.toolkit.mc_loader.events.magic_link.ConnectedEvent;
import group.aelysium.rustyconnector.toolkit.mc_loader.events.magic_link.DisconnectedEvent;
import group.aelysium.rustyconnector.toolkit.mc_loader.events.magic_link.TimeoutEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.*;
import java.nio.file.Path;
import java.util.*;

public class MCLoaderFlame extends ServiceableService<CoreServiceHandler> implements IMCLoaderFlame<CoreServiceHandler> {
    protected final int configVersion;
    protected final String version;

    public MCLoaderFlame(String version, int configVersion, CoreServiceHandler services) {
        super(services);
        this.version = version;
        this.configVersion = configVersion;
    }

    public String versionAsString() { return this.version; }

    @Override
    public void lock() {
        this.services().magicLink().connection().orElseThrow().publish(MCLoader.Lock.build(this));
    }

    @Override
    public void unlock() {
        this.services().magicLink().connection().orElseThrow().publish(MCLoader.Unlock.build(this));
    }

    @Override
    public void send(UUID player, String familyID) {
        Packet message = this.services().packetBuilder().newBuilder()
                .identification(BuiltInIdentifications.SEND_PLAYER)
                .sendingToProxy()
                .parameter(SendPlayerPacket.Parameters.PLAYER_UUID, player.toString())
                .parameter(SendPlayerPacket.Parameters.TARGET_FAMILY_NAME, familyID)
                .build();

        this.services().magicLink().connection().orElseThrow().publish(message);
    }

    public int configVersion() { return this.configVersion; }

    /**
     * Fabricates a new RustyConnector core and returns it.
     * @return A new RustyConnector {@link MCLoaderFlame}.
     */
    public static MCLoaderFlame fabricateNew(MCLoaderTinder api, LangService langService, PluginLogger logger, int port) throws RuntimeException {
        Initialize initialize = new Initialize(api);

        try {
            UUID uuid = initialize.systemUUID();
            String version = initialize.version();
            int configVersion = initialize.configVersion();
            AESCryptor cryptor = initialize.privateKey();
            DefaultConfig defaultConfig = initialize.defaultConfig(langService);
            ServerInfoService serverInfoService = initialize.serverInfo(uuid, defaultConfig, port);

            MessageCacheService messageCacheService = initialize.messageCache();
            RedisConnector messenger = initialize.connectors(cryptor, messageCacheService, logger, langService, serverInfoService.uuid());

            initialize.dynamicTeleport();
            MagicLink magicLinkService = initialize.magicLink(messenger);
            initialize.eventManager();

            MCLoaderFlame flame = new MCLoaderFlame(version, configVersion, new CoreServiceHandler(initialize.getServices()));

            flame.services().add(new MCLoaderPacketBuilder(flame));
            magicLinkService.startHeartbeat(flame);

            return flame;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}





/**
 * The master initializer class.
 * While some methods depend on resources from other methods,
 * assuming you follow the implementation of each method, it should always successfully build the specified service.
 * <p>
 * This class will mutate the provided services and requestedConnectors lists that are provided to it.
 */
class Initialize {
    private final MCLoaderTinder api;
    private final PluginLogger logger;
    private final Map<Class<? extends Service>, Service> services = new HashMap<>();
    private final List<Component> bootOutput = new ArrayList<>();

    public Initialize(MCLoaderTinder tinder) {
        this.api = tinder;
        this.logger = api.logger();
    }

    public Map<Class<? extends Service>, Service> getServices() {
        return this.services;
    }

    public UUID systemUUID() {
        return new UUIDConfig(new File(api.dataFolder(), "metadata/system.uuid")).get(bootOutput);
    }

    public String version() {
        try {
            InputStream stream = IMCLoaderTinder.resourceAsStream("plugin.yml");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            ConfigurationNode node = YamlConfigurationLoader.builder()
                    .indent(2)
                    .source(() -> reader)
                    .build().load();

            stream.close();
            reader.close();
            return node.node("version").getString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public int configVersion() {
        try {
            InputStream stream = MCLoaderTinder.resourceAsStream("plugin.yml");
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream));

            ConfigurationNode node = YamlConfigurationLoader.builder()
                    .indent(2)
                    .source(() -> reader)
                    .build().load();

            stream.close();
            reader.close();
            return node.node("config-version").getInt();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public AESCryptor privateKey() {
        PrivateKeyConfig privateKeyConfig = new PrivateKeyConfig(new File(api.dataFolder(), "metadata/private.key"));
        try {
            return privateKeyConfig.get(bootOutput);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public DefaultConfig defaultConfig(LangService lang) {
        return DefaultConfig.construct(Path.of(api.dataFolder()), lang, this.configVersion());
    }

    public RedisConnector connectors(AESCryptor cryptor, MessageCacheService cacheService, PluginLogger logger, LangService lang, UUID senderUUID) throws IOException {
        logger.send(Component.text("Building Connectors...", NamedTextColor.DARK_GRAY));

        ConnectorsConfig config = ConnectorsConfig.construct(Path.of(api.dataFolder()), lang);

        RedisConnector.Settings spec = new RedisConnector.Settings(
                config.getRedis_address(),
                config.getRedis_user(),
                config.getRedis_protocol(),
                config.getRedis_dataChannel()
        );
        RedisConnector messenger = RedisConnector.create(cryptor, spec);
        services.put(RedisConnector.class, messenger);


        messenger.connect();
        IMessengerConnection connection = messenger.connection().orElseThrow();

        connection.on(new HandshakeSuccessListener(this.api));
        connection.on(new HandshakeFailureListener(this.api));
        connection.on(new HandshakeStalePingListener(this.api));
        connection.on(new CoordinateRequestListener(this.api));
        connection.on(new RankedGameReadyListener(this.api));
        connection.on(new RankedGameImplodedListener(this.api));

        ((RedisConnection) connection).startListening(cacheService, logger, Packet.Node.mcLoader(senderUUID));

        logger.send(Component.text("Finished building Connectors.", NamedTextColor.GREEN));

        return messenger;
    }

    public MagicLink magicLink(IMessengerConnector messenger) {
        logger.send(Component.text("Building magic link service...", NamedTextColor.DARK_GRAY));

        MagicLink service = new MagicLink(messenger);
        services.put(MagicLink.class, service);

        logger.send(Component.text("Finished booting magic link service.", NamedTextColor.GREEN));

        return service;
    }

    public ServerInfoService serverInfo(UUID uuid, DefaultConfig defaultConfig, int port) {
        ServerInfoService serverInfoService = new ServerInfoService(
                uuid,
                defaultConfig.address(),
                defaultConfig.displayName(),
                defaultConfig.magicConfig(),
                port
        );
        services.put(ServerInfoService.class, serverInfoService);

        return serverInfoService;
    }

    public MessageCacheService messageCache() {
        MessageCacheService service = new MessageCacheService(50);
        services.put(MessageCacheService.class, service);

        logger.log("Set message cache size to be: 50");
        return service;
    }

    public void eventManager() {
        EventManager factory = new EventManager();
        services.put(EventManager.class, factory);

        factory.on(ConnectedEvent.class, new OnConnection());
        factory.on(DisconnectedEvent.class, new OnDisconnection());
        factory.on(TimeoutEvent.class, new OnTimeout());
    }

    public void dynamicTeleport() {
        services.put(DynamicTeleportService.class, new DynamicTeleportService());
    }

}