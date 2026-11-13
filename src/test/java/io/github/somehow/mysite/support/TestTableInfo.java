package io.github.somehow.mysite.support;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.github.somehow.mysite.dao.entity.ArticleDO;
import io.github.somehow.mysite.dao.entity.ArticleTagDO;
import io.github.somehow.mysite.dao.entity.CategoryDO;
import io.github.somehow.mysite.dao.entity.CollectionArticleDO;
import io.github.somehow.mysite.dao.entity.CollectionDO;
import io.github.somehow.mysite.dao.entity.CommentDO;
import io.github.somehow.mysite.dao.entity.CommentLikeDO;
import io.github.somehow.mysite.dao.entity.ImageDO;
import io.github.somehow.mysite.dao.entity.TagDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.entity.UserFavoriteArticleDO;
import io.github.somehow.mysite.dao.entity.UserFollowDO;
import io.github.somehow.mysite.dao.entity.UserOperationLogDO;
import io.github.somehow.mysite.journal.dao.entity.SjCustomMoodDO;
import io.github.somehow.mysite.journal.dao.entity.SjDayRecordDO;
import io.github.somehow.mysite.journal.dao.entity.SjGoalCheckinDO;
import io.github.somehow.mysite.journal.dao.entity.SjGoalDO;
import io.github.somehow.mysite.journal.dao.entity.SjGoalTaskDO;
import io.github.somehow.mysite.journal.dao.entity.SjLearningItemDO;
import io.github.somehow.mysite.ragent.dao.entity.ConversationDO;
import io.github.somehow.mysite.ragent.dao.entity.ConversationMessageDO;
import io.github.somehow.mysite.ragent.dao.entity.IntentDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeBaseDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeChunkDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeDocumentDO;
import io.github.somehow.mysite.ragent.dao.entity.KnowledgeVectorDO;
import io.github.somehow.mysite.ragent.dao.entity.LlmProviderSettingDO;
import io.github.somehow.mysite.ragent.dao.entity.LlmUsageDO;
import org.apache.ibatis.builder.MapperBuilderAssistant;

/**
 * Mockito 单测里 LambdaQueryWrapper 的前置条件：实体表元数据缓存。
 *
 * <p>生产环境由 MyBatis 上下文解析 mapper 时填充；纯 Mockito 测试没有 Spring 上下文，
 * 必须手动 {@link TableInfoHelper#initTableInfo}，否则报
 * {@code can not find lambda cache for this entity}。</p>
 *
 * <p>注意：此前部分测试能过是因为与 @SpringBootTest 同 JVM 时缓存被顺带填充——
 * 属于顺序依赖。新写用到 LambdaWrapper 的单测请在 @BeforeAll 调 {@link #initAll()}。</p>
 */
public final class TestTableInfo {

    private TestTableInfo() {
    }

    public static void initAll() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        Class<?>[] entities = {
            ArticleDO.class, ArticleTagDO.class, CategoryDO.class,
            CollectionArticleDO.class, CollectionDO.class, CommentDO.class,
            CommentLikeDO.class, ImageDO.class, TagDO.class, UserDO.class,
            UserFavoriteArticleDO.class, UserFollowDO.class, UserOperationLogDO.class,
            SjCustomMoodDO.class, SjDayRecordDO.class, SjGoalCheckinDO.class,
            SjGoalDO.class, SjGoalTaskDO.class, SjLearningItemDO.class,
            ConversationDO.class, ConversationMessageDO.class, IntentDO.class,
            KnowledgeBaseDO.class, KnowledgeChunkDO.class, KnowledgeDocumentDO.class,
            KnowledgeVectorDO.class, LlmProviderSettingDO.class, LlmUsageDO.class,
        };
        for (Class<?> entity : entities) {
            TableInfoHelper.initTableInfo(assistant, entity);
        }
    }
}
