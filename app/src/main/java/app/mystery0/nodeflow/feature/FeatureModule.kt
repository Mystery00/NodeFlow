package app.mystery0.nodeflow.feature

import app.mystery0.nodeflow.AppViewModel
import app.mystery0.nodeflow.feature.account.AccountViewModel
import app.mystery0.nodeflow.feature.auth.AuthViewModel
import app.mystery0.nodeflow.feature.editor.EditorViewModel
import app.mystery0.nodeflow.feature.home.HomeViewModel
import app.mystery0.nodeflow.feature.node.NodeListViewModel
import app.mystery0.nodeflow.feature.node.NodeViewModel
import app.mystery0.nodeflow.feature.notification.NotificationViewModel
import app.mystery0.nodeflow.feature.profile.ProfileViewModel
import app.mystery0.nodeflow.feature.settings.SettingsViewModel
import app.mystery0.nodeflow.feature.topicdetail.TopicDetailViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val featureModule = module {
    viewModel {
        AppViewModel(get())
    }

    viewModel {
        HomeViewModel(get(), get(), get())
    }

    viewModel {
        NodeViewModel(get(), get(), get(), get(), get())
    }

    viewModel {
        NodeListViewModel(get())
    }

    viewModel {
        TopicDetailViewModel(get(), get())
    }

    viewModel {
        ProfileViewModel(get(), get(), get())
    }

    viewModel {
        SettingsViewModel(get(), get(), get())
    }

    viewModel {
        AccountViewModel(get(), get(), get(), get())
    }

    viewModel {
        AuthViewModel(get())
    }

    viewModel {
        NotificationViewModel(get())
    }

    viewModel {
        EditorViewModel()
    }
}
