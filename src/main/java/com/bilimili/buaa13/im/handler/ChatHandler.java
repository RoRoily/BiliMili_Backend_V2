package com.bilimili.buaa13.im.handler;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bilimili.buaa13.im.IMServer;
import com.bilimili.buaa13.mapper.ChatDetailedMapper;
import com.bilimili.buaa13.entity.ChatDetailed;
import com.bilimili.buaa13.entity.IMResponse;
import com.bilimili.buaa13.service.message.ChatService;
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

@Slf4j
@Component
public class ChatHandler {

    private static ChatService chatService;
    private static ChatDetailedMapper chatDetailedMapper;
    private static UserService userService;
    private static RedisUtil redisUtil;
    private static Executor taskExecutor;

    @Autowired
    private void setDependencies(ChatService chatService,
                                 ChatDetailedMapper chatDetailedMapper,
                                 UserService userService,
                                 RedisUtil redisUtil,
                                 @Qualifier("taskExecutor") Executor taskExecutor) {
        ChatHandler.chatService = chatService;
        ChatHandler.chatDetailedMapper = chatDetailedMapper;
        ChatHandler.userService = userService;
        ChatHandler.redisUtil = redisUtil;
        ChatHandler.taskExecutor = taskExecutor;
    }

    /**
     * 发送消息
     * @param ctx
     * @param tx
     */
    public static void send(ChannelHandlerContext ctx, TextWebSocketFrame tx) {
        try {
            System.out.println("TX IS:" + ctx);
            ChatDetailed chatDetailed = JSONObject.parseObject(tx.text(), ChatDetailed.class);
            //System.out.println("接收到聊天消息：" + chatDetailed);

            // 从channel中获取当前用户id 封装写库
            Integer user_id = (Integer) ctx.channel().attr(AttributeKey.valueOf("userId")).get();
            chatDetailed.setUserId(user_id);
            chatDetailed.setUserDel(0);
            chatDetailed.setAnotherDel(0);
            chatDetailed.setWithdraw(0);
            chatDetailed.setTime(new Date());
            System.out.println("接收到聊天消息：" + chatDetailed);
            chatDetailedMapper.insert(chatDetailed);
            // "chat_detailed_zset:对方:自己"
            redisUtil.zset("chat_detailed_zset:" + user_id + ":" + chatDetailed.getAnotherId(), chatDetailed.getId());
            redisUtil.zset("chat_detailed_zset:" + chatDetailed.getAnotherId() + ":" + user_id, chatDetailed.getId());
            boolean online = chatService.updateChat(user_id, chatDetailed.getAnotherId());

            // 转发到发送者和接收者的全部channel
            Map<String, Object> map = new HashMap<>();
            map.put("type", "接收");
            map.put("online", online);  // 对方是否在窗口
            map.put("detail", chatDetailed);
            CompletableFuture<Void> chatFuture = CompletableFuture.runAsync(() -> {
                map.put("chat", chatService.getChat(user_id, chatDetailed.getAnotherId()));
            }, taskExecutor);
            CompletableFuture<Void> userFuture = CompletableFuture.runAsync(() -> {
                map.put("user", userService.getUserByUId(user_id));
            }, taskExecutor);
            chatFuture.join();
            userFuture.join();

            // 发给自己的全部channel
            Set<Channel> from = IMServer.userChannel.get(user_id);
            System.out.println("from is " + from + "User cid" + chatDetailed.getUserId());
            if (from != null) {
                for (Channel channel : from) {
                    channel.writeAndFlush(IMResponse.message("whisper", map));
                }
            }
            // 发给对方的全部channel
            Set<Channel> to = IMServer.userChannel.get(chatDetailed.getAnotherId());
            System.out.println("to is " + to + "AnotherId" + chatDetailed.getAnotherId());
            if (to != null) {
                for (Channel channel : to) {
                    channel.writeAndFlush(IMResponse.message("whisper", map));
                }
            }

        } catch (Exception e) {
            log.error("发送聊天信息时出错了：" + e);
            ctx.channel().writeAndFlush(IMResponse.error("发送消息时出错了 Σ(ﾟдﾟ;)"));
        }
    }

