package io.github.somehow.mysite.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.somehow.mysite.commons.framework.result.Result;
import io.github.somehow.mysite.config.SiteProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code GET /v1/site/config} 契约测试：返回 {@link SiteProperties} 序列化结果，
 * 字段名（camelCase）必须与前端 {@code SiteConfig} 类型一致。
 */
class SiteConfigControllerTest {

    private final SiteConfigController controller = new SiteConfigController(new SiteProperties());

    @Test
    void returnsSuccessResultWithSiteProperties() {
        Result<SiteProperties> result = controller.getSiteConfig();

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
        assertEquals("MySite", result.getData().getTitle());
        assertTrue(result.getData().isCommentEnabled());
        assertEquals(3, result.getData().getNavigation().size());
    }

    @Test
    void serializesToFrontendContractShape() throws Exception {
        SiteProperties props = new SiteProperties();
        props.setTitle("Test Site");
        props.setSubtitle("副标题");
        props.setAuthorBio(" bio ");
        props.setGithub("https://github.com/x");
        props.setRss("/rss.xml");
        props.setNavigation(List.of(
                new SiteProperties.NavItem(),
                new SiteProperties.NavItem()));

        Map<String, Object> json = new ObjectMapper()
                .readValue(new ObjectMapper().writeValueAsString(props), Map.class);

        for (String key : List.of(
                "title", "subtitle", "description", "author",
                "authorBio", "authorTagline", "avatar", "url",
                "github", "rss", "commentEnabled", "navigation",
                "askExamples", "askDemo")) {
            assertTrue(json.containsKey(key), "missing key: " + key);
        }
        // askExamples 有类内默认 4 条；askDemo 默认 null（前端不渲染示例块）
        assertEquals(4, ((List<?>) json.get("askExamples")).size());
        assertNull(json.get("askDemo"));
        assertEquals("Test Site", json.get("title"));
        assertEquals("副标题", json.get("subtitle"));
        assertEquals(" bio ", json.get("authorBio"));
        assertEquals("https://github.com/x", json.get("github"));
        assertEquals("/rss.xml", json.get("rss"));
        assertEquals(true, json.get("commentEnabled"));
        assertTrue(json.get("navigation") instanceof List);
    }

    @Test
    void bindsKebabCaseKeysAndNavigationList() {
        // 模拟 application.yaml site: 段经 ${ENV:} 解析后的属性源（kebab-case 键）
        Map<String, Object> source = new HashMap<>();
        source.put("site.title", "Bound Site");
        source.put("site.author-bio", "约束绑定的简介");
        source.put("site.comment-enabled", "false");
        source.put("site.navigation[0].label", "首页");
        source.put("site.navigation[0].path", "/");
        source.put("site.navigation[1].label", "归档");
        source.put("site.navigation[1].path", "/archive");

        Binder binder = new Binder(
                new MapConfigurationPropertySource(source));
        SiteProperties bound = binder
                .bind("site", SiteProperties.class)
                .get();

        assertEquals("Bound Site", bound.getTitle());
        assertEquals("约束绑定的简介", bound.getAuthorBio());
        assertFalse(bound.isCommentEnabled());
        assertEquals(2, bound.getNavigation().size());
        assertEquals("首页", bound.getNavigation().get(0).getLabel());
        assertEquals("/archive", bound.getNavigation().get(1).getPath());
    }

    @Test
    void bindsAskExamplesAndAskDemo() {
        // site.ask-examples / site.ask-demo.* 经 yaml 或 additional-location 覆盖的绑定路径
        Map<String, Object> source = new HashMap<>();
        source.put("site.ask-examples[0]", "自定义问题一");
        source.put("site.ask-examples[1]", "自定义问题二");
        source.put("site.ask-demo.question", "示例问题？");
        source.put("site.ask-demo.answer", "示例回答[1]");
        source.put("site.ask-demo.sources[0].title", "示例文章");
        source.put("site.ask-demo.sources[0].section", "§1 开头");
        source.put("site.ask-demo.sources[0].article-id", "123");

        SiteProperties bound = new Binder(
                new MapConfigurationPropertySource(source))
                .bind("site", SiteProperties.class)
                .get();

        assertEquals(List.of("自定义问题一", "自定义问题二"), bound.getAskExamples());
        assertNotNull(bound.getAskDemo());
        assertEquals("示例问题？", bound.getAskDemo().getQuestion());
        assertEquals("示例回答[1]", bound.getAskDemo().getAnswer());
        assertEquals(1, bound.getAskDemo().getSources().size());
        assertEquals("示例文章", bound.getAskDemo().getSources().get(0).getTitle());
        assertEquals("§1 开头", bound.getAskDemo().getSources().get(0).getSection());
        assertEquals("123", bound.getAskDemo().getSources().get(0).getArticleId());
    }
}
