package com.bilimili.buaa13.service.impl.search;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.bilimili.buaa13.entity.HotSearch;
import com.bilimili.buaa13.mapper.HotSearchMapper;
import com.bilimili.buaa13.service.search.SearchService;
import com.bilimili.buaa13.service.utils.EventListenerService;
import com.bilimili.buaa13.utils.ESUtil;
import com.bilimili.buaa13.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {

    @Autowired
    private RedisUtil redisUtil;

    @Autowired
    private ESUtil esUtil;

    @Autowired
    private HotSearchMapper hotSearchMapper;

    @Autowired
    @Qualifier("taskExecutor")
    private Executor taskExecutor;

    @Override
    public String addSearchWord(String text) {
        // 处理传入的内容 去除特殊字符
        String formattedString = formatString(text);
        // 如果格式化后的字符串没有字符，直接返回null
        if (formattedString.isEmpty()) return null;
        //计算输入字符串的中文和字母数量
        int keywordLength = formattedString.replaceAll("[0-9\\s]","").length();
        keywordLength = countNumber(formattedString);
        // 如果长度不符合就直接返回格式化字符串，不存redis和ES
        if (keywordLength < 2 || keywordLength > 25) return formattedString;
        // 查询是否有该词条，用异步线程不耽误后面查询
        CompletableFuture.runAsync(() -> {
            //注释Redis
            /*if (redisUtil.zsetExist("search_word", formattedString)) {
                // 如果有，就热度加一
                redisUtil.zincrby("search_word", formattedString, 1);
            } else {
                // 否则添加成员到redis和ES
                redisUtil.zsetWithScore("search_word", formattedString, 1);
                esUtil.addSearchWord(formattedString);
            }*/
            QueryWrapper<HotSearch> hotSearchQueryWrapper = new QueryWrapper<>();
            hotSearchQueryWrapper.eq("content", formattedString);
            HotSearch hotSearch = hotSearchMapper.selectOne(hotSearchQueryWrapper);
            if (hotSearch == null) {
                //没有这个热度关键词，创建一个存入数据库
                HotSearch newHotSearch = new HotSearch();
                newHotSearch.setContent(formattedString);
                newHotSearch.setHot(1D);
                newHotSearch.setType(1);//设为新词条
                hotSearchMapper.insert(newHotSearch);
                esUtil.addSearchWord(formattedString);
            }
            else {
                //更新热度+1
                UpdateWrapper<HotSearch> hotSearchUpdateWrapper = new UpdateWrapper<>();
                hotSearchUpdateWrapper.eq("hid", hotSearch.getHid());
                hotSearchUpdateWrapper.set("hot", hotSearch.getHot()+1);
                if(hotSearch.getHot() >= 5){
                    hotSearchUpdateWrapper.set("type", 2);
                }
                else hotSearchUpdateWrapper.set("type", 0);
                hotSearchMapper.update(null, hotSearchUpdateWrapper);
            }
        }, taskExecutor);
        return formattedString;
    }

    @Override
    public List<String> getKeyWord(String keyword) {
        return esUtil.getMatchingWord(keyword);
    }

    @Override
    public List<HotSearch> getHotSearch() {
        //注释Redis
       /* List<RedisUtil.ZObjScore> curr = redisUtil.zReverangeWithScores("search_word", 0, 9);
        List<HotSearch> list = new ArrayList<>();
        for (RedisUtil.ZObjScore o : curr) {
            HotSearch word = new HotSearch();
            word.setContent(o.getMember().toString());
            word.setHot(o.getScore());
            Double lastScore = findScoreForName(o.getMember());
            if (lastScore == null) {
                word.setType(1);    // 没有找到就是新词条
                if (o.getScore() > 3) {
                    word.setType(2);    // 短时间内搜索超过3次就是热词条
                }
            } else if (o.getScore() - lastScore > 3) {
                word.setType(2);    // 短时间内搜索超过3次就是热词条
            }
            list.add(word);
        }*/
        return hotSearchMapper.getHotSearchByCount(10);
    }

    @Override
    public List<Long> getCount(String keyword) {
        List<Long> result = new ArrayList<>();
        try {
            // 获取视频数量
            Long videoCount = esUtil.getVideoCount(keyword, true);
            result.add(videoCount);
            // 获取用户数量
            Long userCount = esUtil.getUserCount(keyword);
            result.add(userCount);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            // 出现异常时，返回包含两个0的列表
            result.clear();
            result.add(0L);
            result.add(0L);
        }
        return result;
    }

    /**
     * 格式化包含特殊字符的字符串
     * 2024.08.06
     */
    private String formatString(String input) {
        // 使用正则表达式替换特殊字符，并保留一个空格符
        String formatString = input.replaceAll("[^a-zA-Z0-9\\u4e00-\\u9fffぁ-んァ-ヶ]+", " ");
        // 去除首尾空格
        formatString = formatString.trim();
        return formatString;
    }

    /**
     * 计算输入字符串的中文和字母数量
     * 2024.08.06
     */
    private int countNumber(String input) {
        // 去除数字和空格，计算剩余字符中中文和字母的数量
        return input.replaceAll("[0-9\\s]+", "").length();
    }

    @Nullable
    private Double findScoreByName(Object name) {
        for (RedisUtil.ZObjScore zObjScore : EventListenerService.hotSearchWords) {
            if (zObjScore.getMember().equals(name)) {
                return zObjScore.getScore();
            }
        }
        return null;
    }
}
