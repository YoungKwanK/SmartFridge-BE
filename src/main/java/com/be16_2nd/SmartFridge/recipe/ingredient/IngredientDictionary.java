package com.be16_2nd.SmartFridge.recipe.ingredient;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 재료 사전 (src/main/resources/recipe/ingredient-dictionary.txt)
 * 별칭을 대표 이름으로 바꾸고, 기본 양념인지 알려준다. 비교할 때 띄어쓰기는 무시한다.
 */
@Component
public class IngredientDictionary {

    static final String DEFAULT_LOCATION = "recipe/ingredient-dictionary.txt";
    private static final String PANTRY_PREFIX = "@기본양념";

    // 띄어쓰기를 뺀 이름(대표 이름 포함) → 대표 이름
    private final Map<String, String> canonicalByKey = new HashMap<>();
    private final Set<String> pantry = new HashSet<>();

    public IngredientDictionary() {
        this(new ClassPathResource(DEFAULT_LOCATION));
    }

    IngredientDictionary(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("재료 사전을 읽지 못했습니다: " + resource, e);
        }
    }

    /** 테스트처럼 사전 내용을 직접 줄 때 */
    public static IngredientDictionary of(String content) {
        return new IngredientDictionary(new ByteArrayResource(content.getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 이름을 사전 기준으로 바꾼다. 사전에 없으면 known=false 로 알려주고,
     * "방울 토마토"와 "방울토마토"가 다른 재료로 생기지 않게 띄어쓰기를 빼고 영문은 소문자로 맞춘 이름을 쓴다
     * (DB 유니크 비교가 대소문자를 가리지 않으므로 같은 이름이 두 번 들어가지 않게 하려는 것)
     */
    public Resolved resolve(String name) {
        String key = key(name);
        String canonical = canonicalByKey.get(key);
        if (canonical == null) {
            return new Resolved(key.toLowerCase(Locale.ROOT), false, false);
        }
        return new Resolved(canonical, pantry.contains(canonical), true);
    }

    private void load(String content) {
        int lineNo = 0;
        for (String raw : content.split("\\R")) {
            lineNo++;
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith(PANTRY_PREFIX)) {
                for (String name : split(line.substring(PANTRY_PREFIX.length()))) {
                    pantry.add(name);
                    register(name, name, lineNo);
                }
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalStateException("재료 사전 " + lineNo + "번째 줄 형식이 잘못됐습니다: " + line);
            }
            String canonical = line.substring(0, eq).strip();
            register(canonical, canonical, lineNo);
            for (String alias : split(line.substring(eq + 1))) {
                register(alias, canonical, lineNo);
            }
        }
    }

    private void register(String name, String canonical, int lineNo) {
        String previous = canonicalByKey.putIfAbsent(key(name), canonical);
        if (previous != null && !previous.equals(canonical)) {
            throw new IllegalStateException("재료 사전 " + lineNo + "번째 줄: '" + name + "'이(가) '"
                    + previous + "'와 '" + canonical + "' 두 곳에 들어 있습니다.");
        }
    }

    private static String[] split(String names) {
        return Arrays.stream(names.split(","))
                .map(String::strip)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
    }

    private static String key(String name) {
        return name.replaceAll("\\s+", "");
    }

    /**
     * @param name    대표 이름 (사전에 없으면 받은 이름)
     * @param pantry  기본 양념인지
     * @param known   사전에 있는 이름인지
     */
    public record Resolved(String name, boolean pantry, boolean known) {
    }
}
