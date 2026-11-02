package dev.santos.workstationhelper;

import dev.santos.workstationhelper.gui.AnvilPanels;
import dev.santos.workstationhelper.gui.BrewingPanels;
import dev.santos.workstationhelper.gui.CartographyPanels;
import dev.santos.workstationhelper.gui.EnchantingPanels;
import dev.santos.workstationhelper.gui.FuelTab;
import dev.santos.workstationhelper.gui.GrindstonePanels;
import dev.santos.workstationhelper.gui.LoomPanels;
import dev.santos.workstationhelper.gui.SmithingPanels;
import dev.santos.workstationhelper.gui.StonecutterPanels;
import dev.santos.workstationhelper.gui.TradePanels;
import dev.santos.workstationhelper.scene.BlockSceneRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.PictureInPictureRendererRegistry;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BrewingStandScreen;
import net.minecraft.client.gui.screens.inventory.CartographyTableScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.gui.screens.inventory.GrindstoneScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WorkstationHelperClient implements ClientModInitializer {
	public static final String MOD_ID = "workstationhelper";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		Config.load();
		// своя 3D-сцена из блоков в окнах (полки у стола зачарования)
		PictureInPictureRendererRegistry.register(ctx -> new BlockSceneRenderer());
		// «4/6» в подсказке флага у ткацкого станка
		LoomPanels.registerTooltip();
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			// «Помощник» выключен — мод ничего не добавляет
			if (!Config.get().helper) return;
			if (screen instanceof AnvilScreen anvil) AnvilPanels.attach(anvil);
			if (screen instanceof SmithingScreen smithing) SmithingPanels.attach(smithing);
			if (screen instanceof CartographyTableScreen carto) CartographyPanels.attach(carto);
			if (screen instanceof EnchantmentScreen ench) EnchantingPanels.attach(ench);
			if (screen instanceof LoomScreen loom) LoomPanels.attach(loom);
			if (screen instanceof BrewingStandScreen brew) BrewingPanels.attach(brew);
			if (screen instanceof StonecutterScreen stone) StonecutterPanels.attach(stone);
			if (screen instanceof AbstractFurnaceScreen<?> furnace) FuelTab.attach(furnace);
			if (screen instanceof GrindstoneScreen grind) GrindstonePanels.attach(grind);
			if (screen instanceof MerchantScreen merchant) TradePanels.attach(merchant);
		});
	}
}
