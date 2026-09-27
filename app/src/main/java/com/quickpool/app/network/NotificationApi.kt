package com.quickpool.app.network

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

interface NotificationApi {

    @GET("api/v1/notifications")
    suspend fun list(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = DEFAULT_PAGE_SIZE
    ): Response<PageResponse<NotificationDto>>

    @GET("api/v1/notifications/unread-count")
    suspend fun unreadCount(): Response<UnreadCountDto>

    @PUT("api/v1/notifications/{id}/read")
    suspend fun markRead(@Path("id") id: String): Response<Unit>

    @PUT("api/v1/notifications/read-all")
    suspend fun markAllRead(): Response<Unit>
}
