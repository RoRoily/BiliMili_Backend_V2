package com.bilimili.buaa13.service.impl.comment;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bilimili.buaa13.entity.*;
import com.bilimili.buaa13.im.IMServer;
import com.bilimili.buaa13.mapper.CommentMapper;
import com.bilimili.buaa13.mapper.VideoMapper;
import com.bilimili.buaa13.service.comment.CommentService;
import com.bilimili.buaa13.service.message.MsgUnreadService;
import com.bilimili.buaa13.service.user.UserService;
import com.bilimili.buaa13.service.video.VideoStatsService;
import com.bilimili.buaa13.utils.RedisUtil;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;


@Slf4j
@Service
public class CommentServiceImpl implements CommentService {
    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private CommentMapper commentMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoStatsService videoStatsService;

    @Autowired
    private UserService userService;

    @Autowired
    private MsgUnreadService msgUnreadService;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    /**
     * 获取评论树列表
     * @param vid   对应视频ID
     * @param offset 分页偏移量（已经获取到的评论树的数量）
     * @param sortType  排序类型 1 按热度排序 2 按时间排序
     * @return  评论树列表
     */
    @Override
    public List<CommentTree> getCommentTreeByVid(Integer vid, Long offset, Integer sortType) {
        // 查询父级评论
        List<Comment> rootComments = getRootCommentsByVid(vid, offset, sortType);

        return rootComments.stream().parallel()
                .map(rootComment ->buildCommentTree(rootComment, 0L, 2L))
                .collect(Collectors.toList());
    }

    /**
     * 构建评论树
     * @param comment 根评论
     * @param start 子评论开始偏移量
     * @param end  子评论结束偏移量
     * @return  单棵评论树
     * 2024.08.03
     */
    private CommentTree buildCommentTree(Comment comment, Long start, Long end) {
        //先将子节点设为null，下面再递归构建
        CommentTree commentTree = new CommentTree(
                comment.getCid(),
                comment.getVid(),
                comment.getRootId(),
                comment.getParentId(),
                comment.getContent(),
                userService.getUserByUId(comment.getUid()),
                userService.getUserByUId(comment.getToUserId()),
                comment.getUpVote(),
                comment.getDownVote(),
                null,
                comment.getCreateTime(),
                0L
        );

        // 递归查询构建子评论树
        Integer cid = comment.getCid();
        QueryWrapper<Comment> commentQueryWrapper = new QueryWrapper<>();
        commentQueryWrapper.eq("parent_id", cid).ne("is_deleted", 1);
        long count = commentMapper.selectCount(commentQueryWrapper);
        commentTree.setCount(count);
        List<Comment> comments;
        if(end.equals(-1L)){
            end = Long.MAX_VALUE;
        }
        long limit = end - start +1;
        long currentPage = start / limit + 1;
        Page<Comment> page = new Page<>(currentPage, limit);
        Page<Comment> commentPage = commentMapper.selectPage(page, commentQueryWrapper);
        //查询回复的评论数组
        comments = commentPage.getRecords();
        List<Comment> sonComments = getChildCommentsByRootId(comment.getCid(), start, end);
        if (comment.getRootId() == 0 || (comments!=null && !comments.isEmpty()) || (sonComments !=null && !sonComments.isEmpty())) {
            List<CommentTree> sonTreeList = new ArrayList<>();
            if(sonComments ==null || sonComments.isEmpty()){ sonComments = comments;}
            for(Comment sonComment : sonComments) {
                CommentTree sonNode = buildCommentTree(sonComment, start, end);
                sonTreeList.add(sonNode);
            }
            commentTree.setSonNode(sonTreeList);
        }
        return commentTree;
    }

