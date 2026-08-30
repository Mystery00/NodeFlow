package app.mystery0.nodeflow.di

import app.mystery0.nodeflow.core.common.dispatcherModule
import app.mystery0.nodeflow.core.database.databaseModule
import app.mystery0.nodeflow.core.datastore.dataStoreModule
import app.mystery0.nodeflow.core.network.networkModule
import app.mystery0.nodeflow.core.parser.parserModule
import app.mystery0.nodeflow.core.security.securityModule
import app.mystery0.nodeflow.data.dataSourceModule
import app.mystery0.nodeflow.data.repositoryModule
import app.mystery0.nodeflow.domain.domainModule
import app.mystery0.nodeflow.feature.featureModule

val nodeFlowModules = listOf(
    dispatcherModule,
    dataStoreModule,
    securityModule,
    databaseModule,
    networkModule,
    parserModule,
    dataSourceModule,
    repositoryModule,
    domainModule,
    featureModule,
)
