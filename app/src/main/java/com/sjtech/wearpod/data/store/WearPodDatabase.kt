package com.sjtech.wearpod.data.store

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val description: String,
    val feedUrl: String,
    val artworkUrl: String?,
    val importedAtEpochMillis: Long,
    val refreshedAtEpochMillis: Long,
    val lastRefreshError: String?,
)

@Entity(tableName = "episodes")
data class EpisodeEntity(
    @PrimaryKey val id: String,
    val subscriptionId: String,
    val guid: String,
    val title: String,
    val description: String,
    val audioUrl: String,
    val artworkUrl: String?,
    val publishedAtEpochMillis: Long?,
    val durationSeconds: Int?,
    val sizeBytes: Long?,
    val downloadState: String,
    val downloadedFilePath: String?,
    val downloadedBytes: Long,
    val lastPlayedPositionMs: Long,
    val lastPlayedAtEpochMillis: Long?,
    val isCompleted: Boolean,
)

@Entity(tableName = "favorite_subscriptions")
data class FavoriteSubscriptionEntity(
    @PrimaryKey val subscriptionId: String,
)

@Dao
interface WearPodDao {
    @Query("SELECT * FROM subscriptions ORDER BY title COLLATE NOCASE ASC")
    suspend fun subscriptions(): List<SubscriptionEntity>

    @Query("SELECT * FROM episodes")
    suspend fun episodes(): List<EpisodeEntity>

    @Query("SELECT subscriptionId FROM favorite_subscriptions")
    suspend fun favoriteSubscriptionIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscriptions(items: List<SubscriptionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpisodes(items: List<EpisodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavoriteSubscriptions(items: List<FavoriteSubscriptionEntity>)

    @Query("DELETE FROM subscriptions")
    suspend fun clearSubscriptions()

    @Query("DELETE FROM episodes")
    suspend fun clearEpisodes()

    @Query("DELETE FROM favorite_subscriptions")
    suspend fun clearFavoriteSubscriptions()
}

@Database(
    entities = [
        SubscriptionEntity::class,
        EpisodeEntity::class,
        FavoriteSubscriptionEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class WearPodDatabase : RoomDatabase() {
    abstract fun dao(): WearPodDao
}
