package com.bilimili.backend.service.Update;

import com.bilimili.backend.pojo.CommentTree;
import com.bilimili.backend.pojo.UpdateTree;

public interface UpdateServe {

    UpdateTree sendUpdate(Integer vid, Integer uid, Integer rootId, Integer parentId, Integer toUserId, String content);

}
