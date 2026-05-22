package com.novahub.user.service;

import java.util.Set;

public interface IPermissionService {

    Set<String> getUserPermissions(Long userId);

    Set<String> getUserRoles(Long userId);

    boolean hasPermission(Long userId, String permissionCode);

    boolean hasRole(Long userId, String roleCode);
}
