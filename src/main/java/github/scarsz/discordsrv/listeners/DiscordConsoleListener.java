/*
 * DiscordSRV - A Minecraft to Discord and back link plugin
 * Copyright (C) 2016-2020 Austin "Scarsz" Shapiro
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package github.scarsz.discordsrv.listeners;

import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.util.DiscordUtil;
import github.scarsz.discordsrv.util.LangUtil;
import github.scarsz.discordsrv.util.PluginUtil;
import github.scarsz.discordsrv.util.TimeUtil;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.plugin.InvalidDescriptionException;
import org.bukkit.plugin.InvalidPluginException;
import org.bukkit.plugin.Plugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@SuppressWarnings("ResultOfMethodCallIgnored")
public class DiscordConsoleListener extends ListenerAdapter {

    private List<String> allowedFileExtensions = new ArrayList<String>() {{
        add("jar");
        //add("zip"); todo support uploading compressed plugins & decompress
    }};

    @Override
    public void onGuildMessageReceived(GuildMessageReceivedEvent event) {
        // check if the server hasn't started yet but someone still tried to run a command...
        if (DiscordUtil.getJda() == null) return;
        // if message is from null author or self do not process
        if (event.getAuthor().getId().equals(DiscordUtil.getJda().getSelfUser().getId())) return;
        // only do anything with the messages if it's in the console channel
        if (DiscordSRV.getPlugin().getConsoleChannel() == null || !event.getChannel().getId().equals(DiscordSRV.getPlugin().getConsoleChannel().getId())) return;

        // handle all attachments
        for (Message.Attachment attachment : event.getMessage().getAttachments()) handleAttachment(event, attachment);

        // get if blacklist acts as whitelist
        boolean DiscordConsoleChannelBlacklistActsAsWhitelist = DiscordSRV.config().getBoolean("DiscordConsoleChannelBlacklistActsAsWhitelist");
        // get banned commands
        List<String> DiscordConsoleChannelBlacklistedCommands = DiscordSRV.config().getStringList("DiscordConsoleChannelBlacklistedCommands");
        // convert to all lower case
        for (int i = 0; i < DiscordConsoleChannelBlacklistedCommands.size(); i++) DiscordConsoleChannelBlacklistedCommands.set(i, DiscordConsoleChannelBlacklistedCommands.get(i).toLowerCase());
        // get base command for manipulation
        String requestedCommand = event.getMessage().getContentRaw().trim();
        // select the first part of the requested command, being the main part of it we care about
        requestedCommand = requestedCommand.split(" ")[0].toLowerCase(); // *op* person
        // get the ass end of commands using full qualifiers such as minecraft:say
        while (requestedCommand.contains(":")) requestedCommand = requestedCommand.split(":", 2)[1];
        // command white/blacklist checking
        boolean allowed = DiscordConsoleChannelBlacklistActsAsWhitelist == DiscordConsoleChannelBlacklistedCommands.contains(requestedCommand);
        // return if command not allowed
        if (!allowed) return;

        // log command to console log file, if this fails the command is not executed for safety reasons unless this is turned off
        File logFile = DiscordSRV.getPlugin().getLogFile();
        if (logFile != null) {
            try {
                FileUtils.writeStringToFile(
                    logFile,
                    "[" + TimeUtil.timeStamp() + " | ID " + event.getAuthor().getId() + "] " + event.getAuthor().getName() + ": " + event.getMessage().getContentRaw() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    true
                );
            } catch (IOException e) {
                DiscordSRV.error(LangUtil.InternalMessage.ERROR_LOGGING_CONSOLE_ACTION + " " + logFile.getAbsolutePath() + ": " + e.getMessage());
                if (DiscordSRV.config().getBoolean("CancelConsoleCommandIfLoggingFailed")) return;
            }
        }

        // if server is running paper spigot it has to have it's own little section of code because it whines about timing issues
        Bukkit.getScheduler().runTask(DiscordSRV.getPlugin(), () -> Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), event.getMessage().getContentRaw()));
    }

    private void handleAttachment(GuildMessageReceivedEvent event, Message.Attachment attachment) {
        throw new UnsupportedOperationException("This feature isn't supported on Minehut.");
    }

    private String getPluginName(String pluginName, ZipFile jarZipFile, ZipEntry entry) throws IOException {
        if (!entry.getName().equalsIgnoreCase("plugin.yml")) return pluginName;
        BufferedReader reader = new BufferedReader(new InputStreamReader(jarZipFile.getInputStream(entry)));
        for (String line : reader.lines().collect(Collectors.toList()))
            if (line.trim().startsWith("name:"))
                pluginName = line.replace("name:", "").trim();
        return pluginName;
    }
}
