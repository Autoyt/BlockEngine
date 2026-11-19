package dev.auto.blockengine.resourcepack;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class BlockEnginePackSnapshot {
    private static final String CREATIVE_BOOK = "assets/minecraft/items/enchanted_book.json";
    private static final Pattern LANG = Pattern.compile("assets/[^/]+/lang/[^/]+\\.json");
    private static final ObjectMapper JSON = new ObjectMapper();

    private final String revision;
    private final boolean required;
    private final List<byte[]> archives;

    private BlockEnginePackSnapshot(String revision, boolean required, List<byte[]> archives) {
        this.revision = revision;
        this.required = required;
        this.archives = List.copyOf(archives);
    }

    static BlockEnginePackSnapshot capture(List<GeneratedPack> packs) throws IOException {
        MessageDigest revision;
        try {
            revision = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }

        List<byte[]> archives = new ArrayList<>(packs.size());
        boolean required = false;
        for (GeneratedPack pack : packs) {
            byte[] archive = Files.readAllBytes(pack.zip());
            archives.add(archive);
            revision.update(pack.sha1());
            revision.update((byte) (pack.required() ? 1 : 0));
            required |= pack.required();
        }
        return new BlockEnginePackSnapshot(HexFormat.of().formatHex(revision.digest()), required, archives);
    }

    String revision() {
        return revision;
    }

    boolean required() {
        return required;
    }

    Map<String, byte[]> readEntries() throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        boolean hasCompleteCreativeBook = false;
        for (int index = 0; index < archives.size(); index++) {
            try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archives.get(index)))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    String path = entry.getName();
                    if (entry.isDirectory() || !path.startsWith("assets/")) {
                        continue;
                    }

                    // The primary pack contains creative routes for all BlockEngine systems.
                    if (hasCompleteCreativeBook && path.equals(CREATIVE_BOOK)) {
                        continue;
                    }
                    byte[] contents = zip.readAllBytes();
                    if (entries.containsKey(path) && LANG.matcher(path).matches()) {
                        contents = mergeTranslations(path, entries.get(path), contents);
                    }
                    entries.put(path, contents);
                }
            }
            if (index == 0) {
                hasCompleteCreativeBook = entries.containsKey(CREATIVE_BOOK);
            }
        }
        return entries;
    }

    private static byte[] mergeTranslations(String path, byte[] base, byte[] overlay) throws IOException {
        JsonNode previous = JSON.readTree(base);
        JsonNode next = JSON.readTree(overlay);
        if (!(previous instanceof ObjectNode translations) || !(next instanceof ObjectNode additions)) {
            throw new IOException("Expected JSON objects in BlockEngine translations at " + path);
        }
        translations.setAll(additions);
        return JSON.writeValueAsBytes(translations);
    }
}
