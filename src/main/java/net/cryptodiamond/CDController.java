package net.cryptodiamond;

import com.mojang.brigadier.Command;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;

import static org.sqlite.core.Codes.SQLITE_CONSTRAINT;

public class CDController {
    private static PreparedStatement createAccountStatement, getBalanceStatement, updateBalanceStatement, addTransactionStatement;
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
        BigInteger amountDeposited = deposit(player, numDiamonds);
        sendMessage(player, "Deposited " + amountDeposited + " CD");
        return 1;
    };
    static final Command<ServerCommandSource> withdrawCommand = context -> {
        int numDiamonds = 4;
        PlayerEntity player = context.getSource().getPlayer();
        System.out.println("Player " + player.getName().asString() + " wants to withdraw " + numDiamonds + " diamonds");
        BigInteger amountWithdrawn = withdraw(player, numDiamonds);
        sendMessage(player, amountWithdrawn + " diamonds have been withdrawn and placed in your inventory");
        return 1;
    };
    static final Command<ServerCommandSource> sendCommand = context -> {
        PlayerEntity sender = context.getSource().getPlayer();
        PlayerEntity receiver = context.getArgument("receiver", PlayerEntity.class);
        BigDecimal amount = context.getArgument("amount", BigDecimal.class);
        System.out.println("Player " + sender.getName().asString() + " wants to send " + amount + " CD to " + receiver.getName().asString());
        BigDecimal amountSent = transfer(sender, receiver, amount);
        sendMessage(sender, "Sent " + amountSent + " CD to " + receiver.getName().asString());
        return 1;
    };

    public static void setupDB() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:cryptodiamond.db");
            connection.setAutoCommit(false);

            Statement createTables = connection.createStatement();
            createTables.addBatch("CREATE TABLE IF NOT EXISTS Accounts ( UUID TEXT PRIMARY KEY NOT NULL, Balance DECIMAL DEFAULT 0)");
            createTables.addBatch("CREATE TABLE IF NOT EXISTS Transactions (ID INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "Sender TEXT NOT NULL, " +
                    "Receiver TEXT NOT NULL, " +
                    "Amount DECIMAL DEFAULT 0, " +
                    "Time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(Sender) REFERENCES Accounts(UUID)," +
                    "FOREIGN KEY(Receiver) REFERENCES Accounts(UUID))");
            createTables.executeBatch();
            connection.commit();

            getBalanceStatement = connection.prepareStatement("SELECT Balance FROM Accounts WHERE UUID = ?");
            createAccountStatement = connection.prepareStatement("INSERT INTO Accounts (UUID) VALUES (?)");
            updateBalanceStatement = connection.prepareStatement("UPDATE Accounts SET Balance = ? WHERE UUID = ?");
            addTransactionStatement = connection.prepareStatement("INSERT INTO Transactions (Sender, Receiver, Amount) VALUES (?, ?, ?)");

            System.out.println("CryptoDiamond DB set up");
        } catch (SQLException e) {
            if (connection != null) {
                System.out.println("Transaction is being rolled back");
                try {
                    connection.rollback();
                } catch (SQLException e2) {
                    e2.printStackTrace();
                }
            }
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
            getBalanceStatement.setString(1, player.getUuidAsString());
            ResultSet r = getBalanceStatement.executeQuery();
            if (r.next()) {
                return r.getBigDecimal(1);
            } else {
                return BigDecimal.ONE.negate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigDecimal.ONE.negate();
    }

    private static void createAccountIfNotExists(PlayerEntity player) {
        try {
            createAccountStatement.setString(1, player.getUuidAsString());
            createAccountStatement.executeUpdate();
            connection.commit();
        } catch (SQLException e) {
            if (e.getErrorCode() == SQLITE_CONSTRAINT) return; // Fail gracefully if account already exists
            e.printStackTrace();
        }
    }

    private static BigInteger deposit(PlayerEntity player, int numDiamonds) {
        try {
            if (numDiamonds > player.inventory.count(Items.DIAMOND)) return BigInteger.ZERO;

            BigDecimal balance = getBalance(player);
            balance = balance.add(BigDecimal.valueOf(numDiamonds));
            updateBalanceStatement.setBigDecimal(1, balance);
            updateBalanceStatement.setString(2, player.getUuidAsString());
            updateBalanceStatement.execute();
            connection.commit();

            int numTaken = player.inventory.remove(itemStack -> itemStack.isItemEqual(new ItemStack(Items.DIAMOND)), numDiamonds, player.inventory);
            if (numTaken != numDiamonds) {
                connection.rollback();
                player.giveItemStack(new ItemStack(Items.DIAMOND, numTaken));
            }
            return BigInteger.valueOf(numDiamonds);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigInteger.ZERO;
    }

    private static BigInteger withdraw(PlayerEntity player, int numDiamonds) {
        try {
            BigDecimal balance = getBalance(player);
            BigInteger numToWithdraw = balance.toBigInteger().min(BigInteger.valueOf(numDiamonds));
            balance = balance.add(new BigDecimal(numToWithdraw).negate());
            updateBalanceStatement.setBigDecimal(1, balance);
            updateBalanceStatement.setString(2, player.getUuidAsString());
            updateBalanceStatement.executeUpdate();
            connection.commit();

            if (!player.giveItemStack(new ItemStack(Items.DIAMOND, numToWithdraw.intValue()))) {
                connection.rollback();
            }
            return BigInteger.valueOf(numDiamonds);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return BigInteger.ZERO;
    }

    private static BigDecimal transfer(PlayerEntity sender, PlayerEntity receiver, BigDecimal amount) {
        if (amount.equals(BigDecimal.ZERO)) return BigDecimal.ZERO;

        try {
            BigDecimal senderBalance = getBalance(sender);
            BigDecimal receiverBalance = getBalance(receiver);
            senderBalance = senderBalance.add(amount.negate());
            receiverBalance = receiverBalance.add(amount);
            if (senderBalance.compareTo(BigDecimal.ZERO) < 0) {
                return BigDecimal.ZERO;
            }

            String senderUuid = sender.getUuidAsString();
            String receiverUuid = receiver.getUuidAsString();

            updateBalanceStatement.setBigDecimal(1, senderBalance);
            updateBalanceStatement.setString(2, senderUuid);
            updateBalanceStatement.executeUpdate();

            updateBalanceStatement.setBigDecimal(1, receiverBalance);
            updateBalanceStatement.setString(2, senderUuid);
            updateBalanceStatement.executeUpdate();

            addTransactionStatement.setString(1, senderUuid);
            addTransactionStatement.setString(2, receiverUuid);
            addTransactionStatement.setBigDecimal(3, amount);
            addTransactionStatement.executeUpdate();

            connection.commit();
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException e2) {
                e2.printStackTrace();
            }
            e.printStackTrace();
        }

        return BigDecimal.ZERO;
    }
}
