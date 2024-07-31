package com.bilimili.buaa13.service.impl.user;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bilimili.buaa13.entity.*;
import com.bilimili.buaa13.entity.dto.UserDTO;
import com.bilimili.buaa13.im.IMServer;
import com.bilimili.buaa13.mapper.FavoriteMapper;
import com.bilimili.buaa13.mapper.MsgUnreadMapper;
import com.bilimili.buaa13.mapper.UserMapper;
import com.bilimili.buaa13.service.record.UserRecordService;
import com.bilimili.buaa13.service.user.UserAccountService;
import com.bilimili.buaa13.service.user.UserService;
import com.bilimili.buaa13.service.utils.CurrentUser;
import com.bilimili.buaa13.utils.ESUtil;
import com.bilimili.buaa13.utils.JwtUtil;
import com.bilimili.buaa13.utils.RedisUtil;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Executor;

@Slf4j
@Service
public class UserAccountServiceImpl implements UserAccountService {

    @Autowired
    private UserRecordService userRecordService;
    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private MsgUnreadMapper msgUnreadMapper;

    @Autowired
    private FavoriteMapper favoriteMapper;

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ESUtil esUtil;

    @Autowired
    private CurrentUser currentUser;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationProvider authenticationProvider;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    /**
     * 用户注册
     * @param account 账号
     * @param password 密码
     * @param confirmedPassword 确认密码
     * @return CustomResponse对象
     * 7/30 10：30
     */
    @Override
    @Transactional
    public ResponseResult register(String account, String password, String confirmedPassword) throws IOException {
        ResponseResult responseResult = new ResponseResult();
        if (account == null) {
            responseResult.setCode(403);
            responseResult.setMessage("账号不能为空");
            return responseResult;
        }
        if (password == null || confirmedPassword == null) {
            responseResult.setCode(403);
            responseResult.setMessage("密码不能为空");
            return responseResult;
        }
        account = account.trim();   //删掉用户名的空白符
        if (account.isEmpty()) {
            responseResult.setCode(403);
            responseResult.setMessage("账号不能为空");
            return responseResult;
        }
        if (account.length() > 50) {
            responseResult.setCode(403);
            responseResult.setMessage("账号长度不能大于50");
            return responseResult;
        }
        if (password.isEmpty() || confirmedPassword.isEmpty()) {
            responseResult.setCode(403);
            responseResult.setMessage("密码不能为空");
            return responseResult;
        }
        if (password.length() > 50 || confirmedPassword.length() > 50 ) {
            responseResult.setCode(403);
            responseResult.setMessage("密码长度不能大于50");
            return responseResult;
        }
        if (!password.equals(confirmedPassword)) {
            responseResult.setCode(403);
            responseResult.setMessage("两次输入的密码不一致");
            return responseResult;
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("account", account);
        queryWrapper.ne("state", 2);
        User user = userMapper.selectOne(queryWrapper);   //查询数据库里值等于account并且没有注销的数据
        if (user != null) {
            responseResult.setCode(403);
            responseResult.setMessage("账号已存在");
            return responseResult;
        }

        QueryWrapper<User> queryWrapper1 = new QueryWrapper<>();
        queryWrapper1.orderByDesc("uid").last("limit 1");    // 降序选第一个
        User lastUser = userMapper.selectOne(queryWrapper1);
        int newUserUid;
        if (lastUser == null) {
            newUserUid = 1;
        } else {
            newUserUid = lastUser.getUid() + 1;
        }
        String encodedPassword = passwordEncoder.encode(password);  // 密文存储
        User newUser = getNewUser(account, encodedPassword, newUserUid);
        userMapper.insert(newUser);
        msgUnreadMapper.insert(new MsgUnread(newUser.getUid(),0,0,0,0,0,0));
        favoriteMapper.insert(new Favorite(newUser.getUid(), newUser.getUid(), 1, 1, null, "默认收藏夹", "", 0, null));
        favoriteMapper.insert(new Favorite(5000+ newUser.getUid(), newUser.getUid(), 1, 1, null, "历史记录", "", 0, null));
        List<Integer> zero=new ArrayList<>();
        for(int i=0;i<7;++i) zero.add(0);
        UserRecord userRecord = new UserRecord(
                newUser.getUid(),
                zero, 0,0,
                zero, 0,0,
                zero, 0,0,
                zero, 0,0
        );
        UserRecordString userRecordString = userRecordService.saveUserRecordToString(userRecord);
        userRecordService.saveUserRecordStringToDatabase(userRecordString);
        esUtil.addUser(newUser);
        responseResult.setMessage("注册成功！欢迎加入BiliMili");
        return responseResult;
    }

    private @NotNull User getNewUser(String account, String encodedPassword, int newUserUid) {
        String avatar_url = "https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png";
        String background_url = "https://tinypic.host/images/2023/11/15/69PB2Q5W9D2U7L.png";
        Date now = new Date();
        return new User(
                null,
                account,
                encodedPassword,
                "用户_" + newUserUid,
                avatar_url,
                background_url,
                2,
                "这个人很懒，什么都没留下~",
                0,
                (double) 0,
                0,
                0,
                now,
                null
        );
    }

    /**
     * 获取当前登录的用户
     */
    Map<String,Object>getLoginUser(String account, String password){
        Map<String,Object> map = new HashMap<>();
        ResponseResult responseResult = new ResponseResult();

        //验证是否能正常登录
        //将用户名和密码封装成一个类，这个类不存明文，将是加密后的字符串
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(account, password);

        // 用户名或密码错误会抛出异常
        Authentication authenticate;
        try {
            authenticate = authenticationProvider.authenticate(authenticationToken);
        } catch (Exception e) {
            responseResult.setCode(403);
            responseResult.setMessage("账号或密码不正确");
            map.put("loginUser",null);
            map.put("responseResult",responseResult);
            return map;
        }
        //将用户取出来
        UserDetailsImpl loginUser = (UserDetailsImpl) authenticate.getPrincipal();
        User user = loginUser.getUser();
        map.put("loginUser",user);
        map.put("responseResult",responseResult);
        return map;
    }

    /**
     * 用户登录
     * @param account 账号
     * @param password 密码
     * @return CustomResponse对象
     * 7/30 10：30
     */
    @Override
    public ResponseResult login(String account, String password) {
        Map<String,Object> loginUserMap = getLoginUser(account, password);
        User user = (User) loginUserMap.get("loginUser");
        ResponseResult responseResult = loginUserMap.containsKey("responseResult") ? (ResponseResult) loginUserMap.get("responseResult") : null;
        if(user == null){return responseResult;}
        if (responseResult == null) { responseResult = new ResponseResult();}

        // 更新redis中的数据
        //注释Redis
        //redisUtil.setExObjectValue("user:" + user.getUid(), user);  // 默认存活1小时

        // 检查账号状态，1 表示封禁中，不允许登录
        if (user.getState() == 1) {
            responseResult.setCode(403);
            responseResult.setMessage("账号异常，封禁中");
            return responseResult;
        }

        //将uid封装成一个jwttoken，同时token也会被缓存到redis中
        String token = jwtUtil.createToken(user.getUid().toString(), "user");

        //注释Redis
        /*try {
            // 把完整的用户信息存入redis，时间跟token一样，注意单位
            // 这里缓存的user信息建议只供读取uid用，其中的状态等非静态数据可能不准，所以 redis另外存值
            redisUtil.setExObjectValue("security:user:" + user.getUid(), user, 60L * 60 * 24 * 2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("存储redis数据失败");
            throw e;
        }*/

        // 每次登录顺便返回user信息，就省去再次发送一次获取用户个人信息的请求
        Map<String, Object> UserDTOMap = getUserDTOMap(user,token);
        responseResult.setMessage("登录成功");
        responseResult.setData(UserDTOMap);
        return responseResult;
    }

    /**
     * 管理员登录
     * @param account 账号
     * @param password 密码
     * @return CustomResponse对象
     * 7/30 10：30
     */
    @Override
    public ResponseResult adminLogin(String account, String password) {
        Map<String,Object> loginUserMap = getLoginUser(account, password);
        User user = (User) loginUserMap.get("loginUser");
        ResponseResult responseResult = loginUserMap.containsKey("responseResult") ? (ResponseResult) loginUserMap.get("responseResult") : null;
        if(user == null){return responseResult;}
        if (responseResult == null) { responseResult = new ResponseResult();}
        // 普通用户无权访问
        if (user.getRole() == 0) {
            responseResult.setCode(403);
            responseResult.setMessage("您不是管理员，无权访问");
            return responseResult;
        }
        //注释Redis
        // 顺便更新redis中的数据
        //redisUtil.setExObjectValue("user:" + user.getUid(), user);  // 默认存活1小时
        // 检查账号状态，1 表示封禁中，不允许登录
        if (user.getState() == 1) {
            responseResult.setCode(403);
            responseResult.setMessage("账号异常，封禁中");
            return responseResult;
        }
        //注释Redis
        //将uid封装成一个jwttoken，同时token也会被缓存到redis中
        String token = jwtUtil.createToken(user.getUid().toString(), "admin");
        /*try {
            redisUtil.setExObjectValue("security:admin:" + user.getUid(), user, 60L * 60 * 24 * 2, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("存储redis数据失败");
            throw e;
        }*/
        // 每次登录顺便返回user信息，就省去再次发送一次获取用户个人信息的请求
        Map<String,Object> userDTOMap = getUserDTOMap(user,token);
        responseResult.setMessage("欢迎回来，主人≥⏝⏝≤");
        responseResult.setData(userDTOMap);
        return responseResult;
    }

    /**
     * 获取用户个人信息
     * @return CustomResponse对象
     */
    @Override
    public ResponseResult personalInformation() {
        Integer loginUserId = currentUser.getUserId();
        UserDTO userDTO = userService.getUserByUId(loginUserId);

        ResponseResult responseResult = new ResponseResult();
        // 检查账号状态，1 表示封禁中，不允许登录，2表示账号注销了
        if (judgeState(responseResult, userDTO.getState())) return responseResult;

        responseResult.setData(userDTO);
        return responseResult;
    }

    /**
     * 获取管理员个人信息
     * @return CustomResponse对象
     */
    @Override
    public ResponseResult adminPersonalInformation() {
        Integer LoginUserId = currentUser.getUserId();
        ResponseResult responseResult = new ResponseResult();
        User user = userMapper.selectById(LoginUserId);
        //注释Redis
        // 从redis中获取最新数据
        /*user = redisUtil.getObject("user:" + LoginUserId, User.class);
        // 如果redis中没有user数据，就从mysql中获取并更新到redis
        if (user == null) {
            user = userMapper.selectById(LoginUserId);
            User finalUser = user;
            CompletableFuture.runAsync(() -> {
                redisUtil.setExObjectValue("user:" + finalUser.getUid(), finalUser);  // 默认存活1小时
            }, taskExecutor);
        }*/

        // 普通用户无权访问
        if (user.getRole() == 0) {
            responseResult.setCode(403);
            responseResult.setMessage("您不是管理员，无权访问");
            return responseResult;
        }
        // 检查账号状态，1 表示封禁中，不允许登录，2表示已注销
        if (judgeState(responseResult, user.getState())) return responseResult;
        UserDTO userDTO = setUserDTO(user);
        responseResult.setData(userDTO);
        return responseResult;
    }

    private boolean judgeState(ResponseResult responseResult, Integer state) {
        if (state == 2) {
            responseResult.setCode(404);
            responseResult.setMessage("账号已注销");
            return true;
        }
        if (state == 1) {
            responseResult.setCode(403);
            responseResult.setMessage("账号异常，封禁中");
            return true;
        }
        return false;
    }

    /**
     * 退出登录，清空redis中相关用户登录认证
     */
    @Override
    public void userLogout() {
        Integer LoginUserId = currentUser.getUserId();
        // 清除redis中该用户的登录认证数据
        //注释Redis
        redisUtil.delValue("token:user:" + LoginUserId);
        //redisUtil.delValue("security:user:" + LoginUserId);
        redisUtil.delMember("login_member", LoginUserId);   // 从在线用户集合中移除
        redisUtil.deleteKeysWithPrefix("whisper:" + LoginUserId + ":"); // 清除全部在聊天窗口的状态

        // 断开全部该用户的channel 并从 userChannel 移除该用户
        Set<Channel> userChannels = IMServer.userChannel.get(LoginUserId);
        if(userChannels == null){ return;}
        else{
            for (Channel channel : userChannels) {
                try {
                    channel.close().sync(); // 等待通道关闭完成
                } catch (InterruptedException e) {
                    // 处理异常，如果有必要的话
                    e.printStackTrace();
                }
            }
            IMServer.userChannel.remove(LoginUserId);
        }
    }

    /**
     * 管理员退出登录，清空redis中相关管理员登录认证
     */
    @Override
    public void adminLogout() {
        Integer LoginUserId = currentUser.getUserId();
        // 清除redis中该用户的登录认证数据
        //注释Redis
        redisUtil.delValue("token:admin:" + LoginUserId);
        //redisUtil.delValue("security:admin:" + LoginUserId);
    }

    @Override
    public ResponseResult updatePassword(String oldPassword, String newPassword) {
        ResponseResult responseResult = new ResponseResult();
        if (newPassword == null || oldPassword == null || oldPassword.isEmpty() || newPassword.isEmpty()) {
            responseResult.setCode(500);
            responseResult.setMessage("密码不能为空");
            return responseResult;
        }

        // 取出当前登录的用户
        UsernamePasswordAuthenticationToken authenticationTokenNow = (UsernamePasswordAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl nowUserDetails = (UserDetailsImpl) authenticationTokenNow.getPrincipal();
        User user = nowUserDetails.getUser();

        // 验证旧密码
        UsernamePasswordAuthenticationToken verifyAuthenticationToken = new UsernamePasswordAuthenticationToken(user.getAccount(), oldPassword);
        try {
            authenticationProvider.authenticate(verifyAuthenticationToken);
        } catch (Exception e) {
            responseResult.setCode(403);
            responseResult.setMessage("密码不正确");
            e.printStackTrace();
            return responseResult;
        }

        if (oldPassword.equals(newPassword)) {
            responseResult.setCode(500);
            responseResult.setMessage("新密码不能与旧密码相同");
            return responseResult;
        }

        String encodedNewPassword = passwordEncoder.encode(newPassword);  // 密文存储
        UpdateWrapper<User> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("uid", user.getUid()).set("password", encodedNewPassword);
        userMapper.update(null, updateWrapper);
        userLogout();
        adminLogout();
        return responseResult;
    }

    @NotNull
    @Override
    public UserDTO setUserDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUid(user.getUid());
        userDTO.setNickname(user.getNickname());
        userDTO.setHeadPortrait_url(user.getHeadPortrait());
        userDTO.setBackground_url(user.getBackground());
        userDTO.setGender(user.getGender());
        userDTO.setDescription(user.getDescription());
        userDTO.setExperience(user.getExperience());
        userDTO.setCoin(user.getCoin());
        userDTO.setState(user.getState());
        return userDTO;
    }

    @NotNull
    @Override
    public UserDTO setSignOutUserDTO(User user) {
        UserDTO userDTO = new UserDTO();
        userDTO.setUid(user.getUid());
        userDTO.setState(user.getState());
        userDTO.setNickname("账号已注销");
        userDTO.setHeadPortrait_url("https://cube.elemecdn.com/9/c2/f0ee8a3c7c9638a54940382568c9dpng.png");
        userDTO.setBackground_url("https://tinypic.host/images/2023/11/15/69PB2Q5W9D2U7L.png");
        userDTO.setGender(2);
        userDTO.setDescription("-");
        userDTO.setExperience(0);
        userDTO.setCoin((double) 0);
        userDTO.setVideoCount(0);
        userDTO.setFollowsCount(0);
        userDTO.setFansCount(0);
        userDTO.setLoveCount(0);
        userDTO.setPlayCount(0);
        return userDTO;
    }


    private @NotNull Map<String, Object> getUserDTOMap(User user, String token) {
        UserDTO userDTO = setUserDTO(user);
        Map<String, Object> userDTOMap = new HashMap<>();
        userDTOMap.put("token", token);
        userDTOMap.put("user", userDTO);
        return userDTOMap;
    }
}
