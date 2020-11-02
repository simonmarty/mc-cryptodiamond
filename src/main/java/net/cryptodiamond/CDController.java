package net.cryptodiamond;

import com.mojang.brigadier.Command;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;

import java.math.BigDecimal;
import java.sql.*;

import static org.sqlite.core.Codes.SQLITE_CONSTRAINT;

public class CDController {

    static final Command<ServerCommandSource> hasDiamondsCommand = context -> {
        ServerPlayerEntity player = context.getSource().getPlayer();
        if (player.inventory.contains(new ItemStack(Items.DIAMOND))) {
            sendMessage(player, "Inventory contains diamonds");
        } else {
            sendMessage(player, "Inventory does not contains diamonds");
        }
        return 1;
    };
    private static Connection connection;
    static final Command<ServerCommandSource> balanceCommand = context -> {
        PlayerEntity player = context.getSource().getPlayer();
        System.out.println(player.getName().asString() + "Requested balance");
        BigDecimal balance = getBalance(player);
        sendMessage(context.getSource().getPlayer(), "Balance: " + balance + " CD");
        return 1;
    };
    static final Command<ServerCommandSource> depositCommand = context -> {
        int numDiamonds = 5;
        PlayerEntity player = context.getSource().getPlayer();
        System.out.println("Player " + player.getName().asString() + " wants to deposit " + numDiamonds + " diamonds");
        BigDecimal amountDeposited = deposit(player, numDiamonds);
        sendMessage(player, "Deposited " + amountDeposited + " CD");
        return 1;
    };
    static final Command<ServerCommandSource> withdrawCommand = context -> {
        int numDiamonds = 4;
        PlayerEntity player = context.getSource().getPlayer();
        System.out.println("Player " + player.getName().asString() + " wants to withdraw " + numDiamonds + " diamonds");
        BigDecimal amountWithdrawn = withdraw(player, numDiamonds);
        sendMessage(player, amountWithdrawn + " diamonds have been withdrawn and placed in your inventory");
        return 1;
    };

    static {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:cryptodiamond.db");
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    }

    public static void setupDB() {
        try {
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

    private static void sendMessage(PlayerEntity player, String s) {
        player.sendMessage(new LiteralText(s), false);
    }

    private static BigDecimal getBalance(PlayerEntity player) {

        try {
            createAccountIfNotExists(player);
            PreparedStatement s = connection.prepareStatement("SELECT Balance FROM Accounts WHERE UUID = ?");
            s.setString(1, player.getUuidAsString());
            ResultSet r = s.executeQuery();
            return r.getBigDecimal(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.valueOf(-1L);
    }

    private static void createAccountIfNotExists(PlayerEntity player) {
        try {
            PreparedStatement s = connection.prepareStatement("INSERT INTO Accounts (UUID) VALUES (?)");
            s.setString(1, player.getUuidAsString());
            s.execute();
            s.close();
        } catch (SQLException e) {
            if (e.getErrorCode() == SQLITE_CONSTRAINT) return; // Fail gracefully if account already exists
            e.printStackTrace();
        }
    }

    private static BigDecimal deposit(PlayerEntity player, int numDiamonds) {
        try {
            if (numDiamonds > player.inventory.count(Items.DIAMOND)) return BigDecimal.ZERO;
            player.inventory.remove(itemStack -> itemStack.isItemEqual(new ItemStack(Items.DIAMOND)), numDiamonds, player.inventory);

            BigDecimal balance = getBalance(player);
            balance = balance.add(BigDecimal.valueOf(numDiamonds));
            PreparedStatement s = connection.prepareStatement("UPDATE Accounts SET Balance = ? WHERE UUID = ?");
            s.setBigDecimal(1, balance);
            s.setString(2, player.getUuidAsString());
            s.execute();
            s.close();
            return BigDecimal.valueOf(numDiamonds);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal withdraw(PlayerEntity player, int numDiamonds) {
        try {
            BigDecimal balance = getBalance(player);
            if (balance.compareTo(BigDecimal.valueOf(numDiamonds)) < 0) return BigDecimal.ZERO;
            balance = balance.add(BigDecimal.valueOf(numDiamonds).negate());
            PreparedStatement s = connection.prepareStatement("UPDATE Accounts SET Balance = ? WHERE UUID = ?");
            s.setBigDecimal(1, balance);
            s.setString(2, player.getUuidAsString());
            s.execute();
            player.giveItemStack(new ItemStack(Items.DIAMOND, numDiamonds));
            s.close();
            return BigDecimal.valueOf(numDiamonds);
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
        return BigDecimal.ZERO;
    }
}
