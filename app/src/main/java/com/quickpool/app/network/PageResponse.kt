package com.quickpool.app.network

/** Mirrors the backend's PageResponseDto — one page of a list that can grow without bound
 *  (notifications, ride history, booking requests), plus whether another page exists. */
data class PageResponse<T>(val content: List<T>, val hasNext: Boolean)

/** First-page default matching @PageableDefault(size = 20) on the backend. */
const val DEFAULT_PAGE_SIZE = 20
