package io.github.somehow.mysite.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.CommentDO;
import io.github.somehow.mysite.dao.mapper.CommentMapper;
import io.github.somehow.mysite.dto.req.comment.CommentCreateReqDTO;
import io.github.somehow.mysite.dto.resp.comment.CommentPageQueryRespDTO;
import io.github.somehow.mysite.service.CommentService;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 评论业务逻辑实现层
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, CommentDO> implements CommentService {

    @Override
    public void createComment(CommentCreateReqDTO requestParam) {
        if (StrUtil.isBlank(requestParam.getContent())) {
            throw new ClientException("创建评论失败，请输入评论内容");
        }
        if (StrUtil.isBlank(requestParam.getUserId())
                || StrUtil.isBlank(requestParam.getArticleId())) {
            throw new ClientException("创建评论失败，请传入用户和文章");
        }

        CommentDO commentDO = CommentDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .articleId(Long.parseLong(requestParam.getArticleId()))
                .parentId(Optional.ofNullable(requestParam.getParentId()).map(Long::parseLong).orElse(0L))
                .content(requestParam.getContent())
                .build();

        baseMapper.insert(commentDO);
    }

    @Override
    public void deleteComment(String id) {
        LambdaUpdateWrapper<CommentDO> queryWrapper = Wrappers.lambdaUpdate(CommentDO.class)
                .eq(CommentDO::getId, Long.parseLong(id))
                .eq(CommentDO::getDelFlag, 0)
                .set(CommentDO::getDelFlag, 1);
        baseMapper.update(queryWrapper);
    }

    @Override
    public IPage<CommentPageQueryRespDTO> pageQueryComments(String articleId) {
        return baseMapper.pageCommentResults(Long.parseLong(articleId));
    }
}
