package dev.santos.workstationhelper.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Доступ к положению и размеру окна контейнера (они protected). */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
	@Accessor("leftPos")
	int workstationhelper$leftPos();

	@Accessor("topPos")
	int workstationhelper$topPos();

	@Accessor("imageWidth")
	int workstationhelper$imageWidth();

	@Accessor("imageHeight")
	int workstationhelper$imageHeight();

	@Accessor("inventoryLabelX")
	int workstationhelper$inventoryLabelX();

	@Accessor("inventoryLabelY")
	int workstationhelper$inventoryLabelY();

	@Accessor("playerInventoryTitle")
	net.minecraft.network.chat.Component workstationhelper$playerInventoryTitle();
}
