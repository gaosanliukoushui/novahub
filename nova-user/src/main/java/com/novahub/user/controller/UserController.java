package com.novahub.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.novahub.common.result.PageResult;
import com.novahub.common.result.Result;
import com.novahub.common.utils.SecurityUtils;
import com.novahub.user.dto.UpdateUserRequest;
import com.novahub.user.service.IFollowService;
import com.novahub.user.service.IUserService;
import com.novahub.user.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;
    private final IFollowService followService;

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/me")
    public Result<UserVO> getCurrentUser() {
        Long userId = SecurityUtils.requireUserId();
        return Result.ok(userService.getCurrentUser(userId));
    }

    @Operation(summary = "获取用户详情")
    @GetMapping("/{id}")
    public Result<UserVO> getUserById(@PathVariable Long id) {
        return Result.ok(userService.getUserById(id));
    }

    @Operation(summary = "更新当前用户信息")
    @PutMapping("/me")
    public Result<UserVO> updateUser(@Valid @RequestBody UpdateUserRequest request) {
        Long userId = SecurityUtils.requireUserId();
        return Result.ok(userService.updateUser(userId, request));
    }

    @Operation(summary = "关注用户")
    @PostMapping("/{id}/follow")
    public Result<Void> follow(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        followService.follow(userId, id);
        return Result.ok();
    }

    @Operation(summary = "取消关注")
    @DeleteMapping("/{id}/follow")
    public Result<Void> unfollow(@PathVariable Long id) {
        Long userId = SecurityUtils.requireUserId();
        followService.unfollow(userId, id);
        return Result.ok();
    }

    @Operation(summary = "获取用户粉丝列表")
    @GetMapping("/{id}/followers")
    public Result<PageResult<UserVO>> getFollowers(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        IPage<UserVO> followers = followService.getFollowers(id, page, pageSize);
        return Result.ok(PageResult.of(
                followers.getRecords(),
                followers.getTotal(),
                followers.getCurrent(),
                followers.getSize()
        ));
    }

    @Operation(summary = "获取用户关注列表")
    @GetMapping("/{id}/followings")
    public Result<PageResult<UserVO>> getFollowings(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        IPage<UserVO> followings = followService.getFollowings(id, page, pageSize);
        return Result.ok(PageResult.of(
                followings.getRecords(),
                followings.getTotal(),
                followings.getCurrent(),
                followings.getSize()
        ));
    }
}
