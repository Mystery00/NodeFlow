package app.mystery0.nodeflow.core.database

import android.content.Context
import androidx.room.Room
import app.mystery0.nodeflow.core.database.dao.NodeDao
import app.mystery0.nodeflow.core.database.dao.TopicDao
import app.mystery0.nodeflow.core.database.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NodeFlowDatabase =
        Room.databaseBuilder(
            context,
            NodeFlowDatabase::class.java,
            "nodeflow.db",
        ).fallbackToDestructiveMigration(false).build()

    @Provides
    fun provideTopicDao(database: NodeFlowDatabase): TopicDao = database.topicDao()

    @Provides
    fun provideNodeDao(database: NodeFlowDatabase): NodeDao = database.nodeDao()

    @Provides
    fun provideUserDao(database: NodeFlowDatabase): UserDao = database.userDao()
}
