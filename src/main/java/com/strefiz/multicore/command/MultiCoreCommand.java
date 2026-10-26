package com.strefiz.multicore.command;

import com.strefiz.multicore.MultiCoreMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the {@code /multicore} command tree.
 *
 * <p>The command is a thin shell: all text comes from {@link StatusFormatter} so it stays testable,
 * and all state comes from {@link StatusSource} so it stays decoupled from the modules.
 *
 * <p>Every command result is logged to the server console so admins can see it without being in-game.
 */
public final class MultiCoreCommand {

	private static final Logger LOGGER = LoggerFactory.getLogger(MultiCoreMod.MOD_ID);

	private MultiCoreCommand() {
	}

	/**
	 * Adds the {@code /multicore} command and its subcommands to a dispatcher.
	 *
	 * @param dispatcher the server command dispatcher
	 * @param source the runtime state to report
	 */
	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, StatusSource source) {
		LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("multicore")
				.then(Commands.literal("status")
						.executes(context -> {
							String text = StatusFormatter.status(source);
							logAndSend(context.getSource(), text);
							return 1;
						}))
				.then(Commands.literal("compat")
						.executes(context -> {
							String text = StatusFormatter.compat(source.compatSummary());
							logAndSend(context.getSource(), text);
							return 1;
						}))
				.then(Commands.literal("compat")
						.then(Commands.literal("dump")
								.executes(context -> {
									String text = StatusFormatter.compatDump(source.compatDump());
									logAndSend(context.getSource(), text);
									return 1;
								})))
				.then(Commands.literal("regions")
						.executes(context -> {
							String text = StatusFormatter.regions(source.regionSummary());
							logAndSend(context.getSource(), text);
							return 1;
						}))
				.then(Commands.literal("reload")
						.executes(context -> {
							String text = StatusFormatter.reloadWarning();
							logAndSend(context.getSource(), text);
							return 1;
						}));
		dispatcher.register(root);
	}

	/**
	 * Registers the command through the Fabric command callback.
	 *
	 * @param source the runtime state to report
	 */
	public static void registerEvents(StatusSource source) {
		CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) ->
				register(dispatcher, source));
	}

	/**
	 * Sends the text to the player and logs it to the server console.
	 */
	private static void logAndSend(CommandSourceStack source, String text) {
		LOGGER.info("[MultiCore] " + text.replace("\n", " | "));
		for (String line : text.split("\n", -1)) {
			source.sendSystemMessage(Component.literal(line));
		}
	}
}
