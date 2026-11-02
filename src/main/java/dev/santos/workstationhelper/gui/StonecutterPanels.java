package dev.santos.workstationhelper.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.santos.workstationhelper.Config;
import dev.santos.workstationhelper.mixin.ContainerScreenAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

/**
 * Книга рецептов камнереза — как книга рецептов верстака: вкладки «Все» и «Выгодные», поиск, кнопка «только то,
 * что можно сделать из инвентаря», страницы. Клик по рецепту кладёт блок в камнерез и выбирает рецепт.
 * Мини-кнопка в правом верхнем углу окна прячет книгу.
 */
public final class StonecutterPanels {
	private static final int BOOK_W = 147, BOOK_H = 166, TAB_W = 35, TAB_H = 27, CELL = 25, PER_PAGE = 20;
	private static final Identifier BOOK = Identifier.withDefaultNamespace("textures/gui/recipe_book.png");
	private static final Identifier TAB = Identifier.withDefaultNamespace("recipe_book/tab");
	private static final Identifier TAB_SELECTED = Identifier.withDefaultNamespace("recipe_book/tab_selected");
	private static final Identifier SLOT_OK = Identifier.withDefaultNamespace("recipe_book/slot_craftable");
	private static final Identifier SLOT_NO = Identifier.withDefaultNamespace("recipe_book/slot_uncraftable");
	private static final Identifier FILTER_ON = Identifier.withDefaultNamespace("recipe_book/filter_enabled");
	private static final Identifier FILTER_ON_HOVER = Identifier.withDefaultNamespace("recipe_book/filter_enabled_highlighted");
	private static final Identifier FILTER_OFF = Identifier.withDefaultNamespace("recipe_book/filter_disabled");
	private static final Identifier FILTER_OFF_HOVER = Identifier.withDefaultNamespace("recipe_book/filter_disabled_highlighted");
	private static final Identifier PAGE_FORWARD = Identifier.withDefaultNamespace("recipe_book/page_forward");
	private static final Identifier PAGE_FORWARD_HOVER = Identifier.withDefaultNamespace("recipe_book/page_forward_highlighted");
	private static final Identifier PAGE_BACKWARD = Identifier.withDefaultNamespace("recipe_book/page_backward");
	private static final Identifier PAGE_BACKWARD_HOVER = Identifier.withDefaultNamespace("recipe_book/page_backward_highlighted");

