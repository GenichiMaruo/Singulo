package io.github.genichimaruo.singulo.supporter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** The same ordered, public credit roster is used by the handbook and the ending. */
public final class SupporterCredits {
    public static final int MAX_SUPPORTERS = 100;
    public static final int MAX_NAME_CODE_POINTS = 64;
    public static final Catalog EMPTY = new Catalog(List.of());

    private SupporterCredits() {}

    public enum Tier {
        STANDARD("standard"), RAINBOW("rainbow");

        public final String id;

        Tier(String id) {
            this.id = id;
        }

        public static Tier byId(String id) {
            for (Tier tier : values()) {
                if (tier.id.equals(id)) {
                    return tier;
                }
            }
            throw new IllegalArgumentException("Unknown supporter tier: " + id);
        }
    }

    /** id is a stable, non-private roster identifier, independent of the displayed name. */
    public record Entry(String id, String name, Tier tier) {
        public Entry {
            Objects.requireNonNull(id);
            Objects.requireNonNull(name);
            Objects.requireNonNull(tier);
            if (!id.matches("[a-z0-9_-]{1,64}")) {
                throw new IllegalArgumentException("Supporter id must contain 1–64 lowercase letters, digits, underscores or hyphens");
            }
            name = Normalizer.normalize(name.strip(), Normalizer.Form.NFC);
            int length = name.codePointCount(0, name.length());
            if (length == 0 || length > MAX_NAME_CODE_POINTS
                    || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '\u00A7')) {
                throw new IllegalArgumentException("Supporter name must contain 1–64 visible characters without control or formatting codes");
            }
        }
    }

    public record Catalog(List<Entry> entries) {
        public Catalog {
            entries = List.copyOf(entries);
            if (entries.size() > MAX_SUPPORTERS) {
                throw new IllegalArgumentException("The two supporter tiers share a limit of 100 people");
            }
            var ids = new HashSet<String>();
            for (Entry entry : entries) {
                if (!ids.add(entry.id())) {
                    throw new IllegalArgumentException("Duplicate supporter id: " + entry.id());
                }
            }
        }

        public int pageCount(int pageSize) {
            if (pageSize < 1) {
                throw new IllegalArgumentException("Page size must be positive");
            }
            return entries.isEmpty() ? 0 : 1 + (entries.size() - 1) / pageSize;
        }

        public List<Entry> page(int page, int pageSize) {
            if (page < 0 || pageSize < 1) {
                throw new IllegalArgumentException("Invalid supporter page");
            }
            int from = (int) Math.min(entries.size(), (long) page * pageSize);
            int to = (int) Math.min(entries.size(), (long) from + pageSize);
            return entries.subList(from, to);
        }
    }

    public static Catalog read(Reader reader) {
        try {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonElement supporters = root.get("supporters");
            if (supporters == null || !supporters.isJsonArray()) {
                throw new JsonParseException("Expected a supporters array");
            }
            var entries = new ArrayList<Entry>();
            for (JsonElement element : supporters.getAsJsonArray()) {
                JsonObject entry = element.getAsJsonObject();
                entries.add(new Entry(string(entry, "id"), string(entry, "name"), Tier.byId(string(entry, "tier"))));
            }
            return new Catalog(entries);
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException ex) {
            throw new JsonParseException("Invalid supporter credit roster", ex);
        }
    }

    private static String string(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new JsonParseException("Expected supporter string: " + key);
        }
        return value.getAsString();
    }
}
