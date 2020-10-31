package net.cryptodiamond;

import net.fabricmc.api.DedicatedServerModInitializer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Server implements DedicatedServerModInitializer {

    @Override
    public void onInitializeServer() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        try {
            Connection connection = DriverManager.getConnection("jdbc:sqlite:cryptodiamond.db");
            Statement s = connection.createStatement();
            s.executeUpdate("CREATE TABLE IF NOT EXISTS Accounts ( ID INT PRIMARY KEY NOT NULL, UUID TEXT NOT NULL, Balance DECIMAL DEFAULT 0)");
            s.executeUpdate("CREATE TABLE IF NOT EXISTS Transactions (ID INT PRIMARY KEY NOT NULL," +
                    "Sender TEXT NOT NULL," +
                    "Receiver TEXT NOT NULL," +
                    "Amount DECIMAL DEFAULT 0," +
                    "Time DATETIME DEFAULT CURRENT_TIMESTAMP," +
                    "FOREIGN KEY(Sender) REFERENCES Accounts(UUID)," +
                    "FOREIGN KEY(Receiver) REFERENCES Accounts(UUID))");
            s.close();
            System.out.println("CryptoDiamond set up");
        } catch (SQLException e) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }
    }
}
