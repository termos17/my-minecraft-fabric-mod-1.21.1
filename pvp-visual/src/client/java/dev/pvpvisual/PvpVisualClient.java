package dev.pvpvisual;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;

public class PvpVisualClient implements ClientModInitializer {
	public static KeyMapping openMenu;

	@Override
	public void onInitializeClient() {
		PvpConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("pvpvisual", "main"));
		openMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.pvpvisual.open_menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));

		Hud.register();
		WorldFx.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			Visuals.onTick(client);
			while (openMenu.consumeClick()) {
				client.setScreen(new ConfigScreen());
			}
		});

		// Событие вызывается и на интегрированном сервере, поэтому фильтруем только клиентский мир.
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			Minecraft mc = Minecraft.getInstance();
			if (world instanceof ClientLevel && player == mc.player) {
				Visuals.onAttack(mc, entity);
			}
			return InteractionResult.PASS;
		});
	}
}
