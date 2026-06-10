package io.github.somehow.mysite.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import io.github.somehow.mysite.dao.entity.CommentDO;
import io.github.somehow.mysite.dto.req.comment.CommentCreateReqDTO;
import io.github.somehow.mysite.dto.req.comment.CommentPageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.CommentAdminRespDTO;
import io.github.somehow.mysite.dto.resp.CommentLikeRespDTO;
import io.github.somehow.mysite.dto.resp.CommentTreeRespDTO;

import java.util.List;

public interface CommentService extends IService<CommentDO> {

    void createComment(CommentCreateReqDTO requestParam, String ipAddress, String userAgent);

    void deleteComment(Long id);

    List<CommentTreeRespDTO> getArticleComments(Long articleId, String currentUserId, String currentIp);

    CommentLikeRespDTO toggleLike(Long commentId, String userId, String ipAddress);

    IPage<CommentAdminRespDTO> adminPageQuery(CommentPageQueryReqDTO requestParam);

    void updateStatus(Long id, Integer status);
}
