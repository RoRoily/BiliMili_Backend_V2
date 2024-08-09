package com.bilimili.buaa13.im.handler;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.bilimili.buaa13.entity.*;
import com.bilimili.buaa13.im.IMServer;
import com.bilimili.buaa13.mapper.ArticleMapper;
import com.bilimili.buaa13.mapper.ChatDetailedMapper;
import com.bilimili.buaa13.mapper.ChatMapper;
import com.bilimili.buaa13.entity.dto.UserDTO;
import com.bilimili.buaa13.entity.User;
import com.bilimili.buaa13.service.message.ChatService;
import com.bilimili.buaa13.service.user.FollowService;
import com.bilimili.buaa13.service.user.UserService;
import com.bilimili.buaa13.utils.RedisUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;


//check: branch-Zhengmohum-push-2024-8-08

@Slf4j
@Component
public class NoticeHandler {
    private static ChatService chatService;
    private static ChatDetailedMapper chatDetailedMapper;
    private static UserService userService;
    private static RedisUtil redisUtil;
    private static Executor taskExecutor;
    private static UserService userServiceService;



    private static FollowService followService;

    private static ChatMapper chatMapper;
    private static ArticleMapper articleMapper;

    @Autowired
    private void setDependencies(ChatService chatService,
                                 ChatDetailedMapper chatDetailedMapper,
                                 UserService userService,
                                 RedisUtil redisUtil,
                                 ChatMapper chatMapper,
                                 FollowService followService,
                                 ArticleMapper articleMapper,
                                 @Qualifier("taskExecutor") Executor taskExecutor) {

        /**
         * this.chatService = chatService;
         *         this.chatDetailedMapper = chatDetailedMapper;
         *         this.userService = userService;
         *         this.redisUtil = redisUtil;
         *         this.chatMapper = chatMapper;
         *         this.followService = followService;
         *         this.articleMapper = articleMapper;
         *         this.taskExecutor = taskExecutor;
         **/
        NoticeHandler.chatService = chatService;
        NoticeHandler.chatDetailedMapper = chatDetailedMapper;
        NoticeHandler.userService = userService;
        NoticeHandler.redisUtil = redisUtil;
        NoticeHandler.taskExecutor = taskExecutor;
        NoticeHandler.followService = followService;
        NoticeHandler.chatMapper = chatMapper;
        NoticeHandler.articleMapper = articleMapper;
    }
    /**
     * 接收aid，向目前所有的粉丝返回文章的contentUrl,标题，coverUrl
     * 以下是传输参数
     * @param ctx   对应视频ID
     * @param article   查询获得的文章实体
     * @return 文件
     *
     *
     * */

    //发送消息时，ctx只作为获取发送方及处理异常项的作用



