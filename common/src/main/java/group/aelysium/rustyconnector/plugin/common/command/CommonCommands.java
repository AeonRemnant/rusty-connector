package group.aelysium.rustyconnector.plugin.common.command;

import group.aelysium.ara.Particle;
import group.aelysium.rustyconnector.RC;
import group.aelysium.rustyconnector.RustyConnector;
import group.aelysium.rustyconnector.common.crypt.NanoID;
import group.aelysium.rustyconnector.common.errors.Error;
import group.aelysium.rustyconnector.common.magic_link.packet.Packet;
import group.aelysium.rustyconnector.common.plugins.Plugin;
import group.aelysium.rustyconnector.plugin.common.lang.CommonLang;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.incendo.cloud.annotations.Argument;
import org.incendo.cloud.annotations.Command;
import org.incendo.cloud.annotations.Default;
import org.incendo.cloud.annotations.Permission;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static net.kyori.adventure.text.Component.text;

@Command("rc")
@Permission("rustyconnector.commands.rc")
public class CommonCommands {

    @Command("")
    public void hizfafjjszjivcys(Client<?> client) {
        client.send(RC.Kernel().details());
    }

    @Command("reload")
    public void nglbwcmuzzxvjaon(Client<?> client) {
        try {
            client.send(RC.Lang("rustyconnector-waiting").generate());
            Particle.Flux<?> particle = RustyConnector.Kernel().orElseThrow();
            particle.reignite();
            particle.observe();
            client.send(RC.Lang("rustyconnector-finished").generate());
        } catch (Exception e) {
            RC.Error(Error.from(e).urgent(true));
        }
    }

    @Command("plugin")
    @Command("plugins")
    public void nglbwcmuvchdjaon(Client<?> client) {
        try {
            client.send(RC.Lang("rustyconnector-pluginList").generate(RC.Kernel().allPlugins().keySet()));
        } catch (Exception e) {
            RC.Error(Error.from(e).urgent(true));
        }
    }
    @Command("plugin <pluginTree>")
    @Command("plugins <pluginTree>")
    public void nglbwcmuschdjaon(Client<?> client, String pluginTree) {
        try {
            Particle.Flux<? extends Plugin> flux = fetchPlugin(client, pluginTree);
            if(!flux.exists()) {
                client.send(
                    Error.withHint(
                                "While attempting to fetch the plugin "+pluginTree+" a plugin in the chain was unavailable.",
                                "This issue typically arises when a plugin is being reloaded. In which case wait a bit before attempting to access it."
                        )
                        .causedBy("Attempting to fetch the plugin "+pluginTree)
                );
                return;
            }

            client.send(RC.Lang("rustyconnector-details").generate(flux.orElseThrow()));
        } catch (Exception e) {
            RC.Error(Error.from(e).urgent(true));
        }
    }

    @Command("plugin <pluginTree> reload")
    @Command("plugins <pluginTree> reload")
    public void nglbwzmspchdjaon(Client<?> client, String pluginTree) {
        try {
            Particle.Flux<? extends Plugin> flux = fetchPlugin(client, pluginTree);
            if(flux == null) return;
            client.send(RC.Lang("rustyconnector-waiting").generate());
            flux.reignite().get();
            client.send(RC.Lang("rustyconnector-finished").generate());
        } catch (Exception e) {
            RC.Error(Error.from(e).urgent(true));
        }
    }
    @Command("plugin <pluginTree> stop")
    @Command("plugins <pluginTree> stop")
    public void nglbwzmzpsodjaon(Client<?> client, String pluginTree) {
        Particle.Flux<? extends Plugin> flux = fetchPlugin(client, pluginTree);
        if(flux == null) return;
        if(!flux.exists()) {
            client.send(RC.Lang("rustyconnector-pluginAlreadyStopped").generate());
            return;
        }
        client.send(RC.Lang("rustyconnector-waiting").generate());
        flux.close();
        try {
            client.send(RC.Lang("rustyconnector-finished").generate());
        } catch (NoSuchElementException e) {
            client.send(Component.text("Successfully stopped that plugin!"));
        }
    }
    @Command("plugin <pluginTree> start")
    @Command("plugins <pluginTree> start")
    public void asfdmgfsgsodjaon(Client<?> client, String pluginTree) {
        try {
            Particle.Flux<? extends Plugin> flux = fetchPlugin(client, pluginTree);
            if(flux == null) return;
            if(flux.exists()) {
                client.send(RC.Lang("rustyconnector-pluginAlreadyStarted").generate());
                return;
            }
            client.send(RC.Lang("rustyconnector-waiting").generate());
            flux.observe();
            client.send(RC.Lang("rustyconnector-finished").generate());
        } catch (Exception e) {
            RC.Error(Error.from(e).urgent(true));
        }
    }

