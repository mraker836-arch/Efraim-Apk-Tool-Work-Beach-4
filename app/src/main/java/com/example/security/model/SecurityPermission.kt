package com.example.security.model

import com.example.diagnostics.model.UserRole

enum class SecurityPermission {
    SECURITY_READ,
    SECURITY_SCAN,
    SECURITY_EXPORT,
    SECURITY_REVIEW,
    SECURITY_REMEDIATE,
    SECURITY_ACCEPT_RISK
}

object RoleSecurityPolicy {
    fun getPermissionsForRole(role: UserRole): Set<SecurityPermission> {
        return when (role) {
            UserRole.OWNER -> setOf(
                SecurityPermission.SECURITY_READ,
                SecurityPermission.SECURITY_SCAN,
                SecurityPermission.SECURITY_EXPORT,
                SecurityPermission.SECURITY_REVIEW,
                SecurityPermission.SECURITY_REMEDIATE,
                SecurityPermission.SECURITY_ACCEPT_RISK
            )
            UserRole.EDITOR -> setOf(
                SecurityPermission.SECURITY_READ,
                SecurityPermission.SECURITY_SCAN,
                SecurityPermission.SECURITY_EXPORT,
                SecurityPermission.SECURITY_REVIEW
            )
            UserRole.VIEWER -> setOf(
                SecurityPermission.SECURITY_READ
            )
        }
    }

    fun hasPermission(role: UserRole, permission: SecurityPermission): Boolean {
        return getPermissionsForRole(role).contains(permission)
    }
}