	/**
	 * Рецепты, которые на камнерезе выгоднее, чем на верстаке (или их на верстаке нет совсем): «вход>выход».
	 * Посчитано по рецептам игры 26.3: сравнён выход камнереза с лучшей цепочкой крафтов на верстаке из того же блока.
	 */
	private static final Set<String> PROFITABLE = Set.of(
		"andesite>andesite_stairs", "andesite>polished_andesite_stairs", "black_concrete>black_concrete_stairs", "blackstone>blackstone_stairs",
		"blackstone>polished_blackstone_brick_stairs", "blackstone>polished_blackstone_stairs", "blue_concrete>blue_concrete_stairs",
		"bricks>brick_stairs", "brown_concrete>brown_concrete_stairs", "cinnabar>cinnabar_brick_stairs", "cinnabar>cinnabar_stairs",
		"cinnabar>polished_cinnabar_stairs", "cinnabar_bricks>cinnabar_brick_stairs", "cobbled_deepslate>cobbled_deepslate_stairs",
		"cobbled_deepslate>deepslate_brick_stairs", "cobbled_deepslate>deepslate_tile_stairs", "cobbled_deepslate>polished_deepslate_stairs",
		"cobblestone>cobblestone_stairs", "copper_block>chiseled_copper", "copper_block>copper_grate", "copper_block>cut_copper",
		"copper_block>cut_copper_slab", "copper_block>cut_copper_stairs", "cut_copper>cut_copper_stairs", "cyan_concrete>cyan_concrete_stairs",
		"dark_prismarine>dark_prismarine_stairs", "deepslate>chiseled_deepslate", "deepslate>cobbled_deepslate", "deepslate>cobbled_deepslate_slab",
		"deepslate>cobbled_deepslate_stairs", "deepslate>cobbled_deepslate_wall", "deepslate>deepslate_brick_slab", "deepslate>deepslate_brick_stairs",
		"deepslate>deepslate_brick_wall", "deepslate>deepslate_bricks", "deepslate>deepslate_tile_slab", "deepslate>deepslate_tile_stairs",
		"deepslate>deepslate_tile_wall", "deepslate>deepslate_tiles", "deepslate>polished_deepslate", "deepslate>polished_deepslate_slab",
		"deepslate>polished_deepslate_stairs", "deepslate>polished_deepslate_wall", "deepslate_bricks>deepslate_brick_stairs",
		"deepslate_bricks>deepslate_tile_stairs", "deepslate_tiles>deepslate_tile_stairs", "diorite>diorite_stairs", "diorite>polished_diorite_stairs",
		"end_stone>end_stone_brick_stairs", "end_stone_bricks>end_stone_brick_stairs", "exposed_copper>exposed_chiseled_copper",
		"exposed_copper>exposed_copper_grate", "exposed_copper>exposed_cut_copper", "exposed_copper>exposed_cut_copper_slab",
		"exposed_copper>exposed_cut_copper_stairs", "exposed_cut_copper>exposed_cut_copper_stairs", "granite>granite_stairs",
		"granite>polished_granite_stairs", "gray_concrete>gray_concrete_stairs", "green_concrete>green_concrete_stairs",
		"light_blue_concrete>light_blue_concrete_stairs", "light_gray_concrete>light_gray_concrete_stairs", "lime_concrete>lime_concrete_stairs",
		"magenta_concrete>magenta_concrete_stairs", "mossy_cobblestone>mossy_cobblestone_stairs", "mossy_stone_bricks>mossy_stone_brick_stairs",
		"mud_bricks>mud_brick_stairs", "nether_bricks>nether_brick_stairs", "orange_concrete>orange_concrete_stairs",
		"oxidized_copper>oxidized_chiseled_copper", "oxidized_copper>oxidized_copper_grate", "oxidized_copper>oxidized_cut_copper",
		"oxidized_copper>oxidized_cut_copper_slab", "oxidized_copper>oxidized_cut_copper_stairs", "oxidized_cut_copper>oxidized_cut_copper_stairs",
		"pink_concrete>pink_concrete_stairs", "polished_andesite>polished_andesite_stairs", "polished_blackstone>polished_blackstone_brick_stairs",
		"polished_blackstone>polished_blackstone_stairs", "polished_blackstone_bricks>polished_blackstone_brick_stairs",
		"polished_cinnabar>cinnabar_brick_stairs", "polished_cinnabar>polished_cinnabar_stairs", "polished_deepslate>deepslate_brick_stairs",
		"polished_deepslate>deepslate_tile_stairs", "polished_deepslate>polished_deepslate_stairs", "polished_diorite>polished_diorite_stairs",
		"polished_granite>polished_granite_stairs", "polished_sulfur>polished_sulfur_stairs", "polished_sulfur>sulfur_brick_stairs",
		"polished_tuff>polished_tuff_stairs", "polished_tuff>tuff_brick_stairs", "prismarine>prismarine_stairs",
		"prismarine_bricks>prismarine_brick_stairs", "purple_concrete>purple_concrete_stairs", "purpur_block>purpur_stairs", "quartz_block>quartz_stairs",
		"red_concrete>red_concrete_stairs", "red_nether_bricks>red_nether_brick_stairs", "red_sandstone>red_sandstone_stairs",
		"resin_bricks>resin_brick_stairs", "sandstone>sandstone_stairs", "smooth_quartz>smooth_quartz_stairs",
		"smooth_red_sandstone>smooth_red_sandstone_stairs", "smooth_sandstone>smooth_sandstone_stairs", "stone>cobblestone", "stone>cobblestone_slab",
		"stone>cobblestone_stairs", "stone>cobblestone_wall", "stone>stone_brick_stairs", "stone>stone_stairs", "stone_bricks>stone_brick_stairs",
		"sulfur>polished_sulfur_stairs", "sulfur>sulfur_brick_stairs", "sulfur>sulfur_stairs", "sulfur_bricks>sulfur_brick_stairs",
		"tuff>polished_tuff_stairs", "tuff>tuff_brick_stairs", "tuff>tuff_stairs", "tuff_bricks>tuff_brick_stairs",
		"waxed_copper_block>waxed_chiseled_copper", "waxed_copper_block>waxed_copper_grate", "waxed_copper_block>waxed_cut_copper",
		"waxed_copper_block>waxed_cut_copper_slab", "waxed_copper_block>waxed_cut_copper_stairs", "waxed_cut_copper>waxed_cut_copper_stairs",
		"waxed_exposed_copper>waxed_exposed_chiseled_copper", "waxed_exposed_copper>waxed_exposed_copper_grate",
		"waxed_exposed_copper>waxed_exposed_cut_copper", "waxed_exposed_copper>waxed_exposed_cut_copper_slab",
		"waxed_exposed_copper>waxed_exposed_cut_copper_stairs", "waxed_exposed_cut_copper>waxed_exposed_cut_copper_stairs",
		"waxed_oxidized_copper>waxed_oxidized_chiseled_copper", "waxed_oxidized_copper>waxed_oxidized_copper_grate",
		"waxed_oxidized_copper>waxed_oxidized_cut_copper", "waxed_oxidized_copper>waxed_oxidized_cut_copper_slab",
		"waxed_oxidized_copper>waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper>waxed_oxidized_cut_copper_stairs",
		"waxed_weathered_copper>waxed_weathered_chiseled_copper", "waxed_weathered_copper>waxed_weathered_copper_grate",
		"waxed_weathered_copper>waxed_weathered_cut_copper", "waxed_weathered_copper>waxed_weathered_cut_copper_slab",
		"waxed_weathered_copper>waxed_weathered_cut_copper_stairs", "waxed_weathered_cut_copper>waxed_weathered_cut_copper_stairs",
		"weathered_copper>weathered_chiseled_copper", "weathered_copper>weathered_copper_grate", "weathered_copper>weathered_cut_copper",
		"weathered_copper>weathered_cut_copper_slab", "weathered_copper>weathered_cut_copper_stairs", "weathered_cut_copper>weathered_cut_copper_stairs",
		"white_concrete>white_concrete_stairs", "yellow_concrete>yellow_concrete_stairs");

