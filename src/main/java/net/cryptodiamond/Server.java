package net.cryptodiamond;


import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;

import static net.cryptodiamond.CDController.*;


public class Server implements ModInitializer {


    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            LiteralCommandNode<ServerCommandSource> root = dispatcher.register(CommandManager.literal("cryptodiamond"));

            dispatcher.register(CommandManager.literal("cryptodiamond")
                    .then(CommandManager.literal("balance").redirect(root).executes(balanceCommand))
                    .then(CommandManager.literal("hasDiamonds").redirect(root).executes(hasDiamondsCommand))
                    .then(CommandManager.literal("deposit").redirect(root).executes(depositCommand)));

        });
        setupDB();
        registerPackets();
    }
}
