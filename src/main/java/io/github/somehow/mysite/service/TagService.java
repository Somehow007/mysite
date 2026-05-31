package io.github.somehow.mysite.service;

import io.github.somehow.mysite.dto.req.tag.TagCreateReqDTO;
import io.github.somehow.mysite.dto.req.tag.TagUpdateReqDTO;
import io.github.somehow.mysite.dto.resp.tag.TagRespDTO;

import java.util.List;

public interface TagService {

    void createTag(TagCreateReqDTO requestParam);

    void updateTag(Long id, TagUpdateReqDTO requestParam);

    void deleteTag(Long id);

    List<TagRespDTO> listTags();

    TagRespDTO getTagBySlug(String slug);
}