    /**
     * 撤回消息
     * @param ctx
     * @param tx
     */
    public static void withdraw(ChannelHandlerContext ctx, TextWebSocketFrame tx) {
        try {
            JSONObject jsonObject = JSONObject.parseObject(tx.text());
            Integer id = jsonObject.getInteger("id");
            Integer user_id = (Integer) ctx.channel().attr(AttributeKey.valueOf("userId")).get();

            // 查询数据库
            ChatDetailed chatDetailed = chatDetailedMapper.selectById(id);
            if (chatDetailed == null) {
                ctx.channel().writeAndFlush(IMResponse.error("消息不存在"));
                return;
            }
            if (!Objects.equals(chatDetailed.getUserId(), user_id)) {
                ctx.channel().writeAndFlush(IMResponse.error("无权撤回此消息"));
                return;
            }
            long diff = System.currentTimeMillis() - chatDetailed.getTime().getTime();
            if (diff > 120000) {
                ctx.channel().writeAndFlush(IMResponse.error("发送时间超过两分钟不能撤回"));
                return;
            }
            // 更新 withdraw 字段
            UpdateWrapper<ChatDetailed> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", id).setSql("withdraw = 1");
            chatDetailedMapper.update(null, updateWrapper);

            // 转发到发送者和接收者的全部channel
            Map<String, Object> map = new HashMap<>();
            map.put("type", "撤回");
            map.put("sendId", chatDetailed.getUserId());
            map.put("acceptId", chatDetailed.getAnotherId());
            map.put("id", id);

            // 发给自己的全部channel
            Set<Channel> from = IMServer.userChannel.get(user_id);
            System.out.println("from is " + from + "User cid" + chatDetailed.getUserId());
            if (from != null) {
                for (Channel channel : from) {
                    channel.writeAndFlush(IMResponse.message("whisper", map));
                }
            }
            // 发给对方的全部channel
            Set<Channel> to = IMServer.userChannel.get(chatDetailed.getAnotherId());
            System.out.println("to is " + to + "AnotherId" + chatDetailed.getAnotherId());
            if (to != null) {
                for (Channel channel : to) {
                    channel.writeAndFlush(IMResponse.message("whisper", map));
                }
            }

        } catch (Exception e) {
            log.error("撤回聊天信息时出错了：" + e);
            ctx.channel().writeAndFlush(IMResponse.error("撤回消息时出错了 Σ(ﾟдﾟ;)"));
        }
    }


    //修改于2024.08.08


    public static void withdrawMessage(ChannelHandlerContext ctx, TextWebSocketFrame tx) {
        try {
            JSONObject jsonObject = JSONObject.parseObject(tx.text());
            Integer messageId = jsonObject.getInteger("id");
            Integer userId = Optional.ofNullable((Integer) ctx.channel().attr(AttributeKey.valueOf("userId")).get())
                    .orElseThrow(() -> new IllegalStateException("User ID not found in channel context"));

            ChatDetailed chatDetailed = Optional.ofNullable(chatDetailedMapper.selectById(messageId))
                    .orElseThrow(() -> new IllegalStateException("Message not found"));

            validateUserCanWithdraw(chatDetailed, userId);
            validateWithdrawalTime(chatDetailed);

            updateMessageAsWithdrawn(messageId);

            Map<String, Object> responseMap = createWithdrawResponseMap(chatDetailed, messageId);
            broadcastWithdrawal(ctx, userId, chatDetailed.getAnotherId(), responseMap);

        } catch (Exception e) {
            log.error("Error while withdrawing message: ", e);
            ctx.channel().writeAndFlush(IMResponse.error("撤回消息时出错了 Σ(ﾟдﾟ;)"));
        }
    }

