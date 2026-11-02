package dev.santos.workstationhelper.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.santos.workstationhelper.Config;
import dev.santos.workstationhelper.WorkstationHelperClient;
import dev.santos.workstationhelper.mixin.ContainerScreenAccessor;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

/**
 * Панель справа от окна торговли: все торги, которые бывают у этой профессии, по уровням подряд
 * (Новичок → Мастер), список листается колёсиком. У странствующего торговца — «покупает», «обычные», «редкие».
 * Торги взяты из данных игры 26.3 (data/minecraft/villager_trade и теги к ним) — файл trades.json в моде.
 */
public final class TradePanels {
	private static final int PAD = 7, ROW = 20, HEAD = 12;
	private static final Identifier BUTTON = Identifier.withDefaultNamespace("widget/button");
	private static final Identifier ARROW = Identifier.withDefaultNamespace("container/villager/trade_arrow");
	private static final Identifier SCROLLER = Identifier.withDefaultNamespace("container/villager/scroller");
	private static final Identifier SCROLLER_OFF = Identifier.withDefaultNamespace("container/villager/scroller_disabled");

	/** Все торги: профессия → (уровень или раздел → список торгов). Читается один раз. */
	private static JsonObject trades;

	/** Строка списка: заголовок уровня или торг. */
	private record Line(Component header, boolean reached, JsonObject trade) {
	}

	private final MerchantScreen screen;
	private final Minecraft mc;
	private final Font font;
	private boolean fits;
	private int rx, py, pw, ph;
	private int scroll;
	private boolean scrolledToLevel;
	private final List<Line> lines = new ArrayList<>();
	private String profession;

	private TradePanels(MerchantScreen screen) {
		this.screen = screen;
		this.mc = Minecraft.getInstance();
		this.font = mc.font;
	}

