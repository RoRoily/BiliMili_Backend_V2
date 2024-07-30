package com.bilimili.buaa13.service.postComment;
import com.bilimili.buaa13.entity.*;

import java.util.List;

public interface PostCommentService {
    List<PostCommentTree> getPostCommentTreeByPid(Integer pid, Long offset, Integer type);

    PostCommentTree sendPostComment(Integer vid, Integer uid, Integer rootId, Integer parentId, Integer toUserId, String content);

    ResponseResult deletePostComment(Integer id, Integer uid, boolean isAdmin);

    List<PostComment> getChildPostCommentsByRootId(Integer rootId, Integer vid, Long start, Long stop);

    List<PostComment> getRootPostCommentsByVid(Integer vid, Long offset, Integer type);

    PostCommentTree getMoreCommentsById(Integer id);

    /*
    评论点赞点踩相关
     */
    void updateLikeAndDisLike(Integer id, boolean addLike);

    void updateComment(Integer id, String column, boolean incr, Integer count);
}

