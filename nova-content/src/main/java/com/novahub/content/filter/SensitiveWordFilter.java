package com.novahub.content.filter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class SensitiveWordFilter {

    private final Map<Integer, Integer> dfaMap = new HashMap<>();
    private static final int END_FLAG = -1;

    @PostConstruct
    public void init() {
        loadDefaultWords();
    }

    private void loadDefaultWords() {
        List<String> defaultWords = Arrays.asList(
                "敏感词1", "敏感词2", "违禁词1", "违禁词2",
                "广告", "赌博", "毒品", "暴力"
        );
        for (String word : defaultWords) {
            addWord(word);
        }
        log.info("敏感词过滤器初始化完成，共加载 {} 个词", defaultWords.size());
    }

    public void loadWords(List<String> words) {
        for (String word : words) {
            addWord(word);
        }
    }

    private void addWord(String word) {
        if (word == null || word.isEmpty()) {
            return;
        }
        char[] chars = word.toCharArray();
        int hashcode = 0;
        for (char c : chars) {
            hashcode = charToHash(hashcode, c);
            dfaMap.put(hashcode, 0);
        }
        dfaMap.put(hashcode, END_FLAG);
    }

    private int charToHash(int hashcode, char c) {
        return (hashcode << 7) ^ c;
    }

    public boolean containsSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            int flag = check(text, i);
            if (flag > 0) {
                return true;
            }
        }
        return false;
    }

    public Set<String> findSensitiveWords(String text) {
        Set<String> found = new HashSet<>();
        if (text == null || text.isEmpty()) {
            return found;
        }
        for (int i = 0; i < text.length(); i++) {
            int length = check(text, i);
            if (length > 0) {
                found.add(text.substring(i, i + length));
                i += length - 1;
            }
        }
        return found;
    }

    public String replaceSensitiveWords(String text, char replacement) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            int length = check(text, i);
            if (length > 0) {
                for (int j = 0; j < length; j++) {
                    result.append(replacement);
                }
                i += length - 1;
            } else {
                result.append(ch);
            }
        }
        return result.toString();
    }

    private int check(String text, int start) {
        int hashcode = 0;
        int length = 0;
        for (int i = start; i < text.length(); i++) {
            char ch = text.charAt(i);
            hashcode = charToHash(hashcode, ch);

            Integer value = dfaMap.get(hashcode);
            if (value == null) {
                return 0;
            }
            length++;
            if (value == END_FLAG) {
                return length;
            }
        }
        return 0;
    }
}
