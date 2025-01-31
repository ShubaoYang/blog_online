package com.minzheng.blog.service.impl;

import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.minzheng.blog.dao.*;
import com.minzheng.blog.dto.*;
import com.minzheng.blog.entity.*;
import com.minzheng.blog.service.*;
import com.minzheng.blog.util.BeanCopyUtils;
import com.minzheng.blog.util.PageUtils;
import com.minzheng.blog.util.UserUtils;
import com.minzheng.blog.vo.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.minzheng.blog.constant.CommonConst.FALSE;


/**
 * 文章服务
 *
 * @author yezhiqiu
 * @date 2021/08/10
 */
@Service
public class QuestionServiceImpl extends ServiceImpl<QuestionDao, Question> implements QuestionService {
    private static final Logger logger = LoggerFactory.getLogger(QuestionServiceImpl.class);

    @Autowired
    private CategoryService categoryService;
    @Autowired
    private QuestionDao questionDao;
    @Autowired
    private QuestionTagDao questionTagDao;
    @Autowired
    private QuestionTagService questionTagService;
    @Autowired
    private TagService tagService;
    @Autowired
    private QuestionService questionService;

    public static final int BATCH_QUESTION_NUMBER = 10;


    @Override
    public void saveOrUpdateQuestion(QuestionVo questionVo) {
        // 保存文章分类
        Category category = categoryService.saveOrUpdateCategory(questionVo.getCategoryName());
        // 保存或修改文章
        Question question = BeanCopyUtils.copyObject(questionVo, Question.class);
        if (Objects.nonNull(category)) {
            question.setCategoryId(category.getId());
        }
        question.setUserId(UserUtils.getLoginUser().getUserInfoId());
        this.saveOrUpdate(question);

        saveQuestionTag(questionVo, question.getId());
    }

    /**
     * 保存文章标签
     *
     * @param questionVo 问答
     * @param questionId 问答ID
     */
    private void saveQuestionTag(QuestionVo questionVo, Integer questionId) {
        // 编辑文章则删除文章所有标签
        if (Objects.nonNull(questionVo.getId())) {
            questionTagDao.delete(new LambdaQueryWrapper<QuestionTag>()
                    .eq(QuestionTag::getQuestionId, questionVo.getId()));
        }
        // 添加文章标签
        List<String> tagNames = questionVo.getTagNameList();
        if (CollectionUtils.isNotEmpty(tagNames)) {
            List<Integer> tagIds = tagService.insertNotExistTags(tagNames);
            // 提取标签id绑定文章
            List<QuestionTag> questionTags = tagIds.stream().map(tagId -> QuestionTag.builder()
                    .questionId(questionId)
                    .tagId(tagId)
                    .build())
                    .collect(Collectors.toList());
            questionTagService.saveBatch(questionTags);
        }
    }

    @Override
    public PageResult<QuestionVo> listQuestionBacks(ConditionVO condition) {
        // 查询文章总量
        Integer count = questionDao.countQuestionBacks(condition);
        if (count == 0) {
            return new PageResult<>();
        }
        // 查询后台文章
        List<QuestionDTO> questions = questionDao.listQuestionBacks(PageUtils.getLimitCurrent(), PageUtils.getSize(), condition);
        List<QuestionVo> questionVo = BeanCopyUtils.copyList(questions, QuestionVo.class);
        questionVo.forEach(question -> question.setTags(questionTagService.getTagsByQuestionId(question.getId())));
        return new PageResult<>(questionVo, count);
    }

    @Override
    public QuestionVo getQuestionBackById(Integer questionId) {
//        Question question = questionDao.selectById(questionId);
        logger.info("获取单个问答--{}", questionId);
        QuestionDTO questionDTO = questionDao.getQuestionById(questionId);
        QuestionVo questionVo = BeanCopyUtils.copyObject(questionDTO, QuestionVo.class);
        questionVo.setTagNameList(questionTagService.getTagNamesByQuestionId(questionId));
        logger.info("获取单个问答--{}", JSONUtil.toJsonStr(questionVo));
        return questionVo;
    }

    @Override
    public List<QuestionVo> getQuestionsBatch() {
        List<QuestionDTO> questions = questionDao.getBatchQuestions(BATCH_QUESTION_NUMBER);
        List<QuestionVo> questionVo = BeanCopyUtils.copyList(questions, QuestionVo.class);
        return questionVo;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updateQuestionDelete(DeleteVO deleteVO) {
        // 修改文章逻辑删除状态
        List<Question> questions = deleteVO.getIdList().stream()
                .map(id -> Question.builder()
                        .id(id)
                        .isDelete(deleteVO.getIsDelete())
                        .build())
                .collect(Collectors.toList());
        questionService.updateBatchById(questions);
    }

    //    private void saveQuestionTag(QuestionVo questionVo, Integer id) {
//        // 编辑文章则删除文章所有标签
//        if (Objects.nonNull(questionVo.getId())) {
//            articleTagDao.delete(new LambdaQueryWrapper<ArticleTag>()
//                    .eq(ArticleTag::getArticleId, articleVO.getId()));
//        }
//        // 添加文章标签
//        List<String> tagNameList = articleVO.getTagNameList();
//        if (CollectionUtils.isNotEmpty(tagNameList)) {
//            // 查询已存在的标签
//            List<Tag> existTagList = tagService.list(new LambdaQueryWrapper<Tag>()
//                    .in(Tag::getTagName, tagNameList));
//            List<String> existTagNameList = existTagList.stream()
//                    .map(Tag::getTagName)
//                    .collect(Collectors.toList());
//            List<Integer> existTagIdList = existTagList.stream()
//                    .map(Tag::getId)
//                    .collect(Collectors.toList());
//            // 对比新增不存在的标签
//            tagNameList.removeAll(existTagNameList);
//            if (CollectionUtils.isNotEmpty(tagNameList)) {
//                List<Tag> tagList = tagNameList.stream().map(item -> Tag.builder()
//                        .tagName(item)
//                        .build())
//                        .collect(Collectors.toList());
//                tagService.saveBatch(tagList);
//                List<Integer> tagIdList = tagList.stream()
//                        .map(Tag::getId)
//                        .collect(Collectors.toList());
//                existTagIdList.addAll(tagIdList);
//            }
//            // 提取标签id绑定文章
//            List<ArticleTag> articleTagList = existTagIdList.stream().map(item -> ArticleTag.builder()
//                    .articleId(articleId)
//                    .tagId(item)
//                    .build())
//                    .collect(Collectors.toList());
//            articleTagService.saveBatch(articleTagList);
//        }
//    }
}
