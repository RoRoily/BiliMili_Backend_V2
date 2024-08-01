package com.bilimili.buaa13.controller;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.service.user.UserAccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
public class UserAccountController {

    @Autowired
    private UserAccountService userAccountService;

    public static boolean isValidPassword(String password) {
        // 正则表达式: ^ 表示开始, [a-zA-Z0-9]{6,14} 表示6到14位的字母或数字, $ 表示结束
        String regex = "^[a-zA-Z0-9]{6,14}$";

        // 使用 Pattern 和 Matcher 类进行正则表达式匹配
        return password.matches(regex);
    }

    /**
     * 注册接口
     * @param map 包含 username password confirmedPassword 的 map
     * @return 响应对象
     */
    // 前端使用axios传递的data是Content-Type: application/json，需要用@RequestBody获取参数
    @PostMapping("/user/account/register")
    public ResponseResult register(@RequestBody Map<String, String> map) {
        String username = map.get("username");
        String password = map.get("password");
        if(!isValidPassword(password)){
            return new ResponseResult(500,"抱歉，您的密码不太标准哦",null);
        }
        String confirmedPassword = map.get("confirmedPassword");
        try {
            return userAccountService.register(username, password, confirmedPassword);
        } catch (Exception e) {
            e.printStackTrace();
            ResponseResult responseResult = new ResponseResult();
            responseResult.setCode(500);
            responseResult.setMessage("ERROR");
            return responseResult;
        }
    }

    /**
     * 登录接口
     * @param map 包含 username password 的 map
     * @return 响应对象
     */
    @PostMapping("/user/account/login")
    public ResponseResult login(@RequestBody Map<String, String> map) {
        String username = map.get("username");
        String password = map.get("password");
        return userAccountService.login(username, password);
    }

    /**
     * 管理员登录接口
     * @param map 包含 username password 的 map
     * @return 响应对象
     */
    @PostMapping("/admin/account/login")
    public ResponseResult adminLogin(@RequestBody Map<String, String> map) {
        String username = map.get("username");
        String password = map.get("password");
        return userAccountService.adminLogin(username, password);
    }

    /**
     * 获取当前登录用户信息接口
     * @return 响应对象
     */
    @GetMapping("/user/personal/info")
    public ResponseResult personalInfo() {
        return userAccountService.personalInformation();
    }

    /**
     * 获取当前登录管理员信息接口
     * @return 响应对象
     */
    @GetMapping("/admin/personal/info")
    public ResponseResult adminPersonalInfo() {
        return userAccountService.adminPersonalInformation();
    }

    /**
     * 退出登录接口
     */
    @GetMapping("/user/account/logout")
    public void logout() {
        userAccountService.userLogout();
    }

    /**
     * 管理员退出登录接口
     */
    @GetMapping("/admin/account/logout")
    public void adminLogout() {
        userAccountService.adminLogout();
    }

    /**
     * 修改当前用户密码
     * @param pw    就密码
     * @param npw   新密码
     * @return  响应对象
     */
    @PostMapping("/user/password/update")
    public ResponseResult updatePassword(@RequestParam("pw") String pw, @RequestParam("npw") String npw) {
        return userAccountService.updatePassword(pw, npw);
    }
}
