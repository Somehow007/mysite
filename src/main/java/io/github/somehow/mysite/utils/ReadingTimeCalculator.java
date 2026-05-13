package io.github.somehow.mysite.utils;

import cn.hutool.core.util.StrUtil;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ReadingTimeCalculator {

    private static final int CHINESE_CHARS_PER_MINUTE = 300;
    private static final int ENGLISH_WORDS_PER_MINUTE = 225;
    private static final int CODE_CHARS_PER_MINUTE = 200;
    private static final double IMAGE_TIME_MINUTES = 0.2;

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```[\\s\\S]*?```");
    private static final Pattern IMAGE_PATTERN = Pattern.compile("!\\[[^\\]]*\\]\\([^)]+\\)|<img[^>]+>");
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fa5]");
    private static final Pattern ENGLISH_PATTERN = Pattern.compile("[a-zA-Z]+");
    private static final Pattern CODE_BLOCK_MARKER_PATTERN = Pattern.compile("^```[^\\n]*\\n|\\n?```$");

    private ReadingTimeCalculator() {
    }

    public static int calculate(String content) {
        if (StrUtil.isBlank(content)) {
            return 1;
        }

        StringBuilder codeContent = extractCodeBlocks(content);
        String textWithoutCode = CODE_BLOCK_PATTERN.matcher(content).replaceAll("");
        int imageCount = countImages(textWithoutCode);
        String textWithoutImages = IMAGE_PATTERN.matcher(textWithoutCode).replaceAll("");

        int chineseCharCount = countMatches(CHINESE_PATTERN, textWithoutImages);
        int englishWordCount = countMatches(ENGLISH_PATTERN, textWithoutImages);
        int codeCharCount = codeContent.toString().replaceAll("\\s+", "").length();

        double textMinutes = (double) chineseCharCount / CHINESE_CHARS_PER_MINUTE
                + (double) englishWordCount / ENGLISH_WORDS_PER_MINUTE;
        double codeMinutes = (double) codeCharCount / CODE_CHARS_PER_MINUTE;
        double imageMinutes = imageCount * IMAGE_TIME_MINUTES;

        double totalMinutes = textMinutes + codeMinutes + imageMinutes;
        return Math.max((int) Math.ceil(totalMinutes), 1);
    }

    private static StringBuilder extractCodeBlocks(String content) {
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(content);
        StringBuilder codeContent = new StringBuilder();
        while (matcher.find()) {
            String block = matcher.group();
            String codeText = CODE_BLOCK_MARKER_PATTERN.matcher(block).replaceAll("");
            codeContent.append(codeText).append("\n");
        }
        return codeContent;
    }

    private static int countImages(String content) {
        return countMatches(IMAGE_PATTERN, content);
    }

    private static int countMatches(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }
}
