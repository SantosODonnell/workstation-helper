package dev.santos.workstationhelper.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.santos.workstationhelper.Config;
import dev.santos.workstationhelper.mixin.ContainerScreenAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.banner.BannerFlagModel;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BannerPatternTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BannerPattern;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

/**
 * Две панели у ткацкого станка. Слева — поиск, готовые узоры и свои (флаг, брошенный на плюсик, сохраняет свой узор),
 * справа — название узора (можно переименовать), шаги-кнопки и «Удалить узор». Клик по шагу кладёт в станок флаг,
 * краситель, предмет-узор и сам выбирает узор. Мини-кнопка в правом верхнем углу окна прячет и показывает панели.
 */
public final class LoomPanels {
	private static final int PAD = 7;
	private static final int STEP_ROW = 20;
	/** ячейка с флагом: сам флаг 20×40 и рамка вокруг */
	private static final int CELL_W = 22, CELL_H = 42, GAP = 2;
	/** станок берёт флаг максимум с 6 слоями */
	private static final int MAX_LAYERS = 6;
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier BUTTON_HOVER = Identifier.withDefaultNamespace("widget/button_highlighted");
	private static final Identifier BUTTON_DISABLED = Identifier.withDefaultNamespace("widget/button_disabled");
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/villager/scroller");
	private static final Identifier SCROLLER_OFF = Identifier.withDefaultNamespace("container/villager/scroller_disabled");

	/** Слой узора: id узора (minecraft:rhombus) и цвет. */
	private record Layer(String pattern, DyeColor color) {
	}

	/** Узор флага: название (готовый) или номер (свой), цвет основы и слои. */
	private record Design(String key, Component name, DyeColor base, List<Layer> layers) {
	}

	/** Готовые узоры — флаги, которые придумал автор мода (часть требует предмет-узор: череп, цветок, крипер, кайма, «нечто»). */
	private static final List<Design> READY = List.of(
		ready("portal_ring", DyeColor.BLACK, "stripe_downleft", DyeColor.WHITE, "rhombus", DyeColor.PURPLE, "stripe_downleft", DyeColor.BLACK, "circle", DyeColor.MAGENTA, "flower", DyeColor.BLACK),
		ready("skull", DyeColor.BLACK, "skull", DyeColor.WHITE, "stripe_top", DyeColor.BROWN, "curly_border", DyeColor.BLACK, "triangles_top", DyeColor.BLACK),
		ready("demon", DyeColor.BLACK, "small_stripes", DyeColor.RED, "flower", DyeColor.ORANGE, "curly_border", DyeColor.BLACK, "triangle_top", DyeColor.BLACK, "skull", DyeColor.RED, "creeper", DyeColor.BLACK),
		ready("kontora", DyeColor.BLACK, "flower", DyeColor.YELLOW, "straight_cross", DyeColor.RED, "stripe_middle", DyeColor.YELLOW, "straight_cross", DyeColor.YELLOW, "triangle_top", DyeColor.BLACK, "gradient", DyeColor.RED),
		ready("british", DyeColor.BLUE, "stripe_middle", DyeColor.WHITE, "straight_cross", DyeColor.WHITE, "stripe_downleft", DyeColor.WHITE, "stripe_downright", DyeColor.WHITE, "cross", DyeColor.RED, "straight_cross", DyeColor.RED),
		ready("carrot", DyeColor.RED, "stripe_right", DyeColor.GREEN, "rhombus", DyeColor.BLUE, "half_horizontal_bottom", DyeColor.BLUE, "circle", DyeColor.ORANGE, "diagonal_left", DyeColor.BLUE, "curly_border", DyeColor.BLUE),
		ready("impire", DyeColor.RED, "stripe_center", DyeColor.YELLOW, "triangles_top", DyeColor.RED, "triangles_bottom", DyeColor.RED, "circle", DyeColor.YELLOW, "flower", DyeColor.RED),
		ready("spider", DyeColor.BLACK, "small_stripes", DyeColor.RED, "straight_cross", DyeColor.RED, "triangles_top", DyeColor.RED, "triangles_bottom", DyeColor.RED, "flower", DyeColor.BLACK, "circle", DyeColor.BLACK),
		ready("dragon", DyeColor.RED, "curly_border", DyeColor.BLACK, "stripe_right", DyeColor.RED, "flower", DyeColor.GRAY, "stripe_middle", DyeColor.BLACK, "triangle_top", DyeColor.RED, "mojang", DyeColor.BLACK),
		ready("rabbit", DyeColor.PINK, "circle", DyeColor.WHITE, "flower", DyeColor.WHITE, "cross", DyeColor.WHITE, "stripe_middle", DyeColor.BLACK, "curly_border", DyeColor.MAGENTA, "triangle_bottom", DyeColor.WHITE),
		ready("miror", DyeColor.LIGHT_BLUE, "gradient", DyeColor.WHITE, "diagonal_right", DyeColor.WHITE, "border", DyeColor.BROWN),
		ready("love", DyeColor.BLACK, "cross", DyeColor.PINK, "rhombus", DyeColor.PINK, "curly_border", DyeColor.BLACK, "stripe_downright", DyeColor.BLACK, "circle", DyeColor.BLACK, "stripe_middle", DyeColor.BLACK),
		ready("ghost", DyeColor.WHITE, "creeper", DyeColor.BLACK, "stripe_center", DyeColor.BLACK, "rhombus", DyeColor.BLACK, "triangle_top", DyeColor.WHITE, "curly_border", DyeColor.WHITE, "cross", DyeColor.WHITE));

	private static Design ready(String id, DyeColor base, Object... layers) {
		List<Layer> out = new ArrayList<>();
		for (int i = 0; i < layers.length; i += 2) out.add(new Layer("minecraft:" + layers[i], (DyeColor) layers[i + 1]));
		return new Design("ready:" + id, Component.translatable("workstationhelper.loom.d." + id), base, out);
	}

	/** Выбранный узор — помнится, пока запущена игра. */
	private static String selectedKey;
	/** Открытые сейчас панели — для строки «4/6» в подсказке флага. */
	private static LoomPanels active;

