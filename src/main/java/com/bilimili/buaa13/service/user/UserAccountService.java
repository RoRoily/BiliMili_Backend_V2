package com.bilimili.buaa13.service.user;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.entity.User;
import com.bilimili.buaa13.entity.dto.UserDTO;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public interface UserAccountService {
    /**
     * 用户注册
     * @param username 账号
     * @param password 密码
     * @param confirmedPassword 确认密码
     * @return 响应对象
     */
    ResponseResult register(String username, String password, String confirmedPassword) throws IOException;

    /**
     * 用户登录
     * @param username 账号
     * @param password 密码
     * @return 响应对象
     */
    ResponseResult login(String username, String password);

    /**
     * 管理员登录
     * @param username 账号
     * @param password 密码
     * @return 响应对象
     */
    ResponseResult adminLogin(String username, String password);

    /**
     * 获取用户个人信息
     * @return 响应对象
     */
    ResponseResult personalInformation();

    /**
     * 获取管理员个人信息
     * @return 响应对象
     */
    ResponseResult adminPersonalInformation();

    /**
     * 退出登录，清空redis中相关用户登录认证
     */
    void userLogout();

    /**
     * 管理员退出登录，清空redis中相关管理员登录认证
     */
    void adminLogout();

    /**
     * 重置密码
     *
     * @param oldPassword 旧密码
     * @param newPassword 新密码
     * @return 响应对象
     */
    ResponseResult updatePassword(String oldPassword, String newPassword);

    @NotNull
    UserDTO setUserDTO(User user);

    @NotNull
    UserDTO setSignOutUserDTO(User user);
}
