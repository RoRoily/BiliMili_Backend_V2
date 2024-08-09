package com.bilimili.buaa13.service.message;

import com.bilimili.buaa13.entity.MsgUnread;

public interface MsgUnreadService {

    /**
     * 给指定用户的某一列未读消息加一
     * @param uid   用户ID
     * @param column    msg_unread表列名 "reply"/"at_num"/"up_vote"/"system_message"/"message"/"dynamic"
     */
    void addOneUnread(Integer uid, String column);

    /**
     * 清除指定用户的某一列未读消息
     * @param uid   用户ID
     * @param column    msg_unread表列名 "reply"/"at_num"/"up_vote"/"system_message"/"message"/"dynamic"
     */
    void clearOneUnread(Integer uid, String column);

    /**
     * 私聊消息,减除一定数量的未读
     * @param uid   用户ID
     * @param count 要减多少
     */
    void subUnreadWhisper(Integer uid, Integer count);

    /**
     * 获取某人的全部消息未读数
     * @param uid   用户ID
     * @return  MsgUnread对象
     */
    MsgUnread getUnreadByUid(Integer uid);
}
