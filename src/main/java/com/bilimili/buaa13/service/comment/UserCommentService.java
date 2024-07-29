package com.bilimili.buaa13.service.comment;

import java.util.Map;

public interface UserCommentService {
    Map<String, Object> getUserLikeAndDislike(Integer uid);

    void userSetLikeOrUnlike(Integer uid, Integer id, boolean isLike, boolean isSet);

}