    private static void validateUserCanWithdraw(ChatDetailed chatDetailed, Integer userId) {
        if (!Objects.equals(chatDetailed.getUserId(), userId)) {
            throw new SecurityException("无权撤回此消息");
        }
    }

    private static void validateWithdrawalTime(ChatDetailed chatDetailed) {
        long timeSinceSent = System.currentTimeMillis() - chatDetailed.getTime().getTime();
        if (timeSinceSent > 120000) {
            throw new IllegalStateException("发送时间超过两分钟不能撤回");
        }
    }

    private static void updateMessageAsWithdrawn(Integer messageId) {
        UpdateWrapper<ChatDetailed> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", messageId).set("withdraw", 1);
        chatDetailedMapper.update(null, updateWrapper);
    }

    private static Map<String, Object> createWithdrawResponseMap(ChatDetailed chatDetailed, Integer messageId) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("type", "撤回");
        responseMap.put("sendId", chatDetailed.getUserId());
        responseMap.put("acceptId", chatDetailed.getAnotherId());
        responseMap.put("id", messageId);
        return responseMap;
    }

    private static void broadcastWithdrawal(ChannelHandlerContext ctx, Integer userId, Integer anotherId, Map<String, Object> responseMap) {
        Stream.of(IMServer.userChannel.get(userId), IMServer.userChannel.get(anotherId))
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .forEach(channel -> channel.writeAndFlush(IMResponse.message("whisper", responseMap)));
    }




    public static void undoWithdrawal(ChannelHandlerContext ctx, TextWebSocketFrame tx) {
        try {
            JSONObject jsonObject = JSONObject.parseObject(tx.text());
            Integer messageId = jsonObject.getInteger("id");
            Integer userId = Optional.ofNullable((Integer) ctx.channel().attr(AttributeKey.valueOf("userId")).get())
                    .orElseThrow(() -> new IllegalStateException("User ID not found in channel context"));

            ChatDetailed chatDetailed = Optional.ofNullable(chatDetailedMapper.selectById(messageId))
                    .orElseThrow(() -> new IllegalStateException("Message not found"));

            validateUserCanUndo(chatDetailed, userId);

            restoreMessage(chatDetailed.getId());

            Map<String, Object> responseMap = createUndoResponseMap(chatDetailed, messageId);
            broadcastUndo(ctx, userId, chatDetailed.getAnotherId(), responseMap);

        } catch (Exception e) {
            log.error("Error while undoing withdrawal: ", e);
            ctx.channel().writeAndFlush(IMResponse.error("取消撤回时出错了 Σ(ﾟдﾟ;)"));
        }
    }

    private static void validateUserCanUndo(ChatDetailed chatDetailed, Integer userId) {
        if (!Objects.equals(chatDetailed.getUserId(), userId)) {
            throw new SecurityException("无权取消撤回此消息");
        }
        if (chatDetailed.getWithdraw() == 0) {
            throw new IllegalStateException("消息未撤回，无法取消撤回");
        }
    }

    private static void restoreMessage(Integer messageId) {
        UpdateWrapper<ChatDetailed> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("id", messageId).set("withdraw", 0);
        chatDetailedMapper.update(null, updateWrapper);
    }

    private static Map<String, Object> createUndoResponseMap(ChatDetailed chatDetailed, Integer messageId) {
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("type", "取消撤回");
        responseMap.put("sendId", chatDetailed.getPostId());
        responseMap.put("acceptId", chatDetailed.getAcceptId());
        responseMap.put("id", messageId);
        return responseMap;
    }

    private static void broadcastUndo(ChannelHandlerContext ctx, Integer userId, Integer anotherId, Map<String, Object> responseMap) {
        Stream.of(IMServer.userChannel.get(userId), IMServer.userChannel.get(anotherId))
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .forEach(channel -> channel.writeAndFlush(IMResponse.message("whisper", responseMap)));
    }

}
