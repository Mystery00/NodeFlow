package app.mystery0.nodeflow.domain

import app.mystery0.nodeflow.domain.account.GetAccountOverviewUseCase
import app.mystery0.nodeflow.domain.account.CheckInUseCase
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.auth.SaveAuthSessionUseCase
import app.mystery0.nodeflow.domain.membertag.ObserveMemberTagSyncedAtUseCase
import app.mystery0.nodeflow.domain.membertag.ObserveMemberTagsUseCase
import app.mystery0.nodeflow.domain.membertag.RefreshMemberTagsUseCase
import app.mystery0.nodeflow.domain.membertag.UpdateMemberTagsForUserUseCase
import app.mystery0.nodeflow.domain.node.GetNodeTopicsUseCase
import app.mystery0.nodeflow.domain.node.GetNodeTopicsPagingUseCase
import app.mystery0.nodeflow.domain.node.GetNodeUseCase
import app.mystery0.nodeflow.domain.node.GetNodePlanesUseCase
import app.mystery0.nodeflow.domain.node.BlockNodeUseCase
import app.mystery0.nodeflow.domain.settings.ClearCacheUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.UpdateSettingsUseCase
import app.mystery0.nodeflow.domain.topic.GetLatestTopicsUseCase
import app.mystery0.nodeflow.domain.topic.GetFavoriteTopicsPagingUseCase
import app.mystery0.nodeflow.domain.topic.GetLatestTopicsPagingUseCase
import app.mystery0.nodeflow.domain.topic.GetTopicDetailUseCase
import app.mystery0.nodeflow.domain.topic.SetFavoriteUseCase
import app.mystery0.nodeflow.domain.topic.PrepareImageShareUseCase
import app.mystery0.nodeflow.domain.topic.ThankTopicUseCase
import app.mystery0.nodeflow.domain.topic.ThankReplyUseCase
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import app.mystery0.nodeflow.domain.user.GetUserRecentActivityUseCase
import app.mystery0.nodeflow.domain.reply.AddReplyDraftImageUseCase
import app.mystery0.nodeflow.domain.reply.ClearReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.CreateReplyUseCase
import app.mystery0.nodeflow.domain.reply.GetReplyConstraintsUseCase
import app.mystery0.nodeflow.domain.reply.LoadReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.SaveReplyDraftUseCase
import app.mystery0.nodeflow.domain.reply.UploadImageUseCase
import org.koin.dsl.module

val domainModule = module {
    factory { GetFavoriteTopicsPagingUseCase(get()) }

    factory {
        GetLatestTopicsUseCase(get())
    }

    factory {
        GetLatestTopicsPagingUseCase(get())
    }

    factory {
        GetTopicDetailUseCase(get())
    }

    factory {
        SetFavoriteUseCase(get())
    }

    factory { PrepareImageShareUseCase(get()) }

    factory { ThankTopicUseCase(get()) }
    factory { ThankReplyUseCase(get()) }

    factory {
        GetNodeUseCase(get())
    }

    factory {
        GetNodePlanesUseCase(get())
    }

    factory {
        GetNodeTopicsUseCase(get())
    }

    factory {
        GetNodeTopicsPagingUseCase(get())
    }

    factory {
        BlockNodeUseCase(get())
    }

    factory {
        GetUserProfileUseCase(get())
    }

    factory {
        GetUserRecentActivityUseCase(get())
    }

    factory {
        GetAccountOverviewUseCase(get())
    }

    factory {
        CheckInUseCase(get())
    }

    factory {
        ObserveSettingsUseCase(get())
    }

    factory {
        UpdateSettingsUseCase(get())
    }

    factory {
        ClearCacheUseCase(get())
    }

    factory {
        ObserveAuthSessionUseCase(get())
    }

    factory {
        SaveAuthSessionUseCase(get())
    }

    factory {
        ObserveMemberTagsUseCase(get())
    }

    factory {
        ObserveMemberTagSyncedAtUseCase(get())
    }

    factory {
        RefreshMemberTagsUseCase(get())
    }

    factory {
        UpdateMemberTagsForUserUseCase(get())
    }

    factory { GetReplyConstraintsUseCase(get()) }
    factory { CreateReplyUseCase(get()) }
    factory { UploadImageUseCase(get()) }
    factory { LoadReplyDraftUseCase(get()) }
    factory { SaveReplyDraftUseCase(get()) }
    factory { AddReplyDraftImageUseCase(get()) }
    factory { ClearReplyDraftUseCase(get()) }
}
