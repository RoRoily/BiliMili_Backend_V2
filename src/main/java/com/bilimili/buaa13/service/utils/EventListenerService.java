package com.bilimili.buaa13.service.utils;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.buaa13.mapper.ChatDetailedMapper;
import com.bilimili.buaa13.mapper.VideoMapper;
import com.bilimili.buaa13.entity.ChatDetailed;
import com.bilimili.buaa13.entity.Video;
import com.bilimili.buaa13.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

@Slf4j
@Service
public class EventListenerService {

    @Value("${directory.chunk}")
    private String CHUNK_DIRECTORY;   // 分片存储目录

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private ChatDetailedMapper chatDetailedMapper;

    public static List<RedisUtil.ZObjScore> hotSearchWords = new ArrayList<>();     // 上次更新的热搜词条

    /**
     * 每一小时更新一次热搜词条热度
     */
    @Scheduled(fixedDelay = 1000 * 60 * 60)
    public void updateHotSearch() {
        List<RedisUtil.ZObjScore> list = redisUtil.zReverangeWithScores("search_word", 0, -1);
        if (list == null || list.isEmpty()) return;
        int count = list.size();
        double total = 0;
        // 计算总分数
        for (RedisUtil.ZObjScore o : list) {
            total += o.getScore();
        }
        BigDecimal bt = new BigDecimal(total);
        total = bt.setScale(2, RoundingMode.HALF_UP).doubleValue();
        // 更新每个词条的分数    新分数 = (旧分数 / 总分数) * 词条数
        for (RedisUtil.ZObjScore o : list) {
            BigDecimal b = new BigDecimal((o.getScore() / total) * count);
            double score = b.setScale(2, RoundingMode.HALF_UP).doubleValue();
            o.setScore(score);
        }
        // 批量更新到redis上
        redisUtil.zsetOfCollectionByScore("search_word", list);
        // 保存新热搜
        if (list.size() < 10) {
            hotSearchWords = list.subList(0, list.size());
        } else {
            hotSearchWords = list.subList(0, 10);
        }
    }

    /**
     * 每天4点删除三天前未使用的分片文件
     * @throws IOException
     */
    @Scheduled(cron = "0 0 4 * * ?")  // 每天4点0分0秒触发任务 // cron表达式格式：{秒数} {分钟} {小时} {日期} {月份} {星期} {年份(可为空)}
    public void deleteChunks() {
        try {
            // 获取分片文件的存储目录
            File chunkDir = new File(CHUNK_DIRECTORY);
            // 获取所有分片文件
            File[] chunkFiles = chunkDir.listFiles();
            if (chunkFiles != null && chunkFiles.length > 0) {
                for (File chunkFile : chunkFiles) {
                    Path filePath = chunkFile.toPath();
                    BasicFileAttributes attr = Files.readAttributes(filePath, BasicFileAttributes.class);   // 读取文件属性
                    FileTime createTime = attr.creationTime();  // 文件的创建时间
                    Instant instant = createTime.toInstant();
                    ZonedDateTime zonedDateTime = instant.atZone(ZoneId.systemDefault());   // 转换为本地时区时间
                    LocalDateTime createDateTime = zonedDateTime.toLocalDateTime();     // 获取文件创建时间
                    LocalDateTime threeDaysAgo = LocalDateTime.now().minusDays(3);      // 3天前的时间
                    if (createDateTime.isBefore(threeDaysAgo)) {
//                    System.out.println("删除分片文件 " + chunkFile.getName());
                        // 文件创建时间早于三天前，删除分片文件
                        chunkFile.delete();
                    }
                }
            }
        } catch (IOException ioe) {
            log.error("每天检查删除过期分片时出错了：" + ioe);
        }
    }

    /**
     * 每24小时同步一下各状态的视频集合
     */
    @Scheduled(fixedDelay = 1000 * 60 * 60 * 24)
    public void updateVideoStatus() {
        for (int i = 0; i < 3; i++) {
            QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("status", i).isNull("delete_date").select("vid");
            List<Object> vidList = videoMapper.selectObjs(queryWrapper);
            try {
                redisUtil.delValue("video_status:" + i);   // 先将原来的删掉
                if (vidList != null && !vidList.isEmpty()) {
                    redisUtil.addMembers("video_status:" + i, vidList);
                }
            } catch (Exception e) {
                log.error("redis更新审核视频集合失败");
            }
        }
    }

