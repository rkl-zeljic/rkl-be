package com.rkl.backend.mapper

fun interface Mapper<S, T> {
    fun map(source: S): T
}