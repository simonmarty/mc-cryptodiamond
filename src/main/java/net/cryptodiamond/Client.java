package net.cryptodiamond;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.MessageType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

import java.util.UUID;


public class Client implements ClientModInitializer {

    @Override
    public void onInitializeClient() {

        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
            LiteralCommandNode<ServerCommandSource> root = dispatcher.register(CommandManager.literal("cryptodiamond"));

            dispatcher.register(CommandManager.literal("cryptodiamond")
                    .then(CommandManager.literal("balance").redirect(root).executes(getBalance))
                    .then(CommandManager.literal("hasDiamonds").redirect(root).executes(hasDiamonds)));

        });
    }

    Command<ServerCommandSource> getBalance = context -> {
        System.out.println(context.getSource().getPlayer().getUuid() + "Requested balance");
        sendMessage("Balance: 0 CD");
        return 1;
    };

    Command<ServerCommandSource> hasDiamonds = context -> {
        if (context.getSource().getPlayer().inventory.contains(new ItemStack(Items.DIAMOND))) {
            sendMessage("Inventory contains diamonds");
        } else {
            sendMessage("Inventory does not contains diamonds");
        }

        return 1;
    };

    private void sendMessage(String s) {
        MinecraftClient.getInstance().inGameHud.addChatMessage(
                MessageType.CHAT,
                Text.of(s),
                UUID.randomUUID()
        );
    }
}