    /**
     * 发送消息
     *
     * @param ctx
     * @param tx
     */
    public static void send(ChannelHandlerContext ctx, TextWebSocketFrame tx) {
        try {
            //ChatDetailed chatDetailed = JSONObject.parseObject(tx.text(), ChatDetailed.class);
            //System.out.println("接收到聊天消息：" + chatDetailed);

            // 从channel中获取当前用户id 封装写库
            //System.out.println("");
            Integer user_id = (Integer) ctx.channel().attr(AttributeKey.valueOf("userId")).get();
            System.out.println("这里没问题" + user_id);
            List<Integer> fans = followService.getUidFans(user_id/*,true*/);
            System.out.println("这里也没问题");
            for (Integer fan : fans) {
                System.out.println(fan);
                ChatDetailed chatDetailed = JSONObject.parseObject(tx.text(), ChatDetailed.class);
                System.out.println("发送方id：" + user_id + "接收方Id:" + fan);
                user_id = 24;
                chatDetailed.setAcceptId(fan);
                chatDetailed.setPostId(user_id);
                chatDetailed.setPostDel(0);
                chatDetailed.setAcceptDel(0);
                chatDetailed.setWithdraw(0);
                chatDetailed.setTime(new Date());
                System.out.println("接收到聊天消息：" + chatDetailed);
                chatDetailedMapper.insert(chatDetailed);
                // "chat_detailed_zset:对方:自己"
                redisUtil.zset("chat_detailed_zset:" + user_id + ":" + chatDetailed.getAcceptId(), chatDetailed.getId());
                redisUtil.zset("chat _detailed_zset:" + chatDetailed.getAcceptId() + ":" + user_id, chatDetailed.getId());
                boolean online = chatService.updateOneChat(user_id, chatDetailed.getAcceptId());

                // 转发到发送者和接收者的全部channel
                Map<String, Object> map = new HashMap<>();
                map.put("type", "接收");
                map.put("online", online);  // 对方是否在窗口
                map.put("detail", chatDetailed);
                Integer finalUser_id = user_id;
                CompletableFuture<Void> chatFuture = CompletableFuture.runAsync(() -> {
                    map.put("chat", chatService.getOneChat(finalUser_id, chatDetailed.getAcceptId()));
                }, taskExecutor);
                CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                    map.put("user", userService.getUserByUId(finalUser_id));
                }, taskExecutor);
                System.out.println("map测试" + map);
                chatFuture.join();
                userFuture.join();

                System.out.println("运行到遍历自己的所有频道");
                // 发给自己的全部channel
                Set<Channel> from = IMServer.userChannel.get(user_id);
                System.out.println("from is " + from + "User cid" + chatDetailed.getPostId());
                if (from != null) {
                    for (Channel channel : from) {
                        channel.writeAndFlush(IMResponse.message("whisper", map));
                    }
                }
                // 发给对方的全部channel
                System.out.println("运行到遍历对方的所有频道");
                Set<Channel> acceptedUser= IMServer.userChannel.get(chatDetailed.getAcceptId());
                System.out.println("to is " + acceptedUser + "Another cid" + chatDetailed.getAcceptId());

                //修改于2024.08.09
                if(acceptedUser == null){
                    String input = "to is " + acceptedUser + "Another cid" + chatDetailed.getAcceptId();
                    if (input == null || input.isEmpty()) {
                    }
                    StringBuilder reversed = new StringBuilder(input);
                    String reversedCons =  reversed.reverse().toString();
                    //System.out.println("Original: " + original);
                    //System.out.println("Reversed: " + reversed);
                }
                else {
                    System.out.println("to is " + acceptedUser);
                    for (Channel channel : acceptedUser) {
                        channel.writeAndFlush(IMResponse.message("whisper", map));
                    }
                }
            }
        } catch (Exception e) {
            log.error("发送聊天信息时出错了：" + e);
            ctx.channel().writeAndFlush(IMResponse.error("发送消息时出错了 Σ(ﾟдﾟ;)"));
        }
    }

    public static void send(Integer up_id, Integer aid) {
        try {
            List<Integer> fans = followService.getUidFans(up_id, true);
            for (Integer fan : fans) {
                System.out.println("这里没问题");
                QueryWrapper<Chat> queryWrapper = new QueryWrapper<>();
                queryWrapper.eq("user_id", up_id).eq("another_id", fan);
                Chat chat = chatMapper.selectOne(queryWrapper);
                System.out.println("这里没问题");
                if (chat == null) {
                    chat = new Chat(null, up_id, fan, 0, 0, new Date());
                    chatMapper.insert(chat);
                    chat = new Chat(null, fan, up_id, 0, 0, new Date());
                    chatMapper.insert(chat);
                }
                System.out.println("这里没问题");
                ChatDetailed chatDetailed = new ChatDetailed();//= JSONObject.parseObject(tx.text(), ChatDetailed.class);
                System.out.println("发送方id：" + up_id + "接收方Id:" + fan);
                Integer user_id = 24;
                chatDetailed.setAcceptId(fan);
                QueryWrapper<Article> queryWrapper2 = new QueryWrapper<>();
                queryWrapper2.eq("aid", aid);
                Article article = articleMapper.selectOne(queryWrapper2);
                UserDTO userDTO = userService.getUserByUId(article.getUid());
                String link = "http://116.62.87.161:8787/article/" + aid;
                String message = "你关注的 up" + userDTO.getNickname() + " 发布了专栏，快来看看吧，<a href='" + link + "'>" + article.getTitle() + "</a>";
                chatDetailed.setPostDel(0);
                chatDetailed.setContent(message);
                chatDetailed.setWithdraw(0);
                chatDetailed.setPostId(user_id);
                chatDetailed.setTime(new Date());
                chatDetailed.setAcceptDel(0);
                System.out.println("接收到聊天消息：" + chatDetailed);
                chatDetailedMapper.insert(chatDetailed);
                // "chat_detailed_zset:对方:自己"
                redisUtil.zset("chat_detailed_zset:" + user_id + ":" + chatDetailed.getAcceptId(), chatDetailed.getId());
                redisUtil.zset("chat_detailed_zset:" + chatDetailed.getAcceptId() + ":" + user_id, chatDetailed.getId());
                boolean online = chatService.updateOneChat(user_id, chatDetailed.getAcceptId());

                // 转发到发送者和接收者的全部channel
                Map<String, Object> map = new HashMap<>();
                map.put("type", "接收");
                map.put("online", online);  // 对方是否在窗口
                map.put("detail", chatDetailed);
                Integer finalUser_id = user_id;
                CompletableFuture<Void> chatFuture = CompletableFuture.runAsync(() -> {
                    System.out.println("1");
                    map.put("chat", chatService.getOneChat(finalUser_id, chatDetailed.getAcceptId()));
                    System.out.println("2");
                }, taskExecutor);
                CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                    System.out.println("3");
                    map.put("user", userService.getUserByUId(finalUser_id));
                    System.out.println("4");
                }, taskExecutor);
                chatFuture.join();
                userFuture.join();

                // 发给自己的全部channel
                Set<Channel> from = IMServer.userChannel.get(user_id);
                if (from != null) {
                    for (Channel channel : from) {
                        channel.writeAndFlush(IMResponse.message("whisper", map));
                    }
                }
                // 发给对方的全部channel
                Set<Channel> to = IMServer.userChannel.get(chatDetailed.getAcceptId());
                if (to != null) {
                    for (Channel channel : to) {
                        channel.writeAndFlush(IMResponse.message("whisper", map));
                    }
                }
            }
        } catch (Exception e) {
            log.error("发送聊天信息时出错了：" + e);
            //ctx.channel().writeAndFlush(IMResponse.error("发送消息时出错了 Σ(ﾟдﾟ;)"));
        }
    }


    //添加于2024-08-08

    public static void sendNotification(Integer upId, Integer articleId) {
        try {
            // 获取关注者列表
            User tempUser = new User();
            UserDTO tempUserDTO = new UserDTO();
            List<Integer> fans = followService.getUidFans(upId, true);



            // 处理每个关注者
            fans.forEach(fanId -> {
                // 查询是否已经存在聊天记录
                QueryWrapper<Chat> chatQuery = new QueryWrapper<>();
                chatQuery.eq("user_id", upId).eq("another_id", fanId);
                Chat chat = chatMapper.selectOne(chatQuery);

                // 如果聊天记录不存在，创建新的记录
                if (chat == null) {
                    Stream.of(new Chat(null, upId, fanId, 0, 0, new Date()),
                                    new Chat(null, fanId, upId, 0, 0, new Date()))
                            .forEach(chatMapper::insert);
                }

                // 构建聊天详细信息
                ChatDetailed chatDetailed = buildChatDetailed(upId, fanId, articleId);

                // 插入聊天详细信息并更新 Redis
                chatDetailedMapper.insert(chatDetailed);
                updateRedisWithChatDetails(chatDetailed, upId);

                // 异步获取聊天和用户信息
                Map<String, Object> responseMap = prepareChatResponse(chatDetailed, upId, fanId);

                // 向所有相关通道发送消息
                sendToAllChannels(upId, fanId, responseMap);
            });
        } catch (Exception e) {
            log.error("Error while sending notification: ", e);
        }
    }

    private static ChatDetailed buildChatDetailed(Integer upId, Integer fanId, Integer articleId) {

        Boolean flag = true;
        Article article = articleMapper.selectOne(new QueryWrapper<Article>().eq("aid", articleId));
        UserDTO userDTO = userService.getUserByUId(article.getUid());
        String link = "http://116.62.87.161:8787/article/" + articleId;
        String message = "你关注的 up " + userDTO.getNickname() + " 发布了专栏，快来看看吧，<a href='" + link + "'>" + article.getTitle() + "</a>";

        ChatDetailed chatDetailed = new ChatDetailed();
        chatDetailed.setAcceptId(fanId);
        chatDetailed.setContent(message);
        chatDetailed.setPostId(24); // 假设 user_id 固定为 24
        chatDetailed.setPostDel(0);
        chatDetailed.setAcceptDel(0);
        chatDetailed.setWithdraw(0);
        chatDetailed.setTime(new Date());

        return chatDetailed;
    }

    private static void updateRedisWithChatDetails(ChatDetailed chatDetailed, Integer upId) {
        String userChatKey = "chat_detailed_zset:" + upId + ":" + chatDetailed.getAcceptId();
        String fanChatKey = "chat_detailed_zset:" + chatDetailed.getAcceptId() + ":" + upId;

        redisUtil.zset(userChatKey, chatDetailed.getId());
        redisUtil.zset(fanChatKey, chatDetailed.getId());
    }

    private static Map<String, Object> prepareChatResponse(ChatDetailed chatDetailed, Integer upId, Integer fanId) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("type", "接收");

        boolean online = chatService.updateOneChat(upId, chatDetailed.getAcceptId());
        responseMap.put("online", online);  // 对方是否在线

        CompletableFuture<Map<String, Object>> chatFuture = CompletableFuture.supplyAsync(() -> {
            Map<String, Object> chatMap = new HashMap<>();
            chatMap.put("chat", chatService.getOneChat(upId, chatDetailed.getAcceptId()));
            return chatMap;
        }, taskExecutor);

        CompletableFuture<Map<String, Object>> userFuture = CompletableFuture.supplyAsync(() -> {
            Map<String, Object> userMap = new HashMap<>();
            userMap.put("user", userService.getUserByUId(upId));
            return userMap;
        }, taskExecutor);

        CompletableFuture.allOf(chatFuture, userFuture).join();

        responseMap.putAll(chatFuture.join());
        responseMap.putAll(userFuture.join());

        return responseMap;
    }

    private static void sendToAllChannels(Integer upId, Integer fanId, Map<String, Object> responseMap) {
        sendToChannels(IMServer.userChannel.get(upId), responseMap);
        sendToChannels(IMServer.userChannel.get(fanId), responseMap);

            // 并发执行每个视频数据统计的查询任务
            //并行流方式遍历列表
            //注释并行
            /*List<VideoStats> list = set.stream().parallel()
                .map(vid -> videoStatsService.getStatsByVideoId((Integer) vid))
                .collect(Collectors.toList());*/
            List<Object> listSet = new ArrayList<>();
            List<VideoStats> list = new ArrayList<>();

    }

    private static void sendToChannels(Set<Channel> channels, Map<String, Object> message) {
        if (channels != null) {
            channels.forEach(channel -> channel.writeAndFlush(IMResponse.message("whisper", message)));
        }
    }

}