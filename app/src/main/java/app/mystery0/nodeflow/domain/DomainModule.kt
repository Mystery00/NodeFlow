package app.mystery0.nodeflow.domain

import app.mystery0.nodeflow.domain.account.GetAccountOverviewUseCase
import app.mystery0.nodeflow.domain.account.CheckInUseCase
import app.mystery0.nodeflow.domain.auth.ObserveAuthSessionUseCase
import app.mystery0.nodeflow.domain.auth.SaveAuthSessionUseCase
import app.mystery0.nodeflow.domain.node.GetNodeTopicsUseCase
import app.mystery0.nodeflow.domain.node.GetNodeTopicsPagingUseCase
import app.mystery0.nodeflow.domain.node.GetNodeUseCase
import app.mystery0.nodeflow.domain.node.GetNodePlanesUseCase
import app.mystery0.nodeflow.domain.settings.ClearCacheUseCase
import app.mystery0.nodeflow.domain.settings.ObserveSettingsUseCase
import app.mystery0.nodeflow.domain.settings.UpdateSettingsUseCase
import app.mystery0.nodeflow.domain.topic.GetLatestTopicsUseCase
import app.mystery0.nodeflow.domain.topic.GetLatestTopicsPagingUseCase
import app.mystery0.nodeflow.domain.topic.GetTopicDetailUseCase
import app.mystery0.nodeflow.domain.user.GetUserProfileUseCase
import app.mystery0.nodeflow.domain.user.GetUserRecentActivityUseCase
import org.koin.dsl.module

val domainModule = module {
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
}