	private final LoomScreen screen;
	private final Minecraft mc;
	private final Font font;
	private final BannerFlagModel flag;
	private boolean fits;
	private int lx, rx, py, lpw, pw, ph;
	private int gridTop, gridBottom, cols;
	private int leftScroll, rightScroll;
	private final List<Design> designs = new ArrayList<>();
	/** узоры, подходящие под поиск, — их и показывает сетка */
	private final List<Design> visible = new ArrayList<>();
	private EditBox search, name;
	/** первый клик по «Удалить узор» — спросить «Вы уверены?», второй — удалить */
	private boolean confirmDelete;
	/** левое окно показывает подсказку: где взять предметы-узоры (помнится, пока запущена игра) */
	private static boolean hintMode;
	private int hintScroll;

	/** Предметы-узоры и где их взять. */
	/** Предмет-узор, где его взять и с чем скрафтить из бумаги (null — не крафтится). */
	private record Hint(String item, String how, String ingredient) {
	}

	private static final List<Hint> HINTS = List.of(
		new Hint("flower_banner_pattern", "flower", "oxeye_daisy"),
		new Hint("creeper_banner_pattern", "creeper", "creeper_head"),
		new Hint("skull_banner_pattern", "skull", "wither_skeleton_skull"),
		new Hint("mojang_banner_pattern", "mojang", "enchanted_golden_apple"),
		new Hint("field_masoned_banner_pattern", "bricks", "bricks"),
		new Hint("bordure_indented_banner_pattern", "bordure", "vine"),
		new Hint("globe_banner_pattern", "globe", null),
		new Hint("piglin_banner_pattern", "piglin", null),
		new Hint("flow_banner_pattern", "flow", null),
		new Hint("guster_banner_pattern", "guster", null));
	private static final int HINT_ROW = 20;
	/** Флаг, только что брошенный на плюсик кликом: отпускание той же кнопки его не сохраняет второй раз. */
	private boolean justAdded;
	/** Узор, который надо выбрать в списке станка, как только в нём появятся флаг и краситель. */
	private Holder<BannerPattern> pending;
	private int pendingTicks;
	private final Map<DyeColor, Item> banners = new HashMap<>();

	private LoomPanels(LoomScreen screen) {
		this.screen = screen;
		this.mc = Minecraft.getInstance();
		this.font = mc.font;
		this.flag = new BannerFlagModel(mc.getEntityModels().bakeLayer(ModelLayers.STANDING_BANNER_FLAG));
		for (Item item : BuiltInRegistries.ITEM) {
			if (item instanceof BannerItem b) banners.put(b.getColor(), item);
		}
	}

	/** Строка «4/6» внизу подсказки флага у ткацкого станка: сколько слоёв из 6 возможных на нём уже есть. */
	public static void registerTooltip() {
		ItemTooltipCallback.EVENT.register((stack, ctx, flag, lines) -> {
			if (!Config.get().helper || !(Minecraft.getInstance().gui.screen() instanceof LoomScreen) || !(stack.getItem() instanceof BannerItem)) return;
			int have = stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().size();
			if (have >= 1) lines.add(Component.literal(have + "/" + MAX_LAYERS).withStyle(ChatFormatting.GRAY));
		});
	}