    /**
     * 发送评论，字数不得大于2000或为空
     * @param vid   视频id
     * @param uid   发布者uid
     * @param rootId    楼层id（根评论id）
     * @param parentId  被回复的评论id
     * @param toUserId  被回复用户uid
     * @param content   评论内容
     * @return  true 发送成功 false 发送失败
     */
    @Override
    @Transactional
    public CommentTree sendComment(Integer vid, Integer uid, Integer rootId, Integer parentId, Integer toUserId, String content) {
        if (content == null || content.isEmpty() || content.length() > 2000) return null;
        Comment comment = new Comment(
                null,
                vid,
                uid,
                rootId,
                parentId,
                toUserId,
                content,
                0,
                0,
                new Date(),
                null,
                null
        );
        commentMapper.insert(comment);
        // 更新视频评论 + 1
        videoStatsService.updateVideoStats(comment.getVid(), "comment", true, 1);

        CommentTree commentTree = buildCommentTree(comment, 0L, -1L);

        try {
            CompletableFuture.runAsync(() -> {
                // 如果不是根级评论，则加入 redis 对应的 zset 中
                if (!rootId.equals(0)) {
                    redisUtil.zset("comment_reply:" + rootId, comment.getCid());
                } else {
                    redisUtil.zset("comment_video:"+ vid, comment.getCid());
                }
                // 表示被回复的用户收到的回复评论的 cid 有序集合
                // 如果不是回复自己
                if(!Objects.equals(comment.getToUserId(), comment.getUid())) {
                    redisUtil.zset("reply_zset:" + comment.getToUserId(), comment.getCid());
                    msgUnreadService.addOneUnread(comment.getToUserId(), "reply");

                    // netty 通知未读消息
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "接收");
                    Set<Channel> myChannels = IMServer.userChannel.get(comment.getToUserId());
                    if (myChannels != null) {
                        for (Channel channel: myChannels) {
                            channel.writeAndFlush(IMResponse.message("reply", map));
                        }
                    }
                }
            }, taskExecutor);
        } catch (Exception e) {
            log.error("发送评论过程中出现一点差错");
            e.printStackTrace();
        }

