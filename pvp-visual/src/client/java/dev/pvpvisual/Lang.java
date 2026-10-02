package dev.pvpvisual;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import net.minecraft.client.resources.language.I18n;

/**
 * Переводы мода. Режим «Авто» берёт язык игры (через ванильные lang-файлы),
 * остальные режимы читают выбранный язык напрямую — его можно сменить в меню.
 */
public final class Lang {
	public static final String[] CODES = {"auto", "en_us", "ru_ru", "uk_ua", "de_de", "pl_pl", "es_es", "fr_fr", "tr_tr"};
	public static final String[] NAMES = {null, "English", "Русский", "Українська", "Deutsch", "Polski", "Español", "Français", "Türkçe"};

	private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() { }.getType();
	private static final Map<String, Map<String, String>> CACHE = new HashMap<>();

	private Lang() {
	}

	public static String t(String key) {
		int idx = PvpConfig.get().language;
		if (idx <= 0 || idx >= CODES.length) {
			return I18n.get(key);
		}
		String value = load(CODES[idx]).get(key);
		if (value == null) {
			value = load("en_us").get(key);
		}
		return value != null ? value : key;
	}

	/** Название языка для меню (у «Авто» — переводимое). */
	public static String languageName(int idx) {
		if (idx <= 0 || idx >= NAMES.length) {
			return t("pv.lang.auto");
		}
		return NAMES[idx];
	}

	private static Map<String, String> load(String code) {
		return CACHE.computeIfAbsent(code, c -> {
			try (InputStream in = Lang.class.getResourceAsStream("/assets/pvpvisual/lang/" + c + ".json")) {
				if (in != null) {
					Map<String, String> map = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), MAP_TYPE);
					if (map != null) {
						return map;
					}
				}
			} catch (Exception e) {
				System.err.println("[PvpVisual] Не удалось загрузить язык " + c + ": " + e);
			}
			return new HashMap<>();
		});
	}
}
