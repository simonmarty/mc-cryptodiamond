package net.cryptodiamond;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.MessageType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

import java.util.UUID;


public class Client implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) ->
                dispatcher.register(CommandManager.literal("balance").executes(context -> {
                    System.out.println(context.getSource().getPlayer().getUuid() + "Requested balance");

                    MinecraftClient.getInstance().inGameHud.addChatMessage(
                            MessageType.CHAT,
                            Text.of("Your CryptoDiamond balance is: 0 CD"),
                            UUID.randomUUID()
                    );
                    return 1;
                })));

    }
}
