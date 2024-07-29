package com.bilimili.buaa13.service.record;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.bilimili.buaa13.entity.UserRecord;
import com.bilimili.buaa13.entity.UserRecordString;

import java.util.List;

public interface UserRecordService {

    /**
     * 将UserRecord类记录至对应的UserRecordString类
     * @param userRecord 用户记录
     * @return 用户记录（字符串存储）
     */
    UserRecordString saveUserRecordToString(UserRecord userRecord) throws JsonProcessingException;

    /**
     * 将UserRecordString类记录至对应的UserRecord类
     * @param userRecordString 用户记录（字符串存储）
     * @return 用户记录
     */
    UserRecord findUserRecordByString(UserRecordString userRecordString) throws JsonProcessingException;

    /**
     * 将UserRecordString类存入数据库，看情况更新数据库还是直接存
     * @param userRecordString 用户记录（字符串存储）
     */
    void saveUserRecordStringToDatabase(UserRecordString userRecordString);

    /**
     * 根据uid获取UserRecord,如果redis没有则将数据库的内容存入redis中
     * @param uid 用户uid
     * @return 用户记录
     */
    UserRecord getUserRecordByUid(Integer uid);

    /**
     * 获取近七天播放量增长量
     * @param uid 用户uid
     * @return    一个增长list
     */
    List<Integer> getPlayRecordByUid(Integer uid);
    void setPlayRecordByUid(Integer uid) throws JsonProcessingException;
    /**
     * 获取近七天点赞量增长量
     * @param uid 用户uid
     * @return    一个增长list
     */
    List<Integer> getLoveRecordByUid(Integer uid);
    void setLoveRecordByUid(Integer uid) throws JsonProcessingException;
    /**
     * 获取近七天收藏量增长量
     * @param uid 用户uid
     * @return    一个增长list
     */
    List<Integer> getCollectRecordByUid(Integer uid);
    void setCollectRecordByUid(Integer uid) throws JsonProcessingException;
    /**
     * 获取近七天关注量增长量
     * @param uid 用户uid
     * @return    一个增长list
     */
    List<Integer> getFansRecordByUid(Integer uid);
    void setFansRecordByUid(Integer uid) throws JsonProcessingException;

    /**
     * 更新某个用户的record
     * @param uid 用户uid
     */
    void updateRecordByUid(Integer uid) throws JsonProcessingException;
    /**
     * 实现定时更新，暂定于UTM+8的0点
     */
    void updateRecord() throws JsonProcessingException;
}
