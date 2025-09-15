//package me.petoma21.inventory_share.commands;
//
//public class ReloadCommand {
//}

package me.petoma21.inventory_share.commands;

import me.petoma21.inventory_share.Inventory_Share;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final Inventory_Share plugin;

    public ReloadCommand(Inventory_Share plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("inventoryshare.reload")) {
            sender.sendMessage(ChatColor.RED + "このコマンドを実行する権限がありません。");
            return true;
        }

        // プラグインをリロード
        plugin.reload();
        sender.sendMessage(ChatColor.GREEN + "AIS の設定を再読み込みしました。");
        return true;
    }
}