	/** Рецепт: что кладём, что получаем, и выгоден ли он. */
	private record Recipe(Ingredient input, ItemStack output, ItemStack inputIcon, boolean profitable) {
	}

	/** Ячейка книги: один и тот же результат из разных блоков (ограда — из сланца, кирпичей, плитняка…). */
	private record Group(ItemStack display, List<Recipe> recipes) {
		boolean profitable() {
			return recipes.stream().anyMatch(Recipe::profitable);
		}
	}

	/** Вкладка (0 — все, 1 — выгодные), фильтр «можно сделать» и страница — помнятся, пока запущена игра. */
	private static int tab;
	private static boolean onlyCraftable;
	private int page;

	private final StonecutterScreen screen;
	private final Minecraft mc;
	private final Font font;
	private boolean fits;
	private int bx, by;
	private EditBox search;
	private final List<Group> all = new ArrayList<>();
	private final List<Group> shown = new ArrayList<>();
	/** Рецепт, который надо выбрать в камнерезе, как только в нём окажется нужный блок. */
	private Recipe pending;
	private int pendingTicks;

	private StonecutterPanels(StonecutterScreen screen) {
		this.screen = screen;
		this.mc = Minecraft.getInstance();
		this.font = mc.font;
	}

	public static void attach(StonecutterScreen screen) {
		StonecutterPanels p = new StonecutterPanels(screen);
		p.load();
		p.layout();
		ScreenEvents.afterBackground(screen).register((s, g, mx, my, a) -> p.extract(g, mx, my));
		ScreenEvents.afterTick(screen).register(s -> p.tick());
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !p.click(e.x(), e.y()));
		// отпускание кнопки над книгой тоже наше: иначе игра решит, что мышь «вне окна», и выбросит вещь из руки
		ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !(p.shown() && p.overBook(e.x(), e.y())));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !p.scroll(x, y, v));
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !p.key(e));
	}

	private ContainerScreenAccessor acc() {
		return (ContainerScreenAccessor) screen;
	}

	private StonecutterMenu menu() {
		return screen.getMenu();
	}

	private boolean shown() {
		return fits && Config.get().stonePanels;
	}

	/** Все рецепты камнереза — игра присылает их игроку. */
	private void load() {
		if (mc.level == null) return;
		var ctx = SlotDisplayContext.fromLevel(mc.level);
		for (SelectableRecipe.SingleInputEntry<StonecutterRecipe> e : mc.level.recipeAccess().stonecutterRecipes().entries()) {
			ItemStack out = e.recipe().optionDisplay().resolveForFirstStack(ctx);
			if (out.isEmpty()) continue;
			List<Holder<Item>> inputs = e.input().items().toList();
			if (inputs.isEmpty()) continue;
			boolean good = false;
			String outId = out.getItem().builtInRegistryHolder().key().identifier().getPath();
			for (Holder<Item> h : inputs) {
				if (PROFITABLE.contains(h.unwrapKey().map(k -> k.identifier().getPath()).orElse("") + ">" + outId)) good = true;
			}
			Recipe r = new Recipe(e.input(), out, new ItemStack(inputs.getFirst()), good);
			// тот же результат уже есть — в ту же ячейку; в углу ячейки — лучший выход из одного блока
			Group g = null;
			for (Group o : all) {
				if (o.display().is(out.getItem())) g = o;
			}
			if (g == null) all.add(new Group(out.copy(), new ArrayList<>(List.of(r))));
			else {
				g.recipes().add(r);
				if (out.getCount() > g.display().getCount()) g.display().setCount(out.getCount());
			}
		}
	}

	private void layout() {
		int left = acc().workstationhelper$leftPos(), top = acc().workstationhelper$topPos();
		// книга — слева от окна, как книга рецептов у верстака; слева от неё — вкладки
		bx = left - BOOK_W - 2;
		by = top;
		fits = bx - 30 >= 2;
		search = new EditBox(font, bx + 25, by + 13, 81, 14, Component.translatable("workstationhelper.loom.search"));
		search.setHint(Component.translatable("workstationhelper.stone.search").withStyle(ChatFormatting.ITALIC, ChatFormatting.GRAY));
		search.setMaxLength(50);
		search.setTextColor(0xFFFFFFFF);
		search.setResponder(t -> {
			page = 0;
			filter();
		});
		Screens.getWidgets(screen).add(search);
		filter();
	}

	private boolean canMake(Group g) {
		return g.recipes().stream().anyMatch(this::canMake);
	}

	/** Какой рецепт ячейки делать: из блока, который есть, — сначала выгодный, потом с большим выходом. */
	private Recipe best(Group g) {
		Recipe pick = null;
		for (Recipe r : g.recipes()) {
			if (!canMake(r)) continue;
			if (pick == null || r.profitable() && !pick.profitable()
				|| r.profitable() == pick.profitable() && r.output().getCount() > pick.output().getCount()) pick = r;
		}
		return pick != null ? pick : g.recipes().getFirst();
	}

	private boolean canMake(Recipe r) {
		for (Slot s : menu().slots) {
			if ((s.container instanceof Inventory || s.index == StonecutterMenu.INPUT_SLOT) && r.input().test(s.getItem())) return true;
		}
		return false;
	}

	private void filter() {
		shown.clear();
		String q = search == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
		for (Group g : all) {
			if (tab == 1 && !g.profitable()) continue;
			if (onlyCraftable && !canMake(g)) continue;
			if (!q.isEmpty() && !g.display().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)) continue;
			shown.add(g);
		}
	}

	private int pages() {
		return Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
	}

	/** Мини-кнопка «−/+» в правом верхнем углу окна камнереза. */
	private int toggleX() {
		return Vanilla.toggleRightX(acc().workstationhelper$leftPos(), acc().workstationhelper$imageWidth());
	}

	private int toggleY() {
		return Vanilla.toggleY(acc().workstationhelper$topPos());
	}

	private boolean onToggle(double mx, double my) {
		return inside(mx, my, toggleX(), toggleY(), Vanilla.TOGGLE, Vanilla.TOGGLE);
	}

	private boolean overBook(double mx, double my) {
		return inside(mx, my, bx, by, BOOK_W, BOOK_H) || inside(mx, my, bx - 30, by + 3, TAB_W, TAB_H * 2);
	}

	// ======================== отрисовка ========================

	private void extract(GuiGraphicsExtractor g, int mx, int my) {
		search.visible = shown();
		if (!search.visible) search.setFocused(false);
		if (fits) {
			boolean hover = onToggle(mx, my);
			Vanilla.toggle(g, toggleX(), toggleY(), hover, Config.get().stonePanels);
			if (hover) g.setTooltipForNextFrame(font, Component.translatable(Config.get().stonePanels ? "workstationhelper.hide" : "workstationhelper.show"), mx, my);
		}
		if (!shown()) return;
		// фильтр «можно сделать» зависит от инвентаря — обновляем каждый кадр, пока он включён
		if (onlyCraftable) filter();

		// вкладки слева, как у книги верстака: обычные — под книгой, выбранная — поверх неё, светлее и на 2 пикселя левее
		for (int i = 0; i < 2; i++) {
			if (tab != i) drawTab(g, i, false);
		}
		g.blit(RenderPipelines.GUI_TEXTURED, BOOK, bx, by, 1.0f, 1.0f, BOOK_W, BOOK_H, 256, 256);
		drawTab(g, tab, true);
		String[] tips = {"workstationhelper.stone.tab.all", "workstationhelper.stone.tab.good"};
		for (int i = 0; i < 2; i++) {
			if (inside(mx, my, bx - 30, by + 3 + TAB_H * i, TAB_W, TAB_H)) g.setTooltipForNextFrame(font, Component.translatable(tips[i]), mx, my);
		}

		// кнопка-фильтр справа от поиска
		boolean overFilter = inside(mx, my, bx + 110, by + 12, 26, 16);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, onlyCraftable ? (overFilter ? FILTER_ON_HOVER : FILTER_ON) : (overFilter ? FILTER_OFF_HOVER : FILTER_OFF),
			bx + 110, by + 12, 26, 16);
		if (overFilter) g.setTooltipForNextFrame(font, Component.translatable(onlyCraftable ? "workstationhelper.stone.filter_on" : "workstationhelper.stone.filter_off"), mx, my);

		page = Math.clamp(page, 0, pages() - 1);
		Group tip = null;
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = page * PER_PAGE + i;
			if (idx >= shown.size()) break;
			Group r = shown.get(idx);
			int cx = bx + 11 + CELL * (i % 5), cy = by + 31 + CELL * (i / 5);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, canMake(r) ? SLOT_OK : SLOT_NO, cx, cy, CELL, CELL);
			g.item(r.display(), cx + 4, cy + 4);
			g.itemDecorations(font, r.display(), cx + 4, cy + 4);
			if (inside(mx, my, cx, cy, CELL, CELL)) tip = r;
		}
		if (pages() > 1) {
			String p = (page + 1) + "/" + pages();
			g.text(font, p, bx + 73 - font.width(p) / 2, by + 141, 0xFFFFFFFF, true);
			if (page > 0) {
				boolean h = inside(mx, my, bx + 38, by + 137, 12, 17);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, h ? PAGE_BACKWARD_HOVER : PAGE_BACKWARD, bx + 38, by + 137, 12, 17);
			}
			if (page < pages() - 1) {
				boolean h = inside(mx, my, bx + 93, by + 137, 12, 17);
				g.blitSprite(RenderPipelines.GUI_TEXTURED, h ? PAGE_FORWARD_HOVER : PAGE_FORWARD, bx + 93, by + 137, 12, 17);
			}
		}
		if (tip != null) {
			List<Component> lines = new ArrayList<>();
			// название, «Создается из:» и каждый исходный блок с новой строки
			lines.add(tip.display().getHoverName());
			lines.add(Component.translatable("workstationhelper.stone.made_from").withStyle(ChatFormatting.GRAY));
			List<String> seen = new ArrayList<>();
			for (Recipe r : tip.recipes()) {
				String n = r.inputIcon().getHoverName().getString();
				if (seen.contains(n)) continue;
				seen.add(n);
				lines.add(Component.literal(n).withStyle(ChatFormatting.GRAY));
			}
			g.setComponentTooltipForNextFrame(font, lines, mx, my);
		}
	}

	private void drawTab(GuiGraphicsExtractor g, int i, boolean selected) {
		ItemStack icon = new ItemStack(i == 0 ? Items.STONECUTTER : Items.EMERALD);
		int tx = bx - 30 - (selected ? 2 : 0), ty = by + 3 + TAB_H * i;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, selected ? TAB_SELECTED : TAB, tx, ty, TAB_W, TAB_H);
		g.item(icon, tx + 9, ty + 5);
	}

	// ======================== клики ========================

	private boolean click(double mx, double my) {
		// по полю поиска — пусть игра сама поставит в него курсор; клик в любое другое место убирает курсор
		if (search.visible && inside(mx, my, search.getX(), search.getY(), search.getWidth(), search.getHeight())) return false;
		search.setFocused(false);
		if (screen.getFocused() == search) screen.setFocused(null);
		if (fits && onToggle(mx, my)) {
			Config.get().stonePanels = !Config.get().stonePanels;
			Vanilla.clickSound();
			Config.save();
			return true;
		}
		if (!shown()) return false;
		for (int i = 0; i < 2; i++) {
			if (inside(mx, my, bx - 30, by + 3 + TAB_H * i, TAB_W, TAB_H)) {
				tab = i;
				Vanilla.clickSound();
				page = 0;
				filter();
				return true;
			}
		}
		if (!inside(mx, my, bx, by, BOOK_W, BOOK_H)) return false;
		if (inside(mx, my, bx + 110, by + 12, 26, 16)) {
			onlyCraftable = !onlyCraftable;
			Vanilla.clickSound();
			page = 0;
			filter();
			return true;
		}
		if (page > 0 && inside(mx, my, bx + 38, by + 137, 12, 17)) {
			page--;
			Vanilla.clickSound();
			return true;
		}
		if (page < pages() - 1 && inside(mx, my, bx + 93, by + 137, 12, 17)) {
			page++;
			Vanilla.clickSound();
			return true;
		}
		for (int i = 0; i < PER_PAGE; i++) {
			int idx = page * PER_PAGE + i;
			if (idx >= shown.size()) break;
			// без нашего щелчка — камнерез сам проиграет звук выбора рецепта
			if (inside(mx, my, bx + 11 + CELL * (i % 5), by + 31 + CELL * (i / 5), CELL, CELL)) fill(best(shown.get(idx)));
		}
		return true;
	}

	private boolean scroll(double mx, double my, double v) {
		if (!shown() || !inside(mx, my, bx, by, BOOK_W, BOOK_H)) return false;
		page = Math.clamp(page - (int) Math.signum(v), 0, pages() - 1);
		return true;
	}

	/** Пока курсор в поиске, клавиши — ему (иначе «E» закроет камнерез, а цифры переложат вещи). */
	private boolean key(KeyEvent e) {
		if (!search.isFocused()) return false;
		if (e.key() == InputConstants.KEY_ESCAPE) {
			search.setFocused(false);
			screen.setFocused(null);
			return true;
		}
		search.keyPressed(e);
		return true;
	}

	// ======================== перенос в камнерез ========================

	/** Как в книге верстака: кладёт нужный блок в камнерез (что лежало — обратно в инвентарь) и выбирает рецепт. */
	private void fill(Recipe r) {
		if (mc.gameMode == null || mc.player == null) return;
		StonecutterMenu m = menu();
		Slot in = m.getSlot(StonecutterMenu.INPUT_SLOT);
		if (!r.input().test(in.getItem())) {
			if (!m.getCarried().isEmpty()) {
				error(Component.translatable("workstationhelper.loom.carried"));
				return;
			}
			int from = -1;
			for (Slot s : m.slots) {
				if (s.container instanceof Inventory && r.input().test(s.getItem())) {
					from = s.index;
					break;
				}
			}
			if (from < 0) {
				error(Component.translatable("workstationhelper.brew.missing", r.inputIcon().getHoverName()));
				return;
			}
			int id = m.containerId;
			if (!in.getItem().isEmpty()) {
				mc.gameMode.handleContainerInput(id, StonecutterMenu.INPUT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player);
				if (!in.getItem().isEmpty()) {
					error(Component.translatable("workstationhelper.stone.full"));
					return;
				}
			}
			mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, mc.player);
			mc.gameMode.handleContainerInput(id, StonecutterMenu.INPUT_SLOT, 0, ContainerInput.PICKUP, mc.player);
			if (!m.getCarried().isEmpty()) mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, mc.player);
		}
		pending = r;
		pendingTicks = 20;
		selectPending();
	}

	/** Выбирает рецепт в списке камнереза — так же, как клик по нему. */
	private void selectPending() {
		if (pending == null || mc.player == null || mc.gameMode == null) return;
		var ctx = SlotDisplayContext.fromLevel(mc.level);
		List<SelectableRecipe.SingleInputEntry<StonecutterRecipe>> list = menu().getVisibleRecipes().entries();
		for (int i = 0; i < list.size(); i++) {
			ItemStack out = list.get(i).recipe().optionDisplay().resolveForFirstStack(ctx);
			if (ItemStack.isSameItemSameComponents(out, pending.output()) && out.getCount() == pending.output().getCount()) {
				if (menu().getSelectedRecipeIndex() != i && menu().clickMenuButton(mc.player, i)) {
					mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0f));
					mc.gameMode.handleInventoryButtonClick(menu().containerId, i);
				}
				pending = null;
				return;
			}
		}
	}

	private void tick() {
		if (pending == null) return;
		if (--pendingTicks <= 0) pending = null;
		else selectPending();
	}

	private void error(Component text) {
		mc.gui.chatListener().handleOverlay(text.copy().withStyle(ChatFormatting.RED));
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.DISPENSER_FAIL, 1.0f));
	}

	private static boolean inside(double mx, double my, int x, int y, int w, int h) {
		return mx >= x && my >= y && mx < x + w && my < y + h;
	}
}