        return commentTree;
    }

    /**
     * 删除评论
     * @param cid    评论id
     * @param uid   当前用户id
     * @param isAdmin   是否是管理员
     * @return  响应对象
     */
    @Override
    @Transactional
    public ResponseResult deleteComment(Integer cid, Integer uid, boolean isAdmin) {
        ResponseResult responseResult = new ResponseResult();
        QueryWrapper<Comment> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", cid).ne("is_deleted", 1);
        Comment comment = commentMapper.selectOne(queryWrapper);
        if (comment == null) {
            responseResult.setCode(404);
            responseResult.setMessage("评论不存在");
            return responseResult;
        }

        // 判断该用户是否有权限删除这条评论
        Video video = videoMapper.selectById(comment.getVid());
        if (Objects.equals(comment.getUid(), uid) || isAdmin || Objects.equals(video.getUid(), uid)) {
            // 删除评论
            UpdateWrapper<Comment> commentWrapper = new UpdateWrapper<>();
            commentWrapper.eq("id", comment.getCid()).set("is_deleted", 1);
            commentMapper.update(null, commentWrapper);

            /*
             如果该评论是根节点评论，则删掉其所有回复。
             如果不是根节点评论，则将他所在的 comment_reply(zset) 中的 comment_id 删掉
             */
            if (Objects.equals(comment.getRootId(), 0)) {
                // 查询总共要减少多少评论数
                int count = Math.toIntExact(redisUtil.zCard("comment_reply:" + comment.getCid()));
                videoStatsService.updateVideoStats(comment.getVid(), "comment", false, count + 1);
                redisUtil.zsetDelMember("comment_video:" + comment.getVid(), comment.getCid());
                redisUtil.delValue("comment_reply:" + comment.getCid());
            } else {
                videoStatsService.updateVideoStats(comment.getVid(), "comment", false, 1);
                redisUtil.zsetDelMember("comment_reply:" + comment.getRootId(), comment.getCid());
            }

            responseResult.setCode(200);
            responseResult.setMessage("删除成功!");
        } else {
            responseResult.setCode(403);
            responseResult.setMessage("你无权删除该条评论");
        }
        return responseResult;
    }

    /**
     * @param rootId 根级节点的评论 cid, 即楼层 cid
     * @return 1. 根据 redis 查找出回复该评论的子评论 cid 列表
     * 2. 根据 cid 多线程查询出所有评论的详细信息
     * 2024.08.03
     */
    @Override
    public List<Comment> getChildCommentsByRootId(Integer rootId, Long start, Long end) {
        //注释Redis
        /*Set<Object> replyIds = redisUtil.zRange("comment_reply:" + rootId, start, end);
        if (replyIds == null || replyIds.isEmpty()) return Collections.emptyList();
        QueryWrapper<Comment> wrapper = new QueryWrapper<>();
        wrapper.in("id", replyIds).ne("is_deleted", 1);
        return commentMapper.selectList(wrapper);*/
        if(end.equals(-1L)){
            return commentMapper.getRootCommentByStartNoLimit(rootId, start);
        }
        return commentMapper.getRootCommentsByStartAndLimit(rootId, start, end - start +1);
    }

    /**
     * 根据视频 vid 获取根评论列表，一次查 10 条
     * @param vid 视频 cid
     * @param offset 偏移量，已经获取到的根评论数量
     * @param sortType 1:按热度排序 2:按时间排序
     * @return List<Comment>
     */
    @Override
    public List<Comment> getRootCommentsByVid(Integer vid, Long offset, Integer sortType) {
        Set<Object> rootIdsSet;
        if (sortType == 1) {
            // 按热度排序就不能用时间分数查偏移量了，要全部查出来，后续在MySQL筛选
            rootIdsSet = redisUtil.zReverange("comment_video:" + vid, 0L, -1L);
        } else {
            rootIdsSet = redisUtil.zReverange("comment_video:" + vid, offset, offset + 9L);
        }

        if (rootIdsSet == null || rootIdsSet.isEmpty()) return Collections.emptyList();

        QueryWrapper<Comment> commentQueryWrapper = new QueryWrapper<>();
        commentQueryWrapper.in("id", rootIdsSet).ne("is_deleted", 1);
        if (sortType == 1) { // 热度
            commentQueryWrapper.orderByDesc("(up_vote - down_vote)").last("LIMIT 10 OFFSET " + offset);
        } else if(sortType == 2){ // 时间
            commentQueryWrapper.orderByDesc("create_time");
        }
        return commentMapper.selectList(commentQueryWrapper);
    }

    /**
     * 获取更多回复评论
     * 由于获取的是回复，所以根据当前评论节点为基准，构建评论树
     * @param cid 根评论id
     * @return  包含全部回复评论的评论树
     */
    @Override
    public CommentTree getMoreCommentsById(Integer cid) {
        Comment comment = commentMapper.selectById(cid);
        return buildCommentTree(comment, 0L, -1L);
    }

    /**
     * 同时相对更新点赞和点踩
     * 用于原本点踩了，现在直接点赞，一次改完。
     * @param cid    评论id
     * @param addUpVote   true 点赞 false 点踩
     */
    @Override
    public void updateLikeAndDisLike(Integer cid, boolean addUpVote) {
        UpdateWrapper<Comment> updateWrapper = new UpdateWrapper<>();
        if (addUpVote) {
            updateWrapper.setSql(
                    "up_vote = up_vote + 1, down_vote = CASE WHEN " +
                    "down_vote - 1 < 0 " +
                    "THEN 0 " +
                    "ELSE down_vote - 1 END"
            );
        } else {
            updateWrapper.setSql(
                    "down_vote = down_vote + 1, up_vote = CASE WHEN " +
                    "up_vote - 1 < 0 " +
                    "THEN 0 " +
                    "ELSE up_vote - 1 END"
            );
        }

        commentMapper.update(null, updateWrapper);
    }

    /**
     * 单独更新点赞或点踩
     * @param cid    评论id
     * @param column    "up_vote" 点赞 "down_vote" 点踩
     * @param increase  true 增加 false 减少
     * @param count     更改数量
     */
    @Override
    public void updateComment(Integer cid, String column, boolean increase, Integer count) {
        UpdateWrapper<Comment> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", cid);
        if (increase) {
            updateWrapper.setSql(column + " = " + column + " + " + count);
        } else {
            // 减少对应值，减少后值必须大于等于0，需要检验
            updateWrapper.setSql(column + " = CASE WHEN " + column + " - " + count + " < 0 THEN 0 ELSE " + column + " - " + count + " END");
        }
        commentMapper.update(null, updateWrapper);
    }
}