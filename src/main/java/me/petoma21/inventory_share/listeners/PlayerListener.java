package me.petoma21.inventory_share.listeners;

import me.petoma21.inventory_share.Inventory_Share;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class PlayerListener implements Listener {

    private final Inventory_Share plugin;

    public PlayerListener(Inventory_Share plugin) {
        this.plugin = plugin;
    }

    /**
     * プレイヤーがサーバーに参加したときのイベント
     * 他のサーバーで保存されたインベントリをロード
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        final Player player = event.getPlayer();

        // ロード前にインベントリをクリア（設定に基づいて）
        clearPlayerInventory(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    return;
                }

                player.sendMessage("§2[AIS] §aプレイヤーデータを同期中...");

                boolean syncSuccess = true;
                String failReason = "";

                try {
                    // インベントリのロード
                    boolean invSuccess = plugin.getInventoryManager().loadPlayerInventory(player);
                    if (!invSuccess) {
                        syncSuccess = false;
                        failReason += "インベントリ同期失敗 ";
                    }

                    // エンダーチェストのロード（有効な場合のみ）
                    if (plugin.getPluginConfig().isEnderChestEnabled()) {
                        boolean ecSuccess = plugin.getEnderChestManager().loadPlayerEnderChest(player);
                        if (!ecSuccess) {
                            syncSuccess = false;
                            failReason += "エンダーチェスト同期失敗 ";
                        }
                    }

                    // 所持金のロード
                    if (plugin.getEconomyManager().isEconomyEnabled()) {
                        boolean ecoSuccess = plugin.getEconomyManager().loadPlayerBalance(player);
                        if (!ecoSuccess) {
                            syncSuccess = false;
                            failReason += "経済データ同期失敗 ";
                        }
                    }

                    // 同期結果メッセージ
                    if (syncSuccess) {
                        player.sendMessage("§2[AIS] §aデータ同期完了!");
                    } else {
                        player.sendMessage("§4[AIS] §cデータ同期中にエラーが発生しました: " + failReason);
                        plugin.getLogger().warning(player.getName() + "のデータ同期中にエラーが発生: " + failReason);

                        // リトライ処理（2秒後に再試行）
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!player.isOnline()) return;

                                player.sendMessage("§6[AIS] §e同期を再試行しています...");
                                retrySync(player);
                            }
                        }.runTaskLater(plugin, 40L); // 2秒後に再試行
                    }
                } catch (Exception e) {
                    player.sendMessage("§4[AIS] §c同期エラー: データベース接続を確認してください");
                    plugin.getLogger().severe("プレイヤーデータ同期中に重大なエラーが発生: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }.runTaskLater(plugin, 20L);
    }

    /**
     * 同期処理のリトライを実行
     */
    private void retrySync(Player player) {
        try {
            // データベース接続再チェック
            if (!plugin.getDatabaseManager().ensureConnection()) {
                plugin.getDatabaseManager().reconnect();
            }

            boolean retrySuccess = true;

            // インベントリ再同期
            boolean invSuccess = plugin.getInventoryManager().loadPlayerInventory(player);
            if (!invSuccess) retrySuccess = false;

            // エンダーチェスト再同期（有効な場合のみ）
            if (plugin.getPluginConfig().isEnderChestEnabled()) {
                boolean ecSuccess = plugin.getEnderChestManager().loadPlayerEnderChest(player);
                if (!ecSuccess) retrySuccess = false;
            }

            // 経済データ再同期
            if (plugin.getEconomyManager().isEconomyEnabled()) {
                boolean ecoSuccess = plugin.getEconomyManager().loadPlayerBalance(player);
                if (!ecoSuccess) retrySuccess = false;
            }

            if (retrySuccess) {
                player.sendMessage("§2[AIS] §a再同期が完了しました!");
            } else {
                player.sendMessage("§4[AIS] §c再同期に失敗しました。管理者に連絡してください。");
                plugin.getLogger().severe(player.getName() + "の再同期に失敗しました。データ損失の可能性があります。");
            }
        } catch (Exception e) {
            player.sendMessage("§4[AIS] §c同期エラー: 管理者に連絡してください");
            plugin.getLogger().severe("再同期中に重大なエラーが発生: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * プレイヤーがサーバーから退出したときのイベント
     * インベントリ状態を保存し、保存完了後にインベントリをクリア
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        final Player player = event.getPlayer();
        final String playerName = player.getName();

        try {
            boolean saveSuccess = true;
            StringBuilder failReasons = new StringBuilder();

            // インベントリの保存
            try {
                plugin.getInventoryManager().savePlayerInventory(player);
            } catch (Exception e) {
                saveSuccess = false;
                failReasons.append("インベントリ保存失敗 ");
                plugin.getLogger().warning("インベントリ保存中にエラー: " + e.getMessage());
            }

            // エンダーチェストの保存（有効な場合のみ）
            if (plugin.getPluginConfig().isEnderChestEnabled()) {
                try {
                    plugin.getEnderChestManager().savePlayerEnderChest(player);
                } catch (Exception e) {
                    saveSuccess = false;
                    failReasons.append("エンダーチェスト保存失敗 ");
                    plugin.getLogger().warning("エンダーチェスト保存中にエラー: " + e.getMessage());
                }
            }

            // 所持金の保存
            if (plugin.getEconomyManager().isEconomyEnabled()) {
                try {
                    plugin.getEconomyManager().savePlayerBalance(player);
                } catch (Exception e) {
                    saveSuccess = false;
                    failReasons.append("経済データ保存失敗 ");
                    plugin.getLogger().warning("経済データ保存中にエラー: " + e.getMessage());
                }
            }

            // 保存が終わった後にクリア処理
            clearPlayerInventory(player);

            if (saveSuccess) {
                plugin.getLogger().info(playerName + " player data save was successful!");
            } else {
                plugin.getLogger().warning(playerName + " player data save partially failed: " + failReasons.toString());
            }

        } catch (Exception e) {
            plugin.getLogger().severe("An error occurred while saving " + playerName + " player data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * プレイヤーのインベントリとエンダーチェストをクリアする
     * 設定に基づいてエンダーチェストのクリア処理を行う
     */
    private void clearPlayerInventory(Player player) {
        // メインインベントリをクリア
        player.getInventory().clear();

        // 防具スロットをクリア
        player.getInventory().setArmorContents(new ItemStack[4]);

        // オフハンドをクリア
        player.getInventory().setItemInOffHand(null);

        // エンダーチェストのクリア（設定が有効な場合のみ）
        if (plugin.getPluginConfig().isEnderChestEnabled()) {
            player.getEnderChest().clear();
        }

        // インベントリの更新を強制
        player.updateInventory();
    }
}