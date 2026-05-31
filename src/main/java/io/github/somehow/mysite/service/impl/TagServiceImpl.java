package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.ArticleTagDO;
import io.github.somehow.mysite.dao.entity.TagDO;
import io.github.somehow.mysite.dao.mapper.ArticleTagMapper;
import io.github.somehow.mysite.dao.mapper.TagMapper;
import io.github.somehow.mysite.dto.req.tag.TagCreateReqDTO;
import io.github.somehow.mysite.dto.req.tag.TagUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.tag.TagRespDTO;
import io.github.somehow.mysite.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;

    @Override
    public void createTag(TagCreateReqDTO requestParam) {
        TagDO tagDO = BeanUtil.toBean(requestParam, TagDO.class);
        tagDO.setId(IdUtil.getSnowflakeNextId());
        try {
            tagMapper.insert(tagDO);
        } catch (DuplicateKeyException e) {
            throw new ClientException(ErrorCode.TAG_SLUG_EXISTS);
        }
    }

    @Override
    public void updateTag(Long id, TagUpdateReqDTO requestParam) {
        TagDO existing = tagMapper.selectOne(Wrappers.lambdaQuery(TagDO.class)
                .eq(TagDO::getId, id)
                .eq(TagDO::getDelFlag, 0));
        if (Objects.isNull(existing)) {
            throw new ClientException(ErrorCode.TAG_NOT_FOUND);
        }

        if (!existing.getSlug().equals(requestParam.getSlug())) {
            TagDO slugCheck = tagMapper.selectOne(Wrappers.lambdaQuery(TagDO.class)
                    .eq(TagDO::getSlug, requestParam.getSlug())
                    .eq(TagDO::getDelFlag, 0));
            if (Objects.nonNull(slugCheck)) {
                throw new ClientException(ErrorCode.TAG_SLUG_EXISTS);
            }
        }

        TagDO tagDO = new TagDO();
        tagDO.setId(id);
        tagDO.setName(requestParam.getName());
        tagDO.setSlug(requestParam.getSlug());
        try {
            tagMapper.updateById(tagDO);
        } catch (DuplicateKeyException e) {
            throw new ClientException(ErrorCode.TAG_SLUG_EXISTS);
        }
    }

    @Override
    public void deleteTag(Long id) {
        Long refCount = articleTagMapper.selectCount(Wrappers.lambdaQuery(ArticleTagDO.class)
                .eq(ArticleTagDO::getTagId, id)
                .eq(ArticleTagDO::getDelFlag, 0));
        if (refCount > 0) {
            throw new ClientException(ErrorCode.TAG_HAS_ARTICLES_CANNOT_DELETE);
        }

        TagDO tagDO = new TagDO();
        tagDO.setId(id);
        tagDO.setDelFlag(1);
        tagMapper.updateById(tagDO);
    }

    @Override
    public List<TagRespDTO> listTags() {
        List<TagDO> tags = tagMapper.selectList(Wrappers.lambdaQuery(TagDO.class)
                .eq(TagDO::getDelFlag, 0)
                .orderByDesc(TagDO::getCreateTime));

        return tags.stream().map(tag -> {
            TagRespDTO dto = BeanUtil.toBean(tag, TagRespDTO.class);
            dto.setArticleCount(articleTagMapper.selectCount(Wrappers.lambdaQuery(ArticleTagDO.class)
                    .eq(ArticleTagDO::getTagId, tag.getId())
                    .eq(ArticleTagDO::getDelFlag, 0)));
            return dto;
        }).collect(Collectors.toList());
    }

    @Override
    public TagRespDTO getTagBySlug(String slug) {
        TagDO tagDO = tagMapper.selectOne(Wrappers.lambdaQuery(TagDO.class)
                .eq(TagDO::getSlug, slug)
                .eq(TagDO::getDelFlag, 0));
        if (Objects.isNull(tagDO)) {
            throw new ClientException(ErrorCode.TAG_NOT_FOUND);
        }
        TagRespDTO dto = BeanUtil.toBean(tagDO, TagRespDTO.class);
        dto.setArticleCount(articleTagMapper.selectCount(Wrappers.lambdaQuery(ArticleTagDO.class)
                .eq(ArticleTagDO::getTagId, tagDO.getId())
                .eq(ArticleTagDO::getDelFlag, 0)));
        return dto;
    }
}
