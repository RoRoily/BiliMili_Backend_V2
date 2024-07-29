package com.bilimili.backend.service.comment;

import com.bilimili.backend.pojo.CustomResponse;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserCommentService {
    Map<String, Object> getUserLikeAndDislike(Integer uid);

    void userSetLikeOrUnlike(Integer uid, Integer id, boolean isLike, boolean isSet);

}
