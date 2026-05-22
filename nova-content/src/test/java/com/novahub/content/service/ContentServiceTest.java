package com.novahub.content.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.result.ResultCode;
import com.novahub.common.utils.RedisUtils;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.content.client.UserClient;
import com.novahub.content.dto.ContentQueryRequest;
import com.novahub.content.dto.PublishContentRequest;
import com.novahub.content.dto.UpdateContentRequest;
import com.novahub.content.entity.Content;
import com.novahub.content.kafka.ContentEventProducer;
import com.novahub.content.mapper.ContentMapper;
import com.novahub.content.mapper.ContentTagMapper;
import com.novahub.content.mapper.ContentTagRelMapper;
import com.novahub.content.service.impl.ContentServiceImpl;
import com.novahub.content.vo.ContentVO;
import com.novahub.content.vo.TagVO;
import com.novahub.content.service.ITagService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ContentServiceTest {

    @Mock
    private ContentMapper contentMapper;

    @Mock
    private ContentTagMapper contentTagMapper;

    @Mock
    private ContentTagRelMapper contentTagRelMapper;

    @Mock
    private ITagService tagService;

    @Mock
    private ContentEventProducer contentEventProducer;

    @Mock
    private RedisUtils redisUtils;

    @Mock
    private UserClient userClient;

    private SimpleMeterRegistry meterRegistry;

    private ContentServiceImpl contentService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        contentService = new ContentServiceImpl(
                contentMapper, contentTagMapper, contentTagRelMapper,
                tagService, contentEventProducer, redisUtils, objectMapper, userClient, meterRegistry
        );
    }

    @AfterEach
    void tearDown() {
        SecurityUtils.clear();
    }

    @Nested
    @DisplayName("发布内容测试")
    class PublishTests {

        @BeforeEach
        void setUserContext() {
            SecurityUtils.setUserId(1L);
        }

        @Test
        @DisplayName("发布内容 - 提交审核")
        void publish_submitForReview() {
            PublishContentRequest request = new PublishContentRequest();
            request.setType(1);
            request.setTitle("Test Title");
            request.setContent("Test Content");
            request.setStatus(1);

            when(contentMapper.insert(any(Content.class))).thenAnswer(invocation -> {
                Content c = invocation.getArgument(0);
                c.setId(100L);
                return 1;
            });

            Long contentId = contentService.publish(request);

            assertEquals(100L, contentId);
            verify(contentMapper).insert(any(Content.class));
        }

        @Test
        @DisplayName("发布内容 - 保存草稿")
        void publish_saveAsDraft() {
            PublishContentRequest request = new PublishContentRequest();
            request.setType(1);
            request.setTitle("Draft Title");
            request.setContent("Draft Content");
            request.setStatus(0);

            when(contentMapper.insert(any(Content.class))).thenAnswer(invocation -> {
                Content c = invocation.getArgument(0);
                c.setId(200L);
                return 1;
            });

            Long contentId = contentService.saveDraft(request);

            assertEquals(200L, contentId);
            verify(contentMapper).insert(any(Content.class));
        }

        @Test
        @DisplayName("发布内容 - 附带标签")
        void publish_withTags() {
            PublishContentRequest request = new PublishContentRequest();
            request.setType(1);
            request.setTitle("Titled Content");
            request.setContent("Content body");
            request.setStatus(1);
            request.setTagIds(List.of(10L, 20L));

            when(contentMapper.insert(any(Content.class))).thenAnswer(invocation -> {
                Content c = invocation.getArgument(0);
                c.setId(300L);
                return 1;
            });

            Long contentId = contentService.publish(request);

            assertEquals(300L, contentId);
            verify(contentTagRelMapper).batchInsert(anyList());
            verify(contentTagMapper).incrementUseCount(anyList());
        }

        @Test
        @DisplayName("发布内容 - 未登录抛出异常")
        void publish_unauthenticated() {
            SecurityUtils.clear();

            PublishContentRequest request = new PublishContentRequest();
            request.setType(1);
            request.setTitle("Test");
            request.setContent("Test");

            assertThrows(BusinessException.class, () -> contentService.publish(request));
        }
    }

    @Nested
    @DisplayName("获取内容详情测试")
    class GetByIdTests {

        @BeforeEach
        void setUserContext() {
            SecurityUtils.setUserId(1L);
        }

        @Test
        @DisplayName("获取内容 - 缓存命中")
        void getById_cacheHit() throws Exception {
            Long contentId = 1L;

            ContentVO cachedVO = new ContentVO();
            cachedVO.setId(contentId);
            cachedVO.setTitle("Cached Title");
            cachedVO.setContent("Cached Content");

            String cachedJson = objectMapper.writeValueAsString(cachedVO);
            when(redisUtils.get("content:detail:" + contentId)).thenReturn(cachedJson);

            ContentVO result = contentService.getById(contentId);

            assertNotNull(result);
            assertEquals("Cached Title", result.getTitle());
            verify(contentMapper, never()).selectById(any());
        }

        @Test
        @DisplayName("获取内容 - 缓存未命中，回源数据库")
        void getById_cacheMiss_dbHit() {
            Long contentId = 2L;

            Content content = new Content();
            content.setId(contentId);
            content.setUserId(1L);
            content.setType(1);
            content.setTitle("DB Title");
            content.setContent("DB Content");
            content.setStatus(2);
            content.setIsDeleted(0);
            content.setLikeCount(0);
            content.setCollectCount(0);
            content.setCommentCount(0);
            content.setViewCount(0);

            UserClient.UserInfo userInfo = new UserClient.UserInfo();
            userInfo.setId(1L);
            userInfo.setNickname("Author");

            when(redisUtils.get("content:detail:" + contentId)).thenReturn(null);
            when(redisUtils.setIfAbsent(eq("content:lock:" + contentId), eq("1"), eq(5L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);
            when(contentMapper.selectById(contentId)).thenReturn(content);
            when(userClient.getUserInfo(1L)).thenReturn(userInfo);
            when(tagService.getTagsByContentId(contentId)).thenReturn(Collections.emptyList());

            ContentVO result = contentService.getById(contentId);

            assertNotNull(result);
            assertEquals("DB Title", result.getTitle());
            verify(contentMapper).selectById(contentId);
            verify(redisUtils).set(eq("content:detail:" + contentId), anyString(), eq(10L), eq(TimeUnit.MINUTES));
        }

        @Test
        @DisplayName("获取内容 - 内容不存在")
        void getById_notFound() {
            Long contentId = 999L;

            when(redisUtils.get("content:detail:" + contentId)).thenReturn(null);
            when(contentMapper.selectById(contentId)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contentService.getById(contentId));

            assertEquals(ResultCode.CONTENT_NOT_FOUND.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("获取内容 - 缓存损坏，返回null")
        void getById_cacheCorrupted() {
            Long contentId = 3L;

            Content content = new Content();
            content.setId(contentId);
            content.setUserId(1L);
            content.setType(1);
            content.setTitle("Fresh Title");
            content.setContent("Fresh Content");
            content.setStatus(2);
            content.setIsDeleted(0);
            content.setLikeCount(0);
            content.setCollectCount(0);
            content.setCommentCount(0);
            content.setViewCount(0);

            when(redisUtils.get("content:detail:" + contentId)).thenReturn("invalid json{{{");
            when(redisUtils.setIfAbsent(eq("content:lock:" + contentId), eq("1"), eq(5L), eq(TimeUnit.SECONDS)))
                    .thenReturn(true);
            when(contentMapper.selectById(contentId)).thenReturn(content);
            when(userClient.getUserInfo(1L)).thenReturn(null);
            when(tagService.getTagsByContentId(contentId)).thenReturn(Collections.emptyList());

            ContentVO result = contentService.getById(contentId);

            assertNotNull(result);
            assertEquals("Fresh Title", result.getTitle());
            verify(redisUtils).set(eq("content:detail:" + contentId), anyString(), eq(10L), eq(TimeUnit.MINUTES));
        }
    }

    @Nested
    @DisplayName("更新内容测试")
    class UpdateTests {

        @BeforeEach
        void setUserContext() {
            SecurityUtils.setUserId(1L);
        }

        @Test
        @DisplayName("更新内容 - 成功")
        void update_success() {
            Long contentId = 1L;

            Content existing = new Content();
            existing.setId(contentId);
            existing.setUserId(1L);
            existing.setStatus(1);
            existing.setTitle("Old Title");
            existing.setIsDeleted(0);

            when(contentMapper.selectById(contentId)).thenReturn(existing);

            UpdateContentRequest request = new UpdateContentRequest();
            request.setTitle("New Title");

            assertDoesNotThrow(() -> contentService.update(contentId, request));

            verify(contentMapper).updateById(any(Content.class));
            verify(redisUtils).delete("content:detail:" + contentId);
        }

        @Test
        @DisplayName("更新内容 - 非作者无权操作")
        void update_notAuthor() {
            Long contentId = 1L;

            Content existing = new Content();
            existing.setId(contentId);
            existing.setUserId(999L);
            existing.setStatus(1);
            existing.setIsDeleted(0);

            when(contentMapper.selectById(contentId)).thenReturn(existing);

            UpdateContentRequest request = new UpdateContentRequest();
            request.setTitle("New Title");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contentService.update(contentId, request));

            assertEquals(ResultCode.CONTENT_NOT_AUTHOR.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("更新内容 - 已发布内容不可编辑")
        void update_cannotEditPublished() {
            Long contentId = 1L;

            Content existing = new Content();
            existing.setId(contentId);
            existing.setUserId(1L);
            existing.setStatus(2);
            existing.setIsDeleted(0);

            when(contentMapper.selectById(contentId)).thenReturn(existing);

            UpdateContentRequest request = new UpdateContentRequest();
            request.setTitle("New Title");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contentService.update(contentId, request));

            assertEquals(ResultCode.CONTENT_CANNOT_EDIT.getCode(), ex.getCode());
        }

        @Test
        @DisplayName("更新内容 - 内容不存在")
        void update_contentNotFound() {
            Long contentId = 999L;

            when(contentMapper.selectById(contentId)).thenReturn(null);

            UpdateContentRequest request = new UpdateContentRequest();
            request.setTitle("New Title");

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contentService.update(contentId, request));

            assertEquals(ResultCode.CONTENT_NOT_FOUND.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("删除内容测试")
    class DeleteTests {

        @BeforeEach
        void setUserContext() {
            SecurityUtils.setUserId(1L);
        }

        @Test
        @DisplayName("删除内容 - 成功")
        void delete_success() {
            Long contentId = 1L;

            Content existing = new Content();
            existing.setId(contentId);
            existing.setUserId(1L);
            existing.setIsDeleted(0);

            when(contentMapper.selectById(contentId)).thenReturn(existing);

            assertDoesNotThrow(() -> contentService.delete(contentId));

            verify(contentMapper).updateById(any(Content.class));
            verify(contentTagRelMapper).deleteByContentId(contentId);
            verify(redisUtils).delete("content:detail:" + contentId);
        }

        @Test
        @DisplayName("删除内容 - 非作者无权操作")
        void delete_notAuthor() {
            Long contentId = 1L;

            Content existing = new Content();
            existing.setId(contentId);
            existing.setUserId(999L);
            existing.setIsDeleted(0);

            when(contentMapper.selectById(contentId)).thenReturn(existing);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> contentService.delete(contentId));

            assertEquals(ResultCode.CONTENT_NOT_AUTHOR.getCode(), ex.getCode());
        }
    }

    @Nested
    @DisplayName("分页查询测试")
    class GetPageTests {

        @BeforeEach
        void setUserContext() {
            SecurityUtils.setUserId(1L);
        }

        @Test
        @DisplayName("分页查询 - 有数据")
        void getPage_withData() {
            ContentQueryRequest request = new ContentQueryRequest();
            request.setPage(1L);
            request.setPageSize(10L);

            Content content1 = new Content();
            content1.setId(1L);
            content1.setUserId(2L);
            content1.setType(1);
            content1.setTitle("Title 1");
            content1.setContent("Content 1");
            content1.setStatus(2);
            content1.setIsDeleted(0);
            content1.setLikeCount(10);
            content1.setCollectCount(0);
            content1.setCommentCount(0);
            content1.setViewCount(0);
            content1.setCreateTime(LocalDateTime.now());

            com.baomidou.mybatisplus.core.metadata.IPage<Content> mockPage =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            mockPage.setRecords(Collections.singletonList(content1));
            mockPage.setTotal(1);

            when(contentMapper.selectPage(any(), any())).thenReturn(mockPage);
            when(userClient.getUserInfo(2L)).thenReturn(null);
            when(tagService.getTagsByContentId(1L)).thenReturn(Collections.emptyList());

            var result = contentService.getPage(request);

            assertNotNull(result);
            assertEquals(1, result.getRecords().size());
        }

        @Test
        @DisplayName("分页查询 - 按点赞数排序")
        void getPage_sortByLikeCount() {
            ContentQueryRequest request = new ContentQueryRequest();
            request.setPage(1L);
            request.setPageSize(10L);
            request.setSortBy("likeCount");

            com.baomidou.mybatisplus.core.metadata.IPage<Content> mockPage =
                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(1, 10);
            mockPage.setRecords(Collections.emptyList());
            mockPage.setTotal(0);

            when(contentMapper.selectPage(any(), any())).thenReturn(mockPage);

            var result = contentService.getPage(request);

            assertNotNull(result);
            assertTrue(result.getRecords().isEmpty());
        }
    }
}
