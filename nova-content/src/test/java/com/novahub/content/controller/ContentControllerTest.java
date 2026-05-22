package com.novahub.content.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.novahub.common.exception.BusinessException;
import com.novahub.common.exception.GlobalExceptionHandler;
import com.novahub.common.result.PageResult;
import com.novahub.common.result.ResultCode;
import com.novahub.content.dto.ContentQueryRequest;
import com.novahub.content.dto.PublishContentRequest;
import com.novahub.content.dto.UpdateContentRequest;
import com.novahub.content.service.IContentService;
import com.novahub.content.vo.ContentListVO;
import com.novahub.content.vo.ContentVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ContentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private IContentService contentService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        ContentController contentController = new ContentController(contentService);
        mockMvc = MockMvcBuilders.standaloneSetup(contentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        objectMapper = new ObjectMapper();
    }

    @Nested
    @DisplayName("POST /api/contents")
    class PublishTests {

        @Test
        @DisplayName("发布内容成功")
        void publish_success() throws Exception {
            PublishContentRequest request = new PublishContentRequest();
            request.setType(1);
            request.setTitle("Test Post");
            request.setContent("Test Content Body");
            request.setStatus(1);

            when(contentService.publish(any(PublishContentRequest.class))).thenReturn(10086L);

            mockMvc.perform(post("/api/contents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value(10086));

            verify(contentService).publish(any(PublishContentRequest.class));
        }

        @Test
        @DisplayName("发布失败 - 内容不存在")
        void publish_fail_serviceError() throws Exception {
            PublishContentRequest request = new PublishContentRequest();
            request.setType(1);
            request.setTitle("Test");
            request.setContent("Test");
            request.setStatus(1);

            when(contentService.publish(any(PublishContentRequest.class)))
                    .thenThrow(new BusinessException(ResultCode.CONTENT_NOT_FOUND));

            mockMvc.perform(post("/api/contents")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.CONTENT_NOT_FOUND.getCode()));
        }
    }

    @Nested
    @DisplayName("GET /api/contents/{id}")
    class GetByIdTests {

        @Test
        @DisplayName("获取内容详情成功")
        void getById_success() throws Exception {
            Long contentId = 1L;

            ContentVO contentVO = new ContentVO();
            contentVO.setId(contentId);
            contentVO.setUserId(100L);
            contentVO.setTitle("Test Title");
            contentVO.setContent("Test Content");
            contentVO.setType(1);
            contentVO.setStatus(2);
            contentVO.setAuthorNickname("TestAuthor");

            when(contentService.getById(contentId)).thenReturn(contentVO);

            mockMvc.perform(get("/api/contents/{id}", contentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value(1))
                    .andExpect(jsonPath("$.data.title").value("Test Title"))
                    .andExpect(jsonPath("$.data.authorNickname").value("TestAuthor"));

            verify(contentService).getById(contentId);
        }

        @Test
        @DisplayName("获取内容详情 - 内容不存在")
        void getById_notFound() throws Exception {
            Long contentId = 999L;

            when(contentService.getById(contentId))
                    .thenThrow(new BusinessException(ResultCode.CONTENT_NOT_FOUND));

            mockMvc.perform(get("/api/contents/{id}", contentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.CONTENT_NOT_FOUND.getCode()));
        }
    }

    @Nested
    @DisplayName("PUT /api/contents/{id}")
    class UpdateTests {

        @Test
        @DisplayName("更新内容成功")
        void update_success() throws Exception {
            Long contentId = 1L;

            UpdateContentRequest request = new UpdateContentRequest();
            request.setTitle("Updated Title");

            doNothing().when(contentService).update(eq(contentId), any(UpdateContentRequest.class));

            mockMvc.perform(put("/api/contents/{id}", contentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(contentService).update(eq(contentId), any(UpdateContentRequest.class));
        }

        @Test
        @DisplayName("更新内容 - 非作者无权操作")
        void update_notAuthor() throws Exception {
            Long contentId = 1L;

            UpdateContentRequest request = new UpdateContentRequest();
            request.setTitle("Updated");

            doThrow(new BusinessException(ResultCode.CONTENT_NOT_AUTHOR))
                    .when(contentService).update(eq(contentId), any(UpdateContentRequest.class));

            mockMvc.perform(put("/api/contents/{id}", contentId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.CONTENT_NOT_AUTHOR.getCode()));
        }
    }

    @Nested
    @DisplayName("DELETE /api/contents/{id}")
    class DeleteTests {

        @Test
        @DisplayName("删除内容成功")
        void delete_success() throws Exception {
            Long contentId = 1L;

            doNothing().when(contentService).delete(contentId);

            mockMvc.perform(delete("/api/contents/{id}", contentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));

            verify(contentService).delete(contentId);
        }

        @Test
        @DisplayName("删除内容 - 非作者")
        void delete_notAuthor() throws Exception {
            Long contentId = 1L;

            doThrow(new BusinessException(ResultCode.CONTENT_NOT_AUTHOR))
                    .when(contentService).delete(contentId);

            mockMvc.perform(delete("/api/contents/{id}", contentId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(ResultCode.CONTENT_NOT_AUTHOR.getCode()));
        }
    }

    @Nested
    @DisplayName("GET /api/contents")
    class GetPageTests {

        @Test
        @DisplayName("分页查询内容列表成功")
        void getPage_success() throws Exception {
            ContentQueryRequest request = new ContentQueryRequest();
            request.setPage(1L);
            request.setPageSize(10L);

            ContentListVO item = new ContentListVO();
            item.setId(1L);
            item.setTitle("Post 1");
            item.setType(1);
            item.setLikeCount(100);

            PageResult<ContentListVO> pageResult = PageResult.of(
                    Collections.singletonList(item), 1, 1, 10);

            when(contentService.getPage(any(ContentQueryRequest.class))).thenReturn(pageResult);

            mockMvc.perform(get("/api/contents")
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].id").value(1))
                    .andExpect(jsonPath("$.data.records[0].title").value("Post 1"))
                    .andExpect(jsonPath("$.data.total").value(1));

            verify(contentService).getPage(any(ContentQueryRequest.class));
        }

        @Test
        @DisplayName("分页查询 - 空结果")
        void getPage_empty() throws Exception {
            PageResult<ContentListVO> emptyResult = PageResult.of(
                    Collections.emptyList(), 0, 1, 10);

            when(contentService.getPage(any(ContentQueryRequest.class))).thenReturn(emptyResult);

            mockMvc.perform(get("/api/contents")
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isEmpty())
                    .andExpect(jsonPath("$.data.total").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/contents/users/{userId}/contents")
    class GetUserContentsTests {

        @Test
        @DisplayName("获取用户内容列表成功")
        void getUserContents_success() throws Exception {
            Long userId = 100L;

            ContentListVO item = new ContentListVO();
            item.setId(5L);
            item.setUserId(userId);
            item.setTitle("User's Post");

            PageResult<ContentListVO> pageResult = PageResult.of(
                    Collections.singletonList(item), 1, 1, 10);

            when(contentService.getUserContents(eq(userId), any(ContentQueryRequest.class)))
                    .thenReturn(pageResult);

            mockMvc.perform(get("/api/contents/users/{userId}/contents", userId)
                            .param("page", "1")
                            .param("pageSize", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].id").value(5));

            verify(contentService).getUserContents(eq(userId), any(ContentQueryRequest.class));
        }
    }
}
