package com.bilimili.buaa13.service.user;

import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.List;


public interface FollowService {




    /**
     * 根据是否用户本人获取全部可见的关注列表
     * @param uid   用户ID
     * @param isOwner  是否用户本人
     * @return  关注列表
     */
    List<Integer> getUidFollow(Integer uid, boolean isOwner);
    /**
     * 根据是否用户本人获取全部可见的关注列表
     * @param uid   用户ID
     * @param isOwner  是否用户本人
     * @return  关注列表
     */
    List<Integer> getUidFans(Integer uid, boolean isOwner);
    List<Integer> getUidFans(Integer uid);
    /**
     * 关注用户
     * @param uidFollow   关注者ID
     * @param uidFans   粉丝ID
     * 关注者id对应的用户，有一个粉丝ID
     * 粉丝id对应的用户，有一个关注ID
     */

    void addFollow(Integer uidFollow,Integer uidFans) throws JsonProcessingException;

    /**
     * 取关用户
     * @param uidFollow   关注者ID
     * @param uidFans   被关注者ID
     */

    void delFollow(Integer uidFollow, Integer uidFans) throws JsonProcessingException;

    /**
     * 更新其他人是否可以查看关注列表
     * @param uid   自己的ID
     * @param visible   能否查看,1可以，0不可以
     */
    void updateVisible(Integer uid, Integer visible);

    /**
     * 检查该用户是否被关注
     * @param uidFollow   关注者ID
     * @param uidFans   粉丝ID
     * 关注者id对应的用户，有一个粉丝ID
     * 粉丝id对应的用户，有一个关注ID
     */
    public boolean isHisFans(Integer uidFollow,Integer uidFans);
}

