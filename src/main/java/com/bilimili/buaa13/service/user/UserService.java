package com.bilimili.buaa13.service.user;

import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.entity.dto.UserDTO;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface UserService {
    /**
     * 根据uid查询用户信息
     * @param id 用户ID
     * @return 用户可见信息实体类 UserDTO
     */
    UserDTO getUserByUId(Integer id);

    /**
     * 根据有序uid列表查询用户信息
     * @param list 用户id列表
     * @return  用户信息列表
     */
    List<UserDTO> getUserByUIdList(List<Integer> list);

    /**
     * 更新用户个人信息
     * @param uid   用户uid
     * @param nickname  昵称
     * @param desc  个性签名
     * @param gender    性别： 0 女 1 男 2 双性人
     * @return  响应对象
     */
    ResponseResult updateUserInformation(Integer uid, String nickname, String desc, Integer gender) throws IOException;

    /**
     * 更新用户头像
     * @param uid 用户id
     * @param file 图像文件
     * @return 响应
     */
    ResponseResult updateUserHeadPortrait(Integer uid, MultipartFile file) throws IOException;
}
