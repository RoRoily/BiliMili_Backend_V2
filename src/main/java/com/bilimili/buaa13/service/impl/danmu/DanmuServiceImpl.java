package com.bilimili.buaa13.service.impl.danmu;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bilimili.buaa13.entity.ResponseResult;
import com.bilimili.buaa13.mapper.DanmuMapper;
import com.bilimili.buaa13.mapper.VideoMapper;
import com.bilimili.buaa13.entity.Danmu;
import com.bilimili.buaa13.entity.Video;
import com.bilimili.buaa13.service.danmu.DanmuService;
import com.bilimili.buaa13.service.video.VideoStatsService;
import com.bilimili.buaa13.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
public class DanmuServiceImpl implements DanmuService {

    @Autowired
    private DanmuMapper danmuMapper;

    @Autowired
    private VideoMapper videoMapper;

    @Autowired
    private VideoStatsService videoStatsService;

    @Autowired
    private RedisUtil redisUtil;

    /**
     * 根据弹幕ID集合查询弹幕列表
     * @param idset 弹幕ID集合
     * @return  弹幕列表
     */
    @Override
    public List<Danmu> getDanmuListByIdset(Set<Object> idset) {
        if (idset == null || idset.size() == 0) {
            return null;
        }
        QueryWrapper<Danmu> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("id", idset).eq("state", 1);
        return danmuMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional
    public ResponseResult deleteDanmu(Integer id, Integer uid, boolean isAdmin) {
        ResponseResult responseResult = new ResponseResult();
        QueryWrapper<Danmu> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("id", id).ne("state", 3);
        Danmu danmu = danmuMapper.selectOne(queryWrapper);
        if (danmu == null) {
            responseResult.setCode(404);
            responseResult.setMessage("弹幕不存在");
            return responseResult;
        }
        // 判断该用户是否有权限删除这条评论
        Video video = videoMapper.selectById(danmu.getVid());
        if (Objects.equals(danmu.getUid(), uid) || isAdmin || Objects.equals(video.getUid(), uid)) {
            // 删除弹幕
            UpdateWrapper<Danmu> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("id", id).set("state", 3);
            danmuMapper.update(null, updateWrapper);
            videoStatsService.updateStats(danmu.getVid(), "danmu", false, 1);
            redisUtil.delMember("danmu_idset:" + danmu.getVid(), id);
        } else {
            responseResult.setCode(403);
            responseResult.setMessage("你无权删除该条评论");
        }
        return responseResult;
    }
}
