/*
 * WorldEdit, a Minecraft world manipulation toolkit
 * Copyright (C) sk89q <http://www.sk89q.com>
 * Copyright (C) WorldEdit team and contributors
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
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package com.sk89q.worldedit.bukkit;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.sk89q.bukkit.util.CommandRegistration;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.event.platform.CommandSuggestionEvent;
import com.sk89q.worldedit.extension.platform.Actor;
import com.sk89q.worldedit.internal.util.Substring;
import com.sk89q.worldedit.util.eventbus.EventBus;
import io.papermc.lib.PaperLib;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorldEditPluginCompletionTest {

    private WorldEditPlugin plugin;
    private Server server;

    @BeforeAll
    static void verifiesPackagedClassOriginWhenRequested() throws Exception {
        String testJar = System.getProperty("fawe.completion.testJar");
        if (testJar != null) {
            assertEquals(Path.of(testJar).toRealPath(), Path.of(
                    WorldEditPlugin.class.getProtectionDomain().getCodeSource().getLocation().toURI()
            ).toRealPath());
        }
    }

    @BeforeEach
    void setUp() {
        server = mock(Server.class, RETURNS_DEEP_STUBS);
        plugin = mock(WorldEditPlugin.class, CALLS_REAL_METHODS);
        doReturn(server).when(plugin).getServer();
        doReturn("FastAsyncWorldEdit").when(plugin).getName();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void registersCompletionWhenEventExistsRegardlessOfPaperBrand(boolean isPaper) throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            bukkit.when(Bukkit::getVersion).thenReturn("Youer 1.21.1");
            bukkit.when(Bukkit::getBukkitVersion).thenReturn("1.21.1-R0.1-SNAPSHOT");
            try (MockedStatic<PaperLib> paperLib = mockStatic(PaperLib.class)) {
                paperLib.when(PaperLib::isPaper).thenReturn(isPaper);
                Method registration = WorldEditPlugin.class.getDeclaredMethod("registerAsyncTabCompleteListener");
                registration.setAccessible(true);
                registration.invoke(plugin);

                ArgumentCaptor<Listener> listener = ArgumentCaptor.forClass(Listener.class);
                verify(server.getPluginManager()).registerEvents(listener.capture(), eq(plugin));
                assertEquals("AsyncTabCompleteListener", listener.getValue().getClass().getSimpleName());
            }
        }
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "/fawe h|fawe|/fawe h",
            "//set st|/set|//set st",
            "//schem l|/schem|//schem l",
            "/fastasyncworldedit:fawe h|fawe|/fawe h",
            "/fastasyncworldedit:/set st|/set|//set st",
            "/fastasyncworldedit:/schem l|/schem|//schem l",
            "fastasyncworldedit:fawe h|fawe|/fawe h",
            "fastasyncworldedit:/set st|/set|//set st"
    })
    void completesOwnedCommandUsingItsUnqualifiedLabelAndInput(
            String input, String lookupLabel, String suggestionInput
    ) throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<WorldEdit> worldEditStatic = mockStatic(WorldEdit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            WorldEdit worldEdit = mock(WorldEdit.class, RETURNS_DEEP_STUBS);
            worldEditStatic.when(WorldEdit::getInstance).thenReturn(worldEdit);
            CommandRegistration commands = mock(CommandRegistration.class);
            setCommands(commands);
            String originalLabel = commandLabel(input);
            when(commands.getCommandOwner(originalLabel)).thenReturn(plugin);
            when(worldEdit.getPlatformManager().getPlatformCommandManager().getCommandManager().getCommand(lookupLabel))
                    .thenReturn(Optional.of(mock(org.enginehub.piston.Command.class)));
            CommandSender sender = mock(CommandSender.class);
            doReturn(mock(Actor.class)).when(plugin).wrapCommandSender(sender);
            String expectedCompletion = suggestionInput.endsWith(" h") ? "help"
                    : suggestionInput.endsWith(" l") ? "list" : "stone";
            EventBus eventBus = worldEdit.getEventBus();
            doAnswer(invocation -> {
                CommandSuggestionEvent suggestion = invocation.getArgument(0);
                String arguments = suggestion.getArguments();
                suggestion.setSuggestions(List.of(Substring.wrap(
                        expectedCompletion, arguments.lastIndexOf(' ') + 1, arguments.length()
                )));
                return null;
            }).when(eventBus).post(any(CommandSuggestionEvent.class));

            AsyncTabCompleteEvent event = new AsyncTabCompleteEvent(sender, input, true, null);
            complete(event);

            verify(commands).getCommandOwner(originalLabel);
            verify(worldEdit.getPlatformManager().getPlatformCommandManager().getCommandManager()).getCommand(lookupLabel);
            ArgumentCaptor<CommandSuggestionEvent> suggestion = ArgumentCaptor.forClass(CommandSuggestionEvent.class);
            verify(worldEdit.getEventBus()).post(suggestion.capture());
            assertEquals(suggestionInput, suggestion.getValue().getArguments());
            assertEquals(List.of(expectedCompletion), event.getCompletions());
            assertTrue(event.isHandled());
        }
    }

    @Test
    void skipsRegistrationWhenServerDoesNotProvideTheEvent() throws Exception {
        String pluginClassName = WorldEditPlugin.class.getName();
        URL classes = WorldEditPlugin.class.getProtectionDomain().getCodeSource().getLocation();
        try (URLClassLoader withoutEvent = new URLClassLoader(new URL[]{classes}, WorldEditPlugin.class.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.equals("com.destroystokyo.paper.event.server.AsyncTabCompleteEvent")) {
                    throw new ClassNotFoundException(name);
                }
                if (name.equals(pluginClassName) || name.startsWith(pluginClassName + "$")) {
                    Class<?> loaded = findLoadedClass(name);
                    if (loaded == null) {
                        loaded = findClass(name);
                    }
                    if (resolve) {
                        resolveClass(loaded);
                    }
                    return loaded;
                }
                return super.loadClass(name, resolve);
            }
        }) {
            Class<?> isolatedClass = withoutEvent.loadClass(pluginClassName);
            JavaPlugin isolatedPlugin = (JavaPlugin) mock(isolatedClass, CALLS_REAL_METHODS);
            doReturn(server).when(isolatedPlugin).getServer();
            Method registration = isolatedClass.getDeclaredMethod("registerAsyncTabCompleteListener");
            registration.setAccessible(true);
            registration.invoke(isolatedPlugin);
            verify(server.getPluginManager(), never()).registerEvents(any(Listener.class), eq(isolatedPlugin));
        }
    }

    @Test
    void keepsArgumentSpacing() throws Exception {
        String input = "/fastasyncworldedit:fawe  help ";
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<WorldEdit> worldEditStatic = mockStatic(WorldEdit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            WorldEdit worldEdit = mock(WorldEdit.class, RETURNS_DEEP_STUBS);
            worldEditStatic.when(WorldEdit::getInstance).thenReturn(worldEdit);
            CommandRegistration commands = mock(CommandRegistration.class);
            setCommands(commands);
            when(commands.getCommandOwner("fastasyncworldedit:fawe")).thenReturn(plugin);
            when(worldEdit.getPlatformManager().getPlatformCommandManager().getCommandManager().getCommand("fawe"))
                    .thenReturn(Optional.of(mock(org.enginehub.piston.Command.class)));
            CommandSender sender = mock(CommandSender.class);
            doReturn(mock(Actor.class)).when(plugin).wrapCommandSender(sender);

            complete(new AsyncTabCompleteEvent(sender, input, true, null));

            ArgumentCaptor<CommandSuggestionEvent> suggestion = ArgumentCaptor.forClass(CommandSuggestionEvent.class);
            verify(worldEdit.getEventBus()).post(suggestion.capture());
            assertEquals("/fawe  help ", suggestion.getValue().getArguments());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/fastasyncworldedit:fawe h", "/anotherplugin:fawe h", "/fawe h"})
    void leavesCommandsOwnedByOtherPluginsUntouched(String input) throws Exception {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<WorldEdit> worldEdit = mockStatic(WorldEdit.class)) {
            bukkit.when(Bukkit::getServer).thenReturn(server);
            CommandRegistration commands = mock(CommandRegistration.class);
            setCommands(commands);
            when(commands.getCommandOwner(commandLabel(input))).thenReturn(mock(Plugin.class));
            AsyncTabCompleteEvent event = new AsyncTabCompleteEvent(mock(CommandSender.class), input, true, null);

            complete(event);

            assertFalse(event.isHandled());
            assertTrue(event.getCompletions().isEmpty());
            worldEdit.verify(WorldEdit::getInstance, never());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/fawe", "//set", "/fastasyncworldedit:fawe"})
    void leavesRootCompletionUntouched(String input) throws Exception {
        AsyncTabCompleteEvent event = new AsyncTabCompleteEvent(mock(CommandSender.class), input, true, null);
        complete(event);
        assertFalse(event.isHandled());
    }

    @Test
    void ignoresChatCompletion() throws Exception {
        AsyncTabCompleteEvent event = new AsyncTabCompleteEvent(mock(CommandSender.class), "fawe h", false, null);
        complete(event);
        assertFalse(event.isHandled());
    }

    private void setCommands(CommandRegistration commands) throws Exception {
        BukkitServerInterface platform = mock(BukkitServerInterface.class);
        when(platform.getDynamicCommands()).thenReturn(commands);
        Field field = WorldEditPlugin.class.getDeclaredField("platform");
        field.setAccessible(true);
        field.set(plugin, platform);
    }

    private void complete(AsyncTabCompleteEvent event) throws Exception {
        Class<?> listenerClass = Class.forName(WorldEditPlugin.class.getName() + "$AsyncTabCompleteListener");
        Constructor<?> constructor = listenerClass.getDeclaredConstructor(WorldEditPlugin.class);
        constructor.setAccessible(true);
        Object listener = constructor.newInstance(plugin);
        Method completion = listenerClass.getDeclaredMethod("onAsyncTabComplete", AsyncTabCompleteEvent.class);
        completion.setAccessible(true);
        completion.invoke(listener, event);
    }

    private static String commandLabel(String input) {
        String label = input.substring(0, input.indexOf(' '));
        return label.startsWith("/") ? label.substring(1) : label;
    }

}
