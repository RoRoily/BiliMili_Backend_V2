package com.bilimili.buaa13.service.impl.record;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.buaa13.entity.ResponseResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.bilimili.buaa13.mapper.UserMapper;
import com.bilimili.buaa13.mapper.UserRecordStringMapper;
import com.bilimili.buaa13.entity.UserRecord;
import com.bilimili.buaa13.entity.UserRecordString;
import com.bilimili.buaa13.service.record.UserRecordService;
import com.bilimili.buaa13.utils.JsonUtil;
import com.bilimili.buaa13.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;


import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class UserRecordServiceImpl implements UserRecordService {
    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserRecordStringMapper userRecordStringMapper;
    /**
     * 将UserRecord类记录至对应的UserRecordString类
     *
     * @param userRecord 用户记录
     * @return 用户记录（字符串存储）
     */
    @Override
    public UserRecordString saveUserRecordToString(UserRecord userRecord) throws JsonProcessingException {
        List<Integer> plays = userRecord.getPlay();
        String playJson = JsonUtil.ObjectToJson(plays);
        List<Integer> loves = userRecord.getLove();
        String lovesJson = JsonUtil.ObjectToJson(loves);
        List<Integer> collects = userRecord.getCollect();
        String collectJson = JsonUtil.ObjectToJson(collects);
        List<Integer> fans = userRecord.getFans();
        String fansJson = JsonUtil.ObjectToJson(fans);
        return new UserRecordString(
                userRecord.getUid(),
                playJson,
                userRecord.getPlayNew(),
                userRecord.getPlayOld(),
                lovesJson,
                userRecord.getLoveNew(),
                userRecord.getLoveOld(),
                collectJson,
                userRecord.getCollectNew(),
                userRecord.getCollectOld(),
                fansJson,
                userRecord.getFansNew(),
                userRecord.getFansOld()
        );
    }

    /**
     * 将UserRecordString类记录至对应的UserRecord类
     *
     * @param userRecordString 用户记录（字符串存储）
     * @return 用户记录
     */
    @Override
    public UserRecord findUserRecordByString(UserRecordString userRecordString) throws JsonProcessingException {
        List<Integer> plays = JsonUtil.JsonToObject(userRecordString.getPlayJson(),List.class);
        List<Integer> loves = JsonUtil.JsonToObject(userRecordString.getLoveJson(),List.class);
        List<Integer> collects = JsonUtil.JsonToObject(userRecordString.getCollectJson(),List.class);
        List<Integer> fans = JsonUtil.JsonToObject(userRecordString.getFanJson(),List.class);
        return new UserRecord(
                userRecordString.getUid(),
                plays,
                userRecordString.getPlayNew(),
                userRecordString.getPlayOld(),
                loves,
                userRecordString.getLoveNew(),
                userRecordString.getLoveOld(),
                collects,
                userRecordString.getCollectNew(),
                userRecordString.getCollectOld(),
                fans,
                userRecordString.getFansNew(),
                userRecordString.getFansOld()
        );
    }

    /**
     * 将UserRecordString类存入数据库，看情况更新数据库还是直接存
     *
     * @param userRecordString 用户记录（字符串存储）
     */
    @Override
    public void saveUserRecordStringToDatabase(UserRecordString userRecordString) {
        int uid = userRecordString.getUid();
        QueryWrapper<UserRecordString> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("uid",uid);
        UserRecordString userRecordString1 = userRecordStringMapper.selectOne(queryWrapper);
        if(userRecordString1!=null){
            userRecordStringMapper.delete(queryWrapper);
        }
        userRecordStringMapper.insert(userRecordString);
    }

    /**
     * 根据uid获取UserRecord,如果redis没有则将数据库的内容存入redis中
     *
     * @param uid 用户uid
     * @return 用户记录
     */
    @Override
    public UserRecord getUserRecordByUid(Integer uid) {
        //String key = "userRecord:" + uid;
        UserRecord userRecord = null;
        try{
            //Set<Object> userRecordSet = redisUtil.zRange(key,0,-1);
//            if(userRecordSet!=null && !userRecordSet.isEmpty()){
//                userRecord = (UserRecord) userRecordSet.iterator().next();
//            }
            QueryWrapper<UserRecordString> queryWrapperUserRecordString = new QueryWrapper<>();
            queryWrapperUserRecordString.eq("uid", uid);
            UserRecordString userRecordString = userRecordStringMapper.selectOne(queryWrapperUserRecordString);
            if(userRecordString != null){
                System.out.println("breakpoint 0");
                userRecord = findUserRecordByString(userRecordString);
                //redisUtil.zset(key,userRecord);
            }
        }catch (Exception e){
            e.printStackTrace();
            new ResponseResult(404,"未找到记录",null);
        }
        /*if(userRecord == null){
            try{
                Exception e = new Exception("点赞量为空");
            }catch (Exception e){
                e.printStackTrace();
            }
            return null;
        }*/
        return userRecord;
    }

    /**
     * 获取近七天播放量增长量
     *
     * @param uid 用户uid
     * @return 一个增长list
     */
    @Override
    public List<Integer> getPlayRecordByUid(Integer uid) {
        UserRecord userRecord = getUserRecordByUid(uid);
        return userRecord.getPlay();
    }

    @Override
    public void setPlayRecordByUid(Integer uid) throws JsonProcessingException {
        //String key = "userRecord:" + uid;
        UserRecord userRecord = getUserRecordByUid(uid);
        //redisUtil.zsetDelMember(key,userRecord);
        int delta = userRecord.getPlayNew();
        List<Integer> deltaWeek = new ArrayList<>();
        for(int i=1;i<7;++i){
            deltaWeek.add(userRecord.getPlay().get(i));
        }
        deltaWeek.add(delta);
        userRecord.setPlay(deltaWeek);
        userRecord.setPlayOld(userRecord.getPlayNew());
        userRecord.setPlayNew(0);
        //redisUtil.zset(key,userRecord);
        UserRecordString userRecordString = saveUserRecordToString(userRecord);
        saveUserRecordStringToDatabase(userRecordString);
    }

    /**
     * 获取近七天点赞量增长量
     *
     * @param uid 用户uid
     * @return 一个增长list
     */
    @Override
    public List<Integer> getLoveRecordByUid(Integer uid) {
        UserRecord userRecord = getUserRecordByUid(uid);
        return userRecord.getLove();
    }

    @Override
    public void setLoveRecordByUid(Integer uid) throws JsonProcessingException {
        //String key = "userRecord:" + uid;
        UserRecord userRecord = getUserRecordByUid(uid);
        //redisUtil.zsetDelMember(key,userRecord);
        int delta = userRecord.getLoveNew();
        List<Integer> deltaWeek = new ArrayList<>();
        for(int i=1;i<7;++i){
            deltaWeek.add(userRecord.getLove().get(i));
        }
        deltaWeek.add(delta);
        userRecord.setLove(deltaWeek);
        userRecord.setLoveOld(userRecord.getLoveNew());
        userRecord.setLoveNew(0);
        //redisUtil.zset(key,userRecord);
        UserRecordString userRecordString = saveUserRecordToString(userRecord);
        saveUserRecordStringToDatabase(userRecordString);
    }

    /**
     * 获取近七天收藏量增长量
     *
     * @param uid 用户uid
     * @return 一个增长list
     */
    @Override
    public List<Integer> getCollectRecordByUid(Integer uid) {
        UserRecord userRecord = getUserRecordByUid(uid);
        return userRecord.getCollect();
    }

    @Override
    public void setCollectRecordByUid(Integer uid) throws JsonProcessingException {
        //String key = "userRecord:" + uid;
        UserRecord userRecord = getUserRecordByUid(uid);
        //redisUtil.zsetDelMember(key,userRecord);
        int delta = userRecord.getCollectNew();
        List<Integer> deltaWeek = new ArrayList<>();
        for(int i=1;i<7;++i){
            deltaWeek.add(userRecord.getCollect().get(i));
        }
        deltaWeek.add(delta);
        userRecord.setCollect(deltaWeek);
        userRecord.setCollectOld(userRecord.getCollectNew());
        userRecord.setCollectNew(0);
        //redisUtil.zset(key,userRecord);
        UserRecordString userRecordString = saveUserRecordToString(userRecord);
        saveUserRecordStringToDatabase(userRecordString);
    }

    /**
     * 获取近七天关注量增长量
     *
     * @param uid 用户uid
     * @return 一个增长list
     */
    @Override
    public List<Integer> getFansRecordByUid(Integer uid) {
        UserRecord userRecord = getUserRecordByUid(uid);
        return userRecord.getFans();
    }

    @Override
    public void setFansRecordByUid(Integer uid) throws JsonProcessingException {
        //String key = "userRecord:" + uid;
        UserRecord userRecord = getUserRecordByUid(uid);
        //redisUtil.zsetDelMember(key,userRecord);
        int delta = userRecord.getFansNew();
        List<Integer> deltaWeek = new ArrayList<>();
        for(int i=1;i<7;++i){
            deltaWeek.add(userRecord.getFans().get(i));
        }
        deltaWeek.add(delta);
        userRecord.setFans(deltaWeek);
        userRecord.setFansOld(userRecord.getFansNew());
        userRecord.setFansNew(0);
        //redisUtil.zset(key,userRecord);
        UserRecordString userRecordString = saveUserRecordToString(userRecord);
        saveUserRecordStringToDatabase(userRecordString);
    }

    /**
     * 更新某个用户的record
     * uid 用户uid
     *
     * @param uid   用户uid
     */
    @Override
    public void updateRecordByUid(Integer uid) throws JsonProcessingException {
        setPlayRecordByUid(uid);
        setLoveRecordByUid(uid);
        setCollectRecordByUid(uid);
        setFansRecordByUid(uid);
    }

    /**
     * 实现更新，暂定于UTM+8的0点
     */
    @Override
    // 指定在每天的中国时间0:00运行
    @Scheduled(cron = "0 0 0 * * ?", zone = "Asia/Shanghai")
    public void updateRecord() throws JsonProcessingException {
        List<Integer> userIds = userMapper.getAllUserIds();
        for(Integer uid:userIds){
            updateRecordByUid(uid);
        }
    }
}
