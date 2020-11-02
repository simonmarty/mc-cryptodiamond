package net.cryptodiamond;

import com.mojang.brigadier.Command;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.network.ClientSidePacketRegistry;
import net.fabricmc.fabric.api.network.ServerSidePacketRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.MessageType;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;

import static org.sqlite.core.Codes.SQLITE_CONSTRAINT;

public class CDController {
    static final Identifier DEPOSIT = new Identifier("cryptodiamond-deposit");
    static final Identifier WITHDRAW = new Identifier("cryptodiamond-withdraw");

    static final Command<ServerCommandSource> depositCommand = context -> {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeLong(5);
        ClientSidePacketRegistry.INSTANCE.sendToServer(CDController.DEPOSIT, buf);
        return 1;
    };

    static final Command<ServerCommandSource> balanceCommand = context -> {
        String player = context.getSource().getPlayer().getUuidAsString();
        System.out.println(player + "Requested balance");
        BigDecimal balance = getBalance(player);
        System.out.println(balance.toString());
        sendMessage("Balance: " + balance.toString() + " CD");
        return 1;
    };

    static final Command<ServerCommandSource> hasDiamondsCommand = context -> {
        if (context.getSource().getPlayer().inventory.contains(new ItemStack(Items.DIAMOND))) {
            sendMessage("Inventory contains diamonds");
        } else {
            sendMessage("Inventory does not contains diamonds");
        }
        return 1;
    };

    public static void setupDB() {
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:cryptodiamond.db");
            Statement s = connection.createStatement();
            s.executeUpdate("CREATE TABLE IF NOT EXISTS Accounts ( UUID TEXT PRIMARY KEY NOT NULL, Balance DECIMAL DEFAULT 0)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS Transactions (ID INT PRIMARY KEY NOT NULL," +
                    "Sender TEXT NOT NULL," +
                    "Receiver TEXT NOT NULL," +
                    "Amount DECIMAL DEFAULT 0," +
                    "Time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(Sender) REFERENCES Accounts(UUID)," +
                    "FOREIGN KEY(Receiver) REFERENCES Accounts(UUID))");
            s.close();
            System.out.println("CryptoDiamond DB set up");
        } catch (SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
    }

    private static void sendMessage(String s) {
        MinecraftClient.getInstance().inGameHud.addChatMessage(
                MessageType.CHAT,
                Text.of(s),
                UUID.randomUUID()
        );
    }

    public static void registerPackets() {
        ServerSidePacketRegistry registry = ServerSidePacketRegistry.INSTANCE;
        registry.register(CDController.DEPOSIT, ((packetContext, packetByteBuf) -> {
            int numDiamonds = packetByteBuf.readInt();
            String player = packetContext.getPlayer().getUuidAsString();
            packetContext.getTaskQueue().execute(() -> System.out.println("Player " + player + "wants to deposit " + numDiamonds + " diamonds"));
        }));

        registry.register(CDController.WITHDRAW, (packetContext, packetByteBuf) -> {
            BigDecimal numDiamonds = BigDecimal.valueOf(packetByteBuf.readLong());
            String player = packetContext.getPlayer().getUuidAsString();

            if (getBalance(player).compareTo(numDiamonds) < 0) return;
            packetContext.getTaskQueue().execute(() -> System.out.println("Player " + player + "wants to withdraw " + numDiamonds + " diamonds"));
        });
    }

    private static BigDecimal getBalance(String player) {

        try {
            createAccountIfNotExists(player);
            Connection connection = DriverManager.getConnection("jdbc:sqlite:cryptodiamond.db");
            PreparedStatement s = connection.prepareStatement("SELECT Balance FROM Accounts WHERE UUID = ?");
            s.setString(1, player);
            ResultSet r = s.executeQuery();
            return r.getBigDecimal(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.valueOf(-1L);
    }

    private static void createAccountIfNotExists(String player) {
        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:cryptodiamond.db");
            PreparedStatement s = connection.prepareStatement("INSERT INTO Accounts (UUID) VALUES (?)");
            s.setString(1, player);
            s.execute();
        } catch (SQLException e) {
            if (e.getErrorCode() == SQLITE_CONSTRAINT) return; // Fail gracefully if account already exists
            e.printStackTrace();
        }
    }
}
