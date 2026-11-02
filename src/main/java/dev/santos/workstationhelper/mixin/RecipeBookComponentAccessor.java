package dev.santos.workstationhelper.mixin;

import java.util.List;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Положение книги рецептов и её вкладки (поля private): книга стоит в (width − 147) / 2 − xOffset, (height − 166) / 2. */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
	@Accessor("xOffset")
	int workstationhelper$xOffset();

	@Accessor("width")
	int workstationhelper$width();

	@Accessor("height")
	int workstationhelper$height();

	@Accessor("tabButtons")
	List<RecipeBookTabButton> workstationhelper$tabButtons();

	@Accessor("selectedTab")
	RecipeBookTabButton workstationhelper$selectedTab();
}