	public static void attach(LoomScreen screen) {
		LoomPanels p = new LoomPanels(screen);
		active = p;
		p.reload();
		p.layout();
		ScreenEvents.afterBackground(screen).register((s, g, mx, my, a) -> p.extract(g, mx, my));
		ScreenEvents.afterTick(screen).register(s -> p.tick());
		// подсказка не запоминается: стол закрыли — в следующий раз откроется обычный вид
		ScreenEvents.remove(screen).register(s -> hintMode = false);
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !p.click(e.x(), e.y()));
		// отпускание кнопки над панелями тоже наше: иначе игра решит, что мышь «вне окна», и выбросит вещь из руки
		ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !p.release(e.x(), e.y()));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !p.scroll(x, y, v));
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !p.key(e));
	}

	private ContainerScreenAccessor acc() {
		return (ContainerScreenAccessor) screen;
	}

	private LoomMenu menu() {
		return screen.getMenu();
	}

	private boolean shown() {
		return fits && Config.get().loomPanels;
	}

	private Design selected() {
		for (Design d : designs) {
			if (d.key().equals(selectedKey)) return d;
		}
		return null;
	}

	// ======================== раскладка ========================

	private void layout() {
		int left = acc().workstationhelper$leftPos(), top = acc().workstationhelper$topPos();
		int iw = acc().workstationhelper$imageWidth();
		lpw = Math.min(140, left - 4);
		pw = Math.min(150, screen.width - left - iw - 4);
		fits = lpw >= 100 && pw >= 100;
		ph = acc().workstationhelper$imageHeight();
		py = top;
		lx = left - lpw - 2;
		rx = left + iw + 2;
		gridTop = py + 22;
		gridBottom = py + ph - PAD;
		cols = Math.max(1, (lpw - 2 * PAD - 8 + GAP) / (CELL_W + GAP));
		// поиск — вместо заголовка «Узоры»
		search = new EditBox(font, lx + PAD, py + 6, lpw - 2 * PAD - 16, 13, Component.translatable("workstationhelper.loom.search"));
		search.setHint(Component.translatable("workstationhelper.loom.search").withStyle(ChatFormatting.DARK_GRAY));
		search.setMaxLength(50);
		search.setResponder(t -> {
			leftScroll = 0;
			hintScroll = 0;
			filter();
		});
		// название выбранного узора — вместо заголовка «Шаги»; справа галочка
		name = new EditBox(font, rx + PAD, py + 6, pw - 2 * PAD - 16, 13, Component.translatable("workstationhelper.loom.name"));
		name.setMaxLength(50);
		name.setHint(Component.translatable("workstationhelper.loom.enter_name").withStyle(ChatFormatting.DARK_GRAY));
		Screens.getWidgets(screen).add(search);
		Screens.getWidgets(screen).add(name);
		filter();
		syncName();
		updateBoxes();
	}

	private void updateBoxes() {
		// в подсказке вместо поиска — заголовок «Узоры для флагов»
		search.visible = shown() && !hintMode;
		name.visible = shown() && selected() != null;
		if (!search.visible) search.setFocused(false);
		if (!name.visible) name.setFocused(false);
	}

	private void filter() {
		visible.clear();
		String q = search == null ? "" : search.getValue().trim().toLowerCase(java.util.Locale.ROOT);
		for (Design d : designs) {
			if (q.isEmpty() || d.name().getString().toLowerCase(java.util.Locale.ROOT).contains(q)) visible.add(d);
		}
	}

	/** В поле названия — название выбранного узора. */
	private void syncName() {
		Design d = selected();
		// у своего узора без названия поле пустое — в нём серая подсказка «Введите название»
		boolean unnamed = d != null && d.key().startsWith("custom:") && Config.get().banners.get(Integer.parseInt(d.key().substring("custom:".length()))).name == null;
		if (name != null) name.setValue(d == null || unnamed ? "" : d.name().getString());
		confirmDelete = false;
	}

	private void select(Design d) {
		selectedKey = d == null ? null : d.key();
		rightScroll = 0;
		syncName();
		updateBoxes();
	}

	/** Готовые узоры плюс свои из настроек. */
	private void reload() {
		designs.clear();
		layerCache.clear();
		Config cfg = Config.get();
		for (Design d : READY) {
			String id = d.key().substring("ready:".length());
			if (cfg.hiddenReady.contains(id)) continue;
			String own = cfg.readyNames.get(id);
			designs.add(own == null ? d : new Design(d.key(), Component.literal(own), d.base(), d.layers()));
		}
		List<Config.SavedBanner> saved = cfg.banners;
		for (int i = 0; i < saved.size(); i++) {
			Config.SavedBanner s = saved.get(i);
			List<Layer> layers = new ArrayList<>();
			for (int l = 0; l < Math.min(s.patterns.size(), s.colors.size()); l++) {
				layers.add(new Layer(s.patterns.get(l), DyeColor.byName(s.colors.get(l), DyeColor.WHITE)));
			}
			Component title = s.name != null ? Component.literal(s.name) : Component.translatable("workstationhelper.loom.unnamed");
			designs.add(new Design("custom:" + i, title, DyeColor.byName(s.base, DyeColor.WHITE), layers));
		}
		filter();
	}

	/** Мини-кнопка «−/+» в правом верхнем углу окна. */
	private int toggleX() {
		return Vanilla.toggleRightX(acc().workstationhelper$leftPos(), acc().workstationhelper$imageWidth());
	}

	private int toggleY() {
		return Vanilla.toggleY(acc().workstationhelper$topPos());
	}

	private boolean onToggle(double mx, double my) {
		return inside(mx, my, toggleX(), toggleY(), Vanilla.TOGGLE, Vanilla.TOGGLE);
	}

	// ======================== узоры игры ========================

	private Optional<Holder.Reference<BannerPattern>> pattern(String id) {
		if (mc.level == null) return Optional.empty();
		Identifier rl = Identifier.tryParse(id);
		if (rl == null) return Optional.empty();
		return mc.level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN).get(ResourceKey.create(Registries.BANNER_PATTERN, rl));
	}

	/** Уже собранные слои: «ключ узора/число слоёв» → слои. Очищается, когда меняется список узоров. */
	private final Map<String, BannerPatternLayers> layerCache = new HashMap<>();

	/** Первые n слоёв узора — в том виде, в каком их хранит флаг. */
	private BannerPatternLayers layers(Design d, int n) {
		return layerCache.computeIfAbsent(d.key() + "/" + n, k -> buildLayers(d, n));
	}

	private BannerPatternLayers buildLayers(Design d, int n) {
		List<BannerPatternLayers.Layer> out = new ArrayList<>();
		for (int i = 0; i < Math.min(n, d.layers().size()); i++) {
			Layer l = d.layers().get(i);
			pattern(l.pattern()).ifPresent(h -> out.add(new BannerPatternLayers.Layer(h, l.color())));
		}
		return new BannerPatternLayers(out);
	}

	/** Флаг цвета основы с первыми n слоями. */
	private ItemStack banner(Design d, int n) {
		Item item = banners.get(d.base());
		if (item == null) return ItemStack.EMPTY;
		ItemStack stack = new ItemStack(item);
		BannerPatternLayers l = layers(d, n);
		if (!l.layers().isEmpty()) stack.set(DataComponents.BANNER_PATTERNS, l);
		return stack;
	}

	private static ItemStack dye(DyeColor c) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(c.getName() + "_dye")));
	}

	/** Найденные предметы-узоры: узор → предмет (пусто — предмет не нужен). Ищутся один раз, а не каждый кадр. */
	private final Map<Holder<BannerPattern>, ItemStack> patternItems = new HashMap<>();

	/** Предмет-узор, без которого станок не даст этот узор; пусто — узор есть и так. */
	private ItemStack patternItem(Holder<BannerPattern> h) {
		return patternItems.computeIfAbsent(h, k -> {
			if (k.is(BannerPatternTags.NO_ITEM_REQUIRED)) return ItemStack.EMPTY;
			for (Item item : BuiltInRegistries.ITEM) {
				HolderSet<BannerPattern> set = item.components().get(DataComponents.PROVIDES_BANNER_PATTERNS);
				if (set != null && set.contains(k)) return new ItemStack(item);
			}
			return ItemStack.EMPTY;
		});
	}

	private boolean isBanner(ItemStack s, Design d, int n) {
		return s.getItem() == banners.get(d.base()) && s.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).equals(layers(d, n));
	}

	private static boolean isDye(ItemStack s, DyeColor c) {
		return s.is(ItemTags.LOOM_DYES) && s.get(DataComponents.DYE) == c;
	}

	private static boolean givesPattern(ItemStack s, Holder<BannerPattern> h) {
		HolderSet<BannerPattern> set = s.get(DataComponents.PROVIDES_BANNER_PATTERNS);
		return set != null && set.contains(h);
	}

	/** Флаг, на котором уже есть первые n слоёв узора (или больше): значит, шаг n уже сделан. */
	private boolean hasPrefix(ItemStack s, Design d, int n) {
		if (s.getItem() != banners.get(d.base())) return false;
		List<BannerPatternLayers.Layer> have = s.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
		List<BannerPatternLayers.Layer> want = layers(d, n).layers();
		return have.size() >= want.size() && have.subList(0, want.size()).equals(want);
	}

	// ======================== отрисовка ========================

	private void extract(GuiGraphicsExtractor g, int mx, int my) {
		updateBoxes();
		extractToggle(g, mx, my);
		if (!shown()) return;
		extractLeft(g, mx, my);
		Design d = selected();
		if (d != null) extractRight(g, mx, my, d);
	}

	private void extractToggle(GuiGraphicsExtractor g, int mx, int my) {
		if (!fits) return;
		int x = toggleX(), y = toggleY();
		boolean hover = onToggle(mx, my);
		Vanilla.toggle(g, x, y, hover, Config.get().loomPanels);
		if (hover) g.setTooltipForNextFrame(font, Component.translatable(Config.get().loomPanels ? "workstationhelper.loom.hide" : "workstationhelper.loom.show"), mx, my);
	}

	/** Ячейка i сетки (плюсик идёт последней, после всех узоров). */
	private int cellX(int i) {
		return lx + PAD + (i % cols) * (CELL_W + GAP);
	}

	private int cellY(int i) {
		return gridTop + (i / cols) * (CELL_H + GAP) - leftScroll;
	}

	private int maxLeftScroll() {
		int rows = (visible.size() + 1 + cols - 1) / cols;
		return Math.max(0, rows * (CELL_H + GAP) - GAP - (gridBottom - gridTop));
	}

	private int hintX() {
		return lx + lpw - PAD - 14;
	}

	private void extractHintButton(GuiGraphicsExtractor g, int mx, int my) {
		int x = hintX(), y = py + 5;
		boolean hover = inside(mx, my, x, y, 14, 14);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? BUTTON_HOVER : BUTTON, x, y, 14, 14);
		g.text(font, "?", x + 5, y + 3, hintMode ? 0xFFFFFF55 : 0xFFFFFFFF, true);
		if (hover) g.setTooltipForNextFrame(font, Component.translatable(hintMode ? "workstationhelper.loom.hint_off" : "workstationhelper.loom.hint"), mx, my);
	}

	private static ItemStack stackOf(String id) {
		return new ItemStack(BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id)));
	}

	/** Короткое название узора: «Цветок» из «Узор флага «Цветок»». */
	private static String shortName(ItemStack stack) {
		String full = stack.getHoverName().getString();
		int a = full.indexOf('«'), b = full.lastIndexOf('»');
		if (a >= 0 && b > a) return full.substring(a + 1, b);
		// английский: «Flower Charge Banner Pattern» → «Flower»
		return full.replace("Banner Pattern", "").replace("Charge", "").trim().replaceAll(" +", " ");
	}

	/** Ширина рамки с описанием (слева от ячейки узора). */
	private int howW() {
		return lpw - 2 * PAD - 8 - 22;
	}

	/** Все готовые узоры — в одном столбце у правого края списка, как результат в верстаке. */
	private int resultX() {
		return lx + lpw - PAD - 8 - 18;
	}

	/** Высота рамки с описанием: строки текста и отступы. */
	private int howH(Hint h) {
		return font.split(Component.translatable("workstationhelper.loom.how." + h.how()), howW() - 6).size() * 9 + 5;
	}

	/** Высота вдавленной зоны «где достать + узор». */
	private int zoneH(Hint h) {
		return Math.max(20, howH(h));
	}

	/** Высота записи: название, под ним мини-верстак 2×2 или вдавленная зона с описанием и узором. */
	private int hintHeight(Hint h) {
		if (h.ingredient() != null) return 10 + 2 * 18 + 6;
		return 10 + zoneH(h) + 6;
	}

	/** Ячейки готовых предметов-узоров в подсказке (для выдачи в творческом режиме): x, y, предмет. */
	private final List<Object[]> hintSlots = new ArrayList<>();

	private boolean creative() {
		return mc.player != null && mc.player.hasInfiniteMaterials();
	}

	/**
	 * Творческий режим: 1 штука предмета сразу на курсор, как в творческом инвентаре (так же, как у стола картографа):
	 * предмет кладётся в свободный слот инвентаря и тут же берётся оттуда мышкой.
	 */
	private void giveToCursor(ItemStack stack) {
		if (!creative() || mc.gameMode == null) return;
		LoomMenu m = menu();
		if (!m.getCarried().isEmpty()) return;
		var inv = mc.player.getInventory();
		int free = inv.getFreeSlot();
		if (free < 0) return;
		ItemStack one = stack.copyWithCount(1);
		inv.setItem(free, one);
		int invSlot = free < 9 ? net.minecraft.world.inventory.InventoryMenu.USE_ROW_SLOT_START + free : free;
		// серверу — отдельная копия: пакет уходит не сразу, а этот же предмет мы тут же берём на курсор (иначе кикает)
		mc.gameMode.handleCreativeModeItemAdd(one.copy(), invSlot);
		for (Slot slot : m.slots) {
			if (slot.container == inv && slot.getContainerSlot() == free) {
				mc.gameMode.handleContainerInput(m.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
				break;
			}
		}
	}

	/** Ячейка готового узора: в творческом режиме подсвечивается при наведении, клик выдаёт предмет. */
	private void resultSlot(GuiGraphicsExtractor g, ItemStack stack, int x, int y, boolean hover) {
		Vanilla.inset(g, x, y, 18, 18, Vanilla.SLOT);
		g.item(stack, x + 1, y + 1);
		if (hover && creative()) g.fill(x + 1, y + 1, x + 17, y + 17, 0x80FFFFFF);
		hintSlots.add(new Object[] {x, y, stack});
	}

	/** Серая стрелка, как в мини-верстаке инвентаря. */
	private static void arrow(GuiGraphicsExtractor g, int x, int y) {
		int c = 0xFF8B8B8B;
		g.fill(x, y + 3, x + 9, y + 6, c);
		for (int i = 0; i < 5; i++) g.fill(x + 9 + i, y + i, x + 10 + i, y + 9 - i, c);
	}

	/** Высота строки «Копирование флага»: название и мини-верстак 2×2. */
	private static final int COPY_H = 10 + 2 * 18 + 6;

	/** «Копирование флага» на примере готового флага Impire; возвращает предмет под мышкой (для подсказки). */
	private ItemStack extractCopyHint(GuiGraphicsExtractor g, int mx, int my, int x, int y, boolean inList) {
		Design impire = null;
		for (Design d : READY) {
			if (d.key().equals("ready:impire")) impire = d;
		}
		if (impire == null) return null;
		String title = Component.translatable("workstationhelper.loom.copy").getString();
		g.text(font, title, x + (resultX() + 18 - x - font.width(title)) / 2, y, Vanilla.TEXT, false);
		int sy = y + 10;
		ItemStack full = banner(impire, MAX_LAYERS), blank = banner(impire, 0), two = full.copyWithCount(2);
		ItemStack[] grid = {full, blank, ItemStack.EMPTY, ItemStack.EMPTY};
		int gx = x + (resultX() + 18 - x - (36 + 5 + 14 + 5 + 18)) / 2, ax = gx + 36 + 5, rx0 = ax + 14 + 5, ry0 = sy + 9;
		ItemStack tip = null;
		for (int i = 0; i < 4; i++) {
			int cx = gx + (i % 2) * 18, cy = sy + (i / 2) * 18;
			Vanilla.inset(g, cx, cy, 18, 18, Vanilla.SLOT);
			if (!grid[i].isEmpty()) {
				g.item(grid[i], cx + 1, cy + 1);
				if (inList && inside(mx, my, cx, cy, 18, 18)) tip = grid[i];
			}
		}
		arrow(g, ax, sy + 14);
		Vanilla.inset(g, rx0, ry0, 18, 18, Vanilla.SLOT);
		g.item(two, rx0 + 1, ry0 + 1);
		g.itemDecorations(font, two, rx0 + 1, ry0 + 1);
		if (inList && inside(mx, my, rx0, ry0, 18, 18)) tip = two;
		return tip;
	}

	private void extractHints(GuiGraphicsExtractor g, int mx, int my) {
		g.text(font, Component.translatable("workstationhelper.loom.patterns"), lx + PAD, py + 8, Vanilla.TEXT, false);
		int top = gridTop, bottom = gridBottom;
		int total = 0;
		total += COPY_H;
		for (Hint h : HINTS) total += hintHeight(h);
		int maxScroll = Math.max(0, total - (bottom - top));
		hintScroll = Math.clamp(hintScroll, 0, maxScroll);
		ItemStack tip = null;
		hintSlots.clear();
		g.enableScissor(lx + PAD, top, lx + lpw - PAD, bottom);
		int y = top - hintScroll, x = lx + PAD;
		boolean inList = my >= top && my < bottom;
		// первой — как скопировать флаг: флаг с узором + чистый флаг того же цвета → две копии
		ItemStack copyTip = extractCopyHint(g, mx, my, x, y, inList);
		if (copyTip != null) tip = copyTip;
		y += COPY_H;
		for (Hint h : HINTS) {
			int hh = hintHeight(h);
			if (y + hh >= top && y <= bottom) {
				ItemStack result = stackOf(h.item());
				// название — по центру списка: Узор «Цветок» (у длинной «Зубчатой каймы» — без слова «Узор»)
				String title = Component.translatable("bordure".equals(h.how()) ? "workstationhelper.loom.pattern_title.short" : "workstationhelper.loom.pattern_title", shortName(result)).getString();
				g.text(font, title, x + (resultX() + 18 - x - font.width(title)) / 2, y, Vanilla.TEXT, false);
				int sy = y + 10;
				if (h.ingredient() != null) {
					// мини-верстак 2×2: бумага и второй предмет → предмет-узор
					ItemStack[] grid = {new ItemStack(Items.PAPER), stackOf(h.ingredient()), ItemStack.EMPTY, ItemStack.EMPTY};
					// сетка, стрелка и результат стоят плотно, как в инвентаре, и вместе — по центру строки
					int gx = x + (resultX() + 18 - x - (36 + 5 + 14 + 5 + 18)) / 2, ax = gx + 36 + 5, rx0 = ax + 14 + 5, ry0 = sy + 9;
					for (int i = 0; i < 4; i++) {
						int cx = gx + (i % 2) * 18, cy = sy + (i / 2) * 18;
						Vanilla.inset(g, cx, cy, 18, 18, Vanilla.SLOT);
						if (!grid[i].isEmpty()) {
							g.item(grid[i], cx + 1, cy + 1);
							if (inList && inside(mx, my, cx, cy, 18, 18)) tip = grid[i];
						}
					}
					arrow(g, ax, sy + 14);
					boolean over = inList && inside(mx, my, rx0, ry0, 18, 18);
					resultSlot(g, result, rx0, ry0, over);
					if (over) tip = result;
				} else {
					// не крафтится: одна вдавленная зона — слева где его достать, справа сам узор (на тех же местах)
					int zh = zoneH(h), sx = resultX() - 4, slotY = sy + (zh - 18) / 2;
					var lines = font.split(Component.translatable("workstationhelper.loom.how." + h.how()), howW() - 6);
					// отступ внутри рамки — по 3 пикселя слева (текст) и справа (узор)
					Vanilla.inset(g, x, sy, resultX() + 18 - 2 - x, zh, Vanilla.SLOT);
					int ty = sy + (zh - lines.size() * 9) / 2 + 1;
					for (var line : lines) {
						g.text(font, line, x + 3, ty, 0xFFE0E0E0, true);
						ty += 9;
					}
					boolean over = inList && inside(mx, my, sx, slotY, 18, 18);
					g.item(result, sx + 1, slotY + 1);
					if (over && creative()) g.fill(sx + 1, slotY + 1, sx + 17, slotY + 17, 0x80FFFFFF);
					hintSlots.add(new Object[] {sx, slotY, result});
					if (over) tip = result;
				}
			}
			y += hh;
		}
		g.disableScissor();
		scroller(g, lx + lpw - PAD - 6, top, bottom - top, hintScroll, maxScroll);
		if (tip != null) g.setTooltipForNextFrame(font, tip.getHoverName(), mx, my);
	}

	private void extractLeft(GuiGraphicsExtractor g, int mx, int my) {
		Vanilla.panel(g, lx, py, lpw, ph);
		extractHintButton(g, mx, my);
		if (hintMode) {
			extractHints(g, mx, my);
			return;
		}
		leftScroll = Math.clamp(leftScroll, 0, maxLeftScroll());
		boolean inGrid = my >= gridTop && my < gridBottom;
		Component tip = null;
		g.enableScissor(lx + PAD, gridTop, lx + lpw - PAD, gridBottom);
		for (int i = 0; i <= visible.size(); i++) {
			int x = cellX(i), y = cellY(i);
			if (y + CELL_H < gridTop || y > gridBottom) continue;
			boolean hover = inGrid && inside(mx, my, x, y, CELL_W, CELL_H);
			if (i == visible.size()) {
				// плюсик: сюда бросают флаг, чтобы сохранить его узор
				boolean carrying = menu().getCarried().getItem() instanceof BannerItem;
				Vanilla.inset(g, x, y, CELL_W, CELL_H, carrying && hover ? 0xFF6CC349 : carrying ? 0xFF9DBF8E : Vanilla.SLOT);
				int cx = x + CELL_W / 2, cy = y + CELL_H / 2;
				g.fill(cx - 4, cy - 1, cx + 4, cy + 1, 0xFFFFFFFF);
				g.fill(cx - 1, cy - 4, cx + 1, cy + 4, 0xFFFFFFFF);
				if (hover) tip = Component.translatable("workstationhelper.loom.add");
				continue;
			}
			Design d = visible.get(i);
			Vanilla.inset(g, x, y, CELL_W, CELL_H, Vanilla.SLOT);
			g.bannerPattern(flag, d.base(), layers(d, MAX_LAYERS), x + 1, y + 1, x + 21, y + 41);
			if (d.key().equals(selectedKey)) g.outline(x, y, CELL_W, CELL_H, 0xFFFFFFFF);
			else if (hover) g.outline(x, y, CELL_W, CELL_H, 0xFFE0E0E0);
			if (hover) tip = d.name();
		}
		g.disableScissor();
		scroller(g, lx + lpw - PAD - 6, gridTop, gridBottom - gridTop, leftScroll, maxLeftScroll());
		if (tip != null) g.setTooltipForNextFrame(font, tip, mx, my);
	}

	/** Где на правой панели шаги и кнопка «Удалить узор». Шагов в ванилле не больше 6 — тогда всё помещается. */
	private int stepsTop() {
		return py + 22;
	}

	/** «Удалить узор» всегда внизу; высота 16 — чтобы над ней ровно влезли 6 шагов. */
	private static final int DELETE_H = 16;

	private int deleteY(Design d) {
		return py + ph - 5 - DELETE_H;
	}

	private int rowW() {
		return pw - 2 * PAD;
	}

	private int checkX() {
		return rx + pw - PAD - 14;
	}

	private void extractRight(GuiGraphicsExtractor g, int mx, int my, Design d) {
		Vanilla.panel(g, rx, py, pw, ph);
		// галочка справа от названия — сохранить новое название
		int cx = checkX(), cy = py + 5;
		boolean overCheck = inside(mx, my, cx, cy, 14, 14);
		g.blitSprite(RenderPipelines.GUI_TEXTURED, overCheck ? BUTTON_HOVER : BUTTON, cx, cy, 14, 14);
		check(g, cx + 1, cy + 1, 0xFF55FF55);
		if (overCheck) g.setTooltipForNextFrame(font, Component.translatable("workstationhelper.loom.rename"), mx, my);

		int top = stepsTop(), dy = deleteY(d), bottom = dy - 1;
		int n = d.layers().size();
		int maxScroll = Math.max(0, n * STEP_ROW - (bottom - top));
		rightScroll = Math.clamp(rightScroll, 0, maxScroll);
		int rowW = rowW();
		List<Component> tip = null;
		g.enableScissor(rx + PAD, top, rx + pw - PAD, bottom);
		for (int i = 0; i < n; i++) {
			int y = top + i * STEP_ROW - rightScroll;
			Layer l = d.layers().get(i);
			Optional<Holder.Reference<BannerPattern>> h = pattern(l.pattern());
			boolean hover = inside(mx, my, rx + PAD, y, rowW, STEP_ROW) && my >= top && my < bottom;
			// сделанный шаг — «вдавленный», как неактивная кнопка меню (так же, как в наковальне)
			boolean doneStep = stepDone(d, i);
			g.blitSprite(RenderPipelines.GUI_TEXTURED, doneStep ? BUTTON_DISABLED : hover ? BUTTON_HOVER : BUTTON, rx + PAD, y, rowW, STEP_ROW);
			int x = rx + PAD + 4;
			g.text(font, (i + 1) + ".", x, y + 6, doneStep ? 0xFFA0A0A0 : 0xFFE0E0E0, true);
			x += font.width((i + 1) + ".") + 2;
			// флаг до шага + краситель (+ предмет-узор) → флаг после шага
			ItemStack before = banner(d, i), dyeStack = dye(l.color());
			ItemStack item = h.map(this::patternItem).orElse(ItemStack.EMPTY);
			g.item(before, x, y + 2);
			g.text(font, "+", x + 18, y + 6, 0xFFFFFFFF, true);
			x += 25;
			g.item(dyeStack, x, y + 2);
			if (!item.isEmpty()) {
				g.text(font, "+", x + 18, y + 6, 0xFFFFFFFF, true);
				x += 25;
				g.item(item, x, y + 2);
			}
			int rx2 = rx + PAD + rowW - 20;
			g.text(font, "→", rx2 - 9, y + 6, 0xFFFFFFFF, true);
			g.item(banner(d, i + 1), rx2, y + 2);
			if (hover) {
				tip = new ArrayList<>();
				// на первом шаге — ещё и какой нужен флаг
				// лесенкой, как в подсказках наковальни: флаг → слой → чем его сделать
				String pad = "";
				if (i == 0) {
					tip.add(before.getHoverName().copy().withStyle(ChatFormatting.WHITE));
					pad = "  ";
				}
				if (h.isPresent()) tip.add(Component.literal(pad).append(new BannerPatternLayers.Layer(h.get(), l.color()).description()).withStyle(ChatFormatting.WHITE));
				tip.add(Component.literal(pad + "  ").append(dyeStack.getHoverName()).withStyle(ChatFormatting.GRAY));
				if (!item.isEmpty()) tip.add(Component.literal(pad + "  ").append(item.getHoverName()).withStyle(ChatFormatting.GRAY));
			}
		}
		g.disableScissor();

		// «Удалить узор» → «Вы уверены?» → удалить; увёл мышь — снова «Удалить узор»
		boolean overDelete = inside(mx, my, rx + PAD, dy, rowW, DELETE_H);
		if (!overDelete) confirmDelete = false;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, overDelete ? BUTTON_HOVER : BUTTON, rx + PAD, dy, rowW, DELETE_H);
		Component label = Component.translatable(confirmDelete ? "workstationhelper.loom.sure" : "workstationhelper.loom.delete");
		g.text(font, label, rx + PAD + (rowW - font.width(label)) / 2, dy + 4, confirmDelete ? 0xFFFF5555 : 0xFFFFFFFF, true);

		if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mx, my);
	}

	/** Галочка толщиной 2 пикселя в квадрате 12×12. */
	private static void check(GuiGraphicsExtractor g, int x, int y, int col) {
		int[][] pts = {{2, 6}, {3, 7}, {4, 8}, {5, 7}, {6, 6}, {7, 5}, {8, 4}, {9, 3}};
		for (int[] pt : pts) g.fill(x + pt[0], y + pt[1], x + pt[0] + 1, y + pt[1] + 2, col);
	}

	/** Дорожка с ползунком, как в окне торговли. */
	private static void scroller(GuiGraphicsExtractor g, int x, int y, int h, int scroll, int max) {
		Vanilla.inset(g, x - 1, y, 8, h, 0xFF6F6F6F);
		if (max > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, x, y + 1 + (h - 2 - 27) * scroll / max, 6, 27);
		else g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_OFF, x, y + 1, 6, 27);
	}

	// ======================== поиск вещей ========================

	/** Слоты, откуда можно брать вещи: инвентарь и три входных слота станка. */
	private boolean usable(Slot s) {
		LoomMenu m = menu();
		return s.container instanceof Inventory || s == m.getBannerSlot() || s == m.getDyeSlot() || s == m.getPatternSlot();
	}

	/** Шаг i сделан, если где-то (инвентарь, станок, результат, курсор) есть флаг с первыми i+1 слоями. */
	private boolean stepDone(Design d, int i) {
		if (hasPrefix(menu().getCarried(), d, i + 1)) return true;
		for (Slot s : menu().slots) {
			if ((usable(s) || s == menu().getResultSlot()) && hasPrefix(s.getItem(), d, i + 1)) return true;
		}
		return false;
	}

	/** Слот с подходящей вещью: сначала нужный слот станка, потом инвентарь; -1 — нет. */
	private int find(Slot target, java.util.function.Predicate<ItemStack> p) {
		if (p.test(target.getItem())) return target.index;
		for (Slot s : menu().slots) {
			if (s.container instanceof Inventory && p.test(s.getItem())) return s.index;
		}
		return -1;
	}

	// ======================== клики ========================

	private static boolean onBox(EditBox b, double mx, double my) {
		return b.visible && inside(mx, my, b.getX(), b.getY(), b.getWidth(), b.getHeight());
	}

	private boolean click(double mx, double my) {
		// по полю ввода — пусть игра сама поставит в него курсор; клик в любое другое место убирает курсор из полей
		if (onBox(search, mx, my) || onBox(name, mx, my)) return false;
		search.setFocused(false);
		name.setFocused(false);
		if (screen.getFocused() == search || screen.getFocused() == name) screen.setFocused(null);
		if (fits && onToggle(mx, my)) {
			Config.get().loomPanels = !Config.get().loomPanels;
			Vanilla.clickSound();
			Config.save();
			updateBoxes();
			return true;
		}
		if (!shown()) return false;
		if (inside(mx, my, lx, py, lpw, ph)) {
			if (inside(mx, my, hintX(), py + 5, 14, 14)) {
				hintMode = !hintMode;
				hintScroll = 0;
				Vanilla.clickSound();
				return true;
			}
			if (hintMode) {
				// творческий режим: клик по готовому узору — предмет на курсор
				if (creative() && my >= gridTop && my < gridBottom) {
					for (Object[] hs : hintSlots) {
						if (inside(mx, my, (int) hs[0], (int) hs[1], 18, 18)) giveToCursor((ItemStack) hs[2]);
					}
				}
				return true;
			}
			if (my >= gridTop && my < gridBottom) {
				for (int i = 0; i <= visible.size(); i++) {
					if (!inside(mx, my, cellX(i), cellY(i), CELL_W, CELL_H)) continue;
					if (i == visible.size()) {
						if (addCarried()) {
							justAdded = true;
							Vanilla.clickSound();
						}
					} else {
						// повторный клик по выбранному флагу снимает выбор — правая панель скрывается
						Design d = visible.get(i);
						Vanilla.clickSound();
						select(d.key().equals(selectedKey) ? null : d);
					}
					break;
				}
			}
			return true;
		}
		Design d = selected();
		if (d != null && inside(mx, my, rx, py, pw, ph)) {
			int top = stepsTop(), dy = deleteY(d);
			if (inside(mx, my, checkX(), py + 5, 14, 14)) {
				Vanilla.clickSound();
				rename(d);
			} else if (inside(mx, my, rx + PAD, dy, rowW(), DELETE_H)) {
				Vanilla.clickSound();
				if (confirmDelete) delete(d);
				else confirmDelete = true;
			} else if (my >= top && my < dy - 1) {
				int i = ((int) my - top + rightScroll) / STEP_ROW;
				// без нашего щелчка — станок сам проиграет звук выбора узора
				if (i >= 0 && i < d.layers().size()) fill(d, i);
			}
			return true;
		}
		return false;
	}

	/** Пока курсор в поле ввода, клавиши — ему (иначе «E» закроет станок, а цифры переложат вещи). */
	private boolean key(KeyEvent e) {
		EditBox box = search.isFocused() ? search : name.isFocused() ? name : null;
		if (box == null) return false;
		if (e.key() == InputConstants.KEY_ESCAPE) {
			box.setFocused(false);
			screen.setFocused(null);
			return true;
		}
		if (box == name && (e.key() == InputConstants.KEY_RETURN || e.key() == InputConstants.KEY_NUMPADENTER)) {
			Design d = selected();
			if (d != null) rename(d);
			return true;
		}
		box.keyPressed(e);
		return true;
	}

	/** Сохраняет название из поля. Пустое — вернуть обычное название. */
	private void rename(Design d) {
		String text = name.getValue().trim();
		Config cfg = Config.get();
		if (d.key().startsWith("ready:")) {
			String id = d.key().substring("ready:".length());
			if (text.isEmpty() || text.equals(Component.translatable("workstationhelper.loom.d." + id).getString())) cfg.readyNames.remove(id);
			else cfg.readyNames.put(id, text);
		} else {
			cfg.banners.get(Integer.parseInt(d.key().substring("custom:".length()))).name = text.isEmpty() ? null : text;
		}
		Config.save();
		reload();
		name.setFocused(false);
		syncName();
		mc.gui.chatListener().handleOverlay(Component.translatable("workstationhelper.loom.renamed").withStyle(ChatFormatting.GREEN));
	}

	private void delete(Design d) {
		Config cfg = Config.get();
		if (d.key().startsWith("ready:")) cfg.hiddenReady.add(d.key().substring("ready:".length()));
		else cfg.banners.remove(Integer.parseInt(d.key().substring("custom:".length())));
		Config.save();
		reload();
		select(null);
	}

	private boolean release(double mx, double my) {
		if (!shown()) return false;
		boolean added = justAdded;
		justAdded = false;
		// флаг дотащили до плюсика с зажатой кнопкой
		int plus = visible.size();
		if (!added && !hintMode && inside(mx, my, cellX(plus), cellY(plus), CELL_W, CELL_H) && my >= gridTop && my < gridBottom) addCarried();
		return inside(mx, my, lx, py, lpw, ph) || selected() != null && inside(mx, my, rx, py, pw, ph);
	}

	private boolean scroll(double mx, double my, double v) {
		if (!shown()) return false;
		if (inside(mx, my, lx, py, lpw, ph)) {
			if (hintMode) {
				hintScroll = Math.max(0, hintScroll - (int) Math.signum(v) * HINT_ROW);
				return true;
			}
			leftScroll = Math.clamp(leftScroll - (int) Math.signum(v) * (CELL_H + GAP), 0, maxLeftScroll());
			return true;
		}
		if (selected() != null && inside(mx, my, rx, py, pw, ph)) {
			// полосы прокрутки нет: шагов в ванилле не больше 6, но с модами список крутится колёсиком
			rightScroll = Math.max(0, rightScroll - (int) Math.signum(v) * STEP_ROW);
			return true;
		}
		return false;
	}

	/** Сохраняет узор флага на курсоре как свой. Сам флаг остаётся в руке. */
	private boolean addCarried() {
		ItemStack s = menu().getCarried();
		if (!(s.getItem() instanceof BannerItem b)) return false;
		List<BannerPatternLayers.Layer> layers = s.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers();
		if (layers.isEmpty()) {
			error(Component.translatable("workstationhelper.loom.empty"));
			return true;
		}
		if (layers.size() > MAX_LAYERS) {
			error(Component.translatable("workstationhelper.loom.too_many"));
			return true;
		}
		Config.SavedBanner saved = new Config.SavedBanner();
		saved.base = b.getColor().getName();
		for (BannerPatternLayers.Layer l : layers) {
			saved.patterns.add(l.pattern().unwrapKey().map(k -> k.identifier().toString()).orElse(""));
			saved.colors.add(l.color().getName());
		}
		for (Config.SavedBanner o : Config.get().banners) {
			if (o.base.equals(saved.base) && o.patterns.equals(saved.patterns) && o.colors.equals(saved.colors)) {
				error(Component.translatable("workstationhelper.loom.exists"));
				return true;
			}
		}
		Config.get().banners.add(saved);
		Config.save();
		search.setValue("");
		reload();
		select(designs.get(designs.size() - 1));
		leftScroll = maxLeftScroll();
		mc.gui.chatListener().handleOverlay(Component.translatable("workstationhelper.loom.added").withStyle(ChatFormatting.GREEN));
		return true;
	}

	// ======================== перекладывание в станок ========================

	private void fill(Design d, int i) {
		if (mc.gameMode == null || mc.player == null || stepDone(d, i)) return;
		LoomMenu m = menu();
		if (!m.getCarried().isEmpty()) {
			error(Component.translatable("workstationhelper.loom.carried"));
			return;
		}
		Layer l = d.layers().get(i);
		Optional<Holder.Reference<BannerPattern>> oh = pattern(l.pattern());
		if (oh.isEmpty()) return;
		Holder<BannerPattern> h = oh.get();
		ItemStack need = patternItem(h);
		java.util.function.Predicate<ItemStack> bannerOk = s -> isBanner(s, d, i);
		java.util.function.Predicate<ItemStack> dyeOk = s -> isDye(s, l.color());
		java.util.function.Predicate<ItemStack> itemOk = s -> givesPattern(s, h);
		if (find(m.getBannerSlot(), bannerOk) < 0 || find(m.getDyeSlot(), dyeOk) < 0 || !need.isEmpty() && find(m.getPatternSlot(), itemOk) < 0) {
			error(Component.translatable(m.getResultSlot().hasItem() ? "workstationhelper.loom.result" : "workstationhelper.fill.missing"));
			return;
		}
		int id = m.containerId;
		// лишнее из станка — в инвентарь (предмет-узор тоже, если этому слою он не нужен: он меняет список узоров)
		Slot[] inputs = {m.getBannerSlot(), m.getDyeSlot(), m.getPatternSlot()};
		java.util.List<java.util.function.Predicate<ItemStack>> oks = List.of(bannerOk, dyeOk, need.isEmpty() ? s -> false : itemOk);
		for (int k = 0; k < 3; k++) {
			ItemStack in = inputs[k].getItem();
			if (!in.isEmpty() && !oks.get(k).test(in)) mc.gameMode.handleContainerInput(id, inputs[k].index, 0, ContainerInput.QUICK_MOVE, mc.player);
		}
		for (int k = 0; k < 3; k++) {
			ItemStack in = inputs[k].getItem();
			if (!in.isEmpty() && !oks.get(k).test(in)) {
				error(Component.translatable("workstationhelper.loom.full"));
				return;
			}
		}
		move(find(m.getBannerSlot(), bannerOk), m.getBannerSlot().index);
		move(find(m.getDyeSlot(), dyeOk), m.getDyeSlot().index);
		if (!need.isEmpty()) move(find(m.getPatternSlot(), itemOk), m.getPatternSlot().index);
		pending = h;
		pendingTicks = 20;
		selectPending();
	}

	/** Переносит вещь из слота from в слот станка target (если она уже там — ничего). */
	private void move(int from, int target) {
		if (from < 0 || from == target) return;
		int id = menu().containerId;
		// как мышкой: взять стопку, правым кликом положить 1 штуку, остаток вернуть в ту же ячейку
		mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, mc.player);
		mc.gameMode.handleContainerInput(id, target, 1, ContainerInput.PICKUP, mc.player);
		if (!menu().getCarried().isEmpty()) mc.gameMode.handleContainerInput(id, from, 0, ContainerInput.PICKUP, mc.player);
	}

	/** Выбирает узор в списке станка — так же, как клик по нему. */
	private void selectPending() {
		if (pending == null || mc.player == null || mc.gameMode == null) return;
		List<Holder<BannerPattern>> list = menu().getSelectablePatterns();
		for (int i = 0; i < list.size(); i++) {
			if (list.get(i).is(pending.unwrapKey().orElseThrow())) {
				if (menu().getSelectedBannerPatternIndex() != i && menu().clickMenuButton(mc.player, i)) {
					mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.0f));
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