	public static void attach(MerchantScreen screen) {
		TradePanels p = new TradePanels(screen);
		p.layout();
		ScreenEvents.afterBackground(screen).register((s, g, mx, my, a) -> p.extract(g, mx, my));
		ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !p.click(e.x(), e.y()));
		// отпускание кнопки над панелью тоже наше: иначе игра решит, что мышь «вне окна», и выбросит вещь из руки
		ScreenMouseEvents.allowMouseRelease(screen).register((s, e) -> !(p.shown() && p.over(e.x(), e.y())));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, x, y, h, v) -> !p.scroll(x, y, v));
	}

	private static JsonObject data() {
		if (trades == null) {
			try (InputStream in = TradePanels.class.getResourceAsStream("/assets/workstationhelper/trades.json")) {
				trades = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
			} catch (Exception e) {
				WorkstationHelperClient.LOGGER.error("Не удалось прочитать trades.json", e);
				trades = new JsonObject();
			}
		}
		return trades;
	}

	private ContainerScreenAccessor acc() {
		return (ContainerScreenAccessor) screen;
	}

	private boolean shown() {
		return fits && profession != null && Config.get().tradePanels;
	}

	private void layout() {
		int left = acc().workstationhelper$leftPos(), top = acc().workstationhelper$topPos();
		int iw = acc().workstationhelper$imageWidth();
		pw = Math.min(150, screen.width - left - iw - 4);
		fits = pw >= 120;
		ph = acc().workstationhelper$imageHeight();
		py = top;
		rx = left + iw + 2;
	}

	/** Кто торгует: житель, на которого смотрим, или странствующий торговец. */
	private String detectProfession() {
		Entity e = mc.crosshairPickEntity;
		if (e instanceof WanderingTrader) return "wandering_trader";
		if (e instanceof Villager v) return v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().getPath()).orElse(null);
		return null;
	}

	private void build() {
		lines.clear();
		JsonElement el = profession == null ? null : data().get(profession);
		if (el == null || !el.isJsonObject()) {
			profession = null;
			return;
		}
		JsonObject p = el.getAsJsonObject();
		if (profession.equals("wandering_trader")) {
			for (String sec : new String[] {"buying", "common", "uncommon"}) {
				if (!p.has(sec)) continue;
				lines.add(new Line(Component.translatable("workstationhelper.trade.wander." + sec), true, null));
				for (JsonElement t : p.getAsJsonArray(sec)) lines.add(new Line(null, true, t.getAsJsonObject()));
			}
			return;
		}
		int level = screen.getMenu().getTraderLevel();
		for (int n = 1; n <= 5; n++) {
			JsonArray arr = p.has(String.valueOf(n)) ? p.getAsJsonArray(String.valueOf(n)) : null;
			if (arr == null) continue;
			lines.add(new Line(Component.translatable("merchant.level." + n), n <= level, null));
			for (JsonElement t : arr) lines.add(new Line(null, n <= level, t.getAsJsonObject()));
		}
	}

	private int lineH(Line l) {
		return l.header() != null ? HEAD : ROW;
	}

	private int listTop() {
		return py + 18;
	}

	private int listBottom() {
		return py + ph - PAD;
	}

	private int maxScroll() {
		int total = 0;
		for (Line l : lines) total += lineH(l);
		return Math.max(0, total - (listBottom() - listTop()));
	}

	/** Мини-кнопка «−/+» в правом верхнем углу окна торговли. */
	private int toggleX() {
		// у окна торговли — на 1 пиксель левее, чем у столов
		return Vanilla.toggleRightX(acc().workstationhelper$leftPos(), acc().workstationhelper$imageWidth()) - 1;
	}

	private int toggleY() {
		// и на 1 пиксель ниже
		return Vanilla.toggleY(acc().workstationhelper$topPos()) + 1;
	}

	private boolean onToggle(double mx, double my) {
		return mx >= toggleX() && mx < toggleX() + Vanilla.TOGGLE && my >= toggleY() && my < toggleY() + Vanilla.TOGGLE;
	}

	private boolean over(double mx, double my) {
		return mx >= rx && my >= py && mx < rx + pw && my < py + ph;
	}

	// ======================== предметы ========================

	private static ItemStack stack(String id, int count, String potion) {
		var item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(id));
		ItemStack s = new ItemStack(item, Math.max(1, Math.min(count, item.getDefaultMaxStackSize())));
		if (potion != null) {
			BuiltInRegistries.POTION.get(Identifier.withDefaultNamespace(potion))
				.ifPresent(h -> s.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new PotionContents(h)));
		}
		return s;
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) ? o.get(k).getAsString() : null;
	}

	private static int num(JsonObject o, String k) {
		return o.has(k) ? o.get(k).getAsInt() : 1;
	}

	// ======================== отрисовка ========================

	private void extract(GuiGraphicsExtractor g, int mx, int my) {
		if (profession == null && lines.isEmpty()) {
			profession = detectProfession();
			build();
		}
		if (fits && profession != null) {
			boolean hover = onToggle(mx, my);
			Vanilla.toggle(g, toggleX(), toggleY(), hover, Config.get().tradePanels);
			if (hover) g.setTooltipForNextFrame(font, Component.translatable(Config.get().tradePanels ? "workstationhelper.hide" : "workstationhelper.show"), mx, my);
		}
		if (!shown()) return;
		// при открытии — сразу к следующему уровню жителя
		if (!scrolledToLevel) {
			scrolledToLevel = true;
			int level = screen.getMenu().getTraderLevel(), y = 0, n = 0;
			for (Line l : lines) {
				if (l.header() != null && ++n == level + 1) break;
				y += lineH(l);
			}
			scroll = profession.equals("wandering_trader") ? 0 : Math.min(y, maxScroll());
		}

		Vanilla.panel(g, rx, py, pw, ph);
		g.text(font, Component.translatable("workstationhelper.trade.title"), rx + PAD, py + 7, Vanilla.TEXT, false);
		int top = listTop(), bottom = listBottom(), rowW = pw - 2 * PAD - 8;
		scroll = Math.clamp(scroll, 0, maxScroll());
		List<Component> tip = null;
		g.enableScissor(rx + PAD, top, rx + pw - PAD, bottom);
		int y = top - scroll;
		for (Line l : lines) {
			int h = lineH(l);
			if (y + h > top && y < bottom) {
				if (l.header() != null) {
					// пройденные уровни — тёмным, ещё не открытые — серым
					g.text(font, l.header(), rx + PAD + 1, y + 2, l.reached() ? Vanilla.TEXT : 0xFF7A7A7A, false);
				} else {
					List<Component> t = extractTrade(g, l.trade(), rx + PAD, y, rowW, mx, my, top, bottom);
					if (t != null) tip = t;
				}
			}
			y += h;
		}
		g.disableScissor();
		int max = maxScroll(), sx = rx + pw - PAD - 6;
		Vanilla.inset(g, sx - 1, top, 8, bottom - top, 0xFF6F6F6F);
		if (max > 0) g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER, sx, top + 1 + (bottom - top - 2 - 27) * scroll / max, 6, 27);
		else g.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLLER_OFF, sx, top + 1, 6, 27);
		if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mx, my);
	}

	/** Строка торга как в списке предложений: что отдаёшь (+ ещё что-то) → что получаешь. */
	private List<Component> extractTrade(GuiGraphicsExtractor g, JsonObject t, int x, int y, int w, int mx, int my, int top, int bottom) {
		g.blitSprite(RenderPipelines.GUI_TEXTURED, BUTTON, x, y, w, ROW);
		int wc = num(t, "wc");
		ItemStack want = stack(str(t, "w"), wc < 0 ? 1 : wc, str(t, "wp"));
		ItemStack add = t.has("a") ? stack(str(t, "a"), num(t, "ac"), null) : ItemStack.EMPTY;
		ItemStack give = stack(str(t, "g"), num(t, "gc"), str(t, "gp"));
		String giveNote = str(t, "n");
		// случайное зачарование — предмет светится, как зачарованный
		if ("enchant".equals(giveNote)) give.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		// стрела со случайным зельем — цветная, а не «несоздаваемая»
		if ("potion".equals(giveNote)) {
			BuiltInRegistries.POTION.get(Identifier.withDefaultNamespace("swiftness"))
				.ifPresent(h -> give.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS, new PotionContents(h)));
		}
		int ix = x + 4;
		g.item(want, ix, y + 2);
		if (wc < 0) g.text(font, "?", ix + 11, y + 10, 0xFFFFFFFF, true);
		else g.itemDecorations(font, want, ix, y + 2);
		if (!add.isEmpty()) {
			g.item(add, ix + 20, y + 2);
			g.itemDecorations(font, add, ix + 20, y + 2);
		}
		int ax = x + w - 20 - 4 - 10 - 6;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, ARROW, ax, y + 6, 10, 9);
		int gx = x + w - 20;
		g.item(give, gx, y + 2);
		g.itemDecorations(font, give, gx, y + 2);
		if (my < top || my >= bottom || mx < x || mx >= x + w || my < y || my >= y + ROW) return null;

		// подсказка — как в левом окне игры: только предмет под мышкой; особенности торга — голубым под ним
		boolean onWant = mx >= ix - 1 && mx < ix + 17, onAdd = !add.isEmpty() && mx >= ix + 19 && mx < ix + 37, onGive = mx >= gx - 1 && mx < gx + 17;
		if (!onWant && !onAdd && !onGive) return null;
		List<Component> tip = new ArrayList<>();
		// у стрелы со случайным зельем своё название, иначе игра пишет про «несоздаваемое зелье»
		tip.add(onGive && "potion".equals(str(t, "n")) && give.is(net.minecraft.world.item.Items.TIPPED_ARROW)
			? Component.translatable("workstationhelper.trade.tipped_arrow") : (onWant ? want : onAdd ? add : give).getHoverName());
		if (onWant && wc < 0) tip.add(Component.translatable("workstationhelper.trade.price_varies").withStyle(ChatFormatting.AQUA));
		if (onGive) {
			// у карты название и так говорит, куда она ведёт, — пометку «Карта: …» не пишем
			String note = str(t, "n");
			if (note != null && !note.equals("map") && !(note.equals("potion") && give.is(net.minecraft.world.item.Items.TIPPED_ARROW))) {
				tip.add(Component.translatable("workstationhelper.trade.note." + note).withStyle(ChatFormatting.AQUA));
			}
			if (t.has("b")) {
				List<String> names = new ArrayList<>();
				for (JsonElement b : t.getAsJsonArray("b")) names.add(Component.translatable("workstationhelper.trade.from." + b.getAsString()).getString());
				tip.add(Component.translatable("workstationhelper.trade.biomes", String.join(", ", names)).withStyle(ChatFormatting.AQUA));
			}
			// у зачарованной книги — какие зачарования у жителей не выпадают никогда (их нет в #tradeable)
			if (give.is(net.minecraft.world.item.Items.ENCHANTED_BOOK) && "enchant".equals(note)) {
				// «Исключения:» и каждое зачарование с новой строки
				List<String> except = rareBooks();
				if (!except.isEmpty()) {
					tip.add(Component.translatable("workstationhelper.trade.except").withStyle(ChatFormatting.RED));
					for (String n : except) tip.add(Component.literal(n).withStyle(ChatFormatting.RED));
				}
			}
		}
		return tip;
	}

	/** Зачарования, которых нет у жителей: Проворство, Порыв ветра, Скорость души — названия из игры. */
	private List<String> rareBooks() {
		if (mc.level == null) return List.of();
		var reg = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
		List<String> names = new ArrayList<>();
		for (String id : new String[] {"swift_sneak", "wind_burst", "soul_speed"}) {
			reg.get(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ENCHANTMENT, Identifier.withDefaultNamespace(id)))
				.ifPresent(h -> names.add(h.value().description().getString()));
		}
		return names;
	}

	// ======================== мышь ========================

	private boolean click(double mx, double my) {
		if (fits && profession != null && onToggle(mx, my)) {
			Config.get().tradePanels = !Config.get().tradePanels;
			Vanilla.clickSound();
			Config.save();
			return true;
		}
		return shown() && over(mx, my);
	}

	private boolean scroll(double mx, double my, double v) {
		if (!shown() || !over(mx, my)) return false;
		scroll = Math.clamp(scroll - (int) Math.signum(v) * ROW, 0, maxScroll());
		return true;
	}
}
