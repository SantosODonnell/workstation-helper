package dev.santos.workstationhelper;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

/** Настройки мода и выбранные зачарования для каждого типа предмета. Хранится в config/workstationhelper.json */
public final class Config {
	/** «Специальные возможности → Помощник»: выключает весь мод */
	public boolean helper = true;
	// панели у наковальни (прячутся мини-кнопкой в левом верхнем углу окна)
	public boolean sidePanels = true;

	/** панели у кузнечного стола (мини-кнопка в правом верхнем углу окна) */
	public boolean smithPanels = true;
	/** подсказка у стола картографа (мини-кнопка в правом верхнем углу окна) */
	public boolean cartoPanel = true;
	/** панели у стола зачарования (мини-кнопка в правом верхнем углу окна) */
	public boolean enchantPanels = true;
	/** панели у ткацкого станка (мини-кнопка в правом верхнем углу окна) */
	public boolean loomPanels = true;
	/** панели у зельеварки (мини-кнопка в правом верхнем углу окна) */
	public boolean brewPanels = true;
	/** книга рецептов камнереза (мини-кнопка в правом верхнем углу окна) */
	public boolean stonePanels = true;
	/** подсказка у точила и панель торгов у жителей (мини-кнопка в правом верхнем углу окна) */
	public boolean grindPanel = true;
	public boolean tradePanels = true;

	/** Свой узор флага: цвет основы и слои (id узора + цвет), как на флаге. */
	public static final class SavedBanner {
		public String base;
		/** своё название; null — «Свой узор N» */
		public String name;
		public List<String> patterns = new ArrayList<>();
		public List<String> colors = new ArrayList<>();
	}

	public List<SavedBanner> banners = new ArrayList<>();
	/** удалённые готовые узоры (arrow, heart…) */
	public List<String> hiddenReady = new ArrayList<>();
	/** свои названия готовых узоров: id → название */
	public Map<String, String> readyNames = new LinkedHashMap<>();

	/** Наряд в кастомизаторе кузнечного стола: слот (head/chest/legs/feet) → выбор. */
	public static final class OutfitPiece {
		/** материал брони: leather, chainmail, copper, iron, golden, diamond, netherite, turtle; null — без брони */
		public String material;
		/** краситель для кожи (white, red…); null — без краски */
		public String dye;
		/** id узора отделки (minecraft:sentry); null — без отделки */
		public String pattern;
		/** id материала отделки (minecraft:gold) */
		public String trim;
	}

	public Map<String, OutfitPiece> outfit = new LinkedHashMap<>();

	/** id предмета → (id зачарования → уровень) */
	public Map<String, Map<String, Integer>> picks = new LinkedHashMap<>();

	// ------------------------------------------------------------------

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static Config data;

	public static Config get() {
		if (data == null) load();
		return data;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("workstationhelper.json");
	}

	public static void load() {
		Config loaded = null;
		Path f = file();
		if (Files.exists(f)) {
			try {
				loaded = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), Config.class);
			} catch (Exception e) {
				WorkstationHelperClient.LOGGER.error("Не удалось прочитать {}", f, e);
			}
		}
		if (loaded == null) loaded = new Config();
		if (loaded.picks == null) loaded.picks = new LinkedHashMap<>();
		if (loaded.outfit == null) loaded.outfit = new LinkedHashMap<>();
		if (loaded.banners == null) loaded.banners = new ArrayList<>();
		if (loaded.hiddenReady == null) loaded.hiddenReady = new ArrayList<>();
		if (loaded.readyNames == null) loaded.readyNames = new LinkedHashMap<>();
		data = loaded;
	}

	public static void save() {
		if (data == null) return;
		Path f = file();
		try {
			Files.createDirectories(f.getParent());
			Path tmp = f.resolveSibling(f.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(data), StandardCharsets.UTF_8);
			Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			WorkstationHelperClient.LOGGER.error("Не удалось сохранить {}", f, e);
		}
	}
}
