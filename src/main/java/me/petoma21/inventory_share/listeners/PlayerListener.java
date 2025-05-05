package me.petoma21.inventory_share.listeners;

import me.petoma21.inventory_share.Inventory_Share;
import org.bukkit.Sound;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.EntityPickupItemEvent; // アイテム拾得イベント
import org.bukkit.event.inventory.InventoryClickEvent; // インベントリ操作イベント
import org.bukkit.event.player.PlayerDropItemEvent; // アイテムドロップイベント
import org.bukkit.event.inventory.InventoryCloseEvent; // インベントリクローズイベント
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;

public class PlayerListener implements Listener {
    private final Inventory_Share plugin;

    // 同期処理中のプレイヤーを追跡するためのセット
    public static Set<UUID> syncingPlayers = new HashSet<>();

    // インベントリをクリアしたプレイヤーの一時的なバックアップを保存
    private final Map<UUID, Map<String, Object>> playerBackups = new HashMap<>();

    public PlayerListener(Inventory_Share plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤーがサーバーに参加したときのイベント
     * 他のサーバーで保存されたインベントリを非同期でロードし、メインスレッドで適用
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();
        final UUID playerUUID = player.getUniqueId();

        // 同期処理中にマーク
        syncingPlayers.add(playerUUID);

        // プレイヤーの現在のインベントリをバックアップ（ここではほとんどの場合空になるはず）
//        backupPlayerData(player);

        // 最初にプレイヤーのインベントリをクリア（無限増殖バグ防止）
        clearPlayerInventory(player);
        player.sendMessage("§2[AIS] §aプレイヤーデータを同期中... 動かずにお待ちください");

        // ログイン時はわずかに遅らせてインベントリを同期
        // 他のプラグインによるインベントリ操作の影響を避けるため
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    // プレイヤーがすでにオフラインの場合は同期フラグを解除
                    syncingPlayers.remove(playerUUID);
                    // バックアップを削除（もう必要ない）
                    playerBackups.remove(playerUUID);
                    return;
                }

                // 非同期でデータを読み込む
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    // データ格納用のマップ
                    final Map<String, Object> playerData = new HashMap<>();

