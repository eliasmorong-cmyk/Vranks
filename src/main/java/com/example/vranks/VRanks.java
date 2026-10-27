package com.example.vranks;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StringUtil;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Pattern;

public final class VRanks extends JavaPlugin implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "vranks.admin";
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");
    private static final int MAX_RANK_LENGTH = 32;

    private File dataFile;
    private YamlConfiguration data;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);

        if (getCommand("vranks") == null) {
            getLogger().severe("Command 'vranks' is missing from plugin.yml. Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getCommand("vranks").setExecutor(this);
        getCommand("vranks").setTabCompleter(this);

        getLogger().info("VRanks enabled.");
    }

    @Override
    public void onDisable() {
        saveData();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }
        if (args[0].equalsIgnoreCase("add")) {
            handleAdd(sender, label, args);
        } else {
            sendUsage(sender, label);
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sendUsage(sender, label);
            return;
        }

        String username = args[1];
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            sender.sendMessage(ChatColor.RED + "Invalid username. Use 3-16 letters, numbers, or underscores.");
            return;
        }

        String rankName = String.join(" ", Arrays.copyOfRange(args, 2, args.length)).trim();
        if (rankName.isEmpty()) {
            sender.sendMessage(ChatColor.RED + "Rank name cannot be empty.");
            return;
        }
        if (rankName.length() > MAX_RANK_LENGTH) {
            sender.sendMessage(ChatColor.RED + "Rank name must be " + MAX_RANK_LENGTH + " characters or fewer.");
            return;
        }
        if (isProfane(rankName)) {
            sender.sendMessage(ChatColor.RED + "That rank name is not allowed.");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(username);
        if (!target.isOnline() && !target.hasPlayedBefore()) {
            sender.sendMessage(ChatColor.RED + "Player '" + username + "' has never joined this server.");
            return;
        }

        String name = target.getName() != null ? target.getName() : username;
        setRank(target.getUniqueId(), name, rankName);
        sender.sendMessage(ChatColor.GREEN + "Set " + name + "'s rank to " + rankName + ".");
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "Usage: /" + label + " add <username> <rank_name>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return completions;
        }
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], List.of("add"), completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                names.add(p.getName());
            }
            StringUtil.copyPartialMatches(args[1], names, completions);
        }
        return completions;
    }

    public boolean isProfane(String rankName) {
        if (!getConfig().getBoolean("profanity-filter.enabled", true)) {
            return false;
        }
        String normalized = normalize(rankName);
        for (String word : getConfig().getStringList("profanity-filter.blocked-words")) {
            String blocked = normalize(word);
            if (!blocked.isEmpty() && normalized.contains(blocked)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    public synchronized void setRank(UUID uuid, String playerName, String rankName) {
        String path = "players." + uuid;
        data.set(path + ".name", playerName);
        data.set(path + ".rank", rankName);
        saveData();
    }

    private synchronized void saveData() {
        if (data == null || dataFile == null) {
            return;
        }
        try {
            data.save(dataFile);
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Could not save data.yml", e);
        }
    }
}
