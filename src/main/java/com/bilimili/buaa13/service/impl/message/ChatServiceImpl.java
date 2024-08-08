package com.bilimili.buaa13.service.impl.message;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bilimili.buaa13.entity.Chat;
import com.bilimili.buaa13.entity.IMResponse;
import com.bilimili.buaa13.entity.User;
import com.bilimili.buaa13.im.IMServer;
import com.bilimili.buaa13.mapper.ChatMapper;
import com.bilimili.buaa13.mapper.UserMapper;
import com.bilimili.buaa13.service.message.ChatDetailedService;
import com.bilimili.buaa13.service.message.ChatService;
import com.bilimili.buaa13.service.message.MsgUnreadService;
import com.bilimili.buaa13.service.user.UserService;
import com.bilimili.buaa13.utils.RedisUtil;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
@Service
public class ChatServiceImpl implements ChatService {
    @Autowired
    private ChatMapper chatMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MsgUnreadService msgUnreadService;

    @Autowired
    private UserService userService;

    @Autowired
    private ChatDetailedService chatDetailedService;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    /**
     * 创建聊天
     * @param postId  发消息者UID (我打开对方的聊天框即对方是发消息者)
     * @param acceptId    收消息者UID (我打开对方的聊天框即我是收消息者)
     * @return 包含创建信息"已存在"/"新创建"/"未知用户"以及相关数据（用户资料、最近聊天等）
     */
    //正常返回map:
    //1.chat
    //2.加入zset
    //3.发送者信息
    //4.chatDetail
    @Override
    public Map<String, Object> createOneChat(Integer postId, Integer acceptId) {
        Map<String, Object> map = new HashMap<>();
        QueryWrapper<Chat> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId).eq("accept_id", acceptId);
        Chat chat = chatMapper.selectOne(queryWrapper);
        //在数据库中查找相应的聊天
        if (chat != null) {
            // 曾经创建过
            if (chat.getIsDeleted() == 1) {
                // 但是被移除状态 重新开启
                chat.setIsDeleted(0);
                chat.setLatestTime(new Date());
                chatMapper.updateById(chat);
                map = getChatMap(chat);
                map.put("msg", "新创建");
            } else {
                //此时map中没有chat的相关信息
                // 处于最近聊天中
                map.put("msg", "已存在");
            }
            return map;
        } else {
            // 不存在记录
            // 查询对方用户是否存在
            QueryWrapper<User> queryWrapper1 = new QueryWrapper<>();
            queryWrapper1.orderByDesc("uid").last("LIMIT 1");
            User user = userMapper.selectOne(queryWrapper1);
            if (postId > user.getUid()) {
                map.put("msg", "未知用户");
                return map;
            }
            // 创建新的chat
            chat = new Chat(null, postId, acceptId, 0, 0, new Date());
            chatMapper.insert(chat);
            map = getChatMap(chat);
            map.put("msg", "新创建");
            return map;
        }
    }

    /**
     * 获取聊天列表 包含用户信息和最近的聊天内容 每次查10个
     * @param uid   登录用户ID
     * @param offset    查询偏移量（最近聊天的第几个开始往后查）
     * @return  包含用户信息和最近一条聊天内容的聊天列表
     */
    @Override
    public List<Map<String, Object>> getChatDataList(Integer uid, Long offset) {
        //注释Redis
        /*Set<Object> set = redisUtil.zReverange("chat_zset:" + uid, offset, offset + 9);
        // 没有数据则返回空列表
        if (set == null || set.isEmpty()) return Collections.emptyList();
        // 查询
        QueryWrapper<Chat> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", set).eq("is_deleted", 0).orderByDesc("latest_time");
        List<Chat> chatList = chatMapper.selectList(queryWrapper);*/
        List<Chat> chatList = chatMapper.selectChatByUid(uid);
        // 没有数据则返回空列表
        if (chatList == null || chatList.isEmpty()) return Collections.emptyList();
        Stream<Chat> chatStream;
        if(offset + 10 < chatList.size()) {
            // 封装返回
            chatStream = IntStream.range(offset.intValue(),10+offset.intValue()).mapToObj(chatList::get);
        }
        else chatStream = chatList.stream();
        return chatStream.parallel()
                .map(this::getChatMap)
                .collect(Collectors.toList());
    }

    /**
     * 获取单个聊天
     * @param postId  发消息者UID
     * @param acceptId    收消息者UID
     * @return  Chat对象
     */
    @Override
    public Chat getOneChat(Integer postId, Integer acceptId) {
        QueryWrapper<Chat> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId).eq("accept_id", acceptId);
        return chatMapper.selectOne(queryWrapper);
    }

    /**
     * 移除聊天 并清除未读
     * @param postId  发消息者UID（对方）
     * @param acceptId    收消息者UID（自己）
     */
    @Override
    public void deleteOneChat(Integer postId, Integer acceptId) {
        QueryWrapper<Chat> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("post_id", postId).eq("accept_id", acceptId);
        Chat chat = chatMapper.selectOne(queryWrapper);
        if (chat == null) return;

        // 通知自己的全部channel 移除该聊天
        Map<String, Object> map = new HashMap<>();
        map.put("type", "移除");
        setMapChannel(acceptId, chat, map);

        if (chat.getUnreadNum() > 0) {
            // 原本有未读的话 要额外做一点更新
            // msg_unread中的whisper要减去相应数量
            msgUnreadService. subtractWhisper(acceptId, chat.getUnreadNum());
        }

        // 更新字段伪删除 并清除未读
        UpdateWrapper<Chat> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("post_id", postId).eq("accept_id", acceptId).setSql("is_deleted = 1").setSql("unread_num = 0");
        chatMapper.update(null, updateWrapper);

        // 移出最近聊天集合
        //注释Redis
        /*try {
            redisUtil.zsetDelMember("chat_zset:" + acceptId, chat.getId());
        } catch (Exception e) {
            log.error("redis移除聊天失败");
        }*/
    }

    /**
     * 发送消息时更新对应聊天的未读数和时间
     * @param postId  发送者ID（自己）
     * @param acceptId    接受者ID（对方）
     * @return 返回对方是否在窗口
     */
    @Override
    public boolean updateOneChat(Integer postId, Integer acceptId) {
        // 查询对方是否在窗口
        String key = "whisper:" + acceptId + ":" + postId;  // whisper:用户自己:聊天对象 这里的用户自己就是对方本人 聊天对象就是在发消息的我自己
        boolean online = redisUtil.isExist(key);
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try {
            /*
             既然我要发消息给对方 那么 acceptId -> postId 的 chat 表数据一定是存在的 因为发消息前一定创建了聊天
             所以只要判断 postId -> acceptId 的数据是否存在
             */

            // 创建两个线程分别处理对应数据
            // 先更新 acceptId -> postId 的数据
            Future<Executor> futureAP = executorService.submit(()->{
                UpdateWrapper<Chat> updateWrapperAP = new UpdateWrapper<>();
                updateWrapperAP.eq("post_id", acceptId)
                        .eq("accept_id", postId)
                        .set("is_deleted", 0)
                        .set("latest_time", new Date());
                chatMapper.update(null, updateWrapperAP);
                QueryWrapper<Chat> queryWrapperAP = new QueryWrapper<>();
                queryWrapperAP.eq("post_id", acceptId).eq("accept_id", postId);
                Chat chatAP = chatMapper.selectOne(queryWrapperAP);
                //注释Redis
                //redisUtil.zset("chat_zset:" + postId, chat1.getId());    // 添加到这个用户的最近聊天的有序集合
            },taskExecutor);
            // 再查询 postId -> acceptId 的数据
            Future<Executor> futurePA = executorService.submit(()->{
                QueryWrapper<Chat> queryWrapperPA = new QueryWrapper<>();
                queryWrapperPA.eq("post_id", postId).eq("accept_id", acceptId);
                Chat chatPA = chatMapper.selectOne(queryWrapperPA);
                UpdateWrapper<Chat> updateWrapperPA = new UpdateWrapper<>();
                if (!online) {
                    // 如果不在窗口就未读+1
                    if (chatPA == null) {
                        // 如果对方没聊过天 就创建聊天
                        chatPA = new Chat(null, postId, acceptId, 0, 1, new Date());
                        chatMapper.insert(chatPA);
                    } else {
                        // 如果聊过 就更新未读和时间和未移除
                        updateWrapperPA.eq("id", chatPA.getId())
                                .set("is_deleted", 0).setSql("unread_num = unread_num + 1").set("latest_time", new Date());
                        chatMapper.update(null, updateWrapperPA);
                    }
                    // 更新对方用户的未读消息
                    msgUnreadService.addOneUnread(acceptId, "whisper");
                    //注释Redis
                    //redisUtil.zset("chat_zset:" + acceptId, chatPA.getId());    // 添加到这个用户的最近聊天的有序集合
                } else {
                    // 如果对方在窗口就不更新未读
                    if (chatPA != null) {
                        // 如果聊过 就只更新时间和未移除
                        updateWrapperPA.eq("id", chatPA.getId()).set("is_deleted", 0).set("latest_time", new Date());
                        chatMapper.update(null, updateWrapperPA);
                    } else {
                        // 如果对方没聊过天 就创建聊天
                        chatPA = new Chat(null, postId, acceptId, 0, 0, new Date());
                        chatMapper.insert(chatPA);
                    }
                    //注释Redis
                    //redisUtil.zset("chat_zset:" + acceptId, chatPA.getId());    // 添加到这个用户的最近聊天的有序集合
                }
            },taskExecutor);
            futureAP.get();
            futurePA.get();

        } catch (Exception e) {
            log.error("更新聊天出现错误{}", e.getMessage());
        }finally {
            shutdownAndAwaitTermination(executorService);
        }
        return online;
    }

    /**
     * 更新窗口为在线状态，顺便清除未读
     * @param postId  发消息者UID（对方）
     * @param acceptId    收消息者UID（自己）
     */
    @Override
    public void updateStateOnline(Integer postId, Integer acceptId) {
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        try {
            Future<Executor> future = executorService.submit(()->{
                // 更新为在线状态
                String key = "whisper:" + acceptId + ":" + postId;  // whisper:用户自己:聊天对象
                redisUtil.setValue(key, true);
                // 清除未读

                QueryWrapper<Chat> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("post_id", postId).eq("accept_id", acceptId);
                Chat chat = chatMapper.selectOne(queryWrapper);
                //更新数据库
                UpdateWrapper<Chat> updateWrapper = new UpdateWrapper<>();
                updateWrapper.eq("post_id", postId).eq("accept_id", acceptId).set("unread_num", 0);
                chatMapper.update(null, updateWrapper);
                if (chat.getUnreadNum() > 0) {
                    // 原本有未读的话 要额外做一点更新
                    // 通知自己的全部channel 更新该聊天未读数为0
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "已读");
                    setMapChannel(acceptId, chat, map);
                    // msg_unread中的whisper要减去相应数量
                    msgUnreadService.subtractWhisper(acceptId, chat.getUnreadNum());
                }

            },taskExecutor);
            future.get();
        } catch (Exception e) {
            log.error("更新聊天窗口上线状态失败: {}", e.getMessage());
        }finally {
            shutdownAndAwaitTermination(executorService);
        }
    }

    /**
     * 更新窗口为离开状态
     * @param postId  发消息者UID（对方）
     * @param acceptId    收消息者UID（自己）
     */
    @Override
    public void updateStateOutline(Integer postId, Integer acceptId) {
        try {
            String key = "whisper:" + acceptId + ":" + postId;  // whisper:用户自己:聊天对象
            // 删除key更新为离开状态
            redisUtil.delValue(key);
        } catch (Exception e) {
            log.error("更新聊天窗口下线状态失败: {}", e.getMessage());
        }
    }

    private Map<String,Object> getChatMap(Chat chat){
        Map<String,Object> map = new HashMap<>();
        //注释Redis
        //redisUtil.zset("chat_zset:" + chat.getAcceptId(), chat.getId());    // 添加到这个用户的最近聊天的有序集合
        // 携带信息返回
        map.put("chat", chat);
        CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() ->
                map.put("user", userService.getUserByUId(chat.getPostId())),
                taskExecutor);
        CompletableFuture<Void> detailFuture = CompletableFuture.runAsync(() ->
                map.put("detail", chatDetailedService.getMessage(chat.getPostId(), chat.getAcceptId(), 0L)),
                taskExecutor);
        userFuture.join();
        detailFuture.join();
        return map;
    }

    private void setMapChannel(Integer acceptId, Chat chat, Map<String, Object> map) {
        map.put("id", chat.getId());
        map.put("count", chat.getUnreadNum());
        Set<Channel> myChannels = IMServer.userChannel.get(acceptId);
        if (myChannels != null) {
            for (Channel channel : myChannels) {
                channel.writeAndFlush(IMResponse.message("whisper", map));
            }
        }
    }

    /**
     * 关闭线程
     */
    private static void shutdownAndAwaitTermination(ExecutorService pool) {
        pool.shutdown(); // 禁止提交新任务
        try {
            // 等待现有任务完成
            if (!pool.awaitTermination(60, TimeUnit.SECONDS)) {
                pool.shutdownNow(); // 取消正在执行的任务
                // 等待任务响应中断
                if (!pool.awaitTermination(60, TimeUnit.SECONDS))
                    System.err.println("线程池未能关闭");
            }
        } catch (InterruptedException ie) {
            // 重新中断当前线程
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