    /**
     * 每天4点15分同步一下全部用户的聊天记录
     */
    @Scheduled(cron = "0 15 4 * * ?")   // 每天4点15分0秒触发任务
    public void updateChatDetailedZSet() {
        try {
            QueryWrapper<ChatDetailed> queryWrapper = new QueryWrapper<>();
            List<ChatDetailed> list = chatDetailedMapper.selectList(queryWrapper);
            // 按用户将对应的消息分类整理
            Set<Map<String, Integer>> chatSet = new HashSet<>();
            Map<Integer, Map<Integer, Set<RedisUtil.ZObjTime>>> setMap = new HashMap<>();
            for (ChatDetailed chatDetailed : list) {
                Integer from = chatDetailed.getPostId();    // 发送者ID
                Integer to = chatDetailed.getAcceptId();   // 接收者ID

                // 发送者视角 chat_detailed_zset:to:from
                Map<String, Integer> fromMap = new HashMap<>();
                fromMap.put("user_id", to);
                fromMap.put("another_id", from);
                chatSet.add(fromMap);
                if (chatDetailed.getPostDel() == 0) {
                    // 发送者没删就加到对应聊天的有序集合
                    if (setMap.get(from) == null) {
                        Map<Integer, Set<RedisUtil.ZObjTime>> map = new HashMap<>();
                        Set<RedisUtil.ZObjTime> set = new HashSet<>();
                        set.add(new RedisUtil.ZObjTime(chatDetailed.getId(), chatDetailed.getTime()));
                        map.put(to, set);
                        setMap.put(from, map);
                    } else {
                        if (setMap.get(from).get(to) == null) {
                            Set<RedisUtil.ZObjTime> set = new HashSet<>();
                            set.add(new RedisUtil.ZObjTime(chatDetailed.getId(), chatDetailed.getTime()));
                            setMap.get(from).put(to, set);
                        } else {
                            setMap.get(from).get(to)
                                    .add(new RedisUtil.ZObjTime(chatDetailed.getId(), chatDetailed.getTime()));
                        }
                    }
                }

                // 接收者视角 chat_detailed_zset:from:to
                Map<String, Integer> toMap = new HashMap<>();
                toMap.put("user_id", from);
                toMap.put("another_id", to);
                chatSet.add(toMap);
                if (chatDetailed.getAcceptDel() == 0) {
                    // 接收者没删就加到对应聊天的有序集合
                    if (setMap.get(to) == null) {
                        Map<Integer, Set<RedisUtil.ZObjTime>> map = new HashMap<>();
                        Set<RedisUtil.ZObjTime> set = new HashSet<>();
                        set.add(new RedisUtil.ZObjTime(chatDetailed.getId(), chatDetailed.getTime()));
                        map.put(from, set);
                        setMap.put(to, map);
                    } else {
                        if (setMap.get(to).get(from) == null) {
                            Set<RedisUtil.ZObjTime> set = new HashSet<>();
                            set.add(new RedisUtil.ZObjTime(chatDetailed.getId(), chatDetailed.getTime()));
                            setMap.get(to).put(from, set);
                        } else {
                            setMap.get(to).get(from)
                                    .add(new RedisUtil.ZObjTime(chatDetailed.getId(), chatDetailed.getTime()));
                        }
                    }
                }
            }

            // 更新redis
            for (Map<String, Integer> map : chatSet) {
                Integer uid = map.get("post_id");
                Integer aid = map.get("accept_id");
                String key = "chat_detailed_zset:" + uid + ":" + aid;
                redisUtil.delValue(key);
                redisUtil.zsetOfCollectionByTime(key, setMap.get(uid).get(aid));
            }
        } catch (Exception e) {
            log.error("每天同步聊天记录时出错了：" + e);
        }

    }
}