    private static @Nullable Particle.Flux<? extends Plugin> fetchPlugin(Client<?> client, String plugin) {
        String[] nodes = plugin.split("\\.");
        AtomicReference<Particle.Flux<? extends Plugin>> current = new AtomicReference<>(
                RustyConnector.Kernel()
                .orElseThrow(()->new NoSuchElementException("No RustyConnector Kernel has been registered."))
        );

        for (int i = 0; i < nodes.length; i++) {
            String node = nodes[i];
            boolean isLast = i == (nodes.length - 1);
            if(!current.get().exists()) {
                client.send(Error.withHint(
                                "While attempting to fetch the plugin "+plugin+" a plugin in the chain was unavailable.",
                                "This issue typically arises when a plugin is being reloaded. In which case wait a bit before attempting to access it."
                        )
                        .causedBy("Attempting to fetch the plugin "+plugin)
                );
                return null;
            }

            Plugin resolvedPlugin = current.get().orElseThrow();
            if(!resolvedPlugin.hasPlugins()) {
                client.send(Error.from(
                                node+" doesn't exist on "+resolvedPlugin.name()+". "+resolvedPlugin.name()+" actually doesn't have any children plugins.")
                        .causedBy("Attempting to fetch the plugin "+plugin)
                );
                return null;
            }

            Particle.Flux<? extends Plugin> newCurrent = resolvedPlugin.plugins().get(node);
            if(newCurrent == null) {
                client.send(Error.withSolution(
                            node+" doesn't exist on "+resolvedPlugin.name()+".",
                            "Available plugins are: "+String.join(", "+resolvedPlugin.plugins().keySet())
                    )
                    .causedBy("Attempting to fetch the plugin "+plugin)
                );
                return null;
            }
            if(!newCurrent.exists() && !isLast) {
                client.send(Error.withHint(
                                "Despite existing and being correct; "+node+" is not currently available. It's probably rebooting.",
                                "This issue typically occurs when a plugin is restarting. You can try again after a little bit, or try reloading the plugin directly and see if that works."
                        )
                        .causedBy("Attempting to fetch the plugin "+plugin)
                );
                return null;
            }

            current.set(newCurrent);
        }

        return current.get();
    }

    @Command("error")
    @Command("errors")
    public void nglbwzmxvchdjaon() {
        RC.Adapter().log(
                Component.join(
                        CommonLang.newlines(),
                        Component.empty(),
                        RC.Lang().asciiAlphabet().generate("Errors").color(NamedTextColor.BLUE),
                        Component.empty(),
                        (
                            RC.Errors().fetchAll().isEmpty() ?
                                text("There are no errors to show.", NamedTextColor.DARK_GRAY)
                            :
                                Component.join(
                                    CommonLang.newlines(),
                                    RC.Errors().fetchAll().stream().map(e->Component.join(
                                            CommonLang.newlines(),
                                            text("------------------------------------------------------", NamedTextColor.DARK_GRAY),
                                            e.toComponent()
                                    )).toList()
                                )
                        ),
                        Component.empty()
                )
        );
    }

    @Command("error <uuid>")
    @Command("errors <uuid>")
    public void nglbwzmxvchdjaon(Client<?> client, String uuid) {
        try {
            UUID errorUUID;
            try {
                errorUUID = UUID.fromString(uuid);
            } catch (IllegalArgumentException e) {
                client.send(text("Please provide a valid UUID.", NamedTextColor.BLUE));
                return;
            }

            Error error = RC.Errors().fetch(errorUUID)
                    .orElseThrow(()->new NoSuchElementException("No Error entry exists with the uuid ["+uuid+"]"));
            if(error.throwable() == null) client.send(text("The error ["+uuid+"] doesn't have a throwable to inspect.", NamedTextColor.BLUE));
            RC.Adapter().log(RC.Lang("rustyconnector-exception").generate(error.throwable()));
        } catch (Exception e) {
            RC.Error(Error.from(e).urgent(true));
        }
    }

    @Command("packet")
    @Command("packets")
    public void yckarhhyoblbmbdl(Client<?> client) {
        try {
            List<Packet> messages = RC.MagicLink().packetCache().packets();
            client.send(RC.Lang("rustyconnector-packets").generate(messages));
        } catch (Exception e) {
            RC.Error(Error.from(e).urgent(true));
        }
    }

    @Command("packet clear")
    @Command("packets clear")
    public void wuifhmwefmhuidid(Client<?> client) {
        try {
            client.send(RC.Lang("rustyconnector-waiting").generate());
            RC.MagicLink().packetCache().empty();
            client.send(RC.Lang("rustyconnector-finished").generate());
        } catch (Exception e) {
            RC.Error(Error.from(e).urgent(true));
        }
    }

    @Command("packet <id>")
    @Command("packets <id>")
    public void nidbtmkngikxlzyo(Client<?> client, String id) {
        try {
            client.send(RC.Lang("rustyconnector-packetDetails").generate(
                    RC.MagicLink().packetCache().find(NanoID.fromString(id)).orElseThrow(
                            ()->new NoSuchElementException("Unable to find packet with id "+id)
                    )
            ));
        } catch (Exception e) {
            RC.Error(Error.from(e).urgent(true));
        }
    }

    @Command("send")
    public void acmednrmiufxxviz(Client<?> client) {
        client.send(RC.Lang("rustyconnector-sendUsage").generate());
    }
    @Command("send <playerTarget>")
    public void acmednrmiusgxviz(Client<?> client, String playerTarget) {
        acmednrmiufxxviz(client);
    }
}