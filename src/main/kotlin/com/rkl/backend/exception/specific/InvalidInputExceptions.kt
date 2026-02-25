package com.rkl.backend.exception.specific

import com.rkl.backend.exception.ErrorCode
import com.rkl.backend.exception.base.InvalidInput

class NotSuperAdminException() : InvalidInput(
    ErrorCode.NOT_ADMIN,
    "Only Super Admin can perform this action"
)