                    try {
                        // インベントリデータを読み込み
                        Object inventoryData = plugin.getInventoryManager().loadPlayerInventoryData(playerUUID);
                        if (inventoryData != null) {
                            playerData.put("inventory", inventoryData);
                        } else {
                            plugin.getLogger().warning("Player " + player.getName() + " has no inventory data.");
                        }

                        // エンダーチェストデータを読み込み（設定で有効な場合のみ）
                        if (plugin.getServerSpecificConfig("sync-enderchest", true)) {
                            Object enderChestData = plugin.getEnderChestManager().loadPlayerEnderChestData(playerUUID);
                            if (enderChestData != null) {
                                playerData.put("enderchest", enderChestData);
                            } else {
                                plugin.getLogger().warning("Player " + player.getName() + " has no enderchest data.");
                            }
                        }

                        // 所持金データを読み込み
                        if (plugin.getEconomyManager().isEconomyEnabled()) {
                            Object economyData = plugin.getEconomyManager().loadPlayerBalanceData(playerUUID);
                            if (economyData != null) {
                                playerData.put("economy", economyData);
                            } else {
                                plugin.getLogger().warning("Player " + player.getName() + " has no economy data.");
                            }
                        }

                        // データが全く読み込めなかった場合
                        if (playerData.isEmpty()) {
                            plugin.getLogger().warning("No data found for player " + player.getName() + ". This might be their first login.");
                        }

                        // メインスレッドに戻ってデータを適用
                        final boolean hasData = !playerData.isEmpty();
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            try {
                                if (!player.isOnline()) {
                                    // プレイヤーがすでにオフラインの場合は処理しない
                                    syncingPlayers.remove(playerUUID);
                                    playerBackups.remove(playerUUID);
                                    return;
                                }

                                // データが読み込めた場合のみ適用する
                                if (hasData) {
                                    // item全ドロップ処理
                                    dropAllItems(player);
                                    // インベントリを適用
                                    if (playerData.containsKey("inventory")) {
                                        plugin.getInventoryManager().applyInventoryToPlayer(player, playerData.get("inventory"));
                                    }

                                    // エンダーチェストを適用
                                    if (playerData.containsKey("enderchest")) {
                                        plugin.getEnderChestManager().applyEnderChestToPlayer(player, playerData.get("enderchest"));
                                    }

                                    // 所持金を適用
                                    if (playerData.containsKey("economy")) {
                                        plugin.getEconomyManager().applyBalanceToPlayer(player, playerData.get("economy"));
                                    }
                                } else {
                                    // データが読み込めなかった場合、バックアップがあれば復元する
                                    restorePlayerDataFromBackup(player);
                                }

                                // バックアップは不要になるので削除
                                playerBackups.remove(playerUUID);

                                // 同期処理完了のマークを解除
                                syncingPlayers.remove(playerUUID);
                                player.sendMessage("§2[AIS] §aデータ同期完了!");

                                // 同期完了サウンドを再生（設定で有効な場合のみ）
                                playCompletionSound(player);
                            } catch (Exception e) {
                                // エラー時はバックアップから復元を試みる
                                restorePlayerDataFromBackup(player);

                                // 同期フラグを解除
                                syncingPlayers.remove(playerUUID);
                                playerBackups.remove(playerUUID);
                                player.sendMessage("§2[AIS] §cデータ同期中にエラーが発生しました。スタッフに報告してください！");
                                plugin.getLogger().warning("Error applying player data for " + player.getName() + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                        });
                    } catch (Exception e) {
                        // エラーが発生した場合、メインスレッドでエラーメッセージを表示
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            // エラー時はバックアップから復元を試みる
                            restorePlayerDataFromBackup(player);

                            // エラー時も同期フラグを解除
                            syncingPlayers.remove(playerUUID);
                            playerBackups.remove(playerUUID);
                            if (player.isOnline()) {
                                player.sendMessage("§2[AIS] §cデータ同期中にエラーが発生しました。スタッフに報告してください！");
                            }
                            plugin.getLogger().warning("Error loading player data for " + player.getName() + ": " + e.getMessage());
                            e.printStackTrace();
                        });
                    }
                });
            }
            // 同期開始までの待ち時間
        }.runTaskLater(plugin, 30L);
    }

    /**
     * 同期完了時にサウンドを再生する
     * configで設定された値に基づいて再生
     */
    private void playCompletionSound(Player player) {
        // サウンド設定が有効かどうか確認
        if (plugin.getServerSpecificConfig("sync-completion-sound-enabled", true)) {
            try {
                // configから設定を取得
                String soundName = plugin.getServerSpecificConfig("sync-completion-sound", "ENTITY_PLAYER_LEVELUP");
                float volume = ((Double) plugin.getServerSpecificConfig("sync-completion-sound-volume", 1.0)).floatValue();
                float pitch = ((Double) plugin.getServerSpecificConfig("sync-completion-sound-pitch", 1.0)).floatValue();
                // 文字列からサウンド列挙型に変換
                Sound sound;
                try {
                    sound = Sound.valueOf(soundName);
                } catch (IllegalArgumentException e) {
                    // 無効なサウンド名の場合はデフォルトサウンドを使用
                    plugin.getLogger().warning("Invalid sound name in config: " + soundName + ". Using default sound.");
                    sound = Sound.ENTITY_PLAYER_LEVELUP;
                }

                // サウンドを再生
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (Exception e) {
                plugin.getLogger().warning("Error playing completion sound: " + e.getMessage());
            }
        }
    }

    /**
     * プレイヤーがサーバーから退出したときのイベント
     * インベントリ状態を非同期で保存（同期処理が完了している場合のみ）
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final String playerName = player.getName();
        final UUID playerUUID = player.getUniqueId();

        // 同期処理中であればデータの保存をスキップ
        if (syncingPlayers.contains(playerUUID)) {
            plugin.getLogger().info(playerName + " left during data synchronization. Skipping data save to prevent data loss.");
            // 同期フラグを解除
            syncingPlayers.remove(playerUUID);
            // バックアップも削除
            playerBackups.remove(playerUUID);
            return;
        }

        try {
            // インベントリデータを退出前に取得
            final ItemStack[] inventoryContents = player.getInventory().getContents().clone();
            final ItemStack[] armorContents = player.getInventory().getArmorContents().clone();
            final ItemStack offHandItem = player.getInventory().getItemInOffHand().clone();

            // エンダーチェストデータを退出前に取得
            final ItemStack[] enderChestContents = plugin.getServerSpecificConfig("sync-enderchest", true)
                    ? player.getEnderChest().getContents().clone() : null;

            // 所持金データを退出前に取得
            final double balance = plugin.getEconomyManager().isEconomyEnabled()
                    ? plugin.getEconomyManager().getPlayerBalance(player) : 0.0;

            // インベントリの内容をチェックして、空でない場合のみ保存
            boolean hasItems = false;
            if (inventoryContents != null) {
                for (ItemStack item : inventoryContents) {
                    if (item != null) {
                        hasItems = true;
                        break;
                    }
                }
            }

            if (!hasItems && armorContents != null) {
                for (ItemStack item : armorContents) {
                    if (item != null) {
                        hasItems = true;
                        break;
                    }
                }
            }

            if (!hasItems && offHandItem != null) {
                hasItems = true;
            }

            // アイテムがある場合のみ保存処理を実行
            if (hasItems) {
                // 非同期でデータを保存
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        // インベントリの保存
                        plugin.getInventoryManager().savePlayerInventoryData(playerUUID, inventoryContents, armorContents, offHandItem);

                        // エンダーチェストの保存（設定で有効な場合のみ）
                        if (plugin.getServerSpecificConfig("sync-enderchest", true) && enderChestContents != null) {
                            plugin.getEnderChestManager().savePlayerEnderChestData(playerUUID, enderChestContents);
                        }

                        // 所持金の保存
                        if (plugin.getEconomyManager().isEconomyEnabled()) {
                            plugin.getEconomyManager().savePlayerBalanceData(playerUUID, balance);
                        }

                        // ログに記録
                        plugin.getLogger().info(playerName + " player data save was successful!");
                    } catch (Exception e) {
                        plugin.getLogger().warning("An error occurred while saving " + playerName + " player data.: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            } else {
                plugin.getLogger().info(playerName + " has no items to save. Skipping save operation.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("An error occurred while preparing " + playerName + " player data for save: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * プレイヤーのインベントリとエンダーチェストをクリアする
     * 設定に基づいてエンダーチェストのクリアを制御
     */
    private void clearPlayerInventory(Player player) {
        // メインインベントリをクリア
        player.getInventory().clear();
        // 防具スロットをクリア
        player.getInventory().setArmorContents(new ItemStack[4]);
        // オフハンドをクリア
        player.getInventory().setItemInOffHand(null);

        // エンダーチェストをクリア（設定で有効な場合のみ）
        if (plugin.getServerSpecificConfig("sync-enderchest", true)) {
            player.getEnderChest().clear();
        }

        // インベントリの更新を強制
        player.updateInventory();
    }

    /**
     * プレイヤーのデータをバックアップする
     * クリア前にインベントリの状態を保存
     */
    private void backupPlayerData(Player player) {
        UUID playerUUID = player.getUniqueId();
        Map<String, Object> backup = new HashMap<>();

        // インベントリの保存
        if (player.getInventory() != null) {
            Map<String, ItemStack[]> inventoryBackup = new HashMap<>();
            inventoryBackup.put("contents", player.getInventory().getContents().clone());
            inventoryBackup.put("armor", player.getInventory().getArmorContents().clone());
            inventoryBackup.put("offhand", new ItemStack[]{player.getInventory().getItemInOffHand().clone()});
            backup.put("inventory", inventoryBackup);
        }

        // エンダーチェストの保存
        if (plugin.getServerSpecificConfig("sync-enderchest", true) && player.getEnderChest() != null) {
            backup.put("enderchest", player.getEnderChest().getContents().clone());
        }

        // 経済データの保存
        if (plugin.getEconomyManager().isEconomyEnabled()) {
            backup.put("economy", plugin.getEconomyManager().getPlayerBalance(player));
        }

        // バックアップを保存
        playerBackups.put(playerUUID, backup);
        plugin.getLogger().fine("Backup created for player: " + player.getName());
    }

    /**
     * バックアップからプレイヤーデータを復元する
     */
    private void restorePlayerDataFromBackup(Player player) {
        UUID playerUUID = player.getUniqueId();

        if (!playerBackups.containsKey(playerUUID)) {
            plugin.getLogger().warning("No backup found for player: " + player.getName());
            return;
        }

        Map<String, Object> backup = playerBackups.get(playerUUID);

        try {
            // インベントリの復元
            if (backup.containsKey("inventory")) {
                Map<String, ItemStack[]> inventoryBackup = (Map<String, ItemStack[]>) backup.get("inventory");

                // メインコンテンツの復元
                if (inventoryBackup.containsKey("contents")) {
                    player.getInventory().setContents(inventoryBackup.get("contents"));
                }

                // アーマーの復元
                if (inventoryBackup.containsKey("armor")) {
                    player.getInventory().setArmorContents(inventoryBackup.get("armor"));
                }

                // オフハンドの復元
                if (inventoryBackup.containsKey("offhand") && inventoryBackup.get("offhand").length > 0) {
                    player.getInventory().setItemInOffHand(inventoryBackup.get("offhand")[0]);
                }
            }

            // エンダーチェストの復元
            if (backup.containsKey("enderchest") && plugin.getServerSpecificConfig("sync-enderchest", true)) {
                player.getEnderChest().setContents((ItemStack[]) backup.get("enderchest"));
            }

            // 経済データの復元
            if (backup.containsKey("economy") && plugin.getEconomyManager().isEconomyEnabled()) {
                plugin.getEconomyManager().setPlayerBalance(player, (Double) backup.get("economy"));
            }

            // インベントリ更新を強制
            player.updateInventory();

            plugin.getLogger().info("Restored backup data for player: " + player.getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to restore backup for player: " + player.getName());
            e.printStackTrace();
        }
    }

    /**
     * 同期中にプレイヤーがアイテムを拾うのを防止する
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(EntityPickupItemEvent event) {
        // プレイヤーがアイテムを拾った場合のみ処理
        if (!(event.getEntity() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getEntity();
        UUID playerUUID = player.getUniqueId();

        // 同期中の場合はアイテム拾得をキャンセル
        if (syncingPlayers.contains(playerUUID)) {
            event.setCancelled(true);
        }
    }
//
//     * プレイヤーのインベントリ内のすべてのアイテムをドロップする
    public boolean dropAllItems(Player player) {
        // プレイヤーの場所を取得
        Location dropLocation = player.getLocation();
        boolean itemsFound = false;

        // インベントリ内のアイテムをすべてチェック
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                // アイテムをドロップ
                player.getWorld().dropItemNaturally(dropLocation, item);
                itemsFound = true;
            }
        }

        // アーマースロットのアイテムをチェック
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            if (armor != null && !armor.getType().isAir()) {
                player.getWorld().dropItemNaturally(dropLocation, armor);
                itemsFound = true;
            }
        }

        // オフハンドのアイテムをチェック
        ItemStack offhand = player.getInventory().getItemInOffHand();
        if (offhand != null && !offhand.getType().isAir()) {
            player.getWorld().dropItemNaturally(dropLocation, offhand);
            itemsFound = true;
        }

        // アイテムが見つかった場合、インベントリをクリアして通知
        if (itemsFound) {
            clearPlayerInventory(player);
            player.sendMessage("§2[AIS] §c同期中に所持したアイテムは足元にドロップされました。");
        }

        return itemsFound;
    }


}