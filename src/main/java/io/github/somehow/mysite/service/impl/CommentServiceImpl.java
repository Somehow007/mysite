package io.github.somehow.mysite.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.github.somehow.mysite.commons.context.UserContext;
import io.github.somehow.mysite.commons.enums.UserRole;
import io.github.somehow.mysite.commons.framework.errorcode.ErrorCode;
import io.github.somehow.mysite.commons.framework.exception.ClientException;
import io.github.somehow.mysite.dao.entity.CommentDO;
import io.github.somehow.mysite.dao.entity.CommentLikeDO;
import io.github.somehow.mysite.dao.entity.UserDO;
import io.github.somehow.mysite.dao.mapper.CommentLikeMapper;
import io.github.somehow.mysite.dao.mapper.CommentMapper;
import io.github.somehow.mysite.dao.mapper.UserMapper;
import io.github.somehow.mysite.dto.req.comment.CommentCreateReqDTO;
import io.github.somehow.mysite.dto.req.comment.CommentPageQueryReqDTO;
import io.github.somehow.mysite.dto.resp.CommentAdminRespDTO;
import io.github.somehow.mysite.dto.resp.CommentLikeRespDTO;
import io.github.somehow.mysite.dto.resp.CommentTreeRespDTO;
import io.github.somehow.mysite.service.CommentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CommentServiceImpl extends ServiceImpl<CommentMapper, CommentDO> implements CommentService {

    private final CommentMapper commentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate stringRedisTemplate;

    private static final int MAX_CONTENT_LENGTH = 2000;
    private static final int RATE_LIMIT_MAX = 3;
    private static final int RATE_LIMIT_MINUTES = 1;

    @Override
    @Transactional
    public void createComment(CommentCreateReqDTO requestParam, String ipAddress, String userAgent) {
        if (requestParam.getArticleId() == null) {
            throw new ClientException(ErrorCode.COMMENT_ARTICLE_REQUIRED);
        }
        if (StrUtil.isBlank(requestParam.getContent())) {
            throw new ClientException(ErrorCode.COMMENT_CONTENT_REQUIRED);
        }
        if (requestParam.getContent().length() > MAX_CONTENT_LENGTH) {
            throw new ClientException(ErrorCode.COMMENT_CONTENT_TOO_LONG);
        }

        String currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new ClientException(ErrorCode.SECURITY_NOT_AUTHENTICATED);
        }

        UserDO user = userMapper.selectById(Long.parseLong(currentUserId));
        String nickname;
        String email;
        String avatar;
        if (user != null) {
            nickname = user.getUsername();
            email = user.getEmail();
            avatar = user.getAvatar();
        } else {
            nickname = "匿名用户";
            email = null;
            avatar = null;
        }

        checkRateLimit(ipAddress, currentUserId);

        Long parentId = requestParam.getParentId();
        Long rootId = null;
        if (parentId != null) {
            CommentDO parentComment = commentMapper.selectOne(Wrappers.lambdaQuery(CommentDO.class)
                    .eq(CommentDO::getId, parentId)
                    .eq(CommentDO::getDelFlag, 0));
            if (parentComment == null) {
                throw new ClientException(ErrorCode.COMMENT_PARENT_NOT_FOUND);
            }
            rootId = parentComment.getRootId() != null ? parentComment.getRootId() : parentId;
        }

        CommentDO commentDO = CommentDO.builder()
                .id(IdUtil.getSnowflakeNextId())
                .articleId(requestParam.getArticleId())
                .parentId(parentId)
                .rootId(rootId)
                .userId(currentUserId != null ? Long.parseLong(currentUserId) : null)
                .nickname(nickname)
                .email(email)
                .avatar(avatar)
                .content(requestParam.getContent())
                .ipAddress(ipAddress)
                .userAgent(StrUtil.sub(userAgent, 0, 500))
                .likeCount(0)
                .replyCount(0)
                .status(1)
                .build();

        commentMapper.insert(commentDO);

        if (parentId != null) {
            commentMapper.update(null, Wrappers.lambdaUpdate(CommentDO.class)
                    .eq(CommentDO::getId, parentId)
                    .eq(CommentDO::getDelFlag, 0)
                    .setSql("reply_count = reply_count + 1"));
        }
    }

    @Override
    @Transactional
    public void deleteComment(Long id) {
        CommentDO comment = commentMapper.selectOne(Wrappers.lambdaQuery(CommentDO.class)
                .eq(CommentDO::getId, id)
                .eq(CommentDO::getDelFlag, 0));
        if (comment == null) {
            throw new ClientException(ErrorCode.COMMENT_NOT_FOUND);
        }

        UserRole currentRole = UserContext.getRole();
        String currentUserId = UserContext.getUserId();

        if (!UserRole.ADMIN.equals(currentRole)) {
            if (currentUserId == null || !currentUserId.equals(comment.getUserId() != null ? comment.getUserId().toString() : null)) {
                throw new ClientException(ErrorCode.COMMENT_PERMISSION_DENIED);
            }
        }

        commentMapper.deleteById(id);

        if (comment.getParentId() != null) {
            commentMapper.update(null, Wrappers.lambdaUpdate(CommentDO.class)
                    .eq(CommentDO::getId, comment.getParentId())
                    .eq(CommentDO::getDelFlag, 0)
                    .setSql("reply_count = GREATEST(reply_count - 1, 0)"));
        }
    }

    @Override
    public List<CommentTreeRespDTO> getArticleComments(Long articleId, String currentUserId, String currentIp) {
        List<CommentDO> comments = commentMapper.selectList(Wrappers.lambdaQuery(CommentDO.class)
                .eq(CommentDO::getArticleId, articleId)
                .eq(CommentDO::getStatus, 1)
                .eq(CommentDO::getDelFlag, 0)
                .orderByDesc(CommentDO::getCreateTime));

        Set<Long> likedCommentIds = new HashSet<>();
        if (currentUserId != null) {
            List<CommentLikeDO> likes = commentLikeMapper.selectList(Wrappers.lambdaQuery(CommentLikeDO.class)
                    .eq(CommentLikeDO::getUserId, Long.parseLong(currentUserId))
                    .eq(CommentLikeDO::getDelFlag, 0));
            likes.forEach(like -> likedCommentIds.add(like.getCommentId()));
        } else if (StrUtil.isNotBlank(currentIp)) {
            List<CommentLikeDO> likes = commentLikeMapper.selectList(Wrappers.lambdaQuery(CommentLikeDO.class)
                    .eq(CommentLikeDO::getIpAddress, currentIp)
                    .eq(CommentLikeDO::getDelFlag, 0));
            likes.forEach(like -> likedCommentIds.add(like.getCommentId()));
        }

        Map<Long, CommentTreeRespDTO> dtoMap = new LinkedHashMap<>();
        List<CommentTreeRespDTO> roots = new ArrayList<>();

        // 收集所有有userId的评论对应的用户ID，批量查询最新用户信息
        Set<Long> userIds = comments.stream()
                .map(CommentDO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, UserDO> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userMapper.selectBatchIds(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        for (CommentDO comment : comments) {
            CommentTreeRespDTO dto = BeanUtil.toBean(comment, CommentTreeRespDTO.class);
            dto.setIsLiked(likedCommentIds.contains(comment.getId()));
            dto.setReplies(new ArrayList<>());
            // 用最新用户信息覆盖评论中存储的旧信息
            if (comment.getUserId() != null) {
                UserDO latestUser = userMap.get(comment.getUserId());
                if (latestUser != null) {
                    dto.setNickname(latestUser.getUsername());
                    dto.setEmail(latestUser.getEmail());
                    dto.setAvatar(latestUser.getAvatar());
                }
            }
            dtoMap.put(comment.getId(), dto);
        }

        for (CommentDO comment : comments) {
            CommentTreeRespDTO dto = dtoMap.get(comment.getId());
            if (comment.getParentId() == null) {
                roots.add(dto);
            } else {
                CommentTreeRespDTO parent = dtoMap.get(comment.getParentId());
                if (parent != null) {
                    parent.getReplies().add(dto);
                } else {
                    roots.add(dto);
                }
            }
        }

        return roots;
    }

    @Override
    @Transactional
    public CommentLikeRespDTO toggleLike(Long commentId, String userId, String ipAddress) {
        CommentDO comment = commentMapper.selectOne(Wrappers.lambdaQuery(CommentDO.class)
                .eq(CommentDO::getId, commentId)
                .eq(CommentDO::getDelFlag, 0));
        if (comment == null) {
            throw new ClientException(ErrorCode.COMMENT_NOT_FOUND);
        }

        LambdaQueryWrapper<CommentLikeDO> queryWrapper;
        if (userId != null) {
            queryWrapper = Wrappers.lambdaQuery(CommentLikeDO.class)
                    .eq(CommentLikeDO::getCommentId, commentId)
                    .eq(CommentLikeDO::getUserId, Long.parseLong(userId))
                    .eq(CommentLikeDO::getDelFlag, 0);
        } else {
            queryWrapper = Wrappers.lambdaQuery(CommentLikeDO.class)
                    .eq(CommentLikeDO::getCommentId, commentId)
                    .eq(CommentLikeDO::getIpAddress, ipAddress)
                    .eq(CommentLikeDO::getDelFlag, 0);
        }

        CommentLikeDO existingLike = commentLikeMapper.selectOne(queryWrapper);

        boolean liked;
        if (existingLike != null) {
            // 物理删除，避免唯一约束冲突
            commentLikeMapper.physicalDeleteById(existingLike.getId());
            commentMapper.update(null, Wrappers.lambdaUpdate(CommentDO.class)
                    .eq(CommentDO::getId, commentId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
            liked = false;
        } else {
            CommentLikeDO like = CommentLikeDO.builder()
                    .id(IdUtil.getSnowflakeNextId())
                    .commentId(commentId)
                    .userId(userId != null ? Long.parseLong(userId) : null)
                    .ipAddress(ipAddress)
                    .build();
            commentLikeMapper.insert(like);
            commentMapper.update(null, Wrappers.lambdaUpdate(CommentDO.class)
                    .eq(CommentDO::getId, commentId)
                    .setSql("like_count = like_count + 1"));
            liked = true;
        }

        CommentDO updated = commentMapper.selectById(commentId);
        CommentLikeRespDTO resp = new CommentLikeRespDTO();
        resp.setLiked(liked);
        resp.setLikeCount(updated != null ? updated.getLikeCount() : 0);
        return resp;
    }

    @Override
    public IPage<CommentAdminRespDTO> adminPageQuery(CommentPageQueryReqDTO requestParam) {
        LambdaQueryWrapper<CommentDO> queryWrapper = Wrappers.lambdaQuery(CommentDO.class)
                .eq(CommentDO::getDelFlag, 0)
                .eq(requestParam.getArticleId() != null, CommentDO::getArticleId, requestParam.getArticleId())
                .eq(requestParam.getStatus() != null, CommentDO::getStatus, requestParam.getStatus())
                .like(StrUtil.isNotBlank(requestParam.getKeyword()), CommentDO::getContent, requestParam.getKeyword());

        // 排序逻辑
        String sortField = StrUtil.blankToDefault(requestParam.getSortField(), "createTime");
        String sortOrder = StrUtil.blankToDefault(requestParam.getSortOrder(), "desc");

        switch (sortField) {
            case "likeCount":
                if ("asc".equalsIgnoreCase(sortOrder)) {
                    queryWrapper.orderByAsc(CommentDO::getLikeCount);
                } else {
                    queryWrapper.orderByDesc(CommentDO::getLikeCount);
                }
                break;
            case "replyCount":
                if ("asc".equalsIgnoreCase(sortOrder)) {
                    queryWrapper.orderByAsc(CommentDO::getReplyCount);
                } else {
                    queryWrapper.orderByDesc(CommentDO::getReplyCount);
                }
                break;
            default:
                if ("asc".equalsIgnoreCase(sortOrder)) {
                    queryWrapper.orderByAsc(CommentDO::getCreateTime);
                } else {
                    queryWrapper.orderByDesc(CommentDO::getCreateTime);
                }
                break;
        }

        Page<CommentDO> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<CommentDO> result = commentMapper.selectPage(page, queryWrapper);

        List<Long> articleIds = result.getRecords().stream()
                .map(CommentDO::getArticleId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> articleTitleMap = new HashMap<>();
        if (!articleIds.isEmpty()) {
            List<Map<String, Object>> articles = commentMapper.selectMaps(Wrappers.lambdaQuery(CommentDO.class)
                    .select(CommentDO::getArticleId)
                    .in(CommentDO::getArticleId, articleIds));
        }

        IPage<CommentAdminRespDTO> respPage = result.convert(commentDO -> {
            CommentAdminRespDTO dto = BeanUtil.toBean(commentDO, CommentAdminRespDTO.class);
            return dto;
        });

        return respPage;
    }

    @Override
    @Transactional
    public void updateStatus(Long id, Integer status) {
        if (status == null || status < 0 || status > 2) {
            throw new ClientException(ErrorCode.COMMENT_STATUS_INVALID);
        }
        CommentDO comment = commentMapper.selectOne(Wrappers.lambdaQuery(CommentDO.class)
                .eq(CommentDO::getId, id)
                .eq(CommentDO::getDelFlag, 0));
        if (comment == null) {
            throw new ClientException(ErrorCode.COMMENT_NOT_FOUND);
        }
        commentMapper.update(null, Wrappers.lambdaUpdate(CommentDO.class)
                .eq(CommentDO::getId, id)
                .set(CommentDO::getStatus, status));
    }

    private void checkRateLimit(String ipAddress, String userId) {
        String key = "comment:rate:" + (userId != null ? "u:" + userId : "ip:" + ipAddress);
        String count = stringRedisTemplate.opsForValue().get(key);
        if (count != null && Integer.parseInt(count) >= RATE_LIMIT_MAX) {
            throw new ClientException(ErrorCode.COMMENT_RATE_LIMITED);
        }
        stringRedisTemplate.opsForValue().increment(key);
        stringRedisTemplate.expire(key, RATE_LIMIT_MINUTES, TimeUnit.MINUTES);
    }
}
