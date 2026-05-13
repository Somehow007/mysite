package io.github.somehow.mysite.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ReadingTimeCalculatorTest {

    @Test
    void calculate_nullContent_returns1() {
        assertEquals(1, ReadingTimeCalculator.calculate(null));
    }

    @Test
    void calculate_emptyContent_returns1() {
        assertEquals(1, ReadingTimeCalculator.calculate(""));
    }

    @Test
    void calculate_blankContent_returns1() {
        assertEquals(1, ReadingTimeCalculator.calculate("   "));
    }

    @Test
    void calculate_shortChineseText_returns1() {
        assertEquals(1, ReadingTimeCalculator.calculate("你好世界"));
    }

    @Test
    void calculate_pureChineseText_300charsPerMinute() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("中");
        }
        assertEquals(1, ReadingTimeCalculator.calculate(sb.toString()));
    }

    @Test
    void calculate_pureChineseText_600chars_returns2() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 600; i++) {
            sb.append("中");
        }
        assertEquals(2, ReadingTimeCalculator.calculate(sb.toString()));
    }

    @Test
    void calculate_pureChineseText_900chars_returns3() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 900; i++) {
            sb.append("中");
        }
        assertEquals(3, ReadingTimeCalculator.calculate(sb.toString()));
    }

    @Test
    void calculate_pureEnglishText_225wordsPerMinute() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 225; i++) {
            sb.append("word ");
        }
        assertEquals(1, ReadingTimeCalculator.calculate(sb.toString().trim()));
    }

    @Test
    void calculate_pureEnglishText_450words_returns2() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 450; i++) {
            sb.append("word ");
        }
        assertEquals(2, ReadingTimeCalculator.calculate(sb.toString().trim()));
    }

    @Test
    void calculate_mixedChineseAndEnglish() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            sb.append("中");
        }
        sb.append(" ");
        for (int i = 0; i < 112; i++) {
            sb.append("word ");
        }
        assertEquals(1, ReadingTimeCalculator.calculate(sb.toString().trim()));
    }

    @Test
    void calculate_mixedChineseAndEnglish_combined2Minutes() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("中");
        }
        sb.append(" ");
        for (int i = 0; i < 225; i++) {
            sb.append("word ");
        }
        assertEquals(2, ReadingTimeCalculator.calculate(sb.toString().trim()));
    }

    @Test
    void calculate_codeBlock_slowerReadingSpeed() {
        StringBuilder code = new StringBuilder("```java\n");
        for (int i = 0; i < 200; i++) {
            code.append("int x = 1;\n");
        }
        code.append("```");
        assertEquals(7, ReadingTimeCalculator.calculate(code.toString()));
    }

    @Test
    void calculate_codeBlock_400chars_returns2() {
        StringBuilder code = new StringBuilder("```java\n");
        for (int i = 0; i < 400; i++) {
            code.append("x");
        }
        code.append("\n```");
        assertEquals(2, ReadingTimeCalculator.calculate(code.toString()));
    }

    @Test
    void calculate_codeBlock_languageIdentifierNotCounted() {
        StringBuilder code1 = new StringBuilder("```java\n");
        for (int i = 0; i < 200; i++) {
            code1.append("x");
        }
        code1.append("\n```");

        StringBuilder code2 = new StringBuilder("```\n");
        for (int i = 0; i < 200; i++) {
            code2.append("x");
        }
        code2.append("\n```");

        assertEquals(ReadingTimeCalculator.calculate(code1.toString()),
                ReadingTimeCalculator.calculate(code2.toString()));
    }

    @Test
    void calculate_markdownImage_addsTime() {
        String textOnly = "这是一段测试文本";
        String textWithImage = "这是一段测试文本\n\n![图片](https://example.com/image.png)";

        int timeWithoutImage = ReadingTimeCalculator.calculate(textOnly);
        int timeWithImage = ReadingTimeCalculator.calculate(textWithImage);

        assertTrue(timeWithImage >= timeWithoutImage);
    }

    @Test
    void calculate_multipleImages_addCorrectTime() {
        String content = "![图1](url1)\n\n![图2](url2)\n\n![图3](url3)";
        int result = ReadingTimeCalculator.calculate(content);
        assertEquals(1, result);
    }

    @Test
    void calculate_htmlImage_addsTime() {
        String content = "<img src=\"https://example.com/image.png\" alt=\"图片\">";
        int result = ReadingTimeCalculator.calculate(content);
        assertEquals(1, result);
    }

    @Test
    void calculate_textWithCodeAndImages() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 150; i++) {
            content.append("中");
        }
        content.append("\n\n```java\n");
        for (int i = 0; i < 100; i++) {
            content.append("x");
        }
        content.append("\n```\n\n");
        content.append("![图片](url)\n\n");
        for (int i = 0; i < 50; i++) {
            content.append("word ");
        }

        int result = ReadingTimeCalculator.calculate(content.toString());
        assertTrue(result >= 1);
    }

    @Test
    void calculate_multipleCodeBlocks() {
        StringBuilder content = new StringBuilder();
        content.append("```java\n");
        for (int i = 0; i < 100; i++) {
            content.append("x");
        }
        content.append("\n```\n\n中间文本\n\n```python\n");
        for (int i = 0; i < 100; i++) {
            content.append("y");
        }
        content.append("\n```");

        int result = ReadingTimeCalculator.calculate(content.toString());
        assertEquals(2, result);
    }

    @Test
    void calculate_inlineCodeNotTreatedAsBlock() {
        String content = "使用 `console.log` 调试代码";
        int result = ReadingTimeCalculator.calculate(content);
        assertEquals(1, result);
    }

    @Test
    void calculate_veryLongArticle() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            content.append("这是一段很长的文章内容，用来测试超长文章的阅读时间计算。");
        }
        int result = ReadingTimeCalculator.calculate(content.toString());
        assertTrue(result > 10);
    }

    @Test
    void calculate_codeBlockDoesNotAffectTextCount() {
        StringBuilder contentWithCode = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            contentWithCode.append("中");
        }
        contentWithCode.append("\n\n```java\nSystem.out.println(\"hello\");\n```");

        StringBuilder contentWithoutCode = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            contentWithoutCode.append("中");
        }

        int timeWithCode = ReadingTimeCalculator.calculate(contentWithCode.toString());
        int timeWithoutCode = ReadingTimeCalculator.calculate(contentWithoutCode.toString());

        assertTrue(timeWithCode >= timeWithoutCode);
    }

    @Test
    void calculate_imageNotCountedAsText() {
        String withImage = "![这是一张图片描述](https://example.com/image.png)";
        String plainText = "这是一段纯文本";

        int timeWithImage = ReadingTimeCalculator.calculate(withImage);
        int timePlainText = ReadingTimeCalculator.calculate(plainText);

        assertEquals(timeWithImage, timePlainText);
    }

    @Test
    void calculate_pureCodeBlock_onlyCodeTime() {
        StringBuilder code = new StringBuilder("```\n");
        for (int i = 0; i < 600; i++) {
            code.append("x");
        }
        code.append("\n```");
        assertEquals(3, ReadingTimeCalculator.calculate(code.toString()));
    }

    @Test
    void calculate_5images_returns1() {
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < 5; i++) {
            content.append("![图片").append(i).append("](url").append(i).append(")\n\n");
        }
        assertEquals(1, ReadingTimeCalculator.calculate(content.toString()));
    }

    @Test
    void calculate_chineseWithPunctuation() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            sb.append("中，。！？");
        }
        int result = ReadingTimeCalculator.calculate(sb.toString());
        assertTrue(result >= 1);
    }

    @Test
    void calculate_mixedMarkdownFormat() {
        String content = "# 标题\n\n这是一段**加粗**文本和*斜体*文本。\n\n" +
                "- 列表项1\n- 列表项2\n\n" +
                "```java\nSystem.out.println(\"hello\");\n```\n\n" +
                "![图片](url)\n\n" +
                "[链接](https://example.com)";
        int result = ReadingTimeCalculator.calculate(content);
        assertEquals(1, result);
    }

    @Test
    void calculate_realisticArticle() {
        StringBuilder article = new StringBuilder();
        article.append("# Spring Boot 入门指南\n\n");
        for (int i = 0; i < 200; i++) {
            article.append("Spring Boot 是一个优秀的 Java 框架，它简化了企业级应用的开发。");
        }
        article.append("\n\n## 代码示例\n\n");
        article.append("```java\n");
        article.append("@SpringBootApplication\n");
        article.append("public class Application {\n");
        article.append("    public static void main(String[] args) {\n");
        article.append("        SpringApplication.run(Application.class, args);\n");
        article.append("    }\n");
        article.append("}\n");
        article.append("```\n\n");
        article.append("![架构图](architecture.png)\n\n");
        for (int i = 0; i < 100; i++) {
            article.append("This is an English paragraph about Spring Boot features. ");
        }

        int result = ReadingTimeCalculator.calculate(article.toString());
        assertTrue(result >= 2, "Realistic mixed article should take at least 2 minutes");
    }
}
