package com.novahub.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.content.client.UserClient;
import com.novahub.search.dto.NovaSearchRequest;
import com.novahub.search.service.IndexService;
import com.novahub.search.vo.SearchResultPageVO;
import com.novahub.search.vo.SearchResultVO;
import com.novahub.user.entity.SysUser;
import com.novahub.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ElasticsearchClient esClient;
    private final SysUserMapper userMapper;
    private final UserClient userClient;
    private final IndexService indexService;

    public static final String CONTENT_INDEX = "nova_content";

    public SearchResultPageVO<SearchResultVO> searchContent(NovaSearchRequest request) {
        String keyword = request.getKeyword();
        int page = request.getPage() != null ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
        int from = (page - 1) * pageSize;

        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();

            boolQuery.must(MultiMatchQuery.of(m -> m
                    .query(keyword)
                    .fields("title^10", "content^1", "tagNames^5", "authorNickname^2")
                    .type(TextQueryType.BestFields)
                    .fuzziness("AUTO"))._toQuery());

            boolQuery.filter(TermQuery.of(t -> t.field("status").value(2))._toQuery());

            if (request.getType() != null) {
                boolQuery.filter(TermQuery.of(t -> t.field("type").value(request.getType()))._toQuery());
            }

            if (request.getStartTime() != null || request.getEndTime() != null) {
                RangeQuery.Builder range = new RangeQuery.Builder().field("publishTime");
                if (request.getStartTime() != null) {
                    range.gte(co.elastic.clients.json.JsonData.of(request.getStartTime()));
                }
                if (request.getEndTime() != null) {
                    range.lte(co.elastic.clients.json.JsonData.of(request.getEndTime()));
                }
                boolQuery.filter(range.build()._toQuery());
            }

            Highlight highlight = Highlight.of(h -> h
                    .fields("title", HighlightField.of(f -> f
                            .preTags("<em>").postTags("</em>")
                            .fragmentSize(80).numberOfFragments(2)))
                    .fields("content", HighlightField.of(f -> f
                            .preTags("<em>").postTags("</em>")
                            .fragmentSize(150).numberOfFragments(3)))
                    .fields("tagNames", HighlightField.of(f -> f
                            .preTags("<em>").postTags("</em>"))));

            SearchRequest.Builder searchReq = new SearchRequest.Builder()
                    .index(CONTENT_INDEX)
                    .query(boolQuery.build()._toQuery())
                    .highlight(highlight)
                    .from(from)
                    .size(pageSize);

            String sort = request.getSort();
            if ("createTime".equals(sort) || "publishTime".equals(sort)) {
                searchReq.sort(s -> s.field(f -> f.field("publishTime").order(SortOrder.Desc)));
            } else if ("likeCount".equals(sort)) {
                searchReq.sort(s -> s.field(f -> f.field("likeCount").order(SortOrder.Desc)));
            } else if ("hot".equals(sort)) {
                searchReq.sort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
                searchReq.sort(s -> s.field(f -> f.field("likeCount").order(SortOrder.Desc)));
            } else {
                searchReq.sort(s -> s.score(sc -> sc.order(SortOrder.Desc)));
            }

            SearchResponse<Map> response = esClient.search(searchReq.build(), Map.class);

            List<SearchResultVO> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;

                SearchResultVO vo = buildSearchResultVO(hit, source);
                results.add(vo);
            }

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            log.info("搜索完成: keyword={}, total={}, page={}", keyword, total, page);

            return SearchResultPageVO.of(results, total, page, pageSize);

        } catch (IOException e) {
            log.error("ES搜索失败: keyword={}, error={}", keyword, e.getMessage(), e);
            return SearchResultPageVO.of(Collections.emptyList(), 0, page, pageSize);
        }
    }

    public SearchResultPageVO<SearchResultVO> searchByTag(String tagName, Integer page, Integer pageSize) {
        int p = page != null ? page : 1;
        int ps = pageSize != null ? pageSize : 20;
        int from = (p - 1) * ps;

        try {
            BoolQuery.Builder boolQuery = new BoolQuery.Builder();
            boolQuery.filter(TermQuery.of(t -> t.field("status").value(2))._toQuery());
            boolQuery.must(TermQuery.of(t -> t.field("tags").value(tagName))._toQuery());

            SearchRequest request = SearchRequest.of(s -> s
                    .index(CONTENT_INDEX)
                    .query(boolQuery.build()._toQuery())
                    .sort(so -> so.field(f -> f.field("publishTime").order(SortOrder.Desc)))
                    .from(from)
                    .size(ps));

            SearchResponse<Map> response = esClient.search(request, Map.class);

            List<SearchResultVO> results = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source == null) continue;
                SearchResultVO vo = buildSearchResultVO(hit, source);
                results.add(vo);
            }

            long total = response.hits().total() != null ? response.hits().total().value() : 0;
            return SearchResultPageVO.of(results, total, p, ps);

        } catch (IOException e) {
            log.error("标签搜索失败: tagName={}, error={}", tagName, e.getMessage(), e);
            return SearchResultPageVO.of(Collections.emptyList(), 0, p, ps);
        }
    }

    public SearchResultPageVO<Map> searchUsers(String keyword, Integer page, Integer pageSize) {
        int p = page != null ? page : 1;
        int ps = pageSize != null ? pageSize : 20;

        com.baomidou.mybatisplus.core.metadata.IPage<SysUser> userPage =
                userMapper.selectPage(
                        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(p, ps),
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                                .eq(SysUser::getIsDeleted, 0)
                                .and(w -> w
                                        .like(SysUser::getUsername, keyword)
                                        .or()
                                        .like(SysUser::getNickname, keyword))
                );

        List<Map> records = userPage.getRecords().stream().map(u -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            m.put("avatar", u.getAvatar());
            m.put("bio", u.getBio());
            m.put("followCount", u.getFollowCount());
            m.put("fansCount", u.getFansCount());
            m.put("createTime", u.getCreateTime() != null ? u.getCreateTime().toString() : null);
            return m;
        }).collect(Collectors.toList());

        return SearchResultPageVO.of(records, userPage.getTotal(), p, ps);
    }

    public List<String> searchSuggest(String prefix, int limit) {
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(CONTENT_INDEX)
                    .query(PrefixQuery.of(p -> p
                            .field("title.keyword")
                            .value(prefix))._toQuery())
                    .size(limit)
                    .source(src -> src.filter(f -> f.includes("title"))));

            SearchResponse<Map> response = esClient.search(request, Map.class);

            return response.hits().hits().stream()
                    .map(hit -> {
                        Map<String, Object> source = hit.source();
                        if (source == null) return null;
                        Object title = source.get("title");
                        return title != null ? title.toString() : null;
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("搜索建议失败: prefix={}, error={}", prefix, e.getMessage());
            return Collections.emptyList();
        }
    }

    private SearchResultVO buildSearchResultVO(Hit<Map> hit, Map<String, Object> source) {
        Map<String, List<String>> highlights = hit.highlight();

        String titleHighlight = null;
        String contentHighlight = null;
        List<String> tagHighlights = null;

        if (highlights != null) {
            if (highlights.containsKey("title")) {
                titleHighlight = String.join("...", highlights.get("title"));
            }
            if (highlights.containsKey("content")) {
                contentHighlight = String.join("...", highlights.get("content"));
            }
            if (highlights.containsKey("tagNames")) {
                tagHighlights = highlights.get("tagNames");
            }
        }

        Object typeObj = source.get("type");
        Integer type = typeObj instanceof Number ? ((Number) typeObj).intValue() : null;

        Object likeCountObj = source.get("likeCount");
        Integer likeCount = likeCountObj instanceof Number ? ((Number) likeCountObj).intValue() : 0;

        Object commentCountObj = source.get("commentCount");
        Integer commentCount = commentCountObj instanceof Number ? ((Number) commentCountObj).intValue() : 0;

        Object viewCountObj = source.get("viewCount");
        Integer viewCount = viewCountObj instanceof Number ? ((Number) viewCountObj).intValue() : 0;

        Object userIdObj = source.get("userId");
        Long userId = userIdObj != null ? Long.parseLong(userIdObj.toString()) : null;

        UserClient.UserInfo userInfo = userClient.getUserInfo(userId);

        Object publishTimeObj = source.get("publishTime");
        Long publishTime = publishTimeObj instanceof Number ? ((Number) publishTimeObj).longValue() : null;

        @SuppressWarnings("unchecked")
        List<String> tagNames = source.get("tagNames") instanceof List
                ? (List<String>) source.get("tagNames")
                : null;

        return SearchResultVO.builder()
                .id(hit.id() != null ? Long.parseLong(hit.id()) : null)
                .userId(userId)
                .authorNickname(userInfo != null ? userInfo.getNickname() : null)
                .authorAvatar(userInfo != null ? userInfo.getAvatar() : null)
                .type(type)
                .title(titleHighlight != null ? titleHighlight : (String) source.get("title"))
                .titleHighlight(titleHighlight)
                .content(contentHighlight != null ? contentHighlight : truncateContent((String) source.get("content")))
                .contentHighlight(contentHighlight)
                .tags(tagNames)
                .tagHighlights(tagHighlights)
                .coverUrl((String) source.get("coverUrl"))
                .likeCount(likeCount)
                .commentCount(commentCount)
                .viewCount(viewCount)
                .publishTime(publishTime)
                .score(hit.score())
                .build();
    }

    private String truncateContent(String content) {
        if (content == null) return null;
        return content.length() > 200 ? content.substring(0, 200) + "..." : content;
    }
}
