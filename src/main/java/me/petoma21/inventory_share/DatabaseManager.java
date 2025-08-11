package me.petoma21.inventory_share;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final Inventory_Share plugin;
    private Connection connection;
    private String dbType; // "mysql" or "mariadb"

    public DatabaseManager(Inventory_Share plugin) {
        this.plugin = plugin;
    }

    public boolean connect() {
        Config config = plugin.getPluginConfig();
        this.dbType = config.getDbType().toLowerCase(); // 設定からデータベースタイプを取得

        String url = buildConnectionUrl(config);

        try {
            // データベースタイプに応じてドライバーを選択
            if ("mariadb".equals(dbType)) {
                // MariaDB専用ドライバーを試す
                try {
                    Class.forName("org.mariadb.jdbc.Driver");
                    plugin.getLogger().info("MariaDB JDBCドライバーを使用します");
                } catch (ClassNotFoundException e) {
                    // MariaDBドライバーが見つからない場合はMySQLドライバーを使用
                    plugin.getLogger().warning("MariaDB JDBCドライバーが見つかりません。MySQL JDBCドライバーを使用します");
                    Class.forName("com.mysql.cj.jdbc.Driver");
                }
            } else {
                // MySQLドライバーを使用
                Class.forName("com.mysql.cj.jdbc.Driver");
                plugin.getLogger().info("MySQL JDBCドライバーを使用します");
            }

            connection = DriverManager.getConnection(url, config.getDbUser(), config.getDbPassword());
            createTables();
            plugin.getLogger().info("データベース接続に成功しました (" + dbType + ")");
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "データベース接続エラー: " + e.getMessage(), e);
            return false;
        }
    }

    private String buildConnectionUrl(Config config) {
        String baseUrl;

        if ("mariadb".equals(dbType)) {
            baseUrl = "jdbc:mariadb://" + config.getDbHost() + ":" + config.getDbPort() + "/" + config.getDbName();
        } else {
            baseUrl = "jdbc:mysql://" + config.getDbHost() + ":" + config.getDbPort() + "/" + config.getDbName();
        }

        // 共通パラメータ
        String params = "?useSSL=" + config.isDbUseSSL() +
                "&useUnicode=true&characterEncoding=utf8" +
                "&serverTimezone=UTC" + // タイムゾーン設定
                "&allowPublicKeyRetrieval=true"; // 公開鍵の取得を許可

        return baseUrl + params;
    }

    public void reconnect() {
        disconnect();
        connect();
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("データベース接続を閉じました");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "データベース切断エラー: " + e.getMessage(), e);
        }
    }

    public boolean isConnectionValid() {
        try {
            if (connection == null || connection.isClosed()) {
                return false;
            }

            // タイムアウト5秒で接続の有効性をチェック
            return connection.isValid(5);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "接続チェックエラー: " + e.getMessage(), e);
            return false;
        }
    }

    public boolean ensureConnection() {
        try {
            if (!isConnectionValid()) {
                plugin.getLogger().log(Level.INFO, "データベース接続が無効なため、再接続を試みます。");
                return connect();
            }
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "データベース再接続エラー: " + e.getMessage(), e);
            return false;
        }
    }

    private void createTables() throws SQLException {
        // データベースエンジンの設定（MariaDBとMySQLで互換性を保つ）
        String engineClause = "";
        if ("mariadb".equals(dbType)) {
            engineClause = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        } else {
            engineClause = " ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci";
        }

        try (Statement statement = connection.createStatement()) {
            // インベントリテーブル
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS inventory_data (" +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "server_group VARCHAR(64) NOT NULL, " +
                            "inventory LONGTEXT, " +
                            "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                            "PRIMARY KEY (uuid, server_group)" +
                            ")" + engineClause
            );

            // エンダーチェストテーブル
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS enderchest_data (" +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "server_group VARCHAR(64) NOT NULL, " +
                            "enderchest LONGTEXT, " +
                            "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                            "PRIMARY KEY (uuid, server_group)" +
                            ")" + engineClause
            );

            // 経済データテーブル
            statement.executeUpdate(
                    "CREATE TABLE IF NOT EXISTS economy_data (" +
                            "uuid VARCHAR(36) NOT NULL, " +
                            "server_group VARCHAR(64) NOT NULL, " +
                            "balance DECIMAL(16,2) DEFAULT 0, " +
                            "last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                            "PRIMARY KEY (uuid, server_group)" +
                            ")" + engineClause
            );

            plugin.getLogger().info("データベーステーブルの作成/確認が完了しました");
        }
    }

    public String getDbType() {
        return dbType;
    }

    public String getDatabaseInfo() {
        try {
            if (connection != null && !connection.isClosed()) {
                DatabaseMetaData metaData = connection.getMetaData();
                return metaData.getDatabaseProductName() + " " + metaData.getDatabaseProductVersion();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "データベース情報の取得エラー: " + e.getMessage(), e);
        }
        return "Unknown";
    }

    public void saveInventory(UUID playerUUID, String serverGroup, ItemStack[] inventory) {
        try {
            if (!ensureConnection()) {
                plugin.getLogger().log(Level.SEVERE, "データベース接続が確立できないため、インベントリの保存に失敗しました。");
                return;
            }

            String serializedInventory = itemStackArrayToBase64(inventory);

            String sql = "INSERT INTO inventory_data (uuid, server_group, inventory) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE inventory = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, serverGroup);
                statement.setString(3, serializedInventory);
                statement.setString(4, serializedInventory);
                statement.executeUpdate();
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリ保存エラー: " + e.getMessage(), e);
        }
    }

    public ItemStack[] loadInventory(UUID playerUUID, String serverGroup) {
        try {
            if (!ensureConnection()) {
                plugin.getLogger().log(Level.SEVERE, "データベース接続が確立できないため、インベントリの読み込みに失敗しました。");
                return null;
            }

            String sql = "SELECT inventory FROM inventory_data WHERE uuid = ? AND server_group = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, serverGroup);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String serializedInventory = resultSet.getString("inventory");
                        return itemStackArrayFromBase64(serializedInventory);
                    }
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "インベントリ読み込みエラー: " + e.getMessage(), e);
        }

        return null;
    }

    public void saveEnderChest(UUID playerUUID, String serverGroup, ItemStack[] enderChest) {
        try {
            if (!ensureConnection()) {
                plugin.getLogger().log(Level.SEVERE, "データベース接続が確立できないため、エンダーチェストの保存に失敗しました。");
                return;
            }

            String serializedEnderChest = itemStackArrayToBase64(enderChest);

            String sql = "INSERT INTO enderchest_data (uuid, server_group, enderchest) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE enderchest = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, serverGroup);
                statement.setString(3, serializedEnderChest);
                statement.setString(4, serializedEnderChest);
                statement.executeUpdate();
            }
        } catch (SQLException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "エンダーチェスト保存エラー: " + e.getMessage(), e);
        }
    }

    public ItemStack[] loadEnderChest(UUID playerUUID, String serverGroup) {
        try {
            if (!ensureConnection()) {
                plugin.getLogger().log(Level.SEVERE, "データベース接続が確立できないため、エンダーチェストの読み込みに失敗しました。");
                return null;
            }

            String sql = "SELECT enderchest FROM enderchest_data WHERE uuid = ? AND server_group = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, serverGroup);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        String serializedEnderChest = resultSet.getString("enderchest");
                        return itemStackArrayFromBase64(serializedEnderChest);
                    }
                }
            }
        } catch (SQLException | IOException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "エンダーチェスト読み込みエラー: " + e.getMessage(), e);
        }

        return null;
    }

    public void saveEconomy(UUID playerUUID, String serverGroup, double balance) {
        try {
            if (!ensureConnection()) {
                plugin.getLogger().log(Level.SEVERE, "データベース接続が確立できないため、経済データの保存に失敗しました。");
                return;
            }

            String sql = "INSERT INTO economy_data (uuid, server_group, balance) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE balance = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, serverGroup);
                statement.setDouble(3, balance);
                statement.setDouble(4, balance);
                statement.executeUpdate();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "経済データ保存エラー: " + e.getMessage(), e);
        }
    }

    public double loadEconomy(UUID playerUUID, String serverGroup) {
        try {
            if (!ensureConnection()) {
                plugin.getLogger().log(Level.SEVERE, "データベース接続が確立できないため、経済データの読み込みに失敗しました。");
                return 0.0;
            }

            String sql = "SELECT balance FROM economy_data WHERE uuid = ? AND server_group = ?";

            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, playerUUID.toString());
                statement.setString(2, serverGroup);

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        return resultSet.getDouble("balance");
                    }
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "経済データ読み込みエラー: " + e.getMessage(), e);
        }

        return 0.0;
    }

    private String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);

            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }

            return Base64Coder.encodeLines(outputStream.toByteArray());
        }
    }

    private ItemStack[] itemStackArrayFromBase64(String data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            ItemStack[] items = new ItemStack[dataInput.readInt()];

            for (int i = 0; i < items.length; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }

            return items;
        }
    }
